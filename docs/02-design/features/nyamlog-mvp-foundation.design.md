# nyamlog-mvp-foundation - Design Document

> **Version**: 2.2.0 <br>
> **Date**: 2026-08-23 <br>
> **Status**: Approved (Foundation Complete) <br>
> **Plan**: `docs/01-plan/features/nyamlog-mvp-foundation.plan.md` <br>
> **Current Design Decisions**: `FOUNDATION-006-R1`, `LOCAL-LOGIN-001`

---

## 1. Design Principle

Nyamlog uses the smallest conventional Spring Boot design that makes backend fundamentals visible. The design optimizes for learning, one small deployment, direct use, optional acquaintance feedback, and interview explanation rather than commercial operation or future scale.

When two options are both safe enough for the toy project, prefer the option with fewer components, fewer dependencies, fewer operational steps, and a clearer transaction boundary.

## 2. Application Structure

Use a domain-oriented package structure with the conventional request flow:

```text
HTTP request
  -> Controller and request DTO validation
  -> Service business rules and transaction
  -> Spring Data JPA repository
  -> MySQL
```

- Controllers map HTTP, validate DTOs, delegate, and construct responses.
- Services own business behavior and `@Transactional` boundaries.
- Repositories own persistence access.
- Entities are not returned by public APIs.
- Add a separate policy, mapper, interface, or service only when it removes real duplication or isolates a rule that is independently meaningful.

## 3. Local Runtime Baseline

- Java 17
- Spring Boot 3.5.x
- Gradle Wrapper
- MySQL 8.4.x
- Spring Data JPA
- Flyway with versioned MySQL migrations
- Hibernate `ddl-auto=validate`
- Docker Compose for local MySQL and Mailpit
- Testcontainers only for behavior that must be proven against MySQL
- springdoc OpenAPI and Swagger UI for documentation and manual testing
- one simple protected deployment target for the developer and a few invited acquaintances

The deployment may be a single VM, container host, or similarly simple environment. It needs externalized secrets, persistent database storage, a documented start procedure, and reasonable access protection. A few acquaintances may use it voluntarily for feedback. It does not need high availability, autoscaling, zero-downtime releases, or a commercial operations stack.

## 4. Authentication Boundary

The first authentication flow is intentionally basic:

- Mailpit-based local email verification
- Local email and password registration
- BCrypt password storage through `PasswordEncoder`
- One simple login flow and short-lived Access Token
- One server-managed Refresh Token flow for Access Token reissue, rotation, and logout revocation
- At least one social provider supporting first-time account creation and subsequent login
- The same Nyamlog Access Token and protected-API authorization model for local and social users
- Protected APIs read the authenticated user from Spring Security
- Logout revokes the presented Refresh Token server state and deletes its cookie; the client discards its Access Token, which may otherwise remain valid until its short expiry

Password reset, additional social providers, advanced Refresh Token reuse detection, multi-device session management, production mail, and account lifecycle states are deferred.

The later `social-login` Design selects the first provider and defines its callback and provider API usage. A social identity is keyed by provider plus provider subject. Do not merge a social identity into an existing local account solely because the provider returns the same email; explicit linking or a safe conflict policy must be chosen before that feature is implemented.

## 5. Database and Transaction Rules

- Flyway migrations are the schema source of truth.
- Use database unique and foreign-key constraints for important invariants.
- A service transaction groups writes that must succeed or fail together.
- Registration demonstrates account, credential, consent, and proof-consumption atomicity.
- Meal creation demonstrates user ownership and immutable nutrition snapshots.
- Do not build generic transaction abstractions, event systems, or compensating workflows for the toy project.
- Migration rollback infrastructure, backup snapshots, and restore rehearsals are not required.

## 6. Food and Meal Boundary

The first food implementation is deliberately small:

- Run one manual import from an approved source file or representative subset.
- Store only fields needed for search, detail, meal snapshots, and daily major-nutrient totals.
- Use a simple normalized prefix query and a bounded result size.
- Do not implement release comparison, staging history, scheduled updates, soft inactivation, full-text tuning, or search benchmarks.
- Missing nutrient values remain `NULL` and are never silently changed to zero.
- A meal item copies the nutrition values needed for history so later food changes do not rewrite past meals.
- User-owned meal queries always filter by authenticated owner.

## 7. API Boundary

- Keep APIs under `/api/v1`.
- Use request and response DTOs.
- Use one consistent success/error response envelope when it helps the client.
- Return safe application error codes for meaningful client decisions.
- Do not expose internal identifiers unless the client needs them.
- Swagger descriptions are written in Korean and stay aligned with the implemented Controller and DTOs.
- Swagger Try it out may be enabled only in an explicitly selected local or access-restricted personal environment.

## 8. Verification Strategy

The required evidence is representative, not exhaustive.

| Layer | Required evidence |
|-------|-------------------|
| Unit | A meaningful business boundary or calculation |
| Web | Main success response and representative validation/business error |
| MySQL integration | Fresh migration, one core successful transaction, one rollback, and important constraints |
| Security/ownership | One user cannot access another user's data |
| Manual | Core happy path through Swagger and a smoke test or short acquaintance trial on the deployment |

The following are optional:

- deterministic race orchestration
- load or performance tests
- query-plan benchmarks
- every validation-order permutation
- every Unicode corner case
- exact framework exception-subtype classification
- tests that merely repeat annotations or library behavior

Existing passing tests may remain until the owning code is simplified. They are not future completion gates merely because they already exist.

## 9. Documentation Workflow

- Use one short Plan and one short Design for a meaningful feature or integrated slice.
- Do not create a decision for every endpoint, class, library default, or test case.
- Record a new decision only for a choice that materially changes schema, API, security, ownership, transaction behavior, or project scope.
- Historical audits and superseded decisions are evidence, not current implementation requirements.
- Completion of a working core flow takes priority over expanding documents.

## 10. `FOUNDATION-006` - Toy-Project Scope Reset

**Status:** Approved 2026-08-14 <br>
**Reason:** The prior Foundation accumulated limited-deployment, operational recovery, batch, search-performance, and exhaustive-test contracts disproportionate to a solo junior learning toy project.

`FOUNDATION-006` makes the following changes:

- Supersedes `FOUNDATION-002-R1` where it requires formal invited-tester operations or service-level deployment completion; informal voluntary trial by a few acquaintances remains allowed.
- Defers `FOUNDATION-005` account deletion until after the core local flow is complete.
- Supersedes Foundation-owned interpretations of `FOOD-004` through `FOOD-013` that require staging releases, retained import history, scheduled updates, operational rollback, advanced indexing, concurrency targets, or benchmarks for the first implementation.
- Retains only the food rules needed to prevent silent data corruption: stable identity, correct units, `NULL` for unavailable nutrients, owner isolation, and immutable meal snapshots.
- Keeps basic password hashing, secret protection, ownership enforcement, safe errors, database constraints, and service transactions.
- Makes representative tests, Swagger verification, and one personal-deployment smoke test sufficient evidence.

Older verbose Foundation sections are superseded by this concise Design and are no longer implementation or completion gates. This does not remove deployment, direct developer use, or voluntary trial by a few acquaintances.

### 10.1 `FOUNDATION-006-R1` - Two Signup/Login Methods

**Status:** Approved 2026-08-14 <br>
**Supersedes:** Only the social-login deferral in `FOUNDATION-006`

- Keep email/password signup and login.
- Add at least one social signup/login provider as a separate feature.
- Both methods resolve to the same internal user identity model and Nyamlog Access Token behavior.
- Do not expand the current local `user-registration` transaction with OAuth callback or provider API logic.
- Defer provider selection, provider credentials, provider-subject schema, email collision, and account-linking rules to the bounded `social-login` Plan and Design.

### 10.2 `LOCAL-LOGIN-001` - Persistent Local Login

**Status:** Approved 2026-08-23 <br>
**Supersedes:** Only the Refresh Token rotation deferral and client-only logout clause in section 4

- Add Refresh Token issuance, server validity state, Access Token reissue, atomic rotation, and logout revocation to `local-login`.
- Deliver the Access Token in the response body for Bearer authentication and the Refresh Token only through an HttpOnly cookie.
- Delete the Refresh Token cookie on logout while documenting that an issued Access Token may remain valid until its short expiry.
- Keep advanced reuse detection, multi-device session management, and production-scale session infrastructure deferred.
- Defer token lifetimes, persistence schema, digest algorithm, rotation concurrency, expiry policy, cookie attributes, and CORS/CSRF details to the integrated `local-login` Design.

## 11. Current Feature Order

1. Finish the simplified `user-registration` slice.
2. Implement local Mailpit email verification and login as one bounded authentication flow.
3. Implement one bounded social signup/login provider using the same internal authentication result.
4. Implement simple food import, search, and detail.
5. Implement authenticated meal create/read/delete with snapshots.
6. Implement daily major-nutrient totals.
7. Run the complete flow through Swagger, fix defects, deploy one personal instance, and document startup.

Account deletion and every deferred concern are optional follow-up maintenance work.

## 12. Remaining Foundation Decisions

None. Feature-level Plans and Designs may choose their smallest implementation inside this boundary.

## 13. Decision History

| ID | Status | Current effect |
|----|--------|----------------|
| `FOUNDATION-001` | Retained | Flyway, Hibernate validation, and scoped MySQL tests |
| `FOUNDATION-002` | Superseded | Original broad release scope |
| `FOUNDATION-002-R1` | Superseded by `FOUNDATION-006` | Earlier limited-deployment scope |
| `FOUNDATION-003` | Retained in minimal form | Simple age and consent product rules |
| `FOUNDATION-004` / `FOUNDATION-004-R1` | Retained in minimal form | Feature ownership and signup ordering |
| `FOUNDATION-005` | Deferred by `FOUNDATION-006` | Account deletion is optional after the core flow |
| `FOUNDATION-006` | Approved, clarified | Deployed toy-project scope, optional small acquaintance trial, and representative verification |
| `FOUNDATION-006-R1` | Approved | Email/password and at least one social signup/login method remain in the core project; social details are feature-owned |
| `LOCAL-LOGIN-001` | Approved | Adds server-managed Refresh Token persistence, reissue, rotation, and logout revocation to local login without expanding advanced session scope |

## Version History

| Version | Date | Change |
|---------|------|--------|
| 2.2.0 | 2026-08-23 | Recorded the limited `LOCAL-LOGIN-001` supersession for Refresh Token persistence and server-side logout revocation |
| 2.1.0 | 2026-08-14 | Recorded `FOUNDATION-006-R1`: retained both local and social signup/login without expanding the current registration feature |
| 2.0.1 | 2026-08-14 | Clarified that small voluntary acquaintance use is allowed without introducing commercial service operations |
| 2.0.0 | 2026-08-14 | Replaced the 980-line service-operations Design with the concise `FOUNDATION-006` personal-use toy-project authority |
| 1.2.0 | 2026-08-09 | Historical Foundation Design before the toy-project scope reset |
