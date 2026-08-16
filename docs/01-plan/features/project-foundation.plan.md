# project-foundation - Plan Document

> **Summary**: Establish the minimum reproducible database and configuration foundation before domain feature implementation.
>
> **Project**: Nyamlog
> **Version**: 1.0.0
> **Author**: Project decision record
> **Date**: 2026-08-06
> **Status**: Approved
> **Decision**: `PROJECT-FOUNDATION-001`

---

## 1. Overview

### 1.1 Purpose

Establish a small, verifiable Spring Boot and MySQL foundation before `user-registration` begins. This feature introduces Flyway as the schema-change authority, prevents Hibernate from creating or modifying the schema, defines a secret-safe environment configuration boundary, verifies compatibility with the existing Docker Compose MySQL service, and adds only the minimum real-MySQL Testcontainers coverage.

This feature does not create domain functionality or pre-design later features.

### 1.2 Background

The current repository is an early Spring Boot skeleton. As verified on 2026-08-06:

- `build.gradle` includes Spring Data JPA and the MySQL driver but not Flyway or Testcontainers.
- `application.yml` uses `ddl-auto: update`, enables SQL output, and enables deferred datasource initialization.
- No Flyway Migration directory or Migration file exists.
- `src/main/resources/data.sql` exists but is empty.
- The only automated test is a Spring context-loading test.
- `docker-compose.yml` defines a MySQL `8.4.5` service using environment-variable references.

The applicable approved Foundation direction is `FOUNDATION-001`: Flyway MySQL SQL Migrations are the schema source of truth, Hibernate performs validation only, and Testcontainers is limited to behavior that requires actual MySQL evidence. `FOUNDATION-004` assigns the concrete setup and verification work to this feature before `user-registration`.

### 1.3 Related Documents

- Foundation Plan: `docs/01-plan/features/nyamlog-mvp-foundation.plan.md`
- Foundation Design and decision log: `docs/02-design/features/nyamlog-mvp-foundation.design.md`
- PDCA status: `docs/.pdca-status.json`
- Project rules: `AGENTS.md`

## 2. Goals

- Make Flyway the only approved mechanism for applying database schema changes.
- Configure Hibernate to validate the currently mapped schema without creating or modifying it.
- Define required environment-variable names and environment responsibilities without storing credentials or example secret values.
- Keep the Spring datasource configuration compatible with the existing Docker Compose MySQL service.
- Add a narrowly scoped actual-MySQL integration verification without coupling ordinary tests to Docker.
- Leave the repository ready for the `user-registration` feature to introduce the first real domain Entity and Migration.

## 3. Scope

### 3.1 In Scope

- Flyway activation for MySQL and removal of competing automatic schema or SQL initialization paths
- Hibernate `ddl-auto=validate` configuration
- Environment configuration for application and database startup
- Documentation of required environment-variable names and responsibility by environment
- Compatibility verification with the existing Docker Compose MySQL service
- A limited Testcontainers verification using the approved MySQL baseline
- Separation of ordinary tests from Docker-dependent integration verification
- Reproducible Gradle verification commands and clear reporting when Docker-dependent verification cannot run

### 3.2 Out of Scope

- User, credential, verification-token, food, meal, or any other domain table
- A meaningless placeholder domain table or project-metadata table created only to produce a first Migration
- Domain Entity, Repository, Service, controller, endpoint, request DTO, or response DTO
- `AUTH-003-R1`, password validation, Spring Security, signup, email verification, login, or logout
- Mailpit configuration or email delivery behavior
- Food CSV import, Spring Batch, search, nutrition calculation, or meal snapshots
- Production deployment, production mail, CI/CD, monitoring, backup, or high availability
- Common repository abstractions, container base classes, custom test frameworks, or speculative infrastructure
- Changes to Git tracking policy for configuration, secret, Codex, bkit, or project-rule files

## 4. Requirements

### 4.1 Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| PF-FR-001 | The application uses Flyway as the only approved database schema migration mechanism. | High |
| PF-FR-002 | Hibernate uses `ddl-auto=validate` and does not create, update, or drop database objects. | High |
| PF-FR-003 | Automatic SQL initialization that competes with Flyway is disabled or removed, including the currently empty `data.sql` path. | High |
| PF-FR-004 | Configuration defines only the environment-variable names and environment responsibilities needed to start the application and connect to MySQL. | High |
| PF-FR-005 | Application datasource settings and Docker Compose environment-variable wiring are compatible without embedding credentials. | High |
| PF-FR-006 | A limited integration verification starts an actual MySQL container, applies the available Flyway Migrations, and loads the Spring context with schema validation. | High |
| PF-FR-007 | Ordinary tests remain runnable without Docker and do not inherit from or import container-specific base classes or shared configuration. | High |

### 4.2 Non-Functional Requirements

| Category | Requirement | Verification |
|----------|-------------|--------------|
| Security | Do not commit, print, or document real secrets, local passwords, production credentials, tokens, or plausible example credentials. | Review changed configuration, documentation, logs, and test output |
| Data integrity | Flyway owns schema changes and Hibernate cannot silently mutate the schema. | Configuration inspection and actual-MySQL context startup |
| Portability | Ordinary tests work in an environment without Docker. | Run the normal Gradle test task without requiring a container |
| Reproducibility | Docker-capable environments can repeat the limited MySQL integration verification. | Run the selected integration-test path and record the result |
| Maintainability | Do not introduce domain objects, placeholder tables, framework layers, or abstractions without a current need. | Repository diff review |

## 5. Approved Boundaries and Interpretation

### 5.1 First Migration Boundary

This feature must not add a meaningless domain or project-metadata table merely to create a first Migration. The following choice remains open for Design:

1. Add an intentionally empty baseline Migration, or
2. Add no Migration in this feature and let `user-registration` provide the first real versioned Migration.

Neither option may imply that a domain schema already exists.

### 5.2 Hibernate Validation Boundary

There is no domain Entity yet. Therefore, a successful `ddl-auto=validate` startup in this feature proves only that:

- Hibernate is configured not to create or modify the schema, and
- the application context starts against the schema within the current mapping scope.

It does not prove that any future domain table matches an Entity. Every domain feature must verify its own Entity and Migration agreement when those artifacts are introduced.

### 5.3 Testcontainers Boundary

Testcontainers is used only for the limited actual-MySQL evidence defined by this Plan. The Design must choose one of these execution models:

1. A conditionally executed test such as `disabledWithoutDocker`, or
2. A separate `integrationTest` Gradle task.

Ordinary unit and context-independent tests must not depend on a container base class, container-specific shared configuration, or Docker availability.

### 5.4 Environment and Secret Boundary

The feature may define required environment-variable names and which environment is responsible for providing them. It must not store or reproduce actual `.env` values, local passwords, production credentials, or secret-looking sample values in source, tests, logs, or documentation.

The exact configuration-file structure, safe non-secret defaults, and method of documenting variable names remain Design decisions.

## 6. Design Decisions Still Required

Only the following `project-foundation` details require Design approval before implementation:

1. Empty baseline Migration versus the first real Migration in `user-registration`
2. Conditional Testcontainers execution versus a separate `integrationTest` task
3. Exact Flyway and Testcontainers dependency configuration compatible with the current Spring Boot baseline
4. Exact application configuration structure and responsibility for required environment-variable names
5. The minimal verification scenarios and commands for Docker-present and Docker-absent environments

These decisions do not reopen `FOUNDATION-001` or authorize domain design.

## 7. Success Criteria

- [ ] Flyway is configured as the sole schema-change mechanism.
- [ ] Hibernate uses `ddl-auto=validate` and does not create or modify schema objects.
- [ ] A fresh MySQL instance can follow the selected Migration strategy and start the application context successfully within the current empty domain-mapping scope.
- [ ] The result is not described as completed domain Entity-to-table validation.
- [ ] No meaningless domain or project-metadata table is added.
- [ ] Docker Compose MySQL and application datasource configuration use a consistent environment-variable contract without embedded credentials.
- [ ] Required environment-variable names and environment responsibilities are documented without actual or example secret values.
- [ ] Limited actual-MySQL verification exists and uses the Design-approved Docker-availability strategy.
- [ ] Ordinary tests run without Docker and do not depend on Testcontainers infrastructure.
- [ ] No domain feature, API, authentication behavior, or deferred infrastructure is introduced.
- [ ] Standard Gradle tests and every applicable integration verification are executed and reported, including skipped or unavailable verification.

## 8. Risks and Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| An empty schema-validation success is mistaken for domain-schema verification | High | Medium | State the current mapping boundary in the Design, tests, and completion report |
| A placeholder table is introduced to make Flyway appear active | Medium | Medium | Approve either an empty baseline or no Migration; prohibit meaningless tables |
| Every test becomes dependent on Docker | High | Medium | Isolate Testcontainers using the approved conditional or separate-task strategy |
| Local or production credentials appear in examples or logs | High | Medium | Document names and responsibilities only; review the diff and test output for secret values |
| Flyway and Spring SQL initialization both attempt schema work | High | Medium | Remove or disable the competing initialization path and verify startup behavior |
| Configuration is expanded into deployment architecture | Medium | Low | Limit work to local/test MySQL connectivity and defer deployment mechanics |

## 9. Dependency and Handoff

- Direct dependency: completed `nyamlog-mvp-foundation` Plan and Design, especially `FOUNDATION-001` and `FOUNDATION-004`
- This feature has no dependency on Auth, Food, Meal, or deployment feature details.
- `user-registration` implementation remains blocked until this feature is implemented and verified.
- Completion of this feature does not approve `user-registration`, `AUTH-003-R1`, or any later domain implementation.

## 10. Approval Record

### `PROJECT-FOUNDATION-001` — Approved 2026-08-06

Approved the bounded `project-foundation` Plan covering Flyway, Hibernate schema validation, environment configuration, Docker Compose MySQL compatibility, and limited MySQL Testcontainers verification.

The approval explicitly:

- prohibits meaningless domain or project-metadata tables
- leaves the baseline-Migration strategy to Design
- limits `ddl-auto=validate` evidence to the current mapping scope
- leaves the Docker-availability test strategy to Design
- keeps ordinary tests independent of containers
- permits documentation of environment-variable names and responsibilities only, never actual or example secret values
- authorizes this Plan document only and does not authorize application, dependency, configuration, database, or PDCA phase changes

## 11. Next Step

Review the five remaining `project-foundation` Design decisions one at a time. Do not begin implementation or advance the PDCA phase until the Plan handoff and applicable Design decisions are explicitly approved.

## Version History

| Version | Date | Change | Author |
|---------|------|--------|--------|
| 1.0.0 | 2026-08-06 | Initial approved Plan from `PROJECT-FOUNDATION-001` | Project decision record |
