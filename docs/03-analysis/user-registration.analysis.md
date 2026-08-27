# user-registration - Gap Analysis

> **Superseded Notice (`AUTH-SCOPE-REDUCTION-002`)**: The signup `verificationProof` consumption contract is replaced by direct `email` and `verificationCode` validation. This document remains unchanged as a historical record of the implementation completed at that time.

> **Analysis Type**: Act re-analysis after toy-project simplification <br>
> **Project**: Nyamlog <br>
> **Feature**: `user-registration` <br>
> **Version**: 1.1.0 <br>
> **Date**: 2026-08-15 <br>
> **Status**: Complete - ready for Report approval <br>
> **Current Design**: [`user-registration.design.md`](../02-design/features/user-registration.design.md) <br>
> **Current Match Rate**: 100% (28/28)

---

## 1. Result

The implementation matches the approved Plan and Design 2.0.2 after applying
`USER-REGISTRATION-009`. The former operations-oriented password and concurrency
requirements were removed without weakening the transaction, email-proof, secret,
or database-integrity boundaries that remain part of the feature.

Social signup/login is not a gap. `FOUNDATION-006-R1` assigns provider integration
and first-social-login account creation to the separate future `social-login` feature.
Email challenge delivery, code confirmation, and signup-proof issuance likewise
remain owned by the separate `email-verification` feature.

## 2. Match Summary

| Area | Result | Evidence |
|------|--------|----------|
| API contract | 5/5 | Final signup endpoint, proof-bound identity, required request fields, 201 response, safe public errors |
| Data and transaction | 8/8 | Existing V1 schema, proof hash and lock, expiry check, policy validation, unique guard, atomic writes, proof deletion, rollback |
| Password and secret safety | 5/5 | 8-character minimum, 72-byte boundary, BCrypt, no normalization/blocklist, sensitive-value redaction |
| OpenAPI and Swagger | 4/4 | Korean descriptions, write-only sensitive fields, default-off exposure, Try it out allowed only after explicit enablement |
| Representative verification | 6/6 | Policy, controller, actual MySQL success, rollback/replay/unique, OpenAPI contract |
| **Total** | **28/28** | **100%** |

## 3. Implemented Realignment

### 3.1 Removed obsolete complexity

- Removed the large local password blocklist and its source metadata.
- Removed `CompromisedPasswordBlocklist` and `PASSWORD_COMPROMISED`.
- Removed unused registration-owned `EmailAddress` and `EmailAddressPolicy`.
- Removed the split `RegistrationPersistenceService` and its exact constraint-name parser.
- Removed obsolete blocklist, email-policy, persistence-classification, replacement,
  deterministic concurrency, and broad constraint tests from the completion gate.

### 3.2 Preserved core correctness

- `UserRegistrationService.register` is the visible `@Transactional` boundary.
- The proof is hashed, loaded with a pessimistic write lock, checked for expiry,
  and deleted only after account data is prepared successfully.
- User, local credential, and three consent rows commit together.
- A later persistence failure rolls back the account rows and proof deletion.
- Canonical email is checked in the service and remains protected by the MySQL UNIQUE constraint.
- Passwords are stored only as complete delegating BCrypt values.
- Passwords and verification proofs remain write-only and have no OpenAPI examples.

### 3.3 Simplified public behavior

- Password policy is now at least 8 Java characters and at most 72 UTF-8 bytes.
- Submitted password text is neither normalized nor trimmed.
- The public 422 contract no longer includes `PASSWORD_COMPROMISED`.
- OpenAPI remains disabled by default through `NYAM_OPENAPI_ENABLED=false`.
- When explicitly enabled, Swagger UI uses its normal submit methods so the developer
  can exercise the API in a local or access-restricted environment.

## 4. Verification Evidence

Executed on 2026-08-15 with Java 17 and Gradle 8.14.4:

```text
.\gradlew.bat test
BUILD SUCCESSFUL
tests=38 failures=0 errors=0 skipped=0 suites=12
```

Required actual-MySQL evidence:

```text
UserRegistrationMySqlIntegrationTest
tests=5 failures=0 errors=0 skipped=0
```

Those five tests cover:

1. Fresh-schema successful signup and BCrypt persistence
2. Successful proof consumption and replay rejection
3. Expired proof and duplicate canonical email rejection
4. Real database failure rollback with proof retained for retry
5. Canonical-email UNIQUE enforcement and owned-row cascades

Additional evidence:

```text
PasswordPolicyTest: tests=4 failures=0 errors=0 skipped=0
UserRegistrationServiceTest: tests=5 failures=0 errors=0 skipped=0
OpenApiContractTest: tests=1 failures=0 errors=0 skipped=0
.\gradlew.bat javadoc: BUILD SUCCESSFUL
```

`git diff --check` reported no whitespace errors. The displayed line-ending messages
were Git working-copy warnings for pre-existing modified files, not validation failures.

## 5. Remaining Gaps and Boundaries

No implementation gap remains inside the approved `user-registration` Design.

The following are intentionally separate and not implemented by this feature:

- Mailpit email delivery, code confirmation, and `verificationProof` issuance
- Local login, Access Token issuance, protected API authentication, and logout
- Social-provider authorization and first-social-login onboarding
- Account deletion internals

Swagger can display and submit the final signup request when explicitly enabled,
but the complete human flow cannot begin from email delivery until the separate
`email-verification` feature is implemented.

## 6. Next Step

The Act corrections and re-analysis satisfy the Report quality gate. Keep Report
pending until the user explicitly approves the phase transition.

## Version History

| Version | Date | Change |
|---------|------|--------|
| 1.1.0 | 2026-08-15 | Recorded 28/28 alignment and 38 passing tests after implementing `USER-REGISTRATION-009` |
| 1.0.2 | 2026-08-14 | Recorded that social signup/login remains project scope but is not a gap in local registration |
| 1.0.1 | 2026-08-14 | Clarified that voluntary trial by a few acquaintances is allowed |
| 1.0.0 | 2026-08-14 | Marked the former 100% as historical after the scope reset |
| 0.2.0 | 2026-08-14 | Historical 36/36 result against superseded Plan 1.2.7 and Design 1.2.0 |
