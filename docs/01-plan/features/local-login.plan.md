# local-login - Plan Document

> **Superseded Notice (`AUTH-SCOPE-REDUCTION-002`)**: Only the statement that signup consumes a `verificationProof` is replaced by direct email-code validation; the Access/Refresh Token, cookie, CSRF, rotation, logout, and concurrent single-winner contracts remain in force. This document remains unchanged as a historical record of the implementation completed at that time.

> **Version**: 1.1.0 <br>
> **Date**: 2026-08-24 <br>
> **Status**: Approved <br>
> **Decision**: `LOCAL-LOGIN-001-R1` <br>
> **Related Issue**: #12 <br>
> **Scope Authority**: `FOUNDATION-006-R1`, `AUTH-001-R2`, `USER-REGISTRATION-009`, `EMAIL-VERIFICATION-007`

---

## 1. Purpose

Complete the smallest secure local-login vertical flow for Nyamlog's repeated PWA use. A registered user signs in with email and password, calls protected APIs with a short-lived Access Token, restores access after a PWA restart through a server-managed Refresh Token, and logs out by revoking the Refresh Token.

## 2. Current Baseline

- Signup creates `users` and `local_credentials` together after consuming an email-verification proof; there is no persisted `PENDING` or `ACTIVE` account state.
- Passwords are stored through Spring Security's BCrypt `PasswordEncoder`.
- Spring Security cryptography is present, but protected web authentication and token issuance are not yet implemented.
- OpenAPI and Swagger UI already document the existing authentication endpoints.

## 3. User Flow

```text
email/password login
  -> Access Token in the response body
  -> Refresh Token in an HttpOnly cookie
  -> protected request with Authorization: Bearer
  -> PWA restart or Access Token expiry
  -> Access Token reissue with Refresh Token rotation
  -> logout, server-side Refresh Token revocation, cookie deletion, and client Access Token removal
  -> subsequent refresh rejection
```

## 4. Scope

### Included

- Verify email and password against the existing user and local credential records.
- Return a short-lived Access Token in the response body and authenticate protected APIs through Spring Security Bearer authentication.
- Identify the authenticated user through `SecurityContext`.
- Issue a comparatively long-lived Refresh Token only through an HttpOnly cookie.
- Reissue an Access Token after PWA restart and rotate the Refresh Token atomically.
- Maintain server-side Refresh Token validity state in MySQL and store no raw Refresh Token.
- Revoke the server-side Refresh Token on logout and return a matching cookie-deletion response.
- Keep cookie, CORS, and CSRF handling minimal under a same-site PWA/API deployment assumption where feasible.
- Verify login, refresh, protected access, logout, and refresh rejection through automated web and actual-MySQL integration tests; use Swagger only as supplemental manual evidence.

### Excluded

- Frontend or PWA implementation
- Access Token blacklist or immediate server-side revocation
- Multi-device session management or session-management UI
- Advanced Refresh Token reuse detection, token-family genealogy, or theft monitoring
- Redis or another distributed session store
- Rate limiting, account lockout, security monitoring, or production authentication operations
- Automated expired-session cleanup, signing-key rotation infrastructure, password reset, and social login
- Role/permission expansion and downstream food or meal implementation

An Access Token issued before logout may remain valid until its short expiry. This boundary must be documented in the public contract.

## 5. Functional Requirements

| ID | Requirement |
|----|-------------|
| `LL-01` | Login succeeds only when the user, local credential, and password match. Missing users, missing local credentials, and password mismatches use the same safe authentication-failure contract. |
| `LL-02` | Login returns an Access Token in the response body and a Refresh Token only as an HttpOnly cookie. |
| `LL-03` | Protected APIs accept `Authorization: Bearer <access-token>` and expose the authenticated user through `SecurityContext`. |
| `LL-04` | Refresh accepts the Refresh Token only from its cookie and returns a new Access Token plus a rotated Refresh Token cookie. |
| `LL-05` | Refresh Token rotation changes server validity state atomically so the previous token cannot win a later refresh. |
| `LL-06` | Concurrent refresh requests using the same previous Refresh Token produce one winner; the loser returns no `Set-Cookie` and therefore cannot delete or overwrite the winner's rotated cookie. |
| `LL-07` | Rotated Refresh Token expiry follows the sliding or absolute-expiry contract approved in the integrated Design. |
| `LL-08` | Logout revokes the presented Refresh Token when present, returns the matching cookie-deletion response, and is safe to repeat. |
| `LL-09` | Refresh after logout or with an invalid, expired, or replaced token is rejected without exposing token or persistence details. |

## 6. Security and Data-Integrity Requirements

- Never log or persist passwords, Access Tokens, raw Refresh Tokens, or secret values.
- Do not place the Refresh Token in request/response DTOs or a general authentication header.
- Do not reuse the password-verification BCrypt `PasswordEncoder` to create Refresh Token digests. The integrated Design will select SHA-256 or HMAC based on the final threat and storage model.
- Conditional rotation must preserve one valid server state when the same previous Refresh Token is refreshed concurrently. Global ordering across login, refresh, and logout is a PWA acceptance contract rather than a server guarantee.
- Cookie attributes and deletion attributes must match exactly enough for the browser to replace or remove the intended cookie.
- The Access Token signing key and, when HMAC is selected, the Refresh Token digest secret are supplied outside the repository.
- Keep error responses safe and avoid revealing whether a user, credential, or token record exists.

## 7. Completion Criteria

- [ ] The approved login, refresh, protected-request, logout, and refresh-rejection contracts are implemented.
- [ ] Access and Refresh Token transport boundaries are preserved.
- [ ] Refresh Token rotation is atomic, the previous token is rejected, and concurrent use of the same previous token produces one winner while the loser returns no `Set-Cookie`.
- [ ] Refresh Token expiry after rotation matches the approved sliding or absolute-expiry contract.
- [ ] Logout revokes the server state, deletes the cookie, and documents the remaining short Access Token lifetime.
- [ ] Automated web tests cover representative success and failure contracts.
- [ ] Actual-MySQL integration tests execute and prove rotation, previous-token rejection, concurrency, logout revocation, and post-logout refresh rejection.
- [ ] OpenAPI contract tests and optional Swagger verification remain aligned with the implemented flow.
- [ ] No excluded authentication infrastructure or downstream domain work is introduced.

## 8. Integrated Design Review

One consolidated Design must decide the complete vertical flow before implementation:

1. **Token and API contract**: endpoints, response/error meanings, Access Token claims and lifetime, and Refresh Token generation and transport.
2. **Persistence and session policy**: schema, digest algorithm, lifetime values, sliding versus absolute expiry, rotation transaction, logout idempotency, and concurrency/locking behavior.
3. **Browser security contract**: cookie name/path/domain, `HttpOnly`, `Secure`, `SameSite`, CORS/CSRF assumptions, deletion attributes, loser responses without `Set-Cookie`, and PWA-side authentication-operation serialization.
4. **Verification contract**: representative unit/web/OpenAPI tests and one deterministic actual-MySQL single-winner scenario. PWA response application and authentication-operation serialization remain unexecuted client acceptance criteria until that client exists.

Exact token lifetimes, schema columns, algorithms, locking strategy, cookie attributes, and endpoint payloads remain Design decisions.

## 9. Risks

| Risk | Plan response |
|------|---------------|
| A leaked long-lived Refresh Token extends account access | Store only a digest, rotate on refresh, revoke on logout, and decide expiry policy explicitly. |
| Concurrent refresh causes multiple valid sessions or cookie loss | Define one atomic winner for the same previous token and verify that the loser returns no `Set-Cookie`. |
| Cookie assumptions differ after deployment | Make the same-site assumption and environment-specific cookie attributes explicit in Design. |
| Logout appears immediate while an Access Token remains valid | Use a short Access Token lifetime and document the no-blacklist boundary. |
| Authentication work expands toward production identity infrastructure | Enforce the excluded scope and require a new approved feature decision for expansion. |

## 10. `LOCAL-LOGIN-001` - Minimal Persistent Local Login

**Status:** Approved 2026-08-23

Refresh Token support belongs to the `local-login` vertical flow because repeated PWA use requires login persistence after restart and Access Token expiry. This decision supersedes only these Foundation authentication clauses:

1. Foundation Plan section 4 no longer defers Refresh Token rotation; advanced reuse detection and multi-device session management remain deferred.
2. Foundation Plan section 6 assigns `local-login` Refresh Token issuance, server validity state, Access Token reissue, rotation, and logout revocation in addition to login and protected authentication.
3. Foundation Design section 4 replaces client-only logout with server-side Refresh Token revocation and cookie deletion while retaining client-side Access Token removal and the boundary that an issued Access Token may remain valid until its short expiry.
4. Foundation Design section 4 no longer defers Refresh Token rotation; password reset, additional social providers, advanced reuse detection, device-session management, production mail, and account lifecycle states remain deferred.

All other approved Foundation principles remain unchanged.

### `LOCAL-LOGIN-001-R1` - Concurrency Verification Boundary

**Status:** Approved 2026-08-24 <br>
**Supersedes:** Only the browser response-order automation and broad concurrency wording of `LOCAL-LOGIN-001`

- The server guarantees a single winner only when the same previous Refresh Token is refreshed concurrently.
- The loser returns `401` without `Set-Cookie`; actual MySQL proves the conditional rotation result and web tests prove the response-header contract.
- Login, refresh, and logout are serialized by the future PWA authentication module. This remains unexecuted client acceptance criteria until the PWA exists.
- Automated application of winner and loser responses in different browser arrival orders is not an MVP completion requirement.

All other scope and Foundation supersession in `LOCAL-LOGIN-001` remain approved.

## 11. Next Gate

The consolidated `LOCAL-LOGIN-002` Design is approved. Implementation remains a separate gate.

## Version History

| Version | Date | Change |
|---------|------|--------|
| 1.1.0 | 2026-08-24 | Approved `LOCAL-LOGIN-001-R1`: narrowed concurrency completion evidence to identical-token server rotation and response headers; PWA ordering remains unexecuted client acceptance criteria |
| 1.0.0 | 2026-08-23 | Approved `LOCAL-LOGIN-001` for Access/Refresh Token login persistence; Design and implementation remain separate gates |
