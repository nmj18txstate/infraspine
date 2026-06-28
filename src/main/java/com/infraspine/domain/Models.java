package com.infraspine.domain;

import java.util.List;

public final class Models {
    private Models() {}

    public enum RiskLevel { MEDIUM, HIGH }

    public record Cluster(String id, String name, String region, List<Node> nodes) {}
    public record Node(String id, String name, String zone, int cpuCores, long memoryGiB) {}
    public record StorageClassProfile(String id, String name, boolean supportsExpansion, boolean hasSnapshotPolicy, String replicationMode) {}
    public record StorageVolume(String id, String name, String storageClassId, long capacityGiB, long usedGiB, String nodeId) {}
    public record PersistentVolumeClaim(String id, String name, String namespace, String volumeId, long requestedGiB) {}
    public record Workload(String id, String name, String namespace, String kind, int replicas, List<String> pvcIds, String backupTarget) {}
    public record Incident(String id, String title, RiskLevel riskLevel, String resourceType, String resourceId, String reason) {}
    public record Topology(Cluster cluster, List<StorageClassProfile> storageClasses, List<StorageVolume> volumes,
                           List<PersistentVolumeClaim> pvcs, List<Workload> workloads) {}
    public record BlastRadiusReport(String incidentId, RiskLevel riskLevel, List<String> affectedWorkloads,
                                    List<String> affectedPvcs, List<String> affectedVolumes, String summary) {}
    public record RemediationPlan(String incidentId, List<String> steps) {}
}
