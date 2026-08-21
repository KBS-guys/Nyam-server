# email-verification Analysis Report

> **Analysis Type**: Gap Analysis <br>
> **Project**: Nyamlog <br>
> **Analyst**: Codex <br>
> **Date**: 2026-08-21 <br>
> **Status**: Completed <br>
> **Design Doc**: [email-verification.design.md](../02-design/features/email-verification.design.md)

---

## 1. Analysis Scope

- Approved Design: `docs/02-design/features/email-verification.design.md` version 1.0.0
- Implementation: `build.gradle`, `docker-compose.yml`, V2 Flyway Migration, and the user-domain email-verification source and tests
- Verification command: `.\gradlew.bat test javadoc`
- Actual database baseline: Testcontainers `mysql:8.4.5`
- Actual mail baseline: Testcontainers `axllent/mailpit:v1.30.7`

## 2. Design-to-Implementation Match

### 2.1 Public Flow

| # | Design Contract | Implementation Evidence | Status |
|---|-----------------|-------------------------|--------|
| 1 | Send endpoint accepts an email and returns expiry and resend times | `EmailVerificationController.sendCode`, request/response DTOs | Match |
| 2 | Confirm endpoint accepts email and six-digit code and returns a signup proof | `EmailVerificationController.confirmCode`, request/response DTOs | Match |
| 3 | Approved 400/409/422/429/503/500 codes use the common envelope | `ErrorCode`, `GlobalExceptionHandler`, Controller tests | Match |
| 4 | Korean OpenAPI exposes the flow without code or proof examples/defaults | Controller/DTO annotations and `EmailVerificationOpenApiContractTest` | Match |

### 2.2 Data and Security

| # | Design Contract | Implementation Evidence | Status |
|---|-----------------|-------------------------|--------|
| 5 | One MySQL challenge row per canonical email with approved checks and no FK/cleanup index | V2 Migration, Entity, Repository | Match |
| 6 | Six-digit `SecureRandom` code and exact full HMAC-SHA-256 byte contract | Code generator/verifier and unit tests | Match |
| 7 | ASCII-only 254-character email boundary with `Locale.ROOT` canonicalization | `EmailCanonicalizer` and unit tests | Match |
| 8 | Existing 43-character proof and SHA-256 contract remain unchanged | `VerificationProofGenerator`, existing `VerificationProofHasher` | Match |
| 9 | Required Base64 HMAC secret has no default and fails startup when invalid or short | `EmailVerificationCodeVerifier` constructor validation | Match |
| 10 | Local Mailpit v1.30.7 uses loopback Compose ports, no auth/TLS, and five-second SMTP timeouts | Docker Compose and `EmailVerificationConfiguration` | Match |

### 2.3 Policy, Transaction, and Concurrency

| # | Design Contract | Implementation Evidence | Status |
|---|-----------------|-------------------------|--------|
| 11 | Registered email is checked before challenge creation and mail delivery | `EmailVerificationService.sendCode` | Match |
| 12 | Code expires at exactly five minutes and equality is expired | Service time comparison and boundary tests | Match |
| 13 | Resend waits 60 seconds, allows three resends, replaces the code, and resets mismatches | Service lifecycle and MySQL tests | Match |
| 14 | Five mismatches commit terminal state; expiry starts a new session | Commit-safe confirmation result and MySQL test | Match |
| 15 | Challenge flush and synchronous mail delivery share one transaction; mail failure rolls back | Service transaction, mail sender, rollback test | Match |
| 16 | Success atomically deletes challenge, replaces existing proof, and stores a 15-minute proof | `issueProof` and MySQL proof replacement/replay test | Match |
| 17 | Existing rows use write locks; first-insert uniqueness prevents duplicate state and no automatic retry occurs | Repository lock and MySQL lock/concurrency tests | Match |
| 18 | Cleanup, Redis, outbox, distributed limits, and production SMTP defenses remain out of scope | No added components beyond approved local flow | Match |

### 2.4 Verification Evidence

| # | Design Contract | Evidence | Status |
|---|-----------------|----------|--------|
| 19 | Unit, service, web, OpenAPI, and sensitive-value redaction checks | New focused test suites | Match |
| 20 | Actual MySQL validates Migration, constraints, and existing-row locking | `EmailVerificationMySqlIntegrationTest` | Match |
| 21 | Actual MySQL validates rollback, concurrent first requests, fifth mismatch, proof replacement, and replay prevention | Six MySQL email-verification tests | Match |
| 22 | Swagger-exposed paths support Mailpit send, confirm, proof handoff, and existing signup consumption | `EmailVerificationMailpitFlowIntegrationTest` with actual MySQL and Mailpit | Match |

## 3. Match Rate

```text
Overall Match Rate: 100%

Match:            22 items (100%)
Missing in Code:   0 items (0%)
Changed:           0 items (0%)
Missing in Design: 0 items (0%)
```

## 4. Quality and Risk Review

No critical, high, or medium implementation gap was found.

The following approved MVP limitations remain explicit rather than hidden:

- SMTP success or an ambiguous timeout followed by database rollback can leave an unusable mailed code.
- The same-email database lock can remain held during the bounded SMTP call.
- Registered-email 409 responses disclose account existence for MVP usability.
- Email-level limits are not public-production abuse protection.
- Expired rows for abandoned distinct emails use lazy cleanup only.

The Docker engine executed all Testcontainers tests, but a standalone Docker CLI was not available in the shell PATH, so `docker compose config` was not run. The Compose service definition uses the same Mailpit image and ports exercised by the passing container flow.

## 5. Verification Result

| Verification | Result |
|--------------|--------|
| `.\gradlew.bat test javadoc` | Passed |
| Gradle tests | 63 passed, 0 failed, 0 errors, 0 skipped |
| Email-verification MySQL tests | 6 passed on MySQL 8.4.5 |
| MySQL + Mailpit vertical-flow test | 1 passed on MySQL 8.4.5 and Mailpit v1.30.7 |
| Total actual-MySQL integration tests | 13 passed, 0 skipped |
| JavaDoc | Passed |
| Tracked diff whitespace check | Passed |

## 6. Next Step

Do and Check are complete with no Act correction required. Report remains the next PDCA phase and has not been started.

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0.0 | 2026-08-21 | Recorded 22/22 Design match and final verification evidence | Codex |
