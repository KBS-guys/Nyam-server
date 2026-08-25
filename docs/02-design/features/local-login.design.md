# local-login - Design Document

> **Version**: 1.0.0 <br>
> **Date**: 2026-08-24 <br>
> **Status**: Approved <br>
> **Plan**: `docs/01-plan/features/local-login.plan.md` <br>
> **Related Issue**: #12 <br>
> **Decision**: `LOCAL-LOGIN-002`

---

## 1. Outcome and Boundary

Implement one persistent local-login vertical flow:

```text
email/password login
  -> Access Token response body
  -> Refresh Token HttpOnly cookie
  -> Bearer-protected /me
  -> cookie-based Access Token reissue and Refresh Token rotation
  -> logout, server row deletion, cookie deletion, client Access Token removal
  -> later refresh rejection
```

Access Token authentication remains stateless. MySQL stores one current Refresh Token hash per user. Advanced session lineage, theft detection, multiple device sessions, Redis, Access Token blacklisting, rate limiting, account lockout, and authentication monitoring are excluded.

## 2. Public API and Failure Contract

| Method and path | Authentication | Request | Success |
|---|---|---|---|
| `POST /api/v1/auth/login` | Public | JSON email and password | `200 LOGIN_COMPLETED`, Access Token body, Refresh Token cookie |
| `POST /api/v1/auth/refresh` | Refresh cookie and `X-Nyam-CSRF: 1` | No body | `200 ACCESS_TOKEN_REISSUED`, new Access Token body and rotated cookie |
| `POST /api/v1/auth/logout` | `X-Nyam-CSRF: 1`; Refresh cookie optional | No body | `200 LOGOUT_COMPLETED`, deletion cookie |
| `GET /api/v1/auth/me` | Bearer Access Token | No user identifier | `200 AUTHENTICATED_USER_RETRIEVED`, display email |

Login and refresh return only `accessToken`, `tokenType=Bearer`, and `expiresInSeconds=900` in the data body. The Refresh Token never appears in a DTO, response body, query parameter, Bearer header, log, or OpenAPI example. `/me` derives the positive internal user ID only from `SecurityContext`.

Login, refresh, logout, and `/me` return `Cache-Control: no-store`.

| Condition | HTTP | Code | Cookie response |
|---|---:|---|---|
| Invalid login body | 400 | `INVALID_INPUT` | None |
| User, local credential, or password mismatch | 401 | `LOGIN_FAILED` | None |
| Missing, malformed, unknown, expired, or replaced Refresh Token | 401 | `REFRESH_TOKEN_INVALID` | No `Set-Cookie` |
| Missing, malformed, modified, or expired Access Token | 401 | existing `E003` | None |
| Missing required CSRF marker | 403 | `CSRF_REQUEST_REJECTED` | None |
| Authenticated request denied | 403 | existing `E004` | None |

Bearer failures retain `WWW-Authenticate: Bearer` together with the common safe JSON error envelope. Authentication failures do not disclose whether a user, credential, or token row exists.

Refresh and logout evaluate failures in this order: ignore Bearer for the endpoint, validate `X-Nyam-CSRF`, parse the cookie, perform the database operation, then produce the response. A CSRF failure therefore precedes cookie validation. After CSRF succeeds, logout always returns its idempotent success and deletion cookie.

## 3. Credential and Access Token Design

Login reuses `EmailCanonicalizer` and the existing delegating BCrypt `PasswordEncoder`. It calls `PasswordEncoder.matches` exactly once: a missing user or credential is checked against one startup-generated dummy BCrypt value. All credential failures return `LOGIN_FAILED`; the submitted password is never normalized, echoed, or logged.

| Access Token property | Decision |
|---|---|
| Format and algorithm | JWT, exact HS256 |
| Lifetime | 15 minutes |
| Client storage | Memory only |
| Transport | `Authorization: Bearer` |
| Claims | `iss=nyamlog`, positive decimal `sub`, `aud=nyamlog-api`, `exp` |
| Clock skew | Zero for the single-server MVP |

The decoder validates the signature, algorithm, expiration, issuer, audience, and a positive decimal `sub` that fits the internal `BIGINT` range. `iat`, roles, and scopes are not required. Malformed identity claims are authentication failures rather than server errors.

Spring Security OAuth2 Resource Server performs JWT authentication; no custom JWT parser or Bearer filter is introduced. `NYAM_AUTH_ACCESS_SECRET` supplies a Base64 value decoding to at least 32 random bytes. It has no repository default, is never printed, and causes startup failure when absent or invalid. Tests use a separate non-production key.

## 4. Refresh Token, Persistence, and Time

A Refresh Token is 32 `SecureRandom` bytes encoded as 43 URL-safe Base64 characters without padding. The server stores only SHA-256 of the complete ASCII token. BCrypt is not used for Refresh Token hashing.

The token has one fixed 30-day absolute lifetime. Rotation preserves the original `expires_at`; refresh activity does not extend the session. A successful password login starts a new lifetime and invalidates the user's previous Refresh Token.

Flyway V3 adds:

| Column | Type | Constraint |
|---|---|---|
| `user_id` | `BIGINT` | Primary key, foreign key to `users(user_id)`, cascade delete |
| `token_hash` | `BINARY(32)` | Not null, unique |
| `issued_at` | `DATETIME(6)` | Not null |
| `expires_at` | `DATETIME(6)` | Not null, `expires_at > issued_at` |

One row per user is the complete MVP session model. Login creates the row when absent or updates `token_hash`, `issued_at`, and `expires_at` when present, in one transaction. This wording does not prescribe MySQL `REPLACE`.

All authentication time uses one injected UTC `Clock`; business logic does not call system time directly. Each login or refresh captures `now` once, and persisted values represent UTC independently of JVM, operating-system, or MySQL session time zones.

## 5. Cookie, Origin, and CSRF Contract

| Cookie attribute | Value |
|---|---|
| Name | `__Secure-nyam-refresh` |
| `HttpOnly` | `true` |
| `Secure` | `true` |
| `SameSite` | `Strict` |
| `Path` | `/api/v1/auth` |
| `Domain` | Omitted |
| `Max-Age` | Remaining fixed lifetime in whole seconds |

Deletion uses the same name, path, `Secure`, `HttpOnly`, `SameSite`, and omitted `Domain`, with `Max-Age=0`.

Refresh eligibility requires at least one whole second of remaining lifetime. `remainingSeconds = floor(expiresAt - now)` at zero or below returns `REFRESH_TOKEN_INVALID` without rotation or `Set-Cookie`. A successful rotated cookie uses that positive `remainingSeconds` as `Max-Age`.

The PWA and API use the same origin; HTTPS is required outside localhost, local PWA development uses a same-origin proxy, and credentialed CORS is not enabled. Refresh and logout require the public marker `X-Nyam-CSRF: 1`. `SameSite=Strict`, the custom-header preflight requirement, and the absence of credentialed CORS form the approved cookie-request boundary. A cross-origin deployment requires a new Design.

## 6. Login, Refresh, and Logout Transactions

### Login

After credentials succeed, login captures `now`, prepares both tokens, and creates or updates the user's single Refresh Token row in one transaction. The row commits before the controller returns the Access Token body and Refresh Token cookie.

### Atomic refresh rotation

Refresh hashes the cookie and performs a non-locking lookup to obtain candidate `userId` and `expiresAt`. After capturing `now`, it rejects non-positive `remainingSeconds`, prepares the new Access and Refresh Tokens, and executes one conditional update:

```sql
UPDATE refresh_tokens
SET token_hash = :newHash,
    issued_at = :now
WHERE user_id = :userId
  AND token_hash = :oldHash
  AND expires_at >= :minimumExpiresAt;
```

`minimumExpiresAt` is `now + 1 second`; `expires_at` itself is not changed. The lookup prepares issuance but does not decide whether the submitted token is still current. The update count is the final current-token and concurrency decision:

- one row: commit and return the prepared Access Token and rotated cookie;
- zero rows: discard prepared values, return `401 REFRESH_TOKEN_INVALID`, and return no `Set-Cookie`.

Two concurrent refreshes using the same previous token may both finish the lookup, but only one can replace the old hash. The winner receives one affected row; the loser receives zero. No `SELECT FOR UPDATE` is required.

### Logout

After CSRF validation, a validly formatted cookie is hashed and deleted by its unique hash:

```sql
DELETE FROM refresh_tokens
WHERE token_hash = :tokenHash;
```

A matching row is deleted regardless of whether it is already expired; this is cleanup, not authentication of an expired token. Missing, malformed, unknown, replaced, or already deleted values change no row. Every case returns `200 LOGOUT_COMPLETED` and the deletion cookie without revealing row existence.

The PWA removes its in-memory Access Token. An Access Token issued before logout can remain valid until its 15-minute expiration because this MVP has no Access Token blacklist.

### Concurrency boundary

The server guarantees only atomic single-winner rotation for concurrent refreshes using the same previous Refresh Token, including no `Set-Cookie` on the loser response. It does not globally order concurrent login, refresh, and logout operations. The future PWA authentication module must serialize them with one shared mutex or equivalent mechanism and discard late authentication results after logout begins. Until the PWA exists, this is reported as unexecuted client acceptance criteria, not passed server evidence.

## 7. Spring Security and OpenAPI

The filter chain is stateless, disables form login, HTTP Basic, and server sessions, and uses OAuth2 Resource Server JWT authentication with safe JSON entry-point and access-denied responses. The Bearer resolver ignores Bearer headers on login, refresh, and logout so a stale Access Token cannot block those recovery endpoints.

Existing email-verification and signup APIs remain public. `/me` and later user-owned APIs require Bearer authentication and obtain ownership identity from the authenticated principal.

OpenAPI adds a Bearer JWT scheme, marks protected operations, documents the login password as write-only, and describes cookie and CSRF behavior textually. It defines no password, Access Token, Refresh Token, token hash, or signing-key example/default. OpenAPI stays disabled by default; Swagger is supplemental manual evidence only.

## 8. Verification Contract

The verification list defines contracts that must be proved; it does not require one test class or method per item, and one test may cover several contracts.

Representative unit and web tests cover credential success/failure and the dummy-hash path; JWT claims and rejection; Refresh Token format, SHA-256 storage, fixed expiry and equality boundary; cookie creation/deletion; CSRF priority; Bearer recovery endpoints; `/me` identity from `SecurityContext`; safe errors and headers; sensitive-value non-exposure; and OpenAPI alignment.

Actual MySQL 8.4.5 tests must execute without skip and cover V3 constraints, login row creation/update, rotation and fixed expiration preservation, previous-token rejection, logout deletion/idempotency, post-logout rejection, and the complete login-to-logout flow.

One dedicated concurrency integration test uses two threads with separate connections and transactions, coordinates both non-locking lookups before their conditional updates, and proves:

- affected-row counts are exactly one and zero;
- only the winner's new hash remains in MySQL;
- the winner returns the rotated cookie;
- the loser returns `401` without `Set-Cookie`;
- the previous token is rejected afterward.

Automated browser response-arrival ordering is not required. PWA serialization, Access Token memory removal, single-flight refresh, and late-result rejection remain unexecuted client acceptance criteria until implemented by that client.

Standard verification is `./gradlew.bat test javadoc` on Windows plus `git diff --check`. Passed, failed, errored, skipped, and unexecuted results are reported separately; a skipped MySQL test is not successful evidence.

## 9. Decision and Remaining Gate

### `LOCAL-LOGIN-002` - Simplified Persistent Local Login Design

**Status:** Approved 2026-08-24

Approve the API, 15-minute HS256 Access Token, 30-day fixed opaque Refresh Token, SHA-256 storage, one-row-per-user schema, conditional single-winner rotation, cookie/CSRF policy, idempotent logout, Spring Security boundary, and representative verification contract defined above.

There are no remaining Design decisions. This approval records the Design and completes the Design phase only. Dependency, configuration, Migration, Java, test, stage, commit, push, and Pull Request work remain separately gated.

## Version History

| Version | Date | Change |
|---|---|---|
| 1.0.0 | 2026-08-24 | Approved `LOCAL-LOGIN-002` as the simplified persistent local-login Design |
