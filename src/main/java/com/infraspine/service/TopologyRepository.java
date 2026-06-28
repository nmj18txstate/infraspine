package com.infraspine.service;

import com.infraspine.domain.Models.*;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TopologyRepository {
    private final Topology topology;

    public TopologyRepository() {
        var nodes = List.of(
                new Node("node-a", "worker-a", "us-east-1a", 16, 64),
                new Node("node-b", "worker-b", "us-east-1b", 16, 64));
        var cluster = new Cluster("cluster-1", "lab-primary", "us-east-1", nodes);
        var classes = List.of(
                new StorageClassProfile("sc-fast", "fast-ssd", true, true, "zonal-replicated"),
                new StorageClassProfile("sc-legacy", "legacy-hdd", false, false, "single-zone"));
        var volumes = List.of(
                new StorageVolume("vol-orders", "orders-data", "sc-legacy", 1000, 859, "node-a"),
                new StorageVolume("vol-catalog", "catalog-cache", "sc-fast", 50, 20, "node-b"));
        var pvcs = List.of(
                new PersistentVolumeClaim("pvc-orders", "orders-db-data", "commerce", "vol-orders", 100),
                new PersistentVolumeClaim("pvc-catalog", "catalog-cache", "commerce", "vol-catalog", 50));
        var workloads = List.of(
                new Workload("wl-orders", "orders-db", "commerce", "StatefulSet", 1, List.of("pvc-orders"), ""),
                new Workload("wl-catalog", "catalog-api", "commerce", "Deployment", 3, List.of("pvc-catalog"), "s3://infraspine-backups/catalog"));
        this.topology = new Topology(cluster, classes, volumes, pvcs, workloads);
    }

    public Topology topology() { return topology; }
}
