# InfraSpine

InfraSpine is a hardware-aware production-style Java/Spring Boot prototype for a self-healing infrastructure control plane and learning lab. The MVP focuses on helping JVM engineers reason about Kubernetes storage topology, incidents, blast radius, and safe remediation planning.

## Current repository structure

```text
.
├── pom.xml                         # Java 21 / Spring Boot 3 Maven build
├── src/main/java/com/infraspine
│   ├── InfraSpineApplication.java  # Spring Boot entry point
│   ├── api                         # REST controllers
│   ├── domain                      # Immutable domain records
│   └── service                     # In-memory topology and rule engine
├── src/test/java/com/infraspine    # Rule engine and REST API tests
├── docs/examples                   # Walkthroughs and sample API outputs
└── docs/skills                     # Draft JVM skill submissions
```

The repository previously contained only this README and the project license. The MVP intentionally keeps persistence in memory so the domain model and rules remain easy to review.

## Architecture

InfraSpine uses a simple layered Spring Boot architecture:

- **Domain layer**: Java records model clusters, nodes, storage classes, volumes, PVCs, workloads, incidents, blast-radius reports, and remediation plans.
- **Repository layer**: `TopologyRepository` creates deterministic sample topology data at startup.
- **Service layer**: `IncidentService` evaluates storage and workload rules, computes blast radius, and generates educational remediation steps.
- **API layer**: Spring MVC controllers expose the topology and incident workflow over JSON REST endpoints.

## REST API

Start the service:

```bash
mvn spring-boot:run
```

Endpoints:

- `GET /api/topology` returns the sample cluster topology.
- `GET /api/incidents` returns generated incidents.
- `GET /api/incidents/{id}` returns one incident or `404`.
- `GET /api/incidents/{id}/blast-radius` returns impacted workloads, PVCs, and volumes.
- `GET /api/incidents/{id}/remediation-plan` returns safe, educational remediation steps.

## Examples
- [JVM Storage Architecture Walkthrough](docs/examples/jvm-storage-architecture-walkthrough.md)
  This walkthrough explains how to model storage reliability inside a Java 21 / Spring Boot 3 application using thin REST controllers, immutable Java records, rule engine isolation, deterministic `Clock` injection, and backup-first remediation workflows.
- [Kubernetes Persistent Storage Walkthrough](docs/examples/kubernetes-persistent-storage-walkthrough.md)
  This walkthrough explains the sample `orders-db` storage-risk scenario using an analogy, Mermaid topology diagram, REST API calls, real JSON outputs, gotchas, and Spring Boot reliability   mapping.
  
## MVP rules

InfraSpine currently evaluates these rules:

- PVC usage above 85% creates **HIGH** risk.
- Missing snapshot policy creates **MEDIUM** risk.
- Single-replica stateful workload creates **HIGH** risk.
- StorageClass without expansion support creates **MEDIUM** risk.
- Workload with no backup target creates **HIGH** risk.

## How this maps to future JVM Skills

InfraSpine is both a runnable service and a teaching corpus for JVM-focused infrastructure skills:

- **jvm-storage-architecture**: demonstrates immutable JVM domain modeling, storage capacity rules, backup/restore reasoning, and service-layer tests.
- **kubernetes-persistent-storage**: demonstrates StorageClass, PVC, workload, snapshot, expansion, blast-radius, and remediation concepts through REST APIs and deterministic samples.

The draft skill files in `docs/skills` can evolve into future submissions that teach coding agents what to inspect, what risks to flag, and how to verify fixes in Spring/Kubernetes storage work.

## Build and test

```bash
mvn test
```
