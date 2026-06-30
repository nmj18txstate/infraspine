package com.infraspine.service;

import com.infraspine.domain.Models.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IncidentService {
    private static final double PVC_USAGE_HIGH_RISK_THRESHOLD_PERCENT = 85.0;

    private final TopologyRepository repository;

    public IncidentService(TopologyRepository repository) {
        this.repository = repository;
    }

    public List<Incident> incidents() {
        var topology = repository.topology();
        var volumes = topology.volumes().stream().collect(Collectors.toMap(StorageVolume::id, Function.identity()));
        var incidents = new ArrayList<Incident>();

        for (var pvc : topology.pvcs()) {
            var volume = volumes.get(pvc.volumeId());
            if (volume != null && isUsageAboveThreshold(volume)) {
                incidents.add(new Incident("pvc-usage-" + pvc.id(), "PVC usage above 85%", RiskLevel.HIGH,
                        "PersistentVolumeClaim", pvc.id(), pvc.name() + " is using " + formattedUsagePercent(volume) + "% of capacity."));
            }
        }
        for (var storageClass : topology.storageClasses()) {
            if (!storageClass.hasSnapshotPolicy()) {
                incidents.add(new Incident("snapshot-policy-" + storageClass.id(), "Missing snapshot policy", RiskLevel.MEDIUM,
                        "StorageClassProfile", storageClass.id(), storageClass.name() + " has no snapshot policy."));
            }
            if (!storageClass.supportsExpansion()) {
                incidents.add(new Incident("expansion-" + storageClass.id(), "StorageClass expansion disabled", RiskLevel.MEDIUM,
                        "StorageClassProfile", storageClass.id(), storageClass.name() + " does not support volume expansion."));
            }
        }
        for (var workload : topology.workloads()) {
            if ("StatefulSet".equalsIgnoreCase(workload.kind()) && workload.replicas() == 1) {
                incidents.add(new Incident("single-replica-" + workload.id(), "Single-replica stateful workload", RiskLevel.HIGH,
                        "Workload", workload.id(), workload.name() + " has one replica."));
            }
            if (workload.backupTarget() == null || workload.backupTarget().isBlank()) {
                incidents.add(new Incident("backup-target-" + workload.id(), "Workload missing backup target", RiskLevel.HIGH,
                        "Workload", workload.id(), workload.name() + " has no backup target."));
            }
        }
        return List.copyOf(incidents);
    }

    public Optional<Incident> incident(String id) {
        return incidents().stream().filter(incident -> incident.id().equals(id)).findFirst();
    }

    public Optional<BlastRadiusReport> blastRadius(String incidentId) {
        return incident(incidentId).map(this::toBlastRadius);
    }

    public Optional<RemediationPlan> remediationPlan(String incidentId) {
        return incident(incidentId).map(incident -> {
            List<RemediationStep> steps;
            boolean requiresDowntime;
            switch (incident.resourceType()) {
                case "PersistentVolumeClaim" -> {
                    steps = List.of(
                            new RemediationStep(1, "Confirm current application health and recent backup status before changing storage.", true, "None required for verification step."),
                            new RemediationStep(2, "Inspect PVC, filesystem, and application-level growth patterns.", true, "None required."),
                            new RemediationStep(3, "Expand the PVC if supported, or migrate to an expandable StorageClass during a maintenance window.", false, "PVC storage block allocations cannot be shrunk or reversed automatically; requires a manual downscale data migration workflow."),
                            new RemediationStep(4, "Add alerts at 70%, 80%, and 85% usage thresholds.", true, "Remove alert rules from monitoring configuration."));
                    requiresDowntime = true; //Migration adjustments require a maintenance window
                }
                case "StorageClassProfile" -> {
                    steps = List.of(
                            new RemediationStep(1, "Review provider capabilities and validate whether snapshots and expansion are available.", true, "None required."),
                            new RemediationStep(2, "Create a safer replacement StorageClass with snapshot policy and expansion enabled.", true, "Delete standby StorageClass manifest configuration."),
                            new RemediationStep(3, "Migrate low-risk workloads first, then stateful production workloads with tested rollback steps.", false, "Fail back to original storage configurations using pre-migration block backups."));
                    requiresDowntime = true; //Storage class migration paths for active workloads require maintenance windows
                }
                case "Workload" -> {
                    steps = List.of(
                            new RemediationStep(1, "Identify whether the workload is stateful and what consistency guarantees it needs.", true, "None required."),
                            new RemediationStep(2, "Add a tested backup target and document restore objectives.", true, "Deregister backup configuration targets."),
                            new RemediationStep(3, "For stateful single replicas, evaluate replication, pod disruption budgets, and anti-affinity.", true, "Revert template state back to single replica status."),
                            new RemediationStep(4, "Run a restore rehearsal before declaring the incident remediated.", true, "Tear down temporary verification staging namespace environments."));
                    requiresDowntime = false; //Strategy adjustments can be scheduled as standard rolling infrastructure deployments
                }
                default -> {
                    steps = List.of(
                            new RemediationStep(1, "Triage the resource, capture evidence, and prefer reversible changes.", true, "Rollback strategy dependent on triaged resource context."));
                    requiresDowntime = false;
                }
            }
                    return new RemediationPlan(incident.id(), steps, requiresDowntime);
        });
    }

    private BlastRadiusReport toBlastRadius(Incident incident) {
        var topology = repository.topology();
        var affectedPvcs = new LinkedHashSet<String>();
        var affectedVolumes = new LinkedHashSet<String>();
        var affectedWorkloads = new LinkedHashSet<String>();

        if (incident.resourceType().equals("PersistentVolumeClaim")) affectedPvcs.add(incident.resourceId());
        if (incident.resourceType().equals("Workload")) {
            affectedWorkloads.add(incident.resourceId());
            topology.workloads().stream()
                    .filter(workload -> workload.id().equals(incident.resourceId()))
                    .flatMap(workload -> workload.pvcIds().stream())
                    .forEach(affectedPvcs::add);
        }
        if (incident.resourceType().equals("StorageClassProfile")) {
            topology.volumes().stream()
                    .filter(volume -> volume.storageClassId().equals(incident.resourceId()))
                    .map(StorageVolume::id)
                    .forEach(affectedVolumes::add);
        }
        topology.pvcs().stream()
                .filter(pvc -> affectedVolumes.contains(pvc.volumeId()))
                .map(PersistentVolumeClaim::id)
                .forEach(affectedPvcs::add);
        topology.pvcs().stream()
                .filter(pvc -> affectedPvcs.contains(pvc.id()))
                .map(PersistentVolumeClaim::volumeId)
                .forEach(affectedVolumes::add);
        topology.workloads().stream()
                .filter(workload -> workload.pvcIds().stream().anyMatch(affectedPvcs::contains))
                .map(Workload::id)
                .forEach(affectedWorkloads::add);

        return new BlastRadiusReport(incident.id(), incident.riskLevel(), List.copyOf(affectedWorkloads),
                List.copyOf(affectedPvcs), List.copyOf(affectedVolumes),
                "Potential impact includes " + affectedWorkloads.size() + " workload(s), " + affectedPvcs.size() + " PVC(s), and " + affectedVolumes.size() + " volume(s).");
    }

    private boolean isUsageAboveThreshold(StorageVolume volume) {
        return volume.capacityGiB() > 0
        && usagePercent(volume) > PVC_USAGE_HIGH_RISK_THRESHOLD_PERCENT;
    }

    private double usagePercent(StorageVolume volume) {
        if(volume.capacityGiB() <= 0) {
            return 0.0;
        }
        return (volume.usedGiB() * 100.0) / volume.capacityGiB();
    }

    private String formattedUsagePercent(StorageVolume volume){
        return String.format(Locale.ROOT, "%.1f", usagePercent(volume));
    }
}