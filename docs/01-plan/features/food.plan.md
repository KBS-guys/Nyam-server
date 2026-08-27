# food - Plan Document

> **Summary**: Import the MFDS food CSV through a manual Spring Batch job and expose the first food-domain search and detail flow
>
> **Version**: 1.0.0 <br>
> **Date**: 2026-08-26 <br>
> **Status**: Approved <br>
> **Decision**: `FOOD-001` <br>
> **Related Issue**: [#14](https://github.com/KBS-guys/Nyam-server/issues/14) <br>
> **Scope Authority**: `FOUNDATION-006`, `FOUNDATION-006-R2`

---

## 1. Purpose

Import the complete Ministry of Food and Drug Safety integrated food-nutrition CSV into MySQL through Spring Batch without loading the whole file into memory. The imported foods support normalized name-prefix search and nutrition-detail lookup, and become the stable source data from which the later `meal` feature creates nutrition snapshots.

This feature demonstrates Spring Batch Job and Step state, streaming, chunk transactions, restart, and rerun behavior through one explainable data-ingestion flow. It does not build scheduled synchronization or a general operations platform.

## 2. Current Baseline

- The repository has no food-domain code, food Migration, or Spring Batch dependency.
- Flyway, JPA, MySQL Testcontainers, the common API envelope, and Spring Security Bearer authentication already exist.
- Foundation requires stable external food identity, correct nutrition basis and units, and `NULL` preservation for unavailable nutrient values.
- The source CSV is a local-only reference and must not be included in Git, an Issue, a commit, a Pull Request, or a download API.

## 3. Scope

### 3.1 Included

- A Spring Batch Job that is separated from ordinary API startup and runs only with an explicitly approved complete source CSV
- Prevention of automatic food-import execution during ordinary API application startup
- CSV streaming, required-header and supported-structure validation, and chunk-based parsing, validation, and persistence
- A minimal Flyway-managed food schema and JPA mapping
- Stable identity and duplicate prevention through the external food code
- Nutrition-basis amount and unit plus energy, carbohydrate, protein, and fat values and units
- `NULL` preservation for nutrient values not provided by the source
- Job and Step status plus read, write, filter, and skip counts
- Failed-Job restart and successful-input rerun behavior under an approved policy
- Normalized food-name prefix search with a bounded result count
- Food detail lookup and Korean Swagger documentation
- Unit, web, actual-MySQL integration tests, and a manual full-source smoke test

### 3.2 Out of Scope

- Treating the whole file as one transaction with full-file rollback
- `meal` records and `daily-summary` calculation, which belong to separate follow-up features
- Recurring release synchronization, scheduling, staging releases, previous-release retention, and operational rollback
- Distributed execution, operational monitoring, and alerting
- Full-text search, fuzzy search, n-grams, and search-performance benchmarks
- Custom foods, favorites, an operations administrator UI, and a file-upload API
- Publishing the source CSV in Git or through an API

## 4. Acceptance Scenarios

### A. Successful Import

- An approved complete CSV runs manually through streaming and chunk processing and finishes with completed Job and Step states.
- Ordinary API application startup does not run the food-import Job.
- The Job can run only when an approved input file and required identifying Job Parameters are supplied.
- The final Job and Step states and the complete read, write, filter, and skip counts are observable.

### B. File Structure and Row Errors

- A missing required header or unsupported CSV structure fails the Job before food data is written.
- A structurally invalid row follows the approved failure, filter, or skip policy and is never silently stored as a valid row.

### C. Transactions and Re-execution

- A write failure rolls back the failed chunk and records failed Job and Step states.
- Full-file atomicity does not roll back chunks that were committed before the failure.
- A failed Job can restart only after verifying that the input is identical to the original execution; a successful identical-input rerun follows the approved identity and update policy.
- The database constraint and rerun policy prevent duplicate persisted foods for one external food code.

### D. Data Integrity

- An optional nutrient becomes `NULL` only when the source field is actually blank, and API output distinguishes that value from numeric zero.
- A non-blank nutrient that cannot be parsed as a number is not silently converted to `NULL`; it follows the approved invalid-row policy.
- Nutrition-basis amount and unit remain distinct from each major nutrient value and unit in storage and responses.

### E. Search and Detail

- Food-name prefix search returns a bounded, deterministic result.
- Food detail returns nutrition-basis amount and unit together with energy, carbohydrate, protein, and fat values and units.
- A missing food returns a safe error without internal details.
- Authentication, request, response, and error contracts match the approved Design and Korean Swagger documentation.

## 5. Design Approval Topics

The consolidated `food` Design must decide the following without expanding the feature:

- Minimal food schema, external-code constraint, decimal precision, and unit representation
- Manual Job launch mode, input-path injection, and prevention of automatic execution during ordinary API startup
- Flyway Migration of Spring Batch metadata and disabling Spring Boot Batch schema auto-initialization
- JobRepository and JobLauncher environment and the chunk transaction boundary
- Failure, skip, and filter classification for missing required values, blank optional values, invalid numbers, and policy exclusions
- Input identity through identifying Job Parameters, release identity, and checksum, including input immutability during restart
- Failed-Job restart, successful identical-input rerun, new-release handling, and updates to an existing external food code
- Search normalization, result limit, deterministic ordering, authentication, and search/detail response and error contracts
- Evidence retained from the automated fixture tests and manual full-source smoke test

## 6. Verification Boundary

- Unit tests for CSV headers, structure, parsing, and transformation
- Actual-MySQL fixture tests for Migration, important constraints, `NULL` preservation, chunk rollback, and Job state
- Verification that ordinary API startup does not run the import Job
- Verification that Flyway alone prepares the food and Spring Batch metadata schema in an empty actual MySQL database
- Distinct handling of blank nutrient fields and non-blank invalid numeric fields
- Input-immutability verification during failed-Job restart and successful identical-input rerun verification
- Web and OpenAPI contract tests for food search and detail
- Manual full-source execution and final processing-count confirmation
- `.\gradlew.bat test javadoc`
- `git diff --check`
- Confirmation that Git changes contain neither the source CSV nor secrets

## 7. Risks and Mitigation

| Risk | Impact | Plan response |
|------|--------|---------------|
| Full-source size causes high memory use or long execution | High | Use streaming and chunk processing and perform one full-file smoke test. |
| CSV structure changes or columns map incorrectly | High | Validate required headers and the supported structure before any write. |
| Ordinary API startup unexpectedly runs the full import | High | Disable automatic execution and require an explicit manual input. |
| Blank and invalid values become indistinguishable | High | Preserve only true blanks as `NULL` and route invalid non-blank values through the error policy. |
| Chunk failure is mistaken for full-file rollback | Medium | Guarantee only failed-chunk rollback and explicitly retain earlier commits. |
| Restart or rerun creates duplicates or unintended updates | High | Approve input identity, restart, rerun, and update behavior in Design. |
| Batch grows into an operations platform | Medium | Limit the scope to one manual Job and inspectable execution results. |

## 8. Plan Approval

`FOOD-001` approves:

- The purpose, included and excluded scope, and acceptance scenarios of Issue #14
- Manual full-source ingestion through Spring Batch as the central learning goal
- Review of expensive or data-sensitive choices in one consolidated Design

This approval completes only the Plan phase. It does not approve the Design or authorize dependency, configuration, Migration, Java, test, stage, commit, push, or Pull Request work.

## Version History

| Version | Date | Change |
|---------|------|--------|
| 1.0.0 | 2026-08-26 | Approved `FOOD-001` and translated the complete Plan into English |
| 0.2.0 | 2026-08-26 | Added startup isolation, Batch metadata, input classification, and restart immutability |
| 0.1.0 | 2026-08-26 | Created the initial Plan draft from Issue #14 |
