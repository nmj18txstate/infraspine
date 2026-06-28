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
}
