# project-foundation - Design Document

> **Summary**: Define the minimum Flyway, schema-validation, environment, and MySQL verification design before domain implementation.
>
> **Project**: Nyamlog
> **Version**: 1.0.0
> **Author**: Project decision record
> **Date**: 2026-08-06
> **Status**: Approved
> **Plan**: `docs/01-plan/features/project-foundation.plan.md`

---

## 1. Overview

### 1.1 Design Goals

- Configure Flyway as the sole schema-change authority without inventing a schema change.
- Start Hibernate in validation-only mode against the current empty domain-mapping scope.
- Prove the empty-database startup boundary using actual MySQL.
- Keep ordinary tests independent of Docker and Testcontainers infrastructure.
- Define environment responsibilities without storing or reproducing credentials.

### 1.2 Design Boundaries

This Design owns only Flyway activation, Hibernate schema validation, environment configuration, Docker Compose MySQL compatibility, and limited actual-MySQL verification.

It does not own domain Entity or table design, authentication, API contracts, Mailpit behavior, food import, deployment automation, or any other feature detail. Design approval does not authorize implementation until all remaining decisions in this document are resolved.

### 1.3 Applicable Approved Decisions

- `FOUNDATION-001`: Flyway MySQL SQL Migrations are the schema authority, Hibernate validates only, and Testcontainers is limited to actual-MySQL evidence.
- `FOUNDATION-004`: `project-foundation` precedes `user-registration` and owns the concrete common-foundation setup.
- `PROJECT-FOUNDATION-001`: This feature is limited to Flyway, Hibernate validation, environment configuration, Docker Compose MySQL compatibility, and scoped Testcontainers verification.
- `PROJECT-FOUNDATION-002`: No empty baseline or placeholder Migration is created; the first real feature owns the first versioned application Migration.
- `PROJECT-FOUNDATION-003`: The dedicated MySQL integration test remains in the normal `test` source set and is skipped, not treated as successful MySQL evidence, when Docker is unavailable.
- `PROJECT-FOUNDATION-004`: Spring Boot dependency management owns the approved versionless Flyway and Testcontainers Maven dependencies.
- `PROJECT-FOUNDATION-005`: The MySQL integration test uses `@ServiceConnection` without manual datasource or Flyway connection properties.
- `PROJECT-FOUNDATION-006`: One common `application.yml` and the approved environment-variable contract define application and Docker Compose configuration.
- `PROJECT-FOUNDATION-007`: One dedicated integration test, exact functional assertions, environment-independent execution evidence, commands, and result-reporting rules complete this Design.

## 2. Current Repository Baseline

As verified before this Design was created:

- No Flyway or Testcontainers dependency is active.
- No `src/main/resources/db/migration` path or Migration exists.
- Hibernate currently uses `ddl-auto: update`.
- Deferred SQL initialization is enabled and an empty `data.sql` file exists.
- Docker Compose defines MySQL `8.4.5` through environment-variable references.
- The test suite contains only a Spring context-loading test.
- No domain Entity or application table exists in the repository.

These are implementation facts, not permanent design decisions. They must be rechecked before implementation.

## 3. Approved Migration Lifecycle

### 3.1 No Baseline Application Migration

`project-foundation` does not add any of the following:

- an empty versioned SQL Migration
- a comment-only Migration
- `.gitkeep` solely to preserve a Migration directory
- a placeholder domain table
- an application or project-metadata table created only to demonstrate Flyway

The Migration path may remain absent until the first real Migration is added.

### 3.2 Flyway Execution Boundary

Flyway is activated through Spring Boot configuration and runs during application context startup against a fresh MySQL database.

For this feature, the expected state after successful startup is:

- Flyway is enabled and has executed its migration lifecycle.
- Flyway's own `flyway_schema_history` table exists.
- Zero versioned application Migrations are applied.
- No Nyamlog application table exists.
- `baseline-on-migrate` is not enabled.

`flyway_schema_history` is Flyway-owned migration metadata. It is not a Nyamlog domain table or an arbitrary project-metadata table.

### 3.3 First Real Migration Ownership

The first feature that requires actual schema owns the first versioned application Migration. Under the current approved feature order, that owner is `user-registration`.

The following are deferred to the `user-registration` Design:

- the exact first Migration version and filename
- user and account table definitions
- indexes, constraints, column types, and relationships
- Entity-to-Migration agreement tests

If the implementation order changes through a later approved decision, Migration ownership follows the first feature that actually requires schema.

### 3.4 Existing Schema Adoption

Adopting an existing manually managed schema into Flyway is outside the first MVP. Do not enable `baseline-on-migrate` or introduce another baseline-adoption mechanism for this feature.

## 4. Hibernate Validation Boundary

Hibernate must use `ddl-auto=validate`. Automatic `update`, `create`, `create-drop`, or equivalent schema mutation is not allowed.

Because there is no domain Entity, successful startup proves only that:

- Hibernate is configured in validation-only mode,
- Hibernate does not create or modify application tables, and
- the application context starts within the current mapping scope.

This is not evidence that a future Entity matches a future table. Each domain feature must verify its own Entity and Migration agreement when both artifacts exist.

## 5. Approved Empty-Database Verification Outcomes

The actual-MySQL integration verification must establish all of the following against a fresh database:

1. Flyway is active and executes during startup.
2. `flyway_schema_history` exists after startup.
3. The count of applied versioned application Migrations is zero.
4. Hibernate starts with `ddl-auto=validate`.
5. Hibernate, Spring SQL initialization, and other initialization paths create no application table.
6. The Spring application context starts successfully.

The verification must distinguish Flyway's history table from application tables. It must not describe this result as domain-schema validation.

The exact assertion implementation and configuration details remain unresolved. The source-set and Docker-availability execution model is approved below.

## 6. Approved Test Execution Strategy

### 6.1 Source Set and Test Structure

- Keep the limited MySQL integration verification in the existing `test` source set.
- Apply `@Testcontainers(disabledWithoutDocker = true)` to the dedicated MySQL integration-test class.
- Do not add a separate `integrationTest` source set or Gradle task.
- Do not create a common Testcontainers abstract class, global container configuration, or project-specific container test framework.
- Ordinary unit tests must not reference Testcontainers classes, container fields, Docker settings, or container-specific shared configuration.
- The dedicated Testcontainers test owns full application-context startup verification with actual MySQL, Flyway, and Hibernate.

### 6.2 Standard Command and Docker Availability

The standard verification command remains:

```powershell
.\gradlew.bat test
```

- When Docker is available, the command runs ordinary tests and the MySQL integration test.
- When Docker is unavailable, only the MySQL integration test is recorded as skipped; the remaining tests still run.
- A skipped MySQL integration test is not successful MySQL verification and must be reported as unexecuted evidence.
- Before `project-foundation` is completed, the MySQL integration test must run and pass at least once in a Docker-capable environment.

### 6.3 Existing `contextLoads()` Test

Review whether the existing `contextLoads()` test can provide meaningful application verification without a database.

- Do not make it pass by silently excluding core production auto-configuration that the real application requires.
- Do not introduce an in-memory database as substitute evidence for MySQL behavior.
- Do not keep a second context-loading test merely to preserve the current file when the dedicated Testcontainers context test already provides the meaningful verification.
- If a database-independent purpose cannot be stated and verified, remove or replace the redundant test during implementation instead of weakening production configuration.

### 6.4 Reconsideration Boundary

Do not introduce a separate `integrationTest` task merely because the number of integration tests increases. Reconsider separation only when an observed problem exists in at least one of these areas:

- test execution time
- CI-stage separation
- result visibility
- resource management

Any later separation requires an explicit design amendment based on the observed problem.

## 7. Approved Dependency and Version Management

### 7.1 Version Authority

The current Spring Boot `3.5.10` dependency management is the version authority for Flyway and Testcontainers Maven dependencies.

- Do not write direct versions for the approved Flyway or Testcontainers modules.
- Do not import a separate Flyway BOM or Testcontainers BOM.
- Do not introduce dependency locking, a version catalog, Renovate, or another dependency-management system in this feature.
- Do not override an individual managed version without a concrete compatibility failure and a separately approved amendment that records the cause, selected version, and verification results.

### 7.2 Approved Dependency Coordinates

Add only these versionless declarations for the currently approved behavior:

```gradle
implementation 'org.flywaydb:flyway-core'
runtimeOnly 'org.flywaydb:flyway-mysql'

testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:mysql'
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
```

- Do not declare Testcontainers core separately when it is already provided transitively.
- `org.springframework.boot:spring-boot-testcontainers` is approved only for the `@ServiceConnection` integration selected by `PROJECT-FOUNDATION-005`.
- Do not add convenience libraries or dependency-management infrastructure without a concrete requirement.

### 7.3 Resolved-Dependency Verification

After implementation, use Gradle `dependencyInsight` against the applicable runtime and test-runtime configurations to verify:

- the resolved Flyway and Testcontainers versions
- application of Spring Boot dependency management
- unexpected version conflicts, forced selections, or overrides

The verification report must identify the resolved versions without copying the values into a new manually maintained version source. Any conflict requiring an override blocks completion until an explicit amendment is approved.

### 7.4 MySQL Image Baseline and Reverification

The MySQL container image is an execution-environment baseline, not a Maven dependency. Docker Compose and Testcontainers both use the approved `mysql:8.4.5` tag.

- Changing the MySQL image tag requires rerunning Flyway startup, Hibernate schema validation, and the MySQL Testcontainers verification.
- Changing the Spring Boot version also requires rerunning those three verification boundaries.
- Do not infer MySQL compatibility solely from dependency resolution.

## 8. Approved Testcontainers Service Connection

### 8.1 Container and Connection Declaration

The dedicated MySQL integration-test class uses a static `MySQLContainer` configured with the approved `mysql:8.4.5` image.

Apply both annotations to the field:

- `@Container`
- `@ServiceConnection`

Do not restrict `@ServiceConnection.type` to `JdbcConnectionDetails`. Use the default behavior so the same `JdbcDatabaseContainer` supplies both:

- `JdbcConnectionDetails` for the application `DataSource`
- `FlywayConnectionDetails` for Flyway

Do not test or depend on the concrete internal implementation classes of those connection-detail beans.

### 8.2 Prohibited Manual Connection Overrides

Do not register any of these properties through `@DynamicPropertySource`, `@TestPropertySource`, `application-test.yml`, or system properties:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.flyway.url`
- `spring.flyway.user`
- `spring.flyway.password`

Do not copy the container URL or generated credentials into source, configuration files, test output, or application logs. General runtime configuration remains environment-variable based; the Testcontainers integration test relies on the service connection taking precedence for connection details.

### 8.3 Functional Verification Boundary

Verify behavior rather than Spring Boot's internal connection-detail implementation:

1. The application `DataSource` connects to the Testcontainers MySQL database.
2. `flyway_schema_history` can be queried through that connected database.
3. Flyway reports zero applied versioned application Migrations.
4. Hibernate starts against the same database with `ddl-auto=validate`.
5. The test runs without an external local MySQL connection or database values from `.env`.
6. Hibernate and every other initialization path create no application table.

Do not add assertions for concrete `JdbcConnectionDetails` or `FlywayConnectionDetails` bean classes when the functional database behavior already proves the connection.

### 8.4 Fallback Boundary

Do not add a parallel `@DynamicPropertySource` path. Consider replacing `@ServiceConnection` only if a reproducible conflict or missing connection is demonstrated in the actual project. Any replacement requires a separately approved amendment containing the cause and test results.

## 9. Approved Application and Environment Configuration

### 9.1 Configuration File Structure

Keep one `src/main/resources/application.yml` as the common application-configuration baseline. Do not add `application-local.yml`, `application-test.yml`, or another profile file in this feature.

Retain the local convenience import:

```yaml
spring:
  config:
    import: optional:file:./.env[.properties]
```

This import optionally reads a local `.env` from the process working directory. It is not a deployment contract. When the application starts from another working directory or in a deployed environment, that environment must supply the real environment variables.

### 9.2 Application and Compose Variable Ownership

| Variable | Consumer | Responsibility | Default |
|----------|----------|----------------|---------|
| `MYSQL_URL` | Application | JDBC URL for normal application execution | None |
| `MYSQL_USERNAME` | Application and Docker Compose | Non-root application database user | None |
| `MYSQL_PASSWORD` | Application and Docker Compose | Password for the application database user | None |
| `MYSQL_DB` | Docker Compose | Database created when a new MySQL data directory is initialized | None |
| `MYSQL_ROOT_PASSWORD` | Docker Compose only | Root password used only for MySQL container initialization | None |
| `MYSQL_PORT` | Docker Compose | Host port published for MySQL | `3306` |

The application connects as `MYSQL_USERNAME`, never as root. `MYSQL_ROOT_PASSWORD` must not be referenced by the application datasource.

When the application runs on the host against the Compose MySQL service, the host port and database name encoded in `MYSQL_URL` must match `MYSQL_PORT` and `MYSQL_DB`. Record this relationship in local execution guidance and verification without hard-coding or reproducing the URL or credentials.

### 9.3 Common Spring Settings

Apply this common behavior:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    baseline-on-migrate: false
  sql:
    init:
      mode: never
```

Remove these existing settings and initialization artifacts:

- `spring.jpa.defer-datasource-initialization: true`
- `spring.jpa.show-sql: true`
- global `hibernate.format_sql: true`
- the explicit `MySQL8Dialect`
- the empty `src/main/resources/data.sql`

Do not enable SQL logging in the common baseline.

### 9.4 Local `.env` and Names-Only Example

- Keep `.env` excluded from Git.
- Do not read, print, copy, or summarize its actual contents in source, documentation, logs, tool output, or completion reports.
- Add `.env.example` containing only the six approved variable names with empty values.
- Do not write password examples, `changeme`, plausible credentials, or real or fake production values.
- Do not change the Git ignore policy for `.env` as part of this feature.

### 9.5 Docker Compose Mapping and Required Values

Map project variables to the official MySQL image variables:

| Project variable | MySQL container variable |
|------------------|--------------------------|
| `MYSQL_DB` | `MYSQL_DATABASE` |
| `MYSQL_USERNAME` | `MYSQL_USER` |
| `MYSQL_PASSWORD` | `MYSQL_PASSWORD` |
| `MYSQL_ROOT_PASSWORD` | `MYSQL_ROOT_PASSWORD` |

Use required-value interpolation for `MYSQL_DB`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`, and `MYSQL_ROOT_PASSWORD`, following `${VAR:?message}` semantics. Permit a non-secret default only for the host port, following `${MYSQL_PORT:-3306}` semantics.

MySQL initialization variables apply when initializing a new data directory. Do not assume that changing `.env` database, user, or password values updates an existing MySQL volume. If reinitialization is required, first resolve the exact local volume and obtain confirmation that its data may be deleted before following a separate volume-recreation procedure.

### 9.6 Testcontainers Independence and Placeholder Verification

- The Testcontainers integration test runs through `@ServiceConnection` without `.env` or an external local MySQL server.
- Do not alter the normal datasource variable names or common structure for Testcontainers.
- Do not add test URL, username, or password values to a configuration file.
- During implementation, explicitly verify that the Testcontainers context starts when the normal datasource placeholders are not supplied.
- If unresolved placeholder processing prevents startup, do not add test credentials. Propose a separate amendment that makes normal-runtime required-value validation and service connection coexist.

### 9.7 Secret-Safe Validation and Reporting

- Never store or expose actual `.env` values, local passwords, production credentials, tokens, container credentials, or secret-looking samples.
- Do not expose credentials through test failure messages, Gradle output, container logs, or application logs.
- Do not copy output from `docker compose config`, `docker compose config --environment`, or another command that may contain resolved secret values into documentation, logs, or completion reports.
- Report only variable names, pass/fail results, and reproducibility limitations.

## 10. Approved Verification and Reporting Contract

### 10.1 Dedicated Integration Test

Add exactly one dedicated integration-test class during implementation:

```text
src/test/java/com/nyam/ProjectFoundationMySqlIntegrationTest.java
```

Use:

- `@SpringBootTest`
- `@Testcontainers(disabledWithoutDocker = true)`
- a static `MySQLContainer<?>`
- `@Container`
- `@ServiceConnection`
- the `mysql:8.4.5` image

Do not add a separate test profile, a common Testcontainers abstract class, `@DynamicPropertySource`, manual datasource properties, or test credentials.

Remove the existing `ApplicationTests.contextLoads()` during implementation if it has no independent database-free verification purpose and duplicates the dedicated Testcontainers context test. Do not add a formal replacement test merely to preserve a test class.

### 10.2 Functional Assertions

The integration test verifies these observable outcomes:

1. The complete Spring application context starts.
2. The application `DataSource` uses the running Testcontainers MySQL instance.
3. The connected database name equals the container database name.
4. The connection host and mapped port equal the container connection information.
5. `flyway_schema_history` is queryable through the application `DataSource`.
6. The count of successful versioned application Migrations is zero.
7. The current database contains zero application base tables after excluding `flyway_schema_history`.
8. `spring.jpa.hibernate.ddl-auto=validate` is applied.
9. Hibernate and Spring SQL initialization create no application table.

Do not include a complete JDBC URL or credentials in assertion messages. Do not assume that the total row count in `flyway_schema_history` is zero. Select only successful versioned application Migrations when checking the zero-Migration condition.

Count application tables through `information_schema.tables` using both `table_schema = DATABASE()` and `table_type = 'BASE TABLE'`, excluding `flyway_schema_history`.

### 10.3 `.env` Independence Evidence

Determine independence in two stages:

1. The automated test functionally proves that the actual `DataSource` uses the Testcontainers MySQL instance.
2. Before `project-foundation` completion, `\.\gradlew.bat test` must pass once in a controlled environment where `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`, and a working-directory local `.env` are not provided.

Do not read, move, delete, or modify an existing user's `.env` to construct this controlled environment. If a clean working environment or CI-equivalent environment is unavailable, report this verification as unexecuted and do not mark `project-foundation` complete.

### 10.4 Verification Commands

The standard verification command is:

```powershell
.\gradlew.bat test
```

Inspect resolved dependency versions and selection reasons with:

```powershell
.\gradlew.bat dependencyInsight --dependency org.flywaydb:flyway-core --configuration runtimeClasspath
.\gradlew.bat dependencyInsight --dependency org.flywaydb:flyway-mysql --configuration runtimeClasspath
.\gradlew.bat dependencyInsight --dependency org.springframework.boot:spring-boot-testcontainers --configuration testRuntimeClasspath
.\gradlew.bat dependencyInsight --dependency org.testcontainers:junit-jupiter --configuration testRuntimeClasspath
.\gradlew.bat dependencyInsight --dependency org.testcontainers:mysql --configuration testRuntimeClasspath
```

Where the required Compose environment variables are available locally, validate Compose configuration separately with:

```powershell
docker compose config --quiet
```

Do not copy resolved Compose configuration, `.env` contents, database URLs, usernames, passwords, or container credentials into documentation or completion reports.

### 10.5 Docker-Present and Docker-Absent Outcomes

When Docker is available:

- the MySQL integration test must execute without being skipped and pass before MySQL integration verification is successful
- a real Docker-capable pass is required before `project-foundation` completion

When Docker is unavailable:

- only the MySQL integration test is recorded as skipped and remaining tests continue
- a successful Gradle task does not count as successful MySQL verification
- MySQL integration verification is reported as unexecuted
- the `project-foundation` completion criteria remain unmet

### 10.6 Completion Reporting

Report only applicable items:

- changed files
- resolved Flyway and Testcontainers versions
- commands executed
- passed, failed, and skipped test counts
- whether the MySQL integration test actually executed
- Docker-unavailable behavior when observed
- Compose configuration-validation result
- unexecuted verification and remaining risk

Do not report secret values or fabricate an unrecorded progress or match rate.

## 11. Remaining Design Decisions

None. `PROJECT-FOUNDATION-001` through `PROJECT-FOUNDATION-007` define the complete approved Design for this bounded feature.

Any contradiction found during implementation must be reported and handled through an explicit superseding decision. Do not silently weaken or implement around an approved boundary.

## 12. Decision Log

| ID | Date | Status | Decision |
|----|------|--------|----------|
| PROJECT-FOUNDATION-001 | 2026-08-06 | Approved | Limit the feature to Flyway, Hibernate schema validation, environment configuration, Docker Compose MySQL compatibility, and scoped MySQL Testcontainers verification. |
| PROJECT-FOUNDATION-002 | 2026-08-06 | Approved | Do not create an empty baseline, comment-only Migration, `.gitkeep`, placeholder table, or arbitrary metadata table. Keep `baseline-on-migrate` disabled; verify Flyway history with zero versioned application Migrations; let the first schema-owning feature, currently `user-registration`, create the first real Migration. |
| PROJECT-FOUNDATION-003 | 2026-08-06 | Approved | Keep the dedicated MySQL integration test in the normal `test` source set with `@Testcontainers(disabledWithoutDocker = true)`. Do not add a separate integration-test task, common container base, or global framework. Run all tests through `.\gradlew.bat test`; report Docker-unavailable execution as skipped rather than successful MySQL evidence, and require one real Docker-capable pass before feature completion. Retain the existing `contextLoads()` only if it has a meaningful database-independent purpose without weakening production auto-configuration or substituting an in-memory database. |
| PROJECT-FOUNDATION-004 | 2026-08-06 | Approved | Use Spring Boot `3.5.10` dependency management for versionless Flyway core/MySQL and Testcontainers JUnit Jupiter/MySQL declarations. Do not add duplicate core, separate BOMs, locking, version catalogs, or automated dependency tooling. Add `spring-boot-testcontainers` only if `@ServiceConnection` is later approved. Verify resolved selections with `dependencyInsight`; keep Docker Compose and Testcontainers on `mysql:8.4.5`, and rerun migration, validation, and MySQL verification after Spring Boot or image changes. |
| PROJECT-FOUNDATION-005 | 2026-08-06 | Approved | Add versionless `spring-boot-testcontainers` and use `@Container` plus unrestricted `@ServiceConnection` on the static `mysql:8.4.5` container so DataSource and Flyway receive connection details from the same database. Do not register datasource or Flyway credentials through dynamic, test-property, profile, or system-property overrides. Verify actual DataSource, Flyway history, zero versioned Migrations, Hibernate validation, external-DB independence, and absence of application tables rather than internal connection-detail bean classes. Use `@DynamicPropertySource` only after a separately approved, evidence-backed amendment. |
| PROJECT-FOUNDATION-006 | 2026-08-06 | Approved | Keep one common `application.yml` with optional working-directory `.env` import, environment-variable datasource settings, Hibernate validation, enabled non-baselining Flyway, and disabled SQL initialization and default SQL logging. Define six application/Compose variables, a names-only blank `.env.example`, required Compose interpolation except the `3306` port default, non-root application access, existing-volume cautions, service-connection independence, placeholder compatibility verification, and secret-safe validation and reporting. |
| PROJECT-FOUNDATION-007 | 2026-08-06 | Approved | Add exactly one dedicated `ProjectFoundationMySqlIntegrationTest` using `@SpringBootTest`, conditional Testcontainers execution, `@Container`, unrestricted `@ServiceConnection`, and `mysql:8.4.5`. Verify the actual container connection, Flyway history, zero successful versioned application Migrations, zero application base tables, Hibernate validation, and absence of automatic table creation without exposing connection values. Require a Docker-capable, `.env`-independent pass before feature completion and report Docker-unavailable execution as skipped and unexecuted MySQL evidence. |

## 13. Design Completion Checklist

- [x] First Migration ownership and no-baseline strategy approved
- [x] Empty-database Flyway and Hibernate verification outcomes defined
- [x] Current validation evidence explicitly limited to the empty domain-mapping scope
- [x] Docker-availability and Testcontainers execution strategy approved
- [x] Dependency configuration and version authority approved
- [x] MySQL Testcontainers service-connection strategy approved
- [x] Environment configuration structure and responsibility contract approved
- [x] Exact verification and reporting contract approved
- [x] Design reviewed and explicitly completed

## Version History

| Version | Date | Change | Author |
|---------|------|--------|--------|
| 1.0.0 | 2026-08-06 | Added approved `PROJECT-FOUNDATION-007`, resolved all remaining decisions, and completed the Design | Project decision record |
| 0.5.0 | 2026-08-06 | Added approved `PROJECT-FOUNDATION-006` environment configuration contract | Project decision record |
| 0.4.0 | 2026-08-06 | Added approved `PROJECT-FOUNDATION-005` service-connection strategy | Project decision record |
| 0.3.0 | 2026-08-06 | Added approved `PROJECT-FOUNDATION-004` dependency and version-management strategy | Project decision record |
| 0.2.0 | 2026-08-06 | Added approved `PROJECT-FOUNDATION-003` test execution strategy | Project decision record |
| 0.1.0 | 2026-08-06 | Initial Design with approved `PROJECT-FOUNDATION-002` | Project decision record |
