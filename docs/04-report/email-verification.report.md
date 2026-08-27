# email-verification Completion Report

> **Superseded Notice (`AUTH-SCOPE-REDUCTION-002`)**: The separate code-confirmation and `verificationProof` issuance contracts are replaced by direct verification during signup. This document remains unchanged as a historical record of the implementation completed at that time.

> **Status**: Complete <br>
> **Project**: Nyamlog <br>
> **Completion Date**: 2026-08-21 <br>
> **Current Decision**: `EMAIL-VERIFICATION-007` <br>
> **Related Issue**: [#8](https://github.com/KBS-guys/Nyam-server/issues/8)

---

## 1. Summary

| Item | Result |
|------|--------|
| Feature | `email-verification` |
| Scope | Local Mailpit code delivery through one-time signup proof issuance |
| Completion Rate | 100% |
| Design Match Rate | 100% (22/22) |
| Functional Requirements | 13/13 complete |
| Act | Not required |

The feature now provides one working vertical flow: request a verification code,
inspect the local Mailpit message, confirm the code, receive a one-time
`verificationProof`, and consume that proof through the existing signup API.

This completion covers email possession verification only. Account creation remains
owned by `user-registration`; login, Access Token issuance, logout, social login,
production mail, and account deletion remain separate features.

## 2. Related Documents

| Phase | Document | Status |
|-------|----------|--------|
| Plan | [`email-verification.plan.md`](../01-plan/features/email-verification.plan.md) | Approved 1.1.0 |
| Design | [`email-verification.design.md`](../02-design/features/email-verification.design.md) | Approved 1.0.0 |
| Analysis | [`email-verification.analysis.md`](../03-analysis/email-verification.analysis.md) | Complete 1.0.0 |

## 3. Completed Vertical Flow

1. `POST /api/v1/auth/email-verifications` validates and canonicalizes the email.
2. A registered email is rejected before challenge creation and mail delivery.
3. An available email receives a six-digit code through local Mailpit.
4. `POST /api/v1/auth/email-verifications/confirm` validates the current code,
   expiry, and attempt state.
5. Successful confirmation deletes the challenge and issues a 15-minute,
   one-time `verificationProof` in one transaction.
6. The existing `POST /api/v1/auth/signup` consumes the proof and creates the
   local account atomically.

### 3.1 Data and Security

- Added `email_verification_challenges` through Flyway V2 with one active row per
  canonical email, bounded counters, and timestamp constraints.
- Generated exact six-digit codes with `SecureRandom`.
- Persisted only a full HMAC-SHA-256 verifier bound to the canonical email; raw
  codes are not stored or logged.
- Preserved the existing 43-character proof and SHA-256 lookup contract.
- Required a Base64 HMAC secret of at least 32 decoded bytes with no repository
  default and fail-fast startup validation.
- Used pessimistic locking for existing challenge and proof state.

### 3.2 Operating Policy and Failure Outcomes

- Code lifetime: five minutes; equality with the expiry time is expired.
- Resend wait: 60 seconds; at most three resends in one session.
- Failed attempts: at most five mismatches; expired input does not increase the count.
- Resend replaces the prior code and resets mismatch state for the new code.
- Mail failure rolls back the challenge insert or update.
- Successful confirmation atomically deletes the challenge, replaces any prior
  unused proof for the email, and inserts the new proof.
- Concurrent first sends rely on MySQL primary-key uniqueness; no automatic retry
  is performed.

### 3.3 API and Local Runtime Impact

- Added two Korean OpenAPI-documented endpoints under
  `/api/v1/auth/email-verifications`.
- Added safe public errors for duplicate email, invalid verification state,
  resend limit, attempt limit, and delivery failure.
- Added Spring Mail and loopback-bound `axllent/mailpit:v1.30.7` ports 1025 and 8025.
- Sensitive request fields have no Swagger examples or defaults.

## 4. Verification Results

The following results were produced during Check and were not rerun merely to
write this Report.

| Verification | Result |
|--------------|--------|
| `\.\gradlew.bat test javadoc` | Passed |
| Entire Gradle test suite | 63 passed, 0 failed, 0 errors, 0 skipped |
| Email-verification MySQL suite | 6 passed on actual MySQL 8.4.5 |
| MySQL and Mailpit vertical flow | 1 passed on MySQL 8.4.5 and Mailpit v1.30.7 |
| Total actual-MySQL integration tests | 13 passed, 0 skipped |
| JavaDoc | Passed |
| `git diff --check` | Passed |

The vertical-flow test enabled Swagger endpoints, requested a code, retrieved the
Mailpit message without printing the code, confirmed it, received a proof, called
the existing signup endpoint, and verified challenge and proof consumption.

## 5. Security, Integrity, and Approved Boundaries

- Codes, proofs, passwords, tokens, hashes, credentials, and internal exception
  details are not exposed in public examples or test output.
- Actual MySQL tests cover schema checks, locking, concurrent first sends,
  fifth-mismatch persistence, mail-failure rollback, proof replacement, and replay
  prevention.
- SMTP acceptance followed by timeout or database commit failure can leave an
  unusable delivered code; the local MVP intentionally adds no outbox or distributed
  transaction.
- The same-email database lock may be held during the bounded SMTP call.
- Registered-email conflicts disclose account existence for the approved MVP flow.
- Redis, distributed rate limiting, production SMTP, scheduled cleanup, and
  commercial monitoring are not part of this feature.
- The Docker engine ran the Testcontainers verification, but the standalone Docker
  CLI was unavailable in the shell PATH, so `docker compose config` was not run.

## 6. Lessons and Completion Boundary

- Keeping challenge state in MySQL made resend, attempt, rollback, and concurrency
  behavior observable with the same transaction system that issues proofs.
- Returning confirmation failure state from the transactional service allows a
  fifth mismatch to commit before the controller maps it to the public error.
- One actual Mailpit-to-signup test provides stronger portfolio evidence than
  documenting the endpoints independently without exercising their handoff.
- Grouping the Design review around the complete vertical flow reduced approval
  overhead while preserving security and transaction decisions.

`email-verification` is complete through Report with no Act correction required.
Archive, staging, commit, push, Pull Request creation, `local-login`, and other
features are not performed by this Report.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-08-21 | Completed Report for `EMAIL-VERIFICATION-007` with 22/22 Design match and passing MySQL/Mailpit verification |
