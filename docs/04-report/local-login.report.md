# local-login Completion Report

> **Status**: Complete <br>
> **Project**: Nyamlog <br>
> **Completion Date**: 2026-08-26 <br>
> **Current Decision**: `LOCAL-LOGIN-002` <br>
> **Related Issue**: [#12](https://github.com/KBS-guys/Nyam-server/issues/12)

---

## 1. Summary

| Item | Result |
|------|--------|
| Feature | `local-login` |
| Scope | Persistent email/password login through protected access, refresh rotation, and logout revocation |
| Completion Rate | 100% |
| Design Match Rate | 100% (26/26) |
| Act | One correction cycle completed |

The feature now provides one bounded server vertical flow: a registered local user logs in, receives a short-lived Access Token and an HttpOnly Refresh Token cookie, calls a Bearer-protected current-user endpoint, rotates the Refresh Token to restore access, and logs out by deleting the current server-side Refresh Token state.

This completion covers the Spring Boot and MySQL server flow only. PWA storage, automatic refresh, authentication-operation serialization, and client-side Access Token removal remain client acceptance work for a later frontend task.

## 2. Related Documents

| Phase | Document | Status |
|-------|----------|--------|
| Plan | [`local-login.plan.md`](../01-plan/features/local-login.plan.md) | Approved 1.1.0 |
| Design | [`local-login.design.md`](../02-design/features/local-login.design.md) | Approved 1.0.0 |
| Analysis | [`local-login.analysis.md`](../03-analysis/local-login.analysis.md) | Complete 1.0.0 |

## 3. Completed Vertical Flow

1. `POST /api/v1/auth/login` validates the request and checks the canonical email and BCrypt credential without revealing which credential component failed.
2. Successful login returns a 15-minute HS256 Access Token in the response body and a 30-day opaque Refresh Token only through the approved Secure, HttpOnly, SameSite cookie.
3. MySQL stores one SHA-256 Refresh Token hash per user; a new successful login replaces the previous persistent session.
4. `GET /api/v1/auth/me` authenticates the Bearer token through Spring Security and derives the internal user ID from `SecurityContext`.
5. `POST /api/v1/auth/refresh` validates the CSRF marker and cookie, preserves the fixed absolute expiry, and conditionally rotates the stored hash.
6. Concurrent refreshes using the same previous token produce one winner; the loser receives `401` without `Set-Cookie`.
7. `POST /api/v1/auth/logout` idempotently deletes matching server state and returns the matching cookie-deletion response.
8. Replaced, expired, unknown, and post-logout Refresh Tokens are rejected without exposing token or persistence details.

### 3.1 Security and Data Integrity

- Access Tokens use exact HS256 with `iss=nyamlog`, positive BIGINT `sub`, `aud=nyamlog-api`, required `exp`, a 15-minute lifetime, and zero clock skew.
- The Base64 signing secret must decode to at least 32 bytes, has no repository default, and fails startup when invalid.
- Refresh Tokens use 32 `SecureRandom` bytes and 43-character URL-safe Base64 without padding; only full SHA-256 hashes are persisted.
- Login and refresh each capture the injected UTC clock once and reuse the same instant for token and persistence decisions.
- V3 enforces one row per user, a unique 32-byte token hash, ordered timestamps, and cascade deletion.
- Passwords, Access Tokens, raw Refresh Tokens, hashes, signing keys, and internal failures are excluded from logs, DTO examples, and public error details.

### 3.2 API and Runtime Impact

- Added login, refresh, logout, and current-user endpoints under `/api/v1/auth`.
- Added stateless OAuth2 Resource Server JWT authentication and a Bearer OpenAPI security scheme.
- Added MySQL Flyway V3 for current Refresh Token state.
- Added Spring Security OAuth2 Resource Server and JOSE support without introducing a custom JWT parser or Bearer filter.
- Preserved the existing signup and email-verification public access and did not add downstream food or meal behavior.

## 4. Verification Results

The following results were produced during the final Check and were not rerun merely to write this Report.

| Verification | Result |
|--------------|--------|
| `.\gradlew.bat test javadoc` | Passed (`BUILD SUCCESSFUL`) |
| Entire Gradle test suite | 86 passed, 0 failed, 0 errors, 0 skipped |
| Local-login MySQL suite | 3 passed on actual MySQL 8.4.5, 0 skipped |
| Design match | 26/26, 100% |
| JavaDoc | Passed |
| `git diff --check` | No whitespace errors; line-ending conversion warnings only |

The actual-MySQL tests exercised V3 constraints, repeated-login replacement, fixed-expiry rotation, previous-token rejection, logout deletion and idempotency, post-logout rejection, separate-connection conditional-update concurrency, and the winner/loser HTTP cookie outcome.

## 5. Check and Act Outcome

The initial Check found four bounded gaps: missing required-`exp` enforcement, character-based rather than UTF-8-byte BCrypt input validation, a second clock read during JWT issuance, and incomplete exact-expiry boundary coverage.

Act corrected all four without changing the approved public API, schema, cookie policy, or feature scope. Post-Act reanalysis reached 26/26 Design match with no remaining critical, high, or medium server gap.

## 6. Approved Boundaries and Remaining Client Work

- An Access Token issued before logout can remain valid for at most 15 minutes because this MVP has no Access Token blacklist.
- One current Refresh Token row per user intentionally replaces a prior persistent session after a new successful login.
- PWA-side Access Token memory storage and removal, single-flight refresh, shared authentication-operation serialization, and late-result rejection remain unexecuted until the client exists.
- Advanced reuse detection, token families, multi-device sessions, Redis, rate limiting, account lockout, monitoring, automated cleanup, and signing-key rotation infrastructure remain excluded.
- Clean-checkout local configuration reproducibility remains the inherited project-foundation limitation; no local configuration or secret value is included in this Report.

## 7. Completion Boundary

`local-login` is complete through Report after one Act correction cycle. This Report does not perform staging, commit, push, Pull Request creation, merge, archive, PWA implementation, account deletion, social login, or downstream domain work.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-08-26 | Completed `LOCAL-LOGIN-002` with 26/26 Design match and passing actual MySQL 8.4.5 verification |
