package com.infraspine.service;

import com.infraspine.domain.Models.*;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void detectsFractionalPvcUsageAboveThreshold() {
        var service = new IncidentService(new TestTopologyRepository(new Topology(
                new Cluster("cluster-test", "test", "us-east-1", List.of()),
                List.of(new StorageClassProfile("sc-test", "test-storage", true, true, "zonal")),
                List.of(new StorageVolume("vol-fractional", "fractional-data", "sc-test", 1000, 859, "node-test")),
                List.of(new PersistentVolumeClaim("pvc-fractional", "fractional-data", "default", "vol-fractional", 1000)),
                List.of(new Workload("wl-fractional", "fractional-app", "default", "Deployment", 2, List.of("pvc-fractional"), "s3://backups/fractional")))));

        assertThat(service.incidents()).extracting(Incident::id).contains("pvc-usage-pvc-fractional");
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

    private static class TestTopologyRepository extends TopologyRepository {
        private final Topology topology;

        private TestTopologyRepository(Topology topology) {
            this.topology = topology;
        }

        @Override
        public Topology topology() {
            return topology;
        }
    }
}
