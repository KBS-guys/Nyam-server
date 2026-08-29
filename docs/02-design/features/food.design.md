# food - Design Document

> **Summary**: Approved minimal schema, manual Spring Batch import, restart policy, and authenticated food search/detail contract
>
> **Version**: 1.0.0 <br>
> **Date**: 2026-08-27 <br>
> **Status**: Approved <br>
> **Decision**: `FOOD-002` <br>
> **Plan**: `docs/01-plan/features/food.plan.md` <br>
> **Related Issue**: [#14](https://github.com/KBS-guys/Nyam-server/issues/14) <br>
> **Authority**: `FOOD-001`, `FOUNDATION-006`, `FOUNDATION-006-R2`

---

## 1. Design Goals

- Import the complete 317,766-row MFDS reference through one manually launched, restartable Spring Batch Job.
- Keep ordinary API startup independent from the import path.
- Preserve stable food identity, explicit units, and the distinction between missing and zero nutrient values.
- Provide the smallest authenticated prefix-search and detail API needed by the later meal snapshot flow.
- Demonstrate chunk transaction behavior and actual-MySQL constraints without staging releases, scheduling, or an operations platform.

## 2. Current Baseline and Source Contract

- Spring Boot is 3.5.10 on Java 17 with JPA, Flyway, Spring Security Resource Server, MySQL, and Testcontainers.
- Spring Batch is not yet a dependency, and no food code, configuration, or Migration exists.
- Current security permits only the authentication endpoints and requires a valid Bearer Access Token for other `/api/v1/**` routes.
- The reference snapshot has 317,766 data rows, 45 unique headers, UTF-8 BOM encoding, and a 2026-06-26 reference date.
- `식품코드` is present and unique in the snapshot and has a fixed 19-character format.
- Nutrition basis is either `100g` or `100ml`.
- The source file remains local-only. Neither the document nor runtime logging records raw rows or the source path when it may disclose local environment details.

A read-only profile of the approved local snapshot on 2026-08-27 confirmed 317,766 records with 45 fields each, no food-code/type-prefix mismatch, only `100g` and `100ml` basis values, and at most two effective decimal places in the four imported nutrients. These observations describe the approved snapshot; the validation rules below remain the runtime contract.

### 2.1 Supported CSV Shape and Field Mapping

The supported header sequence is exactly:

```text
식품코드,식품명,데이터구분코드,데이터구분명,영양성분함량기준량,에너지(kcal),수분(g),단백질(g),지방(g),회분(g),탄수화물(g),당류(g),식이섬유(g),칼슘(mg),철(mg),인(mg),칼륨(mg),나트륨(mg),비타민 A(μg RAE),레티놀(μg),베타카로틴(μg),티아민(mg),리보플라빈(mg),니아신(mg),비타민 C(mg),비타민 D(μg),콜레스테롤(mg),포화지방산(g),트랜스지방산(g),폐기율(%),출처코드,출처명,식품중량,수입여부,원산지국코드,원산지국명,품목제조보고번호,업체명,제조사명,수입업체명,유통업체명,데이터생성방법코드,데이터생성방법명,데이터생성일자,데이터기준일자
```

- Input is UTF-8 comma-delimited CSV. One leading BOM is stripped before comparing the header.
- Parsing is quote-aware, including commas and escaped double quotes inside quoted fields.
- Embedded CR or LF characters inside quoted fields are unsupported in the first implementation. A quoted multiline field is an invalid data record and fails the current chunk and Job.
- Every logical data record must produce exactly 45 fields. A blank record, malformed quote, or different field count is an invalid data record.
- Header order, spelling, unit suffixes, and count must match exactly. Missing, additional, duplicate, or reordered headers are unsupported file-level structure.
- The first implementation reads the complete record for structural validation but maps only the fields below. Unmapped source fields are not persisted.

| Source header | Destination meaning |
|---------------|---------------------|
| `식품코드` | `source_food_code` |
| `식품명` | `food_name` and derived `normalized_name` |
| `데이터구분코드` | `food_type` |
| `영양성분함량기준량` | `basis_amount` and `basis_unit` |
| `에너지(kcal)` | `energy`; unit is `KCAL` |
| `탄수화물(g)` | `carbohydrate`; unit is `G` |
| `단백질(g)` | `protein`; unit is `G` |
| `지방(g)` | `fat`; unit is `G` |

## 3. Architecture and Execution Boundary

```text
explicit food-import launch
  -> validate required parameters, readable file, checksum, and exact supported headers
  -> Spring Batch JobRepository records Job and Step state
  -> streaming CSV reader
  -> row validation and normalization processor
  -> JDBC batch upsert writer
  -> MySQL foods table

authenticated API request
  -> food Controller and DTO validation
  -> read-only food service
  -> JPA repository prefix/detail query
  -> common ApiResponse envelope
```

- Add `spring-boot-starter-batch` and use the existing application DataSource.
- Configure the JobRepository as a JDBC-backed persistent repository using the existing application DataSource and one designated JDBC Batch transaction manager. The Batch JobRepository, validation Step, and chunk Step use this same transaction manager, and the JDBC food writer uses the same DataSource and participates in the chunk transaction. A resourceless, in-memory, or otherwise non-persistent JobRepository is not allowed because restart state and execution history must survive process termination.
- The existing JPA transaction manager remains responsible for API-side JPA access. Batch processing does not mix JPA writes into the JDBC chunk transaction, and it does not use a different transaction manager for JobRepository metadata and food chunk writes.
- Set `spring.batch.job.enabled=false` in the ordinary application configuration.
- Provide a dedicated manual `food-import` launch mode that runs without a web server and invokes the Job explicitly.
- The manual launch requires an input path, source release date, and SHA-256 checksum. The path is a process-level launch option, not a Spring Batch Job Parameter, and must be supplied again for every initial execution or restart.
- Only release date and checksum are persisted as Job Parameters. Raw rows and the local source path are excluded from Job Parameters, logs, API responses, and retained manual evidence.
- Exact command, option, and class names remain implementation choices.
- A public or administrator HTTP import endpoint is not created.
- Concurrent executions of different `foodImportJob` instances are unsupported. The operator must not launch another food import while one is running; distributed locking and complete cross-process race prevention are out of scope.

## 4. Database Design

### 4.1 `foods`

| Column | Type | Null | Constraint and meaning |
|--------|------|------|------------------------|
| `food_id` | `BIGINT` | No | Auto-increment primary key used by Nyamlog relationships and APIs |
| `source_food_code` | `VARCHAR(19)` | No | Unique stable MFDS identity matching `[PD][0-9]{3}-[0-9]{9}-[0-9]{4}` |
| `food_name` | `VARCHAR(500)` | No | Source display name |
| `normalized_name` | `VARCHAR(500)` | No | NFKC, trimmed, whitespace-collapsed, lowercase search value; indexed |
| `food_type` | `CHAR(1)` | No | Source type `P` or `D`; must equal the first character of `source_food_code` |
| `basis_amount` | `DECIMAL(12,4)` | No | Parsed nutrition basis amount; `100.0000` in the supported source |
| `basis_unit` | `VARCHAR(8)` | No | `G` or `ML` |
| `energy` | `DECIMAL(12,4)` | Yes | Source energy value |
| `energy_unit` | `VARCHAR(8)` | No | `KCAL` |
| `carbohydrate` | `DECIMAL(12,4)` | Yes | Source carbohydrate value |
| `carbohydrate_unit` | `VARCHAR(8)` | No | `G` |
| `protein` | `DECIMAL(12,4)` | Yes | Source protein value |
| `protein_unit` | `VARCHAR(8)` | No | `G` |
| `fat` | `DECIMAL(12,4)` | Yes | Source fat value |
| `fat_unit` | `VARCHAR(8)` | No | `G` |
| `created_at` | `DATETIME(6)` | No | First insertion time |
| `updated_at` | `DATETIME(6)` | No | Last source update time |

Database constraints enforce unique `source_food_code`, the allowed code pattern, `food_type` equality with the code prefix, `basis_amount = 100.0000`, column-specific units, and non-negative nutrient values when present. The source-code pattern and `P`/`D` type checks are case-sensitive regardless of the column's default collation. `energy_unit` is exactly `KCAL`; `basis_unit` is `G` or `ML`; and the three macronutrient units are exactly `G`.

All basis and nutrient numbers are parsed as Java `BigDecimal`. A non-null nutrient must be between `0.0000` and `99999999.9999` and must be representable at scale 4 without changing its numeric value. Fewer decimal places are zero-padded; implicit MySQL or Java rounding is forbidden. More than four effective decimal places or a value outside the `DECIMAL(12,4)` range fails the row. Unusually high values within the representable range are preserved rather than rejected by an additional policy threshold.

The exact source basis strings `100g` and `100ml` map to `100.0000/G` and `100.0000/ML`. Every other basis string is unsupported.

The first implementation stores only the four nutrients required by Issue #14 and the later daily major-nutrient total. Product weight, manufacturer, micronutrients, quality flags, release tables, and soft inactivation are excluded.

### 4.2 Spring Batch Metadata

- Copy the MySQL metadata DDL supplied by the resolved Spring Batch version into versioned Flyway Migration files.
- Configure `spring.batch.jdbc.initialize-schema=never`; Hibernate does not create Batch or food tables.
- Use the standard `BATCH_*` tables as the only persisted import-history model. Do not add a custom import-history table.
- Verify both an empty-schema migration and Hibernate `ddl-auto=validate` against actual MySQL.

## 5. CSV and Batch Processing Contract

### 5.1 Preflight Validation

Before the chunk Step writes data, a validation Step:

1. Confirms that the input is a readable regular file.
2. Computes SHA-256 and requires an exact match with the identifying checksum parameter.
3. Parses the UTF-8 BOM-aware header.
4. Requires the exact supported 45-column header sequence and file-level encoding, delimiter, and header contract defined in section 2.1.
5. Confirms the declared release date parameter is a valid date.

The validation Step is configured with `allowStartIfComplete(true)` or an equivalent flow guarantee so it runs before the chunk Step on every initial execution and every restart. A preflight failure on an initial execution marks the Job failed and writes no food row. A preflight failure during restart performs no additional food write; chunks committed by the earlier failed execution remain.

Preflight validates file readability, checksum, encoding/delimiter/header compatibility, and parameters without loading all data rows into memory. Record-level quoting and the 45-field count are checked while the Reader streams each record. A record-level structure failure therefore rolls back its current chunk and fails the Job, while earlier committed chunks remain.

### 5.2 Reader, Processor, and Writer

- The Reader streams the CSV, parses quoted fields, requires exactly 45 fields per logical record, and does not retain all rows.
- The Processor validates the mapped fields, enforces source-code/type consistency and exact decimal representability, normalizes the searchable name, and preserves optional nutrient values that are blank after surrounding-whitespace removal as `NULL`.
- Validate the original `food_name` and derived `normalized_name` independently. Each must be non-blank and at most 500 characters after its applicable processing; normalization must never rely on database truncation to fit `VARCHAR(500)`.
- The Writer uses JDBC batching and MySQL upsert by `source_food_code`; JPA is reserved for API reads and mapping validation.
- Upsert equality is null-safe and covers every persisted source-derived or derived search field. `created_at` never changes during upsert, and `updated_at` changes only when at least one compared field changes.
- A chunk is one database transaction. The initial chunk size is 500 and may later be tuned without changing the public contract.
- A writer failure rolls back only the current chunk and marks the Step and Job failed. Earlier committed chunks remain.

### 5.3 Row Classification

| Input condition | Outcome |
|-----------------|---------|
| Unsupported encoding, delimiter, or header sequence | Fail in preflight before writes |
| Quoted field contains embedded CR or LF | Fail the current chunk and Job |
| Blank record, malformed quote, or data record not containing 45 fields | Fail the current chunk and Job |
| Missing or malformed source food code | Fail the current chunk and Job |
| Food type is not `P` or `D`, or does not match the code prefix | Fail the current chunk and Job |
| Original `food_name` is blank or exceeds 500 characters | Fail the current chunk and Job |
| Derived `normalized_name` is blank or exceeds 500 characters | Fail the current chunk and Job |
| Basis is not exactly `100g` or `100ml` | Fail the current chunk and Job |
| Optional nutrient field is empty after surrounding whitespace is removed | Store `NULL` |
| Optional nutrient is non-blank after surrounding whitespace is removed but not a valid non-negative decimal | Fail the current chunk and Job |
| Nutrient has more than four effective decimal places or exceeds `DECIMAL(12,4)` | Fail the current chunk and Job |
| Structurally valid but unusually high nutrient value within the database range | Preserve the source value |
| Valid row excluded only by a future approved business policy | Filter; none exists in this Design |

The initial policy is fail-fast with `skipLimit=0`; `skipCount` and `filterCount` therefore remain zero unless a later approved Design revision introduces an explicit policy. Framework read, write, filter, skip, commit, and rollback counts remain observable for every `StepExecution`. A restarted Job retains the states and counts of each attempt rather than replacing earlier execution evidence.

Nutrient parsing removes only surrounding whitespace before classification and decimal parsing. For example, `" 1.5 "` is stored as `1.5000`, while `"1 5"`, `"-"`, and other values that would require removing or rewriting internal characters fail the current chunk and Job.

`writeCount` is the number of items successfully processed by the Writer. It includes inserted, changed, and unchanged no-op upsert items and does not separately report affected database-row counts for those categories.

## 6. Job Identity, Restart, and Rerun

The Job Instance is identified by:

- Job name `foodImportJob`
- source release date as an identifying Job Parameter
- lowercase SHA-256 checksum as an identifying Job Parameter

For this bounded manual import, “identical input” means the same release-date and checksum pair. The release date is trusted operator metadata for the explicitly approved source. The same bytes declared under a different release date intentionally form a new release and a new Job Instance; this feature does not independently derive or verify a release date from file contents.

The local input path is a non-persisted process option. Before every initial run or restart, the preflight Step recomputes the checksum from the currently supplied path, so a moved file is allowed but changed content is not. The identifying release date and checksum cannot change during restart because changing either selects a different Job Instance.

The supplied source file must remain immutable from the start of preflight until Job completion. Modifying or replacing it during execution is unsupported and invalidates that execution as import evidence. The first implementation relies on this manual operating precondition and does not create a staging copy or hold a cross-process file lock.

| Situation | Result |
|-----------|--------|
| Failed execution with the same release and checksum | Restart the same Job Instance from persisted execution state after preflight succeeds |
| File content changes before restart | Reject before the chunk Step because checksum no longer matches |
| Successful execution with the same release and checksum | Reject without new food writes and report that the input is already complete |
| Different checksum or release date | Create a new Job Instance; the same checksum with a different release date is intentionally treated as a new declared release |
| Existing source code with changed values in a new instance | Update the stored source fields and `updated_at` |
| Existing source code with no changed source value | Keep the row and `updated_at` unchanged |
| New source code | Insert a new food row |
| Source code absent from a later file | Keep the existing row; release comparison and inactivation are out of scope |

`RunIdIncrementer` is not used because an arbitrary run ID would bypass meaningful input identity and restart behavior.

The CSV Reader participates in Spring Batch restart state persistence. Its position is checkpointed in the Step `ExecutionContext` at successful chunk commits and restored when the same failed Job Instance restarts. The restarted execution reads and sends to the Processor and Writer only records after the last committed checkpoint. Records read in a chunk that rolled back are not considered committed and are read again; restarting from the first data record after an earlier committed chunk is not allowed.

## 7. API Contract

Both endpoints require the existing Bearer Access Token.

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/foods/search?query={query}` | Return at most 20 normalized prefix matches |
| `GET` | `/api/v1/foods/{foodId}` | Return one food and its main nutrition detail |

### 7.1 Search Rules

- `query` is required, trimmed, and must contain 1 to 100 characters after normalization.
- Apply Unicode NFKC normalization, collapse consecutive whitespace, and lowercase with `Locale.ROOT`.
- Escape SQL wildcard characters and search `normalized_name` by a literal prefix.
- Return at most 20 rows ordered by `normalized_name`, then `source_food_code` for deterministic duplicates.
- Expose the internal `foodId`, display `name`, and nutrition basis. Do not expose the external source code.

Example `data`:

```json
[
  {
    "foodId": 1,
    "name": "국밥_돼지머리",
    "nutritionBasis": {
      "amount": 100,
      "unit": "g"
    }
  }
]
```

### 7.2 Detail Rules

Example `data`:

```json
{
  "foodId": 1,
  "name": "국밥_돼지머리",
  "nutritionBasis": {
    "amount": 100,
    "unit": "g"
  },
  "energy": {
    "value": 137,
    "unit": "kcal"
  },
  "carbohydrate": {
    "value": 15.94,
    "unit": "g"
  },
  "protein": {
    "value": 6.70,
    "unit": "g"
  },
  "fat": {
    "value": 5.16,
    "unit": "g"
  }
}
```

A missing nutrient uses `"value": null` while retaining its explicit unit. JSON numbers do not promise a fixed trailing-zero representation.

Public unit strings are a stable API contract: nutrition-basis units are `g` or `ml`, energy uses `kcal`, and carbohydrate, protein, and fat use `g`. Persistence values `G`, `ML`, and `KCAL` are mapped to these lowercase response values and are not exposed directly.

### 7.3 Envelope and Errors

- Successful responses use the existing `ApiResponse` envelope and generic success code `S000`.
- Missing or invalid search input returns HTTP 400 with `INVALID_INPUT`.
- Missing or invalid authentication returns the existing HTTP 401 code `E003`.
- A non-numeric, zero, or negative `foodId` returns HTTP 400 with `INVALID_INPUT`.
- A syntactically valid positive `foodId` that does not exist returns HTTP 404 with `FOOD_NOT_FOUND` and a safe Korean message.
- Internal parsing, SQL, Batch, and filesystem details are never returned by the API.
- Controller and DTO Swagger annotations and public error descriptions are written in Korean.

## 8. Verification Contract

| Layer | Required evidence |
|-------|-------------------|
| Unit | Exact header and source-field mapping, quoted-record, embedded-newline rejection, and field-count validation, basis parsing, surrounding-whitespace/blank/invalid/out-of-range numeric classification, independent original/normalized name limits, normalization, and source-code/type validation |
| Application startup | Ordinary API context starts without launching `foodImportJob` |
| Actual MySQL Batch | Empty Flyway migration, successful fixture import, case-sensitive code/type and unit constraints, exact decimal persistence, unique external code, nullable nutrients, null-safe unchanged upsert, counts, and final states |
| Transaction | JobRepository and the chunk Step use the designated JDBC Batch transaction manager; a forced writer failure rolls back the failed chunk while retaining an earlier committed chunk |
| Restart | Force failure after at least one committed chunk; completed preflight runs again; the same-identity restart restores the persisted Reader checkpoint and reads/writes only records after the last committed position, including reprocessing the rolled-back uncommitted chunk; verify this through the restarted `StepExecution.readCount` or captured Writer input rather than final upserted rows alone; changed input fails before Reader resume; completed identical input creates no new writes |
| Identity | Same checksum with a different release date follows the intentional new-release policy; null-safe equal source values preserve `created_at` and `updated_at` |
| Execution boundary | Concurrent imports and in-run source-file mutation are unsupported; manual evidence confirms the single-import and file-immutability preconditions |
| Web | Authenticated prefix search, deterministic limit, stable lowercase units, detail with `NULL`, invalid ID, unauthorized, and not-found responses |
| OpenAPI | Korean operation, field, authentication, validation, and public response documentation |
| Manual | Full local CSV run confirms 317,766 records and records release date, checksum, file size, JobExecution ID, per-attempt Job/Step states and counts, `writeCount` interpretation, and final status without recording raw rows or the local path |

Java or test implementation requires `.\gradlew.bat test javadoc`, actual MySQL tests with zero applicable skips, `git diff --check`, explicit changed-path review, and confirmation that the source CSV and secrets are absent.

## 9. Consolidated Design Review Gate

`FOOD-002` approves the integrated Design of:

- The minimal `foods` schema, precision, units, constraints, and excluded fields
- Exact supported CSV shape and source-field mapping, quoted-multiline rejection, manual launch isolation, Flyway-managed Batch metadata, repeatable preflight, fail-fast rows, and chunk rollback
- A persistent JDBC-backed JobRepository and one JDBC Batch transaction manager for repository metadata and chunk writes, with separate API-side JPA transaction management
- Job identity, non-persisted input-path injection, in-run file immutability, checksum-protected Reader-checkpoint restart, completed-input rejection, null-safe upsert, and declared new-release behavior
- Single-import operation, unsupported concurrent execution, and `writeCount` evidence semantics
- Authenticated search/detail endpoints, normalization, deterministic limit, stable response units, ID validation, and safe errors
- Representative automated and full-source manual evidence

There are no remaining Design decisions. This approval completes only the Design phase. Implementation, dependency, configuration, Migration, Java, tests, staging, commit, push, and Pull Request work require separate authorization.

## Version History

| Version | Date | Change |
|---------|------|--------|
| 1.0.0 | 2026-08-27 | Approved `FOOD-002` as the integrated manual import, restart, persistence, search/detail API, and verification Design |
| 0.4.0 | 2026-08-27 | Required a persistent JDBC JobRepository, made Reader checkpoint restoration directly verifiable, defined nutrient whitespace parsing, and separated original and normalized food-name limits |
| 0.3.0 | 2026-08-27 | Clarified preflight/Reader boundaries, multiline rejection, Batch transaction management, in-run file immutability, write-count meaning, null-safe upsert, case-sensitive codes, and unsupported concurrent imports |
| 0.2.0 | 2026-08-27 | Clarified exact CSV mapping and structure, decimal integrity, repeatable restart preflight, input-path privacy, release identity, schema invariants, API units, and verification evidence |
| 0.1.0 | 2026-08-26 | Created the consolidated `FOOD-002` Design draft for whole-Design review |
