# nyamlog-mvp-foundation - Plan Document

> **Version**: 2.2.0 <br>
> **Date**: 2026-08-23 <br>
> **Status**: Approved (Foundation Complete) <br>
> **Current Scope Decisions**: `FOUNDATION-006-R1`, `LOCAL-LOGIN-001` <br>
> **Supersedes**: the limited-deployment and production-readiness assumptions of `FOUNDATION-002-R1`, the first-MVP inclusion of `FOUNDATION-005`, and detailed Foundation clauses that require operational-scale Food/Auth work

---

## 1. Project Premise

Nyamlog is a learning toy project that the developer deploys and uses directly. The developer may invite a small number of acquaintances to try it voluntarily and provide feedback. It is not intended for commercial launch, exhibition operation, unrestricted public signup, or ongoing service delivery to the general public.

The project is complete enough when one developer can verify the backend and MySQL locally, deploy one personal-use instance, exercise the core flow, explain the database and transaction choices, and run representative automated tests.

The personal deployment needs externalized secrets, persistent data, and a reproducible start procedure. It does not need commercial-production readiness, broad edge-case coverage, large-scale data handling, high availability, or extensive operational automation.

## 2. Learning Goals

- Build REST APIs with Spring Boot using Controller, Service, Repository, Entity, and DTO boundaries.
- Design a small relational schema and evolve it with Flyway.
- Learn service-layer transactions, rollback, database constraints, and user-data ownership.
- Implement basic local authentication without expanding into production identity infrastructure.
- Preserve food and meal data integrity, including unknown nutrition values and immutable meal snapshots.
- Verify important behavior with understandable unit and MySQL integration tests.
- Keep the whole project small enough for one junior developer to explain and maintain.

## 3. Core Toy-Project Flow

The target flow is intentionally small:

1. Verify an email locally with Mailpit.
2. Register and log in with email and password.
3. Register or log in through at least one social provider.
4. Use the same simple Nyamlog Access Token for protected APIs regardless of login method.
5. Load a small or one-time public-food dataset.
6. Search foods and view nutrition details.
7. Create, read, and delete the authenticated user's meal records.
8. View daily calories, carbohydrate, protein, and fat totals.
9. Verify the flow through Swagger and representative tests.
10. Deploy one reasonably protected instance that the developer and a few invited acquaintances can try.

## 4. Explicitly Deferred

- Commercial or public-service launch, unrestricted user recruitment, production mail delivery, and guaranteed ongoing operation
- Mandatory CI/CD, monitoring stacks, alerting, backup automation, high availability, and restore rehearsals
- Load tests, scale benchmarks, search-engine infrastructure, microservices, Kubernetes, and message brokers
- Refresh-token reuse detection, multi-device session management, multiple social providers, automatic local/social account linking, and password reset
- Mandatory account-deletion feature for the first completed toy-project flow
- Repeated food-release synchronization, staging release history, scheduled imports, and operational rollback
- Advanced ranking, fuzzy search, n-gram tuning, query-plan benchmarking, and concurrent search targets
- Custom foods, favorites, water, weight, personalized nutrition prescriptions, PWA, mobile applications, and AI features
- Exhaustive tests for every validation order, Unicode edge case, race, framework detail, or exception subtype

Deferred items are added only when the user chooses them as a new learning goal after the core flow works.

## 5. Stable Safety and Data Rules

Toy-project scope does not remove the following basics:

- Never store or log plaintext passwords, verification proofs, tokens, or real credentials.
- Encode local passwords with Spring Security `PasswordEncoder` and BCrypt.
- Use authenticated identity for user-owned data and prevent cross-user access.
- Use Flyway as schema authority and Hibernate `ddl-auto=validate`.
- Keep database uniqueness and foreign-key constraints that prevent invalid persisted state.
- Keep transaction boundaries in the service layer.
- Do not silently convert unavailable nutrient values to zero.
- Preserve meal nutrition snapshots independently of later food changes.
- Return safe client errors without stack traces or database details.

## 6. Minimal Feature Ownership

| Feature | Owns | Required before |
|---------|------|-----------------|
| `project-foundation` | Local Spring Boot/MySQL/Flyway/Testcontainers baseline | All features |
| `email-verification` | Mailpit send, code confirmation, and signup-proof issuance | Final signup exercise |
| `user-registration` | Final account, credential, consent persistence, and proof consumption | Login |
| `local-login` | Login, short-lived Access Token and protected API authentication, server-managed Refresh Token issuance/reissue/rotation, and logout revocation | User-owned meals |
| `social-login` | At least one provider, provider-subject identity, first-login account creation, and the shared Access Token result | Alternate signup/login method |
| `food` | One simple import, search, and detail API | Meal recording |
| `meal` | Authenticated meal create/read/delete and nutrition snapshots | Daily totals |
| `daily-summary` | Daily major-nutrient totals with incomplete-value disclosure | Core flow completion |
| `small-deployment` | One simple protected deployment for the developer's direct use and voluntary trial by a few acquaintances | Final hands-on use |

Small related endpoints may be implemented together. A separate PDCA document is not required for every endpoint or helper class.

## 7. Verification Boundary

Use the smallest test set that proves the learning goal:

- Unit tests for meaningful business calculations or validation boundaries
- Web tests for the main success response and representative client errors
- MySQL integration tests for Flyway, important constraints, one successful transaction, and one rollback path
- A focused ownership-isolation test for user-owned data
- One successful first-time social signup/login and one subsequent social login test
- Swagger confirmation for the complete happy path
- One deployment smoke test and a short real-use check by the developer or an invited acquaintance

Concurrency, performance, failure-injection, and exhaustive contract tests are optional unless they directly support the transaction or data-integrity topic currently being studied.

## 8. Completion Criteria

- [ ] The local account-to-meal-to-daily-summary flow works.
- [ ] Email/password and at least one social login method both reach the authenticated flow.
- [ ] The developer can explain the schema, transaction boundary, ownership rule, and snapshot rule.
- [ ] Representative tests pass against the intended database behavior.
- [ ] Swagger documents the implemented APIs in Korean and permits manual testing in an explicitly enabled safe environment.
- [ ] The developer can deploy one instance for direct use and optional small acquaintance trial without storing real secrets in the repository.
- [ ] No deferred commercial-production or scale feature blocks completion.

## 9. Decision Maintenance

`FOUNDATION-006` is approved by the user's 2026-08-14 scope correction. It establishes a deployed and directly used toy project as the current authority. The same-day clarification permits voluntary trial and feedback from a small number of acquaintances without redefining Nyamlog as a commercial or unrestricted public service.

`FOUNDATION-006-R1` supersedes only the social-login deferral in `FOUNDATION-006`. The core toy project keeps both email/password and social signup/login. The first social implementation requires at least one provider; provider selection, callback details, provider-subject schema, duplicate-email conflict, and optional account linking belong to a later bounded `social-login` Plan and Design.

`LOCAL-LOGIN-001` supersedes only the Refresh Token rotation deferral and the minimal `local-login` ownership row. It adds Refresh Token issuance, server validity state, Access Token reissue, rotation, and logout revocation to the bounded local-login flow. Advanced reuse detection and multi-device session management remain deferred.

The older Foundation document contained useful exploration, but its formal invited-tester service operations, commercial-production rollback, scheduled import, search-load, detailed batch, and exhaustive verification clauses are no longer implementation or completion requirements. A simple small deployment remains required.

Existing feature decisions remain only where they support the concise core flow and the stable safety/data rules above. A feature Plan or Design must explicitly simplify an older detailed decision when that decision adds work without advancing a selected learning goal.

## 10. Decision History

| ID | Status | Current meaning |
|----|--------|-----------------|
| `FOUNDATION-001` | Retained | Flyway schema authority, Hibernate validation, and scoped real-MySQL tests |
| `FOUNDATION-002` | Superseded | Original broad release boundary |
| `FOUNDATION-002-R1` | Superseded by `FOUNDATION-006` | Earlier limited-deployment portfolio boundary |
| `FOUNDATION-003` | Retained in minimal form | Age eligibility and explicit required consent remain simple product rules |
| `FOUNDATION-004` / `FOUNDATION-004-R1` | Retained in minimal form | Feature ownership and email-verification-before-signup order |
| `FOUNDATION-005` | Deferred by `FOUNDATION-006` | Account deletion is not required before the core toy flow is complete |
| `FOUNDATION-006` | Approved, clarified | Deployed learning toy project for developer use and optional small acquaintance trial; fundamentals take priority over commercial operations, scale, and exhaustive coverage |
| `FOUNDATION-006-R1` | Approved | Retains both email/password and at least one social signup/login path; detailed provider and account-linking choices remain feature-owned |
| `LOCAL-LOGIN-001` | Approved | Adds server-managed Refresh Token login persistence to the bounded local-login flow while retaining advanced session features as deferred |

## Version History

| Version | Date | Change |
|---------|------|--------|
| 2.2.0 | 2026-08-23 | Recorded the limited `LOCAL-LOGIN-001` supersession for Refresh Token login persistence and local-login ownership |
| 2.1.0 | 2026-08-14 | Recorded `FOUNDATION-006-R1`: restored social signup/login as a core method while keeping it separate from local registration implementation |
| 2.0.1 | 2026-08-14 | Clarified that a few acquaintances may voluntarily try the deployed toy project and provide feedback |
| 2.0.0 | 2026-08-14 | Replaced the external-service premise with a concise personally deployed toy-project boundary through `FOUNDATION-006` |
| 1.4.0 | 2026-08-09 | Historical limited-MVP Plan before the toy-project scope reset |
