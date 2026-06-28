package com.infraspine.service;

import com.infraspine.domain.Models.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IncidentService {
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
            if (volume != null && usagePercent(volume) > 85) {
                incidents.add(new Incident("pvc-usage-" + pvc.id(), "PVC usage above 85%", RiskLevel.HIGH,
                        "PersistentVolumeClaim", pvc.id(), pvc.name() + " is using " + usagePercent(volume) + "% of capacity."));
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
        return incident(incidentId).map(incident -> new RemediationPlan(incident.id(), switch (incident.resourceType()) {
            case "PersistentVolumeClaim" -> List.of(
                    "Confirm current application health and recent backup status before changing storage.",
                    "Inspect PVC, filesystem, and application-level growth patterns.",
                    "Expand the PVC if supported, or migrate to an expandable StorageClass during a maintenance window.",
                    "Add alerts at 70%, 80%, and 85% usage thresholds.");
            case "StorageClassProfile" -> List.of(
                    "Review provider capabilities and validate whether snapshots and expansion are available.",
                    "Create a safer replacement StorageClass with snapshot policy and expansion enabled.",
                    "Migrate low-risk workloads first, then stateful production workloads with tested rollback steps.");
            case "Workload" -> List.of(
                    "Identify whether the workload is stateful and what consistency guarantees it needs.",
                    "Add a tested backup target and document restore objectives.",
                    "For stateful single replicas, evaluate replication, pod disruption budgets, and anti-affinity.",
                    "Run a restore rehearsal before declaring the incident remediated.");
            default -> List.of("Triage the resource, capture evidence, and prefer reversible changes.");
        }));
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
            topology.volumes().stream().filter(v -> v.storageClassId().equals(incident.resourceId())).map(StorageVolume::id).forEach(affectedVolumes::add);
        }
        topology.pvcs().stream().filter(pvc -> affectedVolumes.contains(pvc.volumeId())).map(PersistentVolumeClaim::id).forEach(affectedPvcs::add);
        topology.pvcs().stream().filter(pvc -> affectedPvcs.contains(pvc.id())).map(PersistentVolumeClaim::volumeId).forEach(affectedVolumes::add);
        topology.workloads().stream().filter(w -> w.pvcIds().stream().anyMatch(affectedPvcs::contains)).map(Workload::id).forEach(affectedWorkloads::add);

        return new BlastRadiusReport(incident.id(), incident.riskLevel(), List.copyOf(affectedWorkloads),
                List.copyOf(affectedPvcs), List.copyOf(affectedVolumes),
                "Potential impact includes " + affectedWorkloads.size() + " workload(s), " + affectedPvcs.size() + " PVC(s), and " + affectedVolumes.size() + " volume(s).");
    }

    private int usagePercent(StorageVolume volume) {
        return Math.toIntExact((volume.usedGiB() * 100) / volume.capacityGiB());
    }
}
