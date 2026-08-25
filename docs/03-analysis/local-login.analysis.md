# local-login Analysis Report

> **Analysis Type**: Gap Analysis and Post-Act Reanalysis <br>
> **Project**: Nyamlog <br>
> **Analyst**: Codex <br>
> **Date**: 2026-08-26 <br>
> **Status**: Completed <br>
> **Design Doc**: [local-login.design.md](../02-design/features/local-login.design.md)

---

## 1. Analysis Scope

- Approved Plan: `docs/01-plan/features/local-login.plan.md` version 1.1.0
- Approved Design: `docs/02-design/features/local-login.design.md` version 1.0.0
- Implementation: Spring Security configuration, V3 Flyway Migration, and local-login model, repository, service, web, and test artifacts
- Standard verification: `.\gradlew.bat test javadoc` and `git diff --check`
- Actual database baseline: Testcontainers `mysql:8.4.5`
- Excluded client evidence: future PWA authentication-operation serialization and client-side Access Token removal

## 2. Initial Check and Act Corrections

The initial Check found four implementation or verification gaps. They were corrected before the final reanalysis.

| # | Initial Gap | Act Correction | Final Status |
|---|-------------|----------------|--------------|
| 1 | A correctly signed JWT without `exp` could pass the framework timestamp validator | Added an explicit required-expiration validator and missing-`exp` rejection coverage | Resolved |
| 2 | Login limited the password to 72 characters rather than the BCrypt UTF-8 72-byte boundary | Reused the byte-aware password policy in request validation and added a multibyte web test | Resolved |
| 3 | Login and refresh captured service time and then allowed the Access Token issuer to read the clock again | Changed token issuance to accept the request's single captured `Instant` | Resolved |
| 4 | The Refresh Token equality-boundary test used ten remaining seconds and did not prove exact expiry rejection | Added one-second success and exact-expiry failure coverage | Resolved |

No public API, database schema, cookie policy, or approved scope change was required by Act.

## 3. Final Design-to-Implementation Match

### 3.1 Public API and Recovery Flow

| # | Design Contract | Implementation Evidence | Status |
|---|-----------------|-------------------------|--------|
| 1 | Login, refresh, logout, and `/me` use the approved methods, paths, bodies, and success codes | `LocalLoginController`, request/response DTOs, Controller tests | Match |
| 2 | Login returns the Access Token in the body and the Refresh Token only in the approved cookie | Controller response construction and `loginReturnsAccessBodyAndSecureRefreshCookie` | Match |
| 3 | Refresh accepts the cookie after the CSRF marker and returns no cookie on failure | Controller validation order and refresh failure tests | Match |
| 4 | Logout is idempotent, revokes matching server state, and always returns the deletion cookie | Service/controller implementation and logout tests | Match |
| 5 | `/me` derives the positive user ID from `SecurityContext` and returns only display email | Resource Server authentication, Controller, and `/me` tests | Match |
| 6 | Public failures use the approved safe envelope without exposing row existence or internals | `ErrorCode`, exception/filter responders, and web tests | Match |

### 3.2 Credentials and Access Token

| # | Design Contract | Implementation Evidence | Status |
|---|-----------------|-------------------------|--------|
| 7 | Login canonicalizes email and calls BCrypt `matches` exactly once, including the dummy-hash path | `LocalLoginService` and `missingUserUsesOneDummyPasswordComparison` | Match |
| 8 | Invalid login input is rejected before BCrypt, including the UTF-8 72-byte boundary | `LoginRequest`, `PasswordPolicy`, and multibyte web test | Match |
| 9 | Access Tokens use HS256, 15 minutes, and exact issuer, audience, subject, and expiration claims | `AccessTokenIssuer` and exact-claims test | Match |
| 10 | Decoder enforces algorithm, expiration presence and time, issuer, audience, and positive BIGINT subject with zero skew | `SecurityConfiguration` and JWT rejection tests | Match |
| 11 | The Base64 signing secret has no repository default and fails startup when missing, malformed, or short | Secret-key bean and configuration tests | Match |

### 3.3 Refresh Token, Persistence, and Time

| # | Design Contract | Implementation Evidence | Status |
|---|-----------------|-------------------------|--------|
| 12 | Refresh Tokens are 32 random bytes, 43-character URL-safe Base64, with only SHA-256 stored | `RefreshTokenCodec` and codec tests | Match |
| 13 | V3 creates one row per user with 32-byte unique hash, ordered timestamps, and cascade deletion | V3 Migration and MySQL constraint test | Match |
| 14 | Password login creates or replaces the user's single row with a new fixed 30-day expiry | Repository upsert, service transaction, and MySQL complete-flow test | Match |
| 15 | Refresh preserves absolute expiry and accepts at least one whole remaining second | Conditional update, service boundary tests, and MySQL complete-flow test | Match |
| 16 | Concurrent use of the same previous token produces exactly one winner and one loser with no loser cookie | Coordinated separate-connection MySQL and HTTP concurrency test | Match |
| 17 | Replaced, expired, unknown, and post-logout Refresh Tokens are rejected; logout cleanup remains idempotent | Service/web tests and MySQL complete-flow test | Match |
| 18 | Each login or refresh captures the injected UTC clock exactly once and reuses that instant | `LocalLoginService`, `AccessTokenIssuer`, and clock-verification tests | Match |

### 3.4 Browser Security, OpenAPI, and Scope

| # | Design Contract | Implementation Evidence | Status |
|---|-----------------|-------------------------|--------|
| 19 | Creation and deletion cookies match the approved name, path, Secure, HttpOnly, SameSite, Domain, and Max-Age rules | `LocalLoginController` and cookie tests | Match |
| 20 | Only cookie-authenticated refresh and logout require the exact CSRF marker; stale Bearer does not block recovery | Controller checks, Bearer resolver, and recovery tests | Match |
| 21 | OpenAPI publishes Bearer JWT and the four operations without sensitive examples or Refresh Token DTO fields | Swagger annotations and `LocalLoginOpenApiContractTest` | Match |
| 22 | Authentication responses use `Cache-Control: no-store` and protected failures retain the Bearer challenge | Controller/filter response code and web tests | Match |
| 23 | No blacklist, Redis, multi-device session model, rate limiting, account lockout, or downstream domain work was added | Dependency and source review | Match |

### 3.5 Verification Contract

| # | Design Contract | Evidence | Status |
|---|-----------------|----------|--------|
| 24 | Representative unit, service, web, JWT, cookie, CSRF, error, redaction, and OpenAPI contracts execute | Focused local-login suites in the full Gradle run | Match |
| 25 | Actual MySQL 8.4.5 proves V3 constraints, complete flow, fixed expiry, replacement, logout, and deterministic one-winner concurrency | `LocalLoginMySqlIntegrationTest`: 3 passed, 0 skipped | Match |
| 26 | Standard Windows verification and JavaDoc complete without test, error, skip, or whitespace failure | Full Gradle result and diff check | Match |

## 4. Match Rate

```text
Final Overall Match Rate: 100%

Match:            26 items (100%)
Missing in Code:   0 items (0%)
Changed:           0 items (0%)
Missing in Design: 0 items (0%)
```

The initial Check required one Act correction cycle. Final reanalysis found no remaining server implementation gap.

## 5. Verification Result

| Verification | Result |
|--------------|--------|
| `.\gradlew.bat test javadoc` | Passed (`BUILD SUCCESSFUL`) |
| Entire Gradle test suite | 86 passed, 0 failed, 0 errors, 0 skipped |
| Local-login MySQL suite | 3 passed on actual MySQL 8.4.5, 0 skipped |
| JavaDoc | Passed |
| `git diff --check` | No whitespace errors; line-ending conversion warnings only |
| Modified local-login source/test trailing-whitespace scan | Passed |

The actual-MySQL suite executed V3 constraints, repeated login replacement, fixed-expiry rotation, prior-token rejection, logout deletion and idempotency, post-logout rejection, separate-connection conditional-update concurrency, and the winner/loser HTTP cookie outcome.

## 6. Quality and Approved Limitations

No remaining critical, high, or medium server implementation gap was found.

The following approved limitations remain explicit:

- An Access Token issued before logout can remain valid for at most its 15-minute lifetime because the MVP has no Access Token blacklist.
- One current Refresh Token row per user means a successful new login replaces the previous persistent session.
- PWA-side Access Token memory removal, single-flight refresh, shared authentication-operation serialization, and late-result rejection remain unexecuted client acceptance criteria until that client exists.
- Advanced reuse detection, token families, multi-device sessions, rate limiting, account lockout, monitoring, Redis, and signing-key rotation infrastructure remain excluded.
- Clean-checkout local configuration reproducibility remains the inherited project-foundation limitation; no local configuration or secret value is included in this Analysis.

## 7. Next Step

Do, Check, and the required Act correction cycle are complete. The completion Report is the next PDCA phase and has not been started. Staging, commit, push, Pull Request, merge, archive, PWA implementation, and adjacent features are outside this Analysis.

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0.0 | 2026-08-26 | Recorded initial four-gap Check, completed Act corrections, final 26/26 match, and actual MySQL 8.4.5 evidence | Codex |
