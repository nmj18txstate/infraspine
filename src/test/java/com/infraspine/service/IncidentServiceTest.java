package com.infraspine.service;

import com.infraspine.domain.Models.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentServiceTest {
    private final IncidentService incidentService = new IncidentService(new TopologyRepository());

    @Test
    void createsIncidentsForStorageAndWorkloadRules() {
        var incidents = incidentService.incidents();

        assertThat(incidents).extracting("id").contains(
                "pvc-usage-pvc-orders",
                "snapshot-policy-sc-legacy",
                "expansion-sc-legacy",
                "single-replica-wl-orders",
                "backup-target-wl-orders");
        assertThat(incidents).filteredOn(i -> i.id().equals("pvc-usage-pvc-orders"))
                .singleElement().extracting("riskLevel").isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void buildsBlastRadiusFromStorageClassToDependentWorkload() {
        var report = incidentService.blastRadius("snapshot-policy-sc-legacy").orElseThrow();

        assertThat(report.affectedVolumes()).containsExactly("vol-orders");
        assertThat(report.affectedPvcs()).containsExactly("pvc-orders");
        assertThat(report.affectedWorkloads()).containsExactly("wl-orders");
    }

    @Test
    void buildsBlastRadiusFromMissingBackupWorkloadToAttachedStorage() {
        var report = incidentService.blastRadius("backup-target-wl-orders").orElseThrow();

        assertThat(report.affectedWorkloads()).containsExactly("wl-orders");
        assertThat(report.affectedPvcs()).containsExactly("pvc-orders");
        assertThat(report.affectedVolumes()).containsExactly("vol-orders");
    }

    @Test
    void buildsBlastRadiusFromSingleReplicaWorkloadToAttachedStorage() {
        var report = incidentService.blastRadius("single-replica-wl-orders").orElseThrow();

        assertThat(report.affectedWorkloads()).containsExactly("wl-orders");
        assertThat(report.affectedPvcs()).containsExactly("pvc-orders");
        assertThat(report.affectedVolumes()).containsExactly("vol-orders");
    }

    @Test
    void preservesDecimalUsageInIncidentReason() {
        var incident = incidentService.incident("pvc-usage-pvc-orders").orElseThrow();

        assertThat(incident.reason()).isEqualTo("orders-db-data is using 85.9% of capacity.");
    }

    @Test
    void generatesDowntimeRequiredRemediationPlanForPvcUsage() {
        var plan = incidentService.remediationPlan("pvc-usage-pvc-orders").orElseThrow();
        // Verify root-level structural details
        assertThat(plan.incidentId()).isEqualTo("pvc-usage-pvc-orders");
        assertThat(plan.requiresDowntime()).isTrue();
        assertThat(plan.steps()).hasSize(4);
        // Verify detailed, rich elements of the sequential steps
        var firstStep = plan.steps().get(0);
        assertThat(firstStep.sequence()).isEqualTo(1);
        assertThat(firstStep.isReversible()).isTrue();
        assertThat(firstStep.action()).contains("Confirm current application health and recent backup status");
        var irreversibleStep = plan.steps().get(2);
        assertThat(irreversibleStep.sequence()).isEqualTo(3);
        assertThat(irreversibleStep.isReversible()).isFalse();
        assertThat(irreversibleStep.rollbackStrategy()).contains("PVC storage block allocations cannot be shrunk");
    }
    @Test
    void generatesZeroDowntimeRemediationPlanForWorkloadBackupRisk() {
        var plan = incidentService.remediationPlan("backup-target-wl-orders").orElseThrow();
        // Verify strategy-based changes do not flag unnecessary platform downtime
        assertThat(plan.incidentId()).isEqualTo("backup-target-wl-orders");
        assertThat(plan.requiresDowntime()).isFalse();
        assertThat(plan.steps()).hasSize(4);
        var backupStep = plan.steps().get(1);
        assertThat(backupStep.sequence()).isEqualTo(2);
        assertThat(backupStep.isReversible()).isTrue();
        assertThat(backupStep.rollbackStrategy()).isEqualTo("Deregister backup configuration targets.");
    }

    @Test
    void buildsStructuredRemediationPlanForWorkloadRisk() {
        var plan = incidentService.remediationPlan("backup-target-wl-orders").orElseThrow();

        assertThat(plan.incidentId()).isEqualTo("backup-target-wl-orders");
        assertThat(plan.requiresDowntime()).isFalse();
        assertThat(plan.steps()).hasSize(4);
        assertThat(plan.steps().getFirst().sequence()).isEqualTo(1);
        assertThat(plan.steps().getFirst().action())
                .isEqualTo("Identify whether the workload is stateful and what consistency guarantees it needs.");
        assertThat(plan.steps().getFirst().isReversible()).isTrue();
        assertThat(plan.steps().getFirst().rollbackStrategy()).isEqualTo("None required.");
    }
}