# Kubernetes Persistent Storage

## Short description
Guide coding agents that review or implement Kubernetes persistent-storage behavior for JVM/Spring workloads.

## When to use
Use this skill when code, manifests, Helm charts, operators, or documentation touch StorageClass, PersistentVolume, PersistentVolumeClaim, StatefulSet, snapshots, expansion, topology, backup, or restore concerns.

## What the AI coding agent should check
- PVC capacity and utilization thresholds.
- StorageClass expansion support and snapshot policy availability.
- Stateful workload replica count and anti-affinity assumptions.
- Backup target presence and restore rehearsal documentation.
- Zone, node, and volume placement that could increase blast radius.

## JVM/Spring-specific guidance
- Surface persistent-storage dependencies in Spring configuration and health documentation.
- Keep Kubernetes storage concepts mapped to clear JVM domain types.
- Test rule engines without requiring a live Kubernetes cluster.
- Return educational remediation plans rather than unsafe automatic mutations.
- Prefer explicit runbooks for StatefulSet storage changes.

## Examples
- Detect a `StatefulSet` with one replica and mark it HIGH risk.
- Detect a StorageClass without volume expansion and mark it MEDIUM risk.
- Explain which workloads, PVCs, and volumes are impacted by a risky StorageClass.

## Anti-patterns
- Assuming PVC expansion is always enabled.
- Resizing storage without checking filesystem and application behavior.
- Ignoring snapshot policy gaps because a workload has multiple replicas.
- Recommending destructive volume replacement before testing restore paths.

## Verification checklist
- Tests cover PVC usage, snapshot, expansion, replica, and backup rules.
- API responses identify affected workloads, PVCs, and volumes.
- Remediation text is safe, staged, and educational.
- Documentation maps Kubernetes storage concepts to JVM/Spring code.

## How InfraSpine demonstrates the skill
InfraSpine seeds an in-memory Kubernetes-like topology with StorageClass profiles, volumes, PVCs, and workloads. It computes incidents, blast radius, and remediation plans through a Spring Boot backend so agents can practice storage-risk analysis without needing cluster credentials.
