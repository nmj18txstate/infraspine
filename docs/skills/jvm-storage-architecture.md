# JVM Storage Architecture

## Short description

Guide JVM/Spring coding agents through storage-aware application design, capacity risk detection, backup/restore readiness, blast-radius analysis, and safe remediation planning.

## When to use

Use this skill when a JVM service owns or depends on persistent data, local files, object storage, block volumes, database storage, Kubernetes persistent volumes, or backup/DR workflows.

## What the AI coding agent should check

* Domain ownership of persisted data and lifecycle boundaries.
* Capacity thresholds, growth assumptions, and alerting behavior.
* Backup targets, restore objectives, and restore rehearsal evidence.
* Whether storage failures have bounded blast radius.
* Whether remediation steps are reversible and safe for stateful systems.
* Whether storage paths are externalized through Spring configuration.
* Whether storage-risk logic is isolated in testable services rather than hidden in controllers.

## JVM/Spring-specific guidance

* Keep storage decisions in explicit services rather than hidden controller logic.
* Prefer immutable DTOs or Java records for topology, incident, and remediation snapshots.
* Use constructor injection for repositories, rule engines, and analyzers.
* Add focused unit tests for threshold calculations and failure-mode rules.
* Document operational assumptions in the project README, examples, or runbooks.
* Use Spring `@ConfigurationProperties` for filesystem roots, object-storage buckets, diagnostic paths, and temporary directories.
* Prefer educational remediation plans over unsafe automatic mutations for stateful resources.

## Examples

* Flag a PVC-like data store above safe utilization as `HIGH` risk.
* Flag a workload with no backup target as `HIGH` risk.
* Flag missing snapshot or restore rehearsal evidence as a storage-readiness concern.
* Generate remediation steps that first verify backups before resizing or migrating storage.
* Explain which workloads, volumes, filesystems, or database dependencies are affected by a risky storage layer.

## Anti-patterns

* Mutating production storage before checking backup health and replication status.
* Encoding capacity thresholds only in external dashboards with zero test coverage in the codebase.
* Treating stateless and stateful workloads the same during disaster recovery or remediation.
* Hiding complex storage topology or volume relationships behind unstructured strings or untyped maps.
* Hardcoding filesystem paths such as `/var/data/uploads` directly inside Java services.
* Returning free-form remediation text when a structured runbook-like object would be safer.
* Performing storage-risk calculations directly inside Spring MVC controllers.

## Enterprise design patterns agents must enforce

### 1. Immutable topology modeling with Java records

When modeling storage components, capacities, or risk incidents, agents must avoid mutable beans with broad setters. Unstructured strings or loose maps can hide the real topology relationship between volumes, workloads, storage classes, backup targets, and incidents.

Agents should use strongly typed, immutable Java records to represent snapshots of the system state safely across threads and service boundaries.

Recommended pattern:

```java
import java.time.Instant;

public record StorageNode(
        String volumeId,
        String storageClass,
        long capacityBytes,
        long usedBytes,
        boolean expansionSupported
) {
    public double utilizationPercentage() {
        if (capacityBytes <= 0) {
            return 0.0;
        }
        return ((double) usedBytes / capacityBytes) * 100.0;
    }
}

public record StorageIncident(
        String incidentId,
        String targetVolumeId,
        RiskLevel riskLevel,
        String description,
        Instant detectedAt
) {}

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
```

Agents should check:

* Capacity calculations guard against zero or invalid capacity values.
* Utilization math does not accidentally truncate fractional percentages.
* Domain models expose meaningful storage relationships instead of generic maps.
* Incident snapshots are immutable once created.
* Time-based fields, such as `detectedAt`, are explicit.

### 2. Separation of rule logic from web layers

Agents must never embed storage evaluation, capacity threshold logic, backup-readiness checks, or blast-radius calculations directly inside Spring REST controllers.

Risk computations and storage evaluations belong in isolated, testable Spring `@Component` or `@Service` classes using constructor injection.

Recommended pattern:

```java
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StorageRiskEngine {

    private static final double HIGH_RISK_THRESHOLD = 85.0;

    private final Clock clock;

    public StorageRiskEngine(Clock clock) {
        this.clock = clock;
    }

    public List<StorageIncident> evaluateRisks(List<StorageNode> nodes) {
        return nodes.stream()
                .filter(this::isHighRisk)
                .map(this::toIncident)
                .toList();
    }

    private boolean isHighRisk(StorageNode node) {
        return node.utilizationPercentage() > HIGH_RISK_THRESHOLD;
    }

    private StorageIncident toIncident(StorageNode node) {
        return new StorageIncident(
                UUID.randomUUID().toString(),
                node.volumeId(),
                RiskLevel.HIGH,
                "Volume exceeds safe utilization threshold of " + HIGH_RISK_THRESHOLD + "%",
                Instant.now(clock)
        );
    }
}
```
Companion Spring configuration:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

When agents recommend injecting `Clock` for deterministic tests, they must also add a production `Clock` bean or verify one already exists in the application context.
Agents should check:

* Controllers delegate to services.
* Rule engines are unit tested directly.
* Time is injectable through `Clock` when deterministic tests need it, and a production `Clock` bean is defined.
* Threshold values are explicit constants or configuration properties.
* Rule methods are small enough to review and test independently.

### 3. Structured, step-by-step remediation output

When an agent or rule engine suggests a remediation plan for a storage failure mode, the plan must be returned as a structured object containing sequential, reversible steps.

The first step of any stateful remediation plan must validate backup or snapshot availability before attempting storage expansion, migration, deletion, or replacement.

Recommended pattern:

```java
import java.util.List;

public record RemediationStep(
        int sequence,
        String action,
        boolean reversible,
        String rollbackStrategy
) {}

public record RemediationPlan(
        String incidentId,
        List<RemediationStep> steps,
        boolean requiresDowntime
) {}
```

Recommended remediation sequence:

```text
Step 1: Verify the latest snapshot, backup target, or restore point.
Step 2: Confirm application health and current write activity.
Step 3: Stage the expansion, migration, or replacement manifest.
Step 4: Apply the smallest safe change during an approved maintenance window if needed.
Step 5: Monitor filesystem resizing, application health, and error rates.
Step 6: Document rollback or follow-up actions.
```

Agents should check:

* Remediation steps are ordered.
* Backup validation appears before structural mutation.
* Reversibility is explicit.
* Downtime requirements are explicit.
* The plan does not recommend destructive actions first.

### 4. Storage path decoupling through Spring configuration

Agents should not hardcode filesystem paths directly in Java services. Storage roots should be externalized through Spring configuration so local development, Kubernetes manifests, Helm values, and CI environments can provide different paths safely.

Recommended pattern:

```java
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

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

* Java services depend on typed configuration instead of hardcoded strings.
* Environment variable names map cleanly to Kubernetes `ConfigMap`, Helm, or deployment values.
* Tests cover default path resolution.
* Invalid or missing paths fail clearly.
* High-write paths such as uploads, temp files, heap dumps, and diagnostics are intentionally mapped.

### 5. Backup-first mutation rule

Any agent modifying storage behavior must follow a backup-first rule for stateful systems.

Before recommending or implementing storage expansion, migration, cleanup, or deletion, the agent must verify:

* A backup target exists.
* The latest backup or snapshot is recent enough for the required RPO.
* A restore rehearsal or documented restore path exists.
* The affected workload and blast radius are understood.
* Rollback steps are written down.

Agents should reject unsafe plans that mutate stateful storage before backup readiness is established.

## Verification checklist

* Rule engine unit tests cover high-risk and medium-risk findings.
* Capacity threshold tests include fractional utilization, not only whole-number percentages.
* REST controllers delegate evaluation logic to injected domain services.
* Generated remediation plans verify backups before structural mutation steps.
* Domain models use strongly typed Java records instead of generic string-based maps.
* Storage paths are externalized through Spring configuration instead of hardcoded paths.
* Documentation explains storage ownership, blast radius, and remediation assumptions.
* Tests prove that affected workloads, volumes, and storage resources are identified consistently.

## How InfraSpine demonstrates the skill

InfraSpine models storage classes, volumes, PVCs, workloads, incidents, blast-radius reports, and remediation plans natively as Java records. Its service layer evaluates capacity, backup, expansion, and snapshot rules, then exposes the results cleanly through Spring MVC APIs.

The MVP intentionally keeps Kubernetes interaction simulated, so the skill can be reviewed and tested quickly while still teaching production storage concerns such as capacity thresholds, expansion support, snapshot gaps, backup readiness, non-root volume permissions, shared filesystem write safety, and safe remediation planning.
