# Nyamlog MVP Foundation - Artifact Audit

> Version: 0.1.0 | Date: 2026-07-27 | Status: Review Draft
> Purpose: Evidence for the `nyamlog-mvp-foundation` Design phase

## 1. Scope

This document reviews the supplied food CSV, API specification PDF, SQL and ERD export, and the current Spring Boot repository. It records verified facts, contradictions with approved decisions, and recommendations that still require Design approval.

No application feature code or source artifact was modified during this audit.

## 2. Sources Reviewed

| Source | Location | Review Method |
|--------|----------|---------------|
| Food nutrition CSV | `data/reference/food/2026-06-26/식품의약품안전처_통합식품영양성분정보_20260626.csv` | Full-file data profile |
| API specification | `docs/reference/api-spec/API 명세서.pdf` | Three-page render and text extraction |
| SQL export | `docs/reference/erd/NyamLog.sql` | DDL inspection |
| ERD image | `docs/reference/erd/NyamLog.png` | Full-resolution visual inspection |
| Public data metadata | `https://www.data.go.kr/data/15100064/standard.do` | Official metadata review |
| Spring Boot repository | Project root | Build, configuration, source, and test inspection |

The public Notion and ERDCloud pages were not directly accessible from the current environment. The supplied PDF, SQL, and PNG exports were used as the review snapshots.

## 3. Executive Findings

1. The food CSV contains 317,766 rows, not approximately 100,000.
2. `식품코드` is complete and unique across all rows and is the strongest external identity candidate.
3. Every nutrition basis is either `100g` or `100ml`, which simplifies unit modeling.
4. Core nutrient completeness differs sharply by data type. Processed foods are mostly complete, while the `음식` category is missing fat in 68.22% and carbohydrates in 65.44% of rows.
5. Missing nutrient values must remain `null`; converting them to zero would produce misleading daily totals.
6. There are extreme values that require quality flags and review, but ingestion must not reject them solely by a simple threshold.
7. The existing API document is an endpoint inventory, not an implementation-ready API contract.
8. The existing SQL and ERD are drafts with invalid SQL, placeholder fields, missing constraints, and models that contradict approved record policies.
9. The current repository is an early skeleton. Authentication, migrations, entities, food ingestion, and the approved error format are not implemented.
10. A new schema and API Design should use the existing artifacts as references rather than patching them in place.

## 4. Food CSV Profile

### 4.1 File Identity

| Item | Result |
|------|--------|
| File size | 87,197,361 bytes |
| SHA-256 | `f5e533297d00bd6edf48af2526b18f3d79540f0782a5842a75157eda56366096` |
| Encoding | UTF-8 with BOM |
| Delimiter | Comma |
| Rows | 317,766 |
| Columns | 45 |
| Duplicate headers | 0 |
| Exact duplicate rows | 0 |
| Data reference date | 2026-06-26 for all rows |

The official public-data page describes this as a standardized integrated nutrition dataset. It lists an annual update cycle, while also noting that institution data can be merged monthly and may have timing differences.

### 4.2 Data Composition

| Type | Code | Rows | Share |
|------|------|------|-------|
| Processed food | `P` | 298,271 | 93.86% |
| Food or prepared dish | `D` | 19,495 | 6.14% |

| Source | Rows |
|--------|------|
| Ministry of Food and Drug Safety | 317,288 |
| Rural Development Administration | 435 |
| Other institutions | 43 |

| Creation Method | Rows |
|-----------------|------|
| Collected | 312,784 |
| Calculated | 3,140 |
| Analyzed | 1,842 |

### 4.3 Identity Candidates

#### Food Code

- Non-missing: 317,766
- Unique: 317,766
- Duplicate occurrences: 0
- Pattern-valid: 317,766
- Fixed length: 19 characters
- Prefix matches the data type: `P` or `D`

Recommendation requiring approval:

- Use an internal numeric primary key for application relationships.
- Store `식품코드` as `source_food_code` with a unique constraint.
- Use `source_food_code` as the stable upsert key for a dataset release.
- Do not rely on the food name or manufacturer as identity.

The internal key keeps application relationships independent of an external identifier format. The unique external code preserves deterministic imports and updates.

#### Other Candidates

| Candidate | Non-missing | Unique | Duplicate Occurrences | Assessment |
|-----------|-------------|--------|-----------------------|------------|
| Food name | 317,766 | 270,866 | 46,900 | Not an identity |
| Report number | 298,271 | 291,600 | 6,671 | Not globally unique |
| Food name and manufacturer | 317,766 rows evaluated | - | 11,402 | Still not an identity |

### 4.4 Nutrition Basis and Product Weight

| Nutrition Basis | Rows |
|-----------------|------|
| `100g` | 272,565 |
| `100ml` | 45,201 |

There are only two nutrition basis values. The database should not store the combined text as the only representation. Recommended fields are:

- `basis_amount = 100`
- `basis_unit = G` or `ML`

`식품중량` is a different concept: the product or package amount. It is blank in 15,240 rows and has 5,568 distinct non-blank values. It should be parsed into amount and unit only when parsing is reliable, while preserving the raw source text for traceability.

### 4.5 Core Nutrient Completeness

#### Processed Foods

| Nutrient | Blank Rows | Blank Rate |
|----------|------------|------------|
| Energy | 0 | 0% |
| Protein | 0 | 0% |
| Fat | 0 | 0% |
| Carbohydrate | 0 | 0% |
| Sugar | 4 | 0.0013% |
| Sodium | 46 | 0.0154% |

#### Prepared Foods or Dishes

| Nutrient | Blank Rows | Blank Rate |
|----------|------------|------------|
| Energy | 0 | 0% |
| Protein | 0 | 0% |
| Fat | 13,300 | 68.22% |
| Carbohydrate | 12,758 | 65.44% |
| Sugar | 149 | 0.76% |
| Sodium | 61 | 0.31% |

Other nutrient columns such as fiber, vitamins, and minerals are missing in more than 95% of the full dataset in many cases.

Design implications:

- Store nutrient values as nullable decimals.
- Never convert a missing source value to numeric zero.
- Daily totals need a completeness indicator when one or more meal items have missing values.
- Search results and details should distinguish `0` from `not provided`.
- The MVP nutrient scope can include energy, carbohydrate, protein, fat, sugar, sodium, saturated fat, trans fat, and cholesterol, but UI and calculations must tolerate missing data.
- Fiber and micronutrients should not be presented as universally available.

### 4.6 Quality Observations

Examples above simple plausibility thresholds exist:

- Energy greater than 1,000 kcal per basis: 7 rows
- Protein greater than 100g per basis: 1 row
- Fat greater than 100g per basis: 22 rows
- Carbohydrate greater than 100g per basis: 25 rows
- Sugar greater than 100g per basis: 4 rows
- Sodium greater than 10,000mg per basis: 565 rows

Some extreme values can be legitimate for concentrates, oils, seasoning powders, or unit-density differences. Others appear suspicious, such as energy values well above physical expectations.

Recommendation requiring approval:

- Import source values without silently changing them.
- Record data-quality warnings separately.
- Exclude only structurally invalid rows from activation.
- Allow suspicious rows to remain traceable while optionally hiding them from default search until reviewed.
- Store the source release date and import job ID on each active source row or version record.

### 4.7 Update Implications

Recommended first operational model:

1. Import the CSV as an explicit administrative job, not at normal application startup.
2. Calculate and store the file checksum and reference date.
3. Load into a staging table.
4. Validate row count, unique food codes, required fields, units, and parseability.
5. Compare staging with the active release by `source_food_code`.
6. Insert new rows, update changed rows, and mark removed rows inactive.
7. Switch the active release only after validation succeeds.
8. Keep job history and the previous usable release for rollback.

Because the official metadata describes an annual update cycle, an always-running daily schedule is not justified for the MVP. A manual import command plus periodic source-checking is sufficient initially. Automatic scheduling can be added after the update workflow is proven.

## 5. API Specification Audit

### 5.1 Current Content

The PDF contains 38 endpoint inventory rows across:

- Authentication and onboarding
- Foods and favorites
- Recent searches
- Water and weight
- Diets and meal sets
- Calendar achievements
- Notifications
- Analysis and reports
- User profile and nutrition
- AI history and chat

The document records feature names, methods for many rows, and endpoint paths for many rows. It does not define request fields, response fields, status codes, validation, authorization, error codes, pagination, or ownership rules.

### 5.2 Useful Endpoint Directions

The following paths are useful references and can be retained or refined:

- `/auth/login`
- `/foods/search`
- `/foods/{foodId}`
- `/foods/{foodId}/favorites`
- `/foods/favorites`
- `/foods/custom`
- `/records/water`
- `/records/weight`
- `/users/me`
- `/users/me/nutrition`

### 5.3 Required Revisions

#### Authentication

- Social login has no endpoint path.
- Local login has an endpoint but the full token and cookie behavior is undefined.
- Signup, email verification, resend, token refresh, logout, and account linking are missing.
- `아이디 찾기` conflicts with email-as-login and should be removed.
- Password reset needs request and confirmation endpoints rather than one unspecified operation.
- Onboarding state and nutrition-goal creation need explicit contracts.

#### Food

- Search needs query, filter, sort, and pagination parameters.
- Custom food management is missing lookup and update operations.
- Favorites need uniqueness and ownership behavior.
- Recent search is deferred from the MVP unless explicitly restored.
- Missing nutrient values need an explicit JSON representation.

#### Meals

- `GET /diets/{mealType}` has no date and is ambiguous.
- Meal update and deletion are missing.
- A meal containing multiple food items and snapshots is not represented.
- Daily summary needs a date parameter and completeness semantics.
- Meal sets are deferred.

#### Water and Weight

- Current endpoints model only summary lookup and creation.
- Approved policy requires individual events and multiple weight records, so list, update, and delete behavior must be defined.
- Date-range and daily-summary queries should be separated from event resources.

#### Deferred Areas

- Calendar achievements
- Notifications
- Advanced analysis
- AI reports, history, and chat

These should not influence the first schema unless an approved MVP change restores them.

### 5.4 API Recommendation

Use the existing HTTP methods and paths as migration inputs, not as a fixed contract. The replacement OpenAPI Design must define:

- Request and response DTOs
- Correct HTTP status codes
- `ApiResponse<T>` success bodies
- RFC 9457 error bodies
- Validation and application error codes
- Authentication and ownership
- Pagination and sorting
- Date and time representation
- Examples and acceptance tests

## 6. SQL and ERD Audit

### 6.1 Existing Tables

The draft includes:

- `users`
- `social_accounts`
- `user_goal`
- `foods`
- `user_favorite_foods`
- `meal_log`
- `water_daily`
- `weight_history`
- `daily_progress_summary`
- `chat_thread`
- `chat_message`

### 6.2 Structural Problems

- Several columns remain named `Field`, `Field2`, and similar placeholders.
- `JASON` is an invalid type and appears to mean `JSON`.
- Several `ENUM` declarations and defaults are not valid MySQL DDL.
- `VARCHAR` is used without a length.
- `AUTO_INCREMENT` is incorrectly applied to foreign-key `user_id` columns.
- Composite primary keys combine generated IDs with `user_id` without a demonstrated need.
- `users.email` has no unique constraint.
- Favorite foreign keys and uniqueness constraints are missing.
- Social provider identity uniqueness is missing.
- Many foreign keys shown in the ERD are absent from the SQL export.
- Chat identifiers and timestamps use generic strings.
- Audit and soft-delete fields are copied broadly without lifecycle justification.

### 6.3 Contradictions with Approved Decisions

- `water_daily` stores one daily aggregate, but approved behavior stores individual water-intake events.
- `meal_log` stores totals but has no meal-item table or nutrition snapshot.
- `foods` cannot adequately distinguish source foods, private custom foods, source release, and data quality.
- `user_goal` stores only one current row and lacks formula version, target period, and history.
- `users` requires onboarding health fields immediately, which conflicts with email verification, social signup, and incomplete onboarding states.
- Authentication tables for verification, password reset, and refresh sessions are absent.
- AI tables are included even though AI is deferred.
- `daily_progress_summary` is undefined and may be derivable rather than persisted.

### 6.4 Schema Direction Requiring Design

Candidate core entities:

- `User`
- `UserProfile`
- `SocialAccount`
- `EmailVerificationToken`
- `PasswordResetToken`
- `RefreshSession`
- `NutritionGoal`
- `Food`
- `SourceFoodDetail` or source metadata fields
- `CustomFood`
- `FavoriteFood`
- `Meal`
- `MealItem`
- `WaterIntake`
- `WeightMeasurement`
- `FoodDatasetRelease`
- `FoodImportJob`

This is a candidate list, not an approved final ERD. The next schema Design must decide whether public and custom foods use one table with explicit source type or separate subtype tables.

## 7. Current Repository Audit

### 7.1 Verified Stack

- Java 17
- Spring Boot 3.5.10
- Spring MVC
- Spring Data JPA
- MySQL Connector/J
- Gradle Wrapper
- MySQL 8.4.5 Docker service

### 7.2 Current Implementation State

- Spring Security dependencies are commented out.
- There are no JPA entities or repositories.
- There is one `/test` endpoint.
- `ApiResponse` uses `S000` and does not include the approved tracking fields.
- Errors are returned through `ApiResponse`, not RFC 9457 `ProblemDetail`.
- `INVALID_INPUT` incorrectly maps to `502 Bad Gateway`.
- The general exception handler does not log the exception despite having a logger.
- `Timestamped` uses `LocalDateTime` and does not by itself guarantee UTC audit instants.
- JPA auditing enablement and auditor resolution are not present.
- `application.yml` uses `ddl-auto: update`, SQL logging, and schema/data initialization settings that must not be used unchanged in production.
- There is no migration tool.
- `data.sql` is empty.
- The only test is an application-context load test.

### 7.3 Implementation Implication

The repository is sufficiently early that correcting the common foundation before entities are added is lower risk than preserving the current placeholder structures. Existing files should still be changed only through a dedicated `project-foundation` Design and implementation PDCA.

## 8. Draft Glossary

| Term | Meaning |
|------|---------|
| Source Food | Food imported from a public external dataset |
| Custom Food | Private food created and owned by a user |
| Nutrition Basis | Amount and unit to which source nutrient values apply, currently 100g or 100ml |
| Product Weight | Package or total product amount, distinct from nutrition basis |
| Meal | A user's eating event grouped by date, time, and meal type |
| Meal Item | One consumed food and amount inside a meal |
| Nutrition Snapshot | Nutrient values copied to a meal item at recording time |
| Nutrition Goal | Versioned target calories, macros, weight, period, and calculation inputs |
| Water Intake | One timestamped water-consumption event |
| Weight Measurement | One timestamped body-weight record |
| Dataset Release | One validated source-file version identified by reference date and checksum |
| Import Job | One attempt to validate and activate a dataset release |

## 9. Recommended Design Order

1. Approve the artifact-audit findings.
2. Decide the source-food identity and release/import lifecycle.
3. Decide missing nutrient and data-quality behavior.
4. Design the core glossary and ERD without deferred AI and notification tables.
5. Fix nutrition units and calculation rules.
6. Design authentication and account lifecycle.
7. Rewrite the MVP OpenAPI contract.
8. Design backend conventions, migrations, logging, and tests.
9. Create bounded feature-level PDCA plans.

## 10. Decisions Requested Next

The next discussion should approve or revise these recommendations:

1. Use an internal numeric food ID plus unique `source_food_code`.
2. Preserve missing nutrients as `null` and expose completeness information.
3. Model nutrition basis as amount plus `G` or `ML`.
4. Use staging, validation, release activation, and rollback for CSV updates.
5. Start with a manual administrative import and periodic source check rather than an automatic daily schedule.
6. Treat suspicious numeric values as quality warnings rather than silently correcting or automatically deleting them.
7. Replace `water_daily` with individual `water_intake` events.
8. Split meals into `meal` and `meal_item` with nutrition snapshots.
9. Remove deferred AI, notification, meal-set, and advanced-analysis structures from the MVP ERD.
10. Rewrite the API contract while retaining useful existing paths where they still match the approved domain.
