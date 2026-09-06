# nyamlog-mvp-foundation - Plan Document

> **Revision**: 2.6.0 / `FOUNDATION-006-R5` — Approved (2026-09-03). See §11. <br>
> Sections 1–10 preserve the earlier approved baseline; §11 is the current limited supersession. This approval does not authorize implementation, verification, Git publication, or external work.

> **Version**: 2.6.0 <br>
> **Date**: 2026-09-03 <br>
> **Status**: Approved (Foundation Complete) <br>
> **Current Scope Decisions**: `FOUNDATION-006-R1`, `FOUNDATION-006-R2`, `FOUNDATION-006-R3`, `FOUNDATION-006-R4`, `FOUNDATION-006-R5`, `LOCAL-LOGIN-001` <br>
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
9. Explore APIs through Swagger and verify the flow with representative tests and curl/Postman deployment smoke.
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
| `small-deployment` | Render backend, external Managed MySQL and real mail for the first protected core-API smoke | Later Vercel integration and final hands-on use |

Small related endpoints may be implemented together. A separate PDCA document is not required for every endpoint or helper class.

## 7. Verification Boundary

Use the smallest test set that proves the learning goal:

- Unit tests for meaningful business calculations or validation boundaries
- Web tests for the main success response and representative client errors
- MySQL integration tests for Flyway, important constraints, one successful transaction, and one rollback path
- A focused ownership-isolation test for user-owned data
- One successful first-time social signup/login and one subsequent social login test
- One curl/Postman HTTP smoke of the current local-auth core flow on the first Render backend deployment
- One later browser-cookie smoke through the Vercel `/api/*` rewrite and final OpenAPI confirmation

Concurrency, performance, failure-injection, and exhaustive contract tests are optional unless they directly support the transaction or data-integrity topic currently being studied.

## 8. Completion Criteria

- [ ] The local account-to-meal-to-daily-summary flow works.
- [ ] Email/password and at least one social login method both reach the authenticated flow.
- [ ] The developer can explain the schema, transaction boundary, ownership rule, and snapshot rule.
- [ ] Representative tests pass against the intended database behavior.
- [ ] Swagger documents the implemented APIs in Korean; public documentation and Try it out are allowed on the explicitly selected Render personal deployment under `FOUNDATION-006-R4`, while its official core smoke remains curl/Postman.
- [ ] The developer can deploy one instance for direct use and optional small acquaintance trial without storing real secrets in the repository.
- [ ] No deferred commercial-production or scale feature blocks completion.

## 9. Decision Maintenance

`FOUNDATION-006` is approved by the user's 2026-08-14 scope correction. It establishes a deployed and directly used toy project as the current authority. The same-day clarification permits voluntary trial and feedback from a small number of acquaintances without redefining Nyamlog as a commercial or unrestricted public service.

`FOUNDATION-006-R1` supersedes only the social-login deferral in `FOUNDATION-006`. The core toy project keeps both email/password and social signup/login. The first social implementation requires at least one provider; provider selection, callback details, provider-subject schema, duplicate-email conflict, and optional account linking belong to a later bounded `social-login` Plan and Design.

`FOUNDATION-006-R2` supersedes only the earlier remaining feature implementation order. The project completes the distinctive food-to-meal-to-daily-summary domain flow before adding the second signup/login method. Its requirement to finish social login before any personal deployment is superseded only by `FOUNDATION-006-R3`.

`FOUNDATION-006-R3` supersedes only the remaining milestone order after `daily-summary`. First deploy the current local-auth backend to a Render Docker Web Service connected to external Managed MySQL and real mail, and prove the core API flow with an HTTP smoke test. Next connect the Vercel frontend through a browser-path-preserving `/api/*` external rewrite with rewrite caching explicitly disabled, then implement Google social login, and finally perform end-to-end and OpenAPI hardening. Social login remains a core completion requirement; the first backend deployment is evidence before that later feature, not final Nyamlog completion.

`FOUNDATION-006-R4`, approved on 2026-09-03, supersedes only the local/access-restricted-only Swagger Try it out environment rule for the selected Render personal deployment. Public Swagger UI, OpenAPI documents, and Try it out are allowed there without weakening API authentication, ownership, cookie/CSRF protections, or secret non-disclosure. Official deployment smoke evidence remains curl/Postman. This does not change the `FOUNDATION-006-R3` milestone order or authorize unrestricted user recruitment, implementation, Git publication, or actual deployment.

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
| `FOUNDATION-006-R2` | Approved, order refined by `FOUNDATION-006-R3` | Implements food, meal, and daily summary before the remaining milestones |
| `FOUNDATION-006-R3` | Approved | Runs the Render backend and core HTTP smoke before Vercel integration and Google social login; social login remains required before final hardening and completion |
| `FOUNDATION-006-R4` | Approved | Allows public Swagger/OpenAPI and Try it out on the selected Render personal deployment; preserves API security and curl/Postman as the official smoke tools |
| `FOUNDATION-006-R5` | Approved | Replaces only the first-deployment real-mail and account/token-acquisition smoke with seeded A/B users and the existing JWT/ownership domain smoke; final authentication requirements remain separate |
| `LOCAL-LOGIN-001` | Approved | Adds server-managed Refresh Token login persistence to the bounded local-login flow while retaining advanced session features as deferred |

## 11. FOUNDATION-006-R5 — First-deployment scope revision

**Status: Approved (2026-09-03).** Preserve `FOUNDATION-006-R3/R4` and their approval records. R5 narrowly replaces the first-deployment requirement for real mail and signup/login/refresh/logout smoke; it does not remove authentication, ownership, or final product acceptance requirements.

- First-deployment target: Render + Aiven MySQL + Docker/TLS/Flyway + full food import + public Swagger/health + the complete food → meal → daily-summary HTTP smoke.
- Prepare two synthetic users A/B through an explicitly invoked local seed tool. Use database-generated user IDs, preserve the existing users → meals → meal_items relationships, and keep user IDs out of public request inputs. Do not seed credentials, verification proofs, consents, or tokens as substitutes for completed registration.
- Obtain 15-minute Access JWTs only through a local development tool using the existing issuer contract and the separately authorized deployment signing key. Requests pass through the existing JWT decoder, SecurityContext and ownership checks. No new smoke guard, shared static bearer credential, fixed user ID, or public token-generation endpoint is introduced.
- The deployment profile must explicitly block `/api/v1/auth/**` on the server, including requests carrying a valid Access JWT. Food, meal and daily-summary remain authenticated. Existing authentication/email code and behavior outside this deployment mode are retained.
- Verify A's non-empty daily summary before deleting A's meal, B's isolation and rejected cross-user deletion, then A's deletion and an empty summary on the controlled test date. Verify missing, tampered and expired JWT rejection as well as the existing issuer/audience/subject validation contract.
- Real email, SMTP2GO eligibility/setup, signup/login, refresh/logout and browser cookie smoke are not first-deployment acceptance conditions under R5. The official domain smoke tools remain curl/Postman; public Swagger/Try it out does not replace them.
- Defer how end users obtain accounts and tokens to a subsequent bounded authentication feature. Choosing loginId/password or Google, removing existing email authentication, or changing the final local/social-login requirements requires a separate decision. The R3 Vercel, Google and final E2E work is neither completed nor discarded here; actual browser cookie smoke depends on restoring an approved account/token flow first.

`SMALL-DEPLOYMENT-001-R2` and `SMALL-DEPLOYMENT-003` are the related approved Plan/Design decisions. Preserve the existing local Do evidence; it is not evidence for this revised deployment mode. This approval records the three document decisions and PDCA status only. Implementation, local re-verification, GitHub Issue approval-status synchronization, Git publication and external work remain separate gates.

## Version History

| Version | Date | Change |
|---------|------|--------|
| 2.6.0 | 2026-09-03 | Approved `FOUNDATION-006-R5`: seeded A/B users and local short-lived JWT domain smoke replace first-deployment real-mail/account acquisition while final authentication requirements remain separate; no implementation approval |
| 2.5.0 | 2026-09-03 | Recorded `FOUNDATION-006-R4`: allowed public Render Swagger/OpenAPI and Try it out while preserving API security and official curl/Postman smoke |
| 2.4.0 | 2026-09-03 | Recorded `FOUNDATION-006-R3`: moved the first Render backend deployment and core smoke before Vercel integration and Google social login without removing social login from completion |
| 2.3.0 | 2026-08-26 | Recorded `FOUNDATION-006-R2`: moved social login after the core food and meal flow without removing it from MVP completion |
| 2.2.0 | 2026-08-23 | Recorded the limited `LOCAL-LOGIN-001` supersession for Refresh Token login persistence and local-login ownership |
| 2.1.0 | 2026-08-14 | Recorded `FOUNDATION-006-R1`: restored social signup/login as a core method while keeping it separate from local registration implementation |
| 2.0.1 | 2026-08-14 | Clarified that a few acquaintances may voluntarily try the deployed toy project and provide feedback |
| 2.0.0 | 2026-08-14 | Replaced the external-service premise with a concise personally deployed toy-project boundary through `FOUNDATION-006` |
| 1.4.0 | 2026-08-09 | Historical limited-MVP Plan before the toy-project scope reset |
