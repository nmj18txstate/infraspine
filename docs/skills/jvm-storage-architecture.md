# JVM Storage Architecture

## Short description
Guide JVM/Spring coding agents through storage-aware application design, capacity risk detection, backup/restore readiness, and safe remediation planning.

## When to use
Use this skill when a JVM service owns or depends on persistent data, local files, object storage, block volumes, database storage, or backup/DR workflows.

## What the AI coding agent should check
- Domain ownership of persisted data and lifecycle boundaries.
- Capacity thresholds, growth assumptions, and alerting behavior.
- Backup targets, restore objectives, and restore rehearsal evidence.
- Whether storage failures have bounded blast radius.
- Whether remediation steps are reversible and safe for stateful systems.

## JVM/Spring-specific guidance
- Keep storage decisions in explicit services rather than hidden controller logic.
- Prefer immutable DTOs or records for topology and incident snapshots.
- Use constructor injection for repositories and rule engines.
- Add focused tests for threshold and failure-mode rules.
- Document operational assumptions in README or runbooks.

## Examples
- Flag a PVC-like data store above 85% utilization as HIGH risk.
- Flag a workload with no backup target as HIGH risk.
- Generate remediation steps that first verify backups before resizing or migrating storage.

## Anti-patterns
- Mutating production storage before checking backup health.
- Encoding capacity thresholds only in dashboards with no test coverage.
- Treating stateless and stateful workloads the same during remediation.
- Hiding storage topology behind unstructured strings.

## Verification checklist
- Rule engine tests cover high-risk and medium-risk findings.
- REST or service tests expose storage risks consistently.
- Remediation plans mention backup verification and rollback safety.
- Documentation explains storage ownership and blast radius.

## How InfraSpine demonstrates the skill
InfraSpine models storage classes, volumes, PVCs, workloads, incidents, blast-radius reports, and remediation plans as Java records. Its service layer evaluates capacity, backup, expansion, and snapshot rules, then exposes the results through Spring MVC APIs.
