# Kubernetes Persistent Storage

## Short description

Guide coding agents that review or implement Kubernetes persistent-storage behavior for JVM/Spring workloads.

## When to use

Use this skill when code, manifests, Helm charts, operators, or documentation touch `StorageClass`, `PersistentVolume`, `PersistentVolumeClaim`, `StatefulSet`, snapshots, expansion, topology, backup, or restore concerns.

## What the AI coding agent should check

* PVC capacity and utilization thresholds.
* StorageClass expansion support and snapshot policy availability.
* Stateful workload replica count and anti-affinity assumptions.
* Backup target presence and restore rehearsal documentation.
* Zone, node, and volume placement that could increase blast radius.

## JVM/Spring-specific guidance

* Surface persistent-storage dependencies in Spring configuration and health documentation.
* Keep Kubernetes storage concepts mapped to clear JVM domain types.
* Test rule engines without requiring a live Kubernetes cluster.
* Return educational remediation plans rather than unsafe automatic mutations.
* Prefer explicit runbooks for StatefulSet storage changes.

## Examples

* Detect a `StatefulSet` with one replica and mark it `HIGH` risk.
* Detect a `StorageClass` without volume expansion and mark it `MEDIUM` risk.
* Explain which workloads, PVCs, and volumes are impacted by a risky `StorageClass`.

## Anti-patterns

* Assuming PVC expansion is always enabled.
* Resizing storage without checking filesystem and application behavior.
* Ignoring snapshot policy gaps because a workload has multiple replicas.
* Recommending destructive volume replacement before testing restore paths.
* Hardcoding filesystem paths directly in Java services.
* Writing to shared `ReadWriteMany` volumes without explicit application-level coordination.

## Enterprise failure modes agents must handle

### 1. Non-root container permissions and the `fsGroup` trap

Modern Spring Boot containers often run as non-root users, especially when built with Cloud Native Buildpacks, distroless images, or hardened base images. Kubernetes-mounted persistent volumes may be owned by `root` by default, which can cause `AccessDeniedException` during application startup or file writes.

Agents should check:

* Whether the container runs as a non-root UID/GID.
* Whether mounted volume paths are writable by the JVM process.
* Whether the pod or container `securityContext` includes appropriate `runAsUser`, `runAsGroup`, and `fsGroup` values.
* Whether `fsGroupChangePolicy: "OnRootMismatch"` should be used to avoid unnecessary recursive ownership changes on large volumes.
* Whether the underlying CSI driver delegates `fsGroup` handling through CSI mount-group support, in which case Kubernetes may not apply `fsGroupChangePolicy`.
* Whether the application writes to a path that is actually backed by the mounted volume.

Recommended pattern:

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000
  fsGroup: 1000
  fsGroupChangePolicy: "OnRootMismatch"
```

Important storage-provider guidance:

* `fsGroup` is commonly useful for block-storage-backed PVCs such as EBS-style CSI volumes.
* For large volumes with many existing files, recursive ownership changes can delay pod startup.
* For NFS/EFS-style shared filesystems, ownership behavior depends heavily on the CSI driver, access point, mount options, and server-side permissions.
* Agents must not blindly add `fsGroup` and assume the pod will start quickly or that permissions will be correct.
* Agents should inspect the `StorageClass`, CSI driver documentation, and existing pod events before recommending permission changes.

Do not assume that a mounted PVC is writable by the application user. Always verify ownership, write permissions, startup events, and storage-driver behavior.

### 2. File locking, access modes, and distributed pods

Java file I/O code that works on one pod may fail or corrupt data when the workload scales horizontally. Multiple Spring Boot pods writing to the same path can overwrite each other, create inconsistent lock behavior, or depend on storage semantics that differ between block storage, NFS, and object storage.

Agents should check:

* Whether the PVC access mode is `ReadWriteOnce`, `ReadWriteOncePod`, `ReadWriteMany`, or `ReadOnlyMany`.
* Whether more than one pod can write to the same path.
* Whether the application uses `java.nio.file.Files`, `FileWriter`, `FileOutputStream`, `FileChannel`, `FileImageOutputStream`, local caches, append-only files, or lock files.
* Whether `ReadWriteMany` is used without an explicit application-level lock provider.
* Whether the workload should use object storage such as S3 or MinIO instead of shared filesystem writes.
* Whether each pod needs its own volume, as with StatefulSet `volumeClaimTemplates`.

Guidance:

* Use `ReadWriteOnce` or `ReadWriteOncePod` for pod-owned state where only one pod should write.
* Use `ReadWriteMany` only when the storage backend supports the required concurrency, consistency, and locking semantics.
* Flag `FileWriter`, `FileOutputStream`, `FileImageOutputStream`, or direct `java.nio.file.Files.write(...)` usage on shared `ReadWriteMany` volumes unless there is an explicit lock strategy.
* Acceptable lock strategies include a database-backed lock, ShedLock, Spring Integration `LockRegistry`, Kubernetes Lease coordination, or another tested distributed-lock provider.
* Prefer object storage for concurrent uploads, shared artifacts, image/report generation, and cross-pod access.
* Do not scale file-writing pods horizontally unless write coordination is explicit, tested, and documented.

Anti-pattern example:

```java
try (var writer = new FileWriter("/workspace/shared/report.csv", true)) {
    writer.write(line);
}
```

This is unsafe on a shared multi-pod filesystem unless the application uses an explicit distributed lock or another coordination mechanism.

Safer review guidance:

```text
If a Spring Boot workload writes to a shared PVC and replicas > 1, require one of:
1. a tested distributed lock,
2. per-pod storage isolation,
3. a database/object-storage design,
4. or a documented reason why concurrent writes cannot happen.
```

### 3. Graceful lifecycle, temporary files, and ephemeral storage overflow

Agents often place JVM runtime artifacts on the container’s ephemeral filesystem. Heap dumps, temporary files, uploaded multipart files, rolling logs, and diagnostic artifacts can quickly fill ephemeral storage and trigger Kubernetes `DiskPressure` evictions.

Agents should check:

* Whether `java.io.tmpdir` points to an intentional mounted path.
* Whether heap dumps are enabled and where they are written.
* Whether upload buffers, report exports, transaction logs, or diagnostics write to the container layer.
* Whether high-write paths use a PVC, `emptyDir` with size limits, or object storage.
* Whether graceful shutdown gives the application time to flush or upload important files.

Recommended JVM/property patterns:

```properties
-Djava.io.tmpdir=/workspace/tmp
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/workspace/diagnostics
```

Recommended Kubernetes pattern:

```yaml
volumeMounts:
  - name: tmp
    mountPath: /workspace/tmp
  - name: diagnostics
    mountPath: /workspace/diagnostics

volumes:
  - name: tmp
    emptyDir:
      sizeLimit: 1Gi
  - name: diagnostics
    persistentVolumeClaim:
      claimName: app-diagnostics
```

Do not allow heap dumps, temporary files, or large generated artifacts to silently fill the container writable layer.

### 4. Strict property decoupling for storage paths

Agents should not hardcode filesystem paths such as `/var/data/uploads` directly in Java services. Storage roots should be externalized through Spring configuration so Kubernetes manifests, ConfigMaps, Helm values, and local development profiles can supply environment-specific paths.

Recommended Spring Boot pattern:

```java
@ConfigurationProperties(prefix = "infraspine.storage")
public record StorageProperties(
        Path uploadRoot,
        Path tempRoot,
        Path diagnosticsRoot
) {}
```

Recommended application configuration:

```yaml
infraspine:
  storage:
    upload-root: ${INFRASPINE_STORAGE_UPLOAD_ROOT:/workspace/uploads}
    temp-root: ${INFRASPINE_STORAGE_TEMP_ROOT:/workspace/tmp}
    diagnostics-root: ${INFRASPINE_STORAGE_DIAGNOSTICS_ROOT:/workspace/diagnostics}
```

Agents should check:

* Storage paths are configured through `@ConfigurationProperties`.
* Environment variable names map cleanly to Kubernetes `ConfigMap` or Helm values.
* Application code depends on typed configuration, not hardcoded strings.
* Tests cover path resolution and invalid path handling.

## Verification checklist

* Tests cover PVC usage, snapshot, expansion, replica, and backup rules.
* API responses identify affected workloads, PVCs, and volumes.
* Remediation text is safe, staged, and educational.
* Documentation maps Kubernetes storage concepts to JVM/Spring code.
* Storage permission guidance includes non-root JVM containers and `fsGroup` behavior.
* Shared filesystem write guidance includes access modes, multi-pod concurrency, and lock-provider requirements.
* Storage paths are externalized through Spring configuration instead of hardcoded paths.

## How InfraSpine demonstrates the skill

InfraSpine seeds an in-memory Kubernetes-like topology with `StorageClass` profiles, volumes, PVCs, and workloads. It computes incidents, blast-radius reports, and remediation plans through a Spring Boot backend so agents can practice storage-risk analysis without needing cluster credentials.

The MVP intentionally keeps Kubernetes interaction simulated, so the skill can be reviewed and tested quickly while still teaching production storage concerns such as capacity thresholds, expansion support, snapshot gaps, backup readiness, non-root volume permissions, shared filesystem write safety, and safe remediation planning.
