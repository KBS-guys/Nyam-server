# email-verification - Design Document

> **Superseded Notice (`AUTH-SCOPE-REDUCTION-002`)**: The separate code-confirmation and `verificationProof` issuance contracts are replaced by direct verification during signup. This document remains unchanged as a historical record of the implementation completed at that time.

> **Summary**: Define the minimal Mailpit email-verification flow through a consolidated, security-focused Design review. <br>
> **Project**: Nyamlog <br>
> **Version**: 1.0.0 <br>
> **Author**: Project decision record <br>
> **Date**: 2026-08-21 <br>
> **Status**: Approved <br>
> **Plan**: docs/01-plan/features/email-verification.plan.md <br>
> **Current Decision**: EMAIL-VERIFICATION-007

---

## 1. Overview

### 1.1 Design Goal

Complete the Mailpit code-send, code-confirmation, one-time proof issuance, and existing signup handoff flow with the smallest secure Spring Boot and MySQL design that remains understandable to a junior developer.

### 1.2 Design Boundaries

This Design owns only the email-verification scope approved by EMAIL-VERIFICATION-001.

It does not own account creation, login, Access Token issuance, logout, social login, password recovery, production mail, distributed rate limiting, advanced cleanup, account deletion, or commercial security operations.

EMAIL-VERIFICATION-007 approves the complete Design after integrating EMAIL-VERIFICATION-002 through EMAIL-VERIFICATION-006 and the consolidated review corrections. Design is complete, but implementation remains a separate approval gate.

### 1.3 Applicable Authority

- FOUNDATION-006-R1: deployed toy-project scope with email/password and separate social login
- USER-REGISTRATION-009: transaction-focused signup that consumes a proof
- EMAIL-VERIFICATION-001: minimal Mailpit email-verification Plan
- Issue #8: Mailpit delivery, confirmation, proof issuance, simple resend and attempt limits
- Existing runtime contract: Flyway schema authority, Hibernate ddl-auto=validate, and actual MySQL 8.4.5 verification

## 2. Current Implementation Facts

The current repository provides:

- users with a UNIQUE canonical_email constraint
- email_verification_proofs containing a proof digest, display email, canonical email, created time, and expiry
- VerificationProofHasher validating a 43-character URL-safe proof and generating its SHA-256 digest
- signup proof lookup with a pessimistic write lock
- one signup transaction that checks proof expiry, creates account data, and deletes the proof
- actual MySQL 8.4.5 integration tests that seed proof records directly

The current repository does not provide:

- a verification challenge table or entity
- verification-code generation or hashing
- resend or failed-attempt state
- Mailpit in Docker Compose
- Spring Mail dependency or mail configuration
- code-send or code-confirmation APIs
- end-to-end email-to-signup verification

These are implementation facts, not approved future details.

## 3. Approved Challenge-State Storage

### 3.1 Decision

EMAIL-VERIFICATION-002 approves storing the active email-verification challenge in MySQL.

Use one active challenge record per canonical email to hold the state required for:

- verification-code verifier
- code expiry
- resend control
- failed-attempt control

The physical table is `email_verification_challenges`. Section 9.4 defines its approved Migration contract.

### 3.2 Responsibility Separation

Challenge state and proof state have different responsibilities:

| State | Responsibility | Lifetime |
|-------|----------------|----------|
| Active challenge | Tracks code confirmation, expiry, resend, and failed attempts | From send until successful confirmation, invalidation, or replacement |
| Verification proof | Carries the confirmed email into existing signup | From successful confirmation until signup consumption or expiry |

The existing email_verification_proofs table remains responsible only for the signup handoff proof. Do not overload it with verification-code, resend, or failed-attempt state without a separately approved superseding decision.

### 3.3 Canonical-Email Boundary

A canonical email identifies the one active challenge for that email. This does not create a user account and does not replace the users canonical-email UNIQUE constraint.

The send request performs the approved registered-email lookup before creating or replacing a challenge. Final account uniqueness remains authoritative in the existing signup transaction and MySQL constraint.

### 3.4 Why MySQL

MySQL is already required by the project and supports the current learning goals:

- persistent state across application restart
- explicit schema and constraints through Flyway
- observable transaction and locking behavior
- rollback verification against actual MySQL
- no additional Redis runtime, configuration, or deployment responsibility

Application memory is not used because restart would discard active challenge state and because code consumption followed by MySQL proof issuance would span two state systems.

Redis is not introduced because the current single-instance toy-project scope has no observed traffic, expiry-volume, or multi-instance problem that justifies another datastore. Redis may be reconsidered only after a concrete operational need is observed and approved.

### 3.5 Final Consequences

- MySQL is the challenge-state datastore.
- There is one active challenge identity per canonical email.
- Challenge state is separate from the existing proof state.
- Application memory and Redis are not used for authoritative challenge state.
- The approved physical schema, policy limits, API, mail failure boundary, and verification contract are defined in Section 9.
- Implementation requires a Flyway Migration and actual MySQL 8.4.5 verification after separate authorization.

## 4. Approved Challenge Lifecycle and Transaction Boundaries

EMAIL-VERIFICATION-003 approves the lifecycle, conceptual field responsibilities, database atomicity boundary, and concurrency principles below. It does not approve a Flyway Migration or concrete Java structure.

### 4.1 Conceptual Current-Challenge Row

The following field responsibilities are realized by the physical schema in Section 9.4.

| Conceptual Field | Responsibility |
|------------------|----------------|
| Canonical email | Identifies the single current challenge row, supplies the HMAC email input, and binds issued proof state to the confirmed identity. |
| Display email | Preserves the stripped submitted representation used only for mail delivery and public response display. |
| Code verifier | Stores a safe verification value instead of the raw code. A simple unkeyed hash of the short code is prohibited. |
| Code expiry | Defines when the current code can no longer be confirmed. |
| Verification started at | Identifies the beginning of the current verification session rather than the physical age of a reused row. |
| Current-code request or issue time | Supports later resend-delay rules. Its final name depends on the approved mail-delivery failure contract. |
| Resend count | Counts resends within the current verification session. |
| Failed-attempt count | Counts mismatches against the current code. |

The raw verification code is never persisted in the application database or authentication-domain state. The code verifier follows the approved HMAC contract in Section 5.

### 4.2 Initial Request, Resend, and Expiry

- An accepted request with no row starts a new verification session with zero resend and failed-attempt counts.
- An allowed resend while the current session remains valid keeps the verification-start time, replaces the code verifier and expiry, increments the resend count, and resets the failed-attempt count for the new code.
- Replacing the verifier makes the previous code unusable immediately.
- A request after code expiry starts a new verification session in the same conceptual row: update the verification-start time and reset both counts.
- A resend is eligible 60 seconds after the current code issue time, with at most three resends after the initial send.
- Five mismatches make the current session terminal until code expiry and block further resends during that period.

An expired row is current storage but not a valid active code. Reusing the row does not carry historical counters into the new verification session.

### 4.3 Confirmation State Change

Confirmation resolves and write-locks the row for the canonical email before changing it.

The validation order is:

1. Require the row to exist.
2. Reject an expired code without counting it as a code mismatch.
3. Reject a session whose failed-attempt count is already five.
4. Verify the submitted code against the stored verifier.
5. On mismatch, increment the failed-attempt count in the same database transaction.
6. On success, transition atomically from challenge state to proof state.

Mismatches one through four return the invalid-verification result after committing the increment. The fifth mismatch commits a failed-attempt count of five and returns the attempt-limit result. Later attempts return the attempt-limit result without another comparison or increment.

### 4.4 Successful Confirmation Transaction

Successful confirmation performs these database changes in one MySQL transaction:

1. Delete the locked current-challenge row.
2. Replace any existing unconsumed proof for the same canonical email and store the new one-time proof digest with a 15-minute lifetime.

The transaction guarantees only database atomicity: challenge deletion, existing-proof replacement, and new proof storage commit together or roll back together. It does not guarantee that the client receives the raw proof after commit. A lost HTTP response may leave a valid proof digest in the database while the client has no raw proof; the user restarts verification, and a later successful confirmation replaces that unused proof.

### 4.5 Concurrency Boundary

- When a current-challenge row exists, a write lock serializes changes for the same canonical email.
- When no row exists, canonical-email uniqueness and insertion-conflict handling ensure that concurrent first requests cannot create multiple rows.
- In the normal concurrent-first-insert conflict, only the request that establishes the row sends mail; the losing request returns the send-limit result.
- A rare deadlock or lock failure may instead roll back one transaction and return an internal error while preserving data integrity.
- The MVP performs no automatic database retry, because retrying a transaction must not blindly repeat Mailpit delivery.
- Exact SQL and repository method names remain implementation choices and require actual MySQL 8.4.5 tests.

### 4.6 Lazy Expiry Cleanup

No scheduled cleanup job is introduced for this MVP. Confirmation rejects expired rows, a later accepted request replaces the row as a new session, and successful confirmation deletes it. Rows for distinct abandoned email addresses may accumulate; that limited operational tradeoff is accepted for the current toy-project scope and does not imply production cleanup readiness.

## 5. Approved Verification Code and Keyed Verifier

EMAIL-VERIFICATION-004 approves the verification-code format, generation method, exact keyed-verifier byte contract, comparison rule, and secret-management boundary below.

### 5.1 Code Format and Generation

- Generate the verification code uniformly with `SecureRandom` from 0 inclusive to 1,000,000 exclusive.
- Format the generated value as exactly six ASCII digits. Leading zeroes are significant and allowed.
- Validate the request representation with `^[0-9]{6}$`.
- After formatting, keep the code as a string across mail content, API input, and HMAC input. Do not parse it as an integer, trim it, or apply numeric normalization.

### 5.2 Exact HMAC Byte Contract

Store the full 32-byte HMAC-SHA-256 result as the code verifier. Construct the HMAC input in this exact order:

1. `US-ASCII("nyamlog:email-verification-code:v1")`
2. one zero byte, `0x00`
3. `UTF-8(canonicalEmail)`
4. one zero byte, `0x00`
5. `US-ASCII(verificationCode)`

The domain prefix prevents accidental cross-purpose verifier reuse. The separators make the variable-length fields unambiguous. The canonical-email transformation is the ASCII-only, `Locale.ROOT` lowercasing contract in Section 9.1, and that resulting string is the value encoded here.

### 5.3 Raw-Code Storage Boundary

- The Nyamlog application must not store the raw verification code in its application database, authentication-domain state, or application logs.
- Mailpit may retain the raw code as part of the delivered email body according to Mailpit's configured message retention. That local mail-system copy is an intentional development-tool boundary, not application-domain persistence.

### 5.4 Secret Configuration and Change Behavior

- Read the HMAC secret from `NYAM_EMAIL_VERIFICATION_HMAC_SECRET`.
- Interpret the value as RFC 4648 standard Base64 and require the decoded secret to contain at least 32 cryptographically random bytes.
- Provide no default value. Missing configuration, invalid Base64, or a decoded value shorter than 32 bytes must fail application startup.
- Keep the same secret across application restarts and instances that need to verify the same active challenges.
- Never store the secret in the repository or database, and never expose it through logs or Swagger.
- Changing the secret intentionally invalidates all active verification codes. The MVP does not support multiple active keys or key-version migration.

### 5.5 Comparison and Threat Boundary

- Recompute the full 32-byte HMAC for the submitted code and compare fixed-length byte arrays with a constant-time comparison suitable for secret verification.
- Six decimal digits alone are not sufficient protection. The approved five-minute expiry, five-mismatch limit, 60-second resend delay, three-resend maximum, and one-time successful consumption apply together.

### 5.6 Verification-Proof Separation

The existing verification proof is a separate, high-entropy value whose SHA-256 digest contract remains unchanged. Do not reuse `VerificationProofHasher` to verify the short email code, and do not replace the proof's existing hashing contract with this HMAC construction.

## 6. Approved Verification-Code Lifetime

EMAIL-VERIFICATION-005 approves the lifetime and expiry-comparison contract for the current verification code.

### 6.1 Five-Minute Lifetime

- A current verification code remains eligible for confirmation for exactly five minutes from its issue time.
- The issue time is one server time value recorded when the newly generated code becomes the current valid code.
- The expiry time is calculated by adding exactly five minutes to that issue time.
- The current-code issue time is distinct from the verification-session start time.

### 6.2 Confirmation-Time Evaluation

- Confirmation first resolves and write-locks the challenge row for the canonical email.
- After acquiring the lock, read the server current time once and use that single value for the expiry decision.
- The code is eligible for verification only when the current time is strictly earlier than the expiry time.
- When the current time is equal to or later than the expiry time, reject the request as expired and do not increment the failed-attempt count.

### 6.3 Resend Interaction

- When an allowed resend replaces the current code, the previous code becomes invalid immediately.
- Record a new current-code issue time and calculate a new expiry time five minutes later.
- Preserve the original verification-session start time, increment the resend count, and reset the failed-attempt count for the replacement code, as approved by EMAIL-VERIFICATION-003.

## 7. Conceptual Data Flow

The following flow summarizes the approved public and transaction behavior. Section 9 defines the exact API and mail-delivery boundary.

### 7.1 Send

1. Receive the submitted email.
2. Validate and canonicalize it.
3. Check whether the canonical email is already registered.
4. If registered, create no challenge and send no mail.
5. Otherwise generate a code, flush authoritative challenge state in MySQL, deliver the code synchronously to Mailpit inside the transaction, and commit only after delivery returns successfully.

### 7.2 Confirm

1. Receive the email and submitted code.
2. Resolve the active challenge by canonical email.
3. Write-lock the current row and validate expiry, failed-attempt state, and the code verifier.
4. On mismatch, persist the failed-attempt increment transactionally.
5. On success, delete the challenge and store the proof digest in one MySQL transaction.
6. Return the raw proof once after successful database completion; HTTP delivery itself is not part of the database atomicity guarantee.

### 7.3 Signup Handoff

The existing signup endpoint hashes and locks the proof, validates expiry, creates account data, and deletes the proof in its current transaction. This Design must remain compatible with that contract.

## 8. Security and Integrity Constraints

The following constraints are part of the approved final Design:

- Never persist or log a raw verification code or raw proof in Nyamlog application state; Mailpit retention of the email body follows the explicit development-tool boundary in Section 5.3.
- Protect the short verification code with the exact HMAC-SHA-256 and secret-management contract in Section 5; a simple unkeyed hash is prohibited.
- Never expose mail credentials, database details, or internal exceptions.
- Do not place verification-code or proof examples or defaults in Swagger.
- A resend must make the previous code unusable.
- Successful confirmation must prevent code replay.
- Successful signup must prevent proof replay.
- MySQL-specific constraints, locking, and rollback require actual MySQL 8.4.5 evidence.
- A Docker-unavailable skipped test is not successful MySQL verification.

## 9. Approved Consolidated Design

EMAIL-VERIFICATION-007 resolves the consolidated review scope established by EMAIL-VERIFICATION-006.

### 9.1 Email Identity Contract

- Strip only outer whitespace and require a non-empty ASCII email string of at most 254 characters that passes the application's basic email-format validation.
- Internationalized email addresses and Unicode domains are outside this MVP. Do not perform Unicode normalization, IDN conversion, or provider-specific transformations.
- Preserve `+tag`, dots, and all other accepted provider-significant characters.
- Lowercase the entire accepted email with `Locale.ROOT` to produce `canonical_email`.
- Preserve the stripped submitted value as `display_email`; successful API responses return this display value.
- `canonical_email` is the challenge identity, the HMAC email input, and the email stored beside the proof digest. `display_email` is only delivery and response representation.
- `VerificationProofHasher` continues to hash only the high-entropy raw proof with SHA-256. Canonical-email binding is the proof row association and does not change that hash input.

The send endpoint checks whether `canonical_email` is already registered before creating or replacing a challenge. Returning `409 EMAIL_ALREADY_REGISTERED` intentionally exposes account existence as an accepted MVP usability tradeoff. The signup transaction and users-table uniqueness remain the final defense against a registration race.

### 9.2 Operating Policy

| Policy | Approved Rule |
|--------|---------------|
| Code lifetime | Exactly five minutes from `code_issued_at`; equality with expiry is expired. |
| Resend delay | 60 seconds from the current `code_issued_at`; equality with the available time permits resend. |
| Resend maximum | Three resends after the initial send, for four successful sends in one session. |
| Mismatch maximum | Five mismatches against the current code. |
| Terminal state | The fifth mismatch commits count five and returns the attempt-limit result; further confirmation and resend requests remain blocked until code expiry. |
| Resend reset | An allowed resend replaces the verifier, gives the new code a fresh five-minute lifetime, increments resend count, and resets failed attempts to zero. |
| Session reset | A send request at or after expiry starts a new session and resets resend and failed-attempt counts. |
| Proof lifetime | Exactly 15 minutes from successful confirmation. |
| Existing proof | A later successful confirmation atomically replaces any unused proof for the same canonical email. |
| Cleanup | Lazy expiry handling only; no scheduled cleanup job or cleanup index. |

Expired confirmation does not increment failed attempts. Mismatch results are committed business outcomes rather than rollback-triggering failures.

### 9.3 Public API Contract

Both endpoints are unauthenticated and use the existing `ApiResponse<T>` envelope.

| Method | Path | Request | Success |
|--------|------|---------|---------|
| POST | `/api/v1/auth/email-verifications` | `email` | HTTP 200, `EMAIL_VERIFICATION_CODE_SENT`, with `email`, `codeExpiresAt`, and `resendAvailableAt` |
| POST | `/api/v1/auth/email-verifications/confirm` | `email`, `verificationCode` | HTTP 200, `EMAIL_VERIFICATION_CONFIRMED`, with `verificationProof` and `proofExpiresAt` |

- `verificationCode` is exactly six ASCII digits and is request-only sensitive input.
- Timestamps are server-produced ISO-8601 instants.
- The returned `verificationProof` is passed unchanged to the existing signup request's field of the same name.
- Swagger documents purpose, validation, business limits, response fields, and public HTTP/application codes in Korean.
- Swagger provides no example or default for verification code, proof, HMAC, password, token, or secret fields.

| HTTP | Application Code | Meaning |
|------|------------------|---------|
| 400 | `INVALID_INPUT` | Request body, email boundary, or six-digit code representation is invalid. |
| 409 | `EMAIL_ALREADY_REGISTERED` | The send email is already registered; no challenge is changed and no mail is sent. |
| 422 | `EMAIL_VERIFICATION_INVALID` | Challenge is absent or expired, or attempts one through four mismatch. Do not reveal which case occurred. |
| 429 | `EMAIL_VERIFICATION_SEND_LIMITED` | Resend delay, resend maximum, or terminal failed-attempt state blocks sending. |
| 429 | `EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED` | The fifth or a later mismatching confirmation reaches or remains at the attempt limit. |
| 503 | `EMAIL_DELIVERY_UNAVAILABLE` | Synchronous Mailpit delivery did not complete successfully and database changes were rolled back. |
| 500 | `INTERNAL_SERVER_ERROR` | Commit, deadlock, lock, or other unexpected internal failure occurred without exposing internals. |

### 9.4 Physical MySQL Schema

Add `email_verification_challenges` in a new Flyway Migration with this contract:

| Column | SQL Contract |
|--------|--------------|
| `canonical_email` | `VARCHAR(254)` primary key; one current challenge per canonical identity |
| `display_email` | `VARCHAR(254) NOT NULL`; latest stripped delivery representation |
| `code_verifier` | `BINARY(32) NOT NULL`; full HMAC-SHA-256 bytes |
| `verification_started_at` | `DATETIME(6) NOT NULL`; current session start |
| `code_issued_at` | `DATETIME(6) NOT NULL`; current code issue and resend-delay base |
| `expires_at` | `DATETIME(6) NOT NULL`; current code expiry |
| `resend_count` | non-negative integer, `NOT NULL`; zero for initial send and at most three |
| `failed_attempt_count` | non-negative integer, `NOT NULL`; zero through five |

Database checks enforce both counter ranges, `code_issued_at >= verification_started_at`, and `expires_at > code_issued_at`. The table has no users-table foreign key because verification precedes account creation. The primary key is sufficient for MVP access; do not add a cleanup index without an observed cleanup query.

The Entity mirrors this schema, but raw code and raw proof are never fields. Flyway remains schema authority and Hibernate validation must accept the Migration on MySQL 8.4.5.

### 9.5 Mail and Transaction Boundary

- Use Spring Mail and synchronously deliver through local Mailpit after challenge persistence has been flushed but before the service transaction commits.
- A mail exception or timeout rolls back the initial insert or resend update. Exception conversion must preserve `RuntimeException` rollback behavior or declare an equivalent rollback rule.
- SMTP success followed by database commit failure may leave a delivered code with no committed matching state.
- Mailpit may accept a message while its response is delayed or lost; the sender can time out and roll back even though Mailpit retains the message.
- Such stale mailed codes are unusable. The user requests another code; the MVP adds no outbox, delivery-status reconciliation, or distributed transaction.
- The same-email database lock may be held during SMTP for up to the configured timeout. This bounded local-development tradeoff is accepted.

Add Mailpit to Docker Compose as `axllent/mailpit:v1.30.7` and bind only to loopback:

```yaml
ports:
  - "127.0.0.1:1025:1025"
  - "127.0.0.1:8025:8025"
```

Use local SMTP without authentication or TLS and configure connection, read, and write timeouts to five seconds. Do not introduce real SMTP credentials or change the repository's configuration-tracking policy in this feature.

This policy is only for the local Mailpit portfolio MVP. It is not a complete abuse defense for public operation with real SMTP; cross-email/IP-wide rate limiting, delivery cost protection, and production monitoring remain out of scope.

### 9.6 Concurrency Outcomes

- Existing challenge rows are write-locked before resend or confirmation state changes.
- Canonical-email primary-key uniqueness prevents two current rows during concurrent first sends.
- In the normal insert conflict, only the winning transaction reaches mail delivery; the loser returns `EMAIL_VERIFICATION_SEND_LIMITED` without sending.
- A rare InnoDB deadlock or lock failure may roll back a competing request and return `INTERNAL_SERVER_ERROR`. The Design guarantees integrity, not that every losing request is always a 429.
- No automatic retry is performed, preventing implicit duplicate mail delivery.
- Successful confirmation atomically deletes the locked challenge, replaces an existing proof, and inserts the new proof digest. A second use of the same code cannot issue another proof.

### 9.7 Component Responsibilities

- Controller: HTTP mapping, validation, delegation, `ApiResponse`, and detailed Korean OpenAPI contract.
- Service: canonicalization orchestration, policy decisions, server-time capture, transaction boundaries, and public result mapping.
- Repository and Entity: current-row lookup/locking and persistence matching the Flyway schema.
- Code generator and verifier: six-digit `SecureRandom`, exact HMAC contract, startup validation of the configured secret, and constant-time comparison.
- Mail component: minimal non-sensitive message construction and synchronous `JavaMailSender` delivery without logging the code.
- Configuration: Mailpit SMTP properties and required secret binding without a repository default.

Exact class names, repository method names, exception hierarchy, SQL statement text, test method names, and package paths remain reversible implementation choices.

### 9.8 Verification Contract

Representative verification must cover:

- deterministic unit tests for ASCII canonicalization, six-digit generation boundaries, HMAC byte contract, constant-time verification behavior, five-minute expiry, 60-second resend edge, resend/session resets, five-attempt transition, and 15-minute proof expiry
- web and OpenAPI tests for both endpoints, validation, success envelopes, safe error mapping, write-only sensitive request fields, and absence of sensitive examples/defaults
- actual MySQL 8.4.5 tests for Migration validation, counter and time checks, existing-row locks, concurrent first insertion, fifth-mismatch commit, mail-failure rollback, atomic challenge-to-proof transition, proof replacement, and same-code replay prevention
- logging checks that code, proof, secret, password, and internal exception details are not emitted
- manual Swagger and Mailpit flow: send, inspect local mail, confirm, receive proof, and call the existing signup endpoint
- standard Java verification with `.\gradlew.bat test javadoc`; a skipped MySQL test is not successful evidence

### 9.9 Remaining Design Decisions and Gate

There are no remaining Design decisions. Implementation may begin only after separate explicit authorization. A new decision is required only if implementation reveals a concrete conflict affecting schema, public API, security, ownership, transaction behavior, concurrency outcome, external-side-effect behavior, or feature scope.

## 10. Decision Log

| ID | Date | Status | Decision |
|----|------|--------|----------|
| EMAIL-VERIFICATION-001 | 2026-08-21 | Approved | Limit the feature to local Mailpit delivery, code confirmation, simple resend and attempt limits, one-time proof issuance, existing signup handoff, Swagger verification, and representative tests. |
| EMAIL-VERIFICATION-002 | 2026-08-21 | Approved | Store one active challenge state per canonical email in MySQL, separate challenge responsibility from the existing proof table, and do not use application memory or Redis as the authoritative challenge store. This decision deferred exact schema and lifecycle to the later consolidated Design. |
| EMAIL-VERIFICATION-003 | 2026-08-21 | Approved | Keep one current verification session per canonical email, replace its verifier on resend, reset session state after expiry, atomically exchange challenge state for proof state in MySQL, distinguish existing-row locking from concurrent first insertion, and use lazy expiry cleanup. Prohibit raw-code and simple unkeyed-hash persistence; defer the exact keyed verifier, mail boundary, limits, physical schema, and repository strategy. |
| EMAIL-VERIFICATION-004 | 2026-08-21 | Approved | Generate an exact six-ASCII-digit code with `SecureRandom`, preserve its string representation, store a full HMAC-SHA-256 verifier over the versioned canonical-email-and-code byte contract, require a stable Base64-configured secret of at least 32 decoded random bytes with fail-fast startup, compare in constant time, keep Mailpit retention outside application-domain storage, and keep the existing high-entropy proof hashing contract separate. |
| EMAIL-VERIFICATION-005 | 2026-08-21 | Approved | Give each current code an exact five-minute lifetime from one server-recorded issue time, evaluate expiry once after locking the challenge row, accept only when current time is strictly earlier than expiry, do not count expired submissions as mismatches, and give a replacement code a new five-minute window while preserving the verification-session boundary. |
| EMAIL-VERIFICATION-006 | 2026-08-21 | Approved | Preserve EMAIL-VERIFICATION-002 through EMAIL-VERIFICATION-005, replace parameter-by-parameter approval with one consolidated remaining Design draft, keep public API, schema, security, transaction, concurrency, failure outcomes, and representative verification in Design, defer reversible internal implementation details, and require one whole-Design approval before separate implementation authorization. |
| EMAIL-VERIFICATION-007 | 2026-08-21 | Approved | Approve the consolidated final Design with ASCII canonical email identity, MVP limits, public APIs, physical challenge schema, synchronous Mailpit transaction boundary, explicit ambiguous-delivery and concurrency outcomes, loopback-bound Mailpit v1.30.7, and representative verification; keep implementation separately gated. |

## 11. Current Handoff Boundary

EMAIL-VERIFICATION-007 is recorded and the Design phase is complete. No implementation, Migration, dependency, configuration, or test change has started. The next gate is explicit authorization for the approved vertical-slice implementation.

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1.0 | 2026-08-21 | Recorded EMAIL-VERIFICATION-002 MySQL challenge-state storage decision | Project decision record |
| 0.2.0 | 2026-08-21 | Recorded EMAIL-VERIFICATION-003 challenge lifecycle, database atomicity, and concurrency boundary | Project decision record |
| 0.3.0 | 2026-08-21 | Recorded EMAIL-VERIFICATION-004 six-digit code, HMAC verifier, and secret-management decision | Project decision record |
| 0.4.0 | 2026-08-21 | Recorded EMAIL-VERIFICATION-005 five-minute code-lifetime and expiry-comparison decision | Project decision record |
| 0.5.0 | 2026-08-21 | Recorded EMAIL-VERIFICATION-006 consolidated remaining Design review and approval process | Project decision record |
| 1.0.0 | 2026-08-21 | Integrated the approved consolidated Design, five review corrections, final schema, API, operating policy, failure boundaries, and verification contract | Project decision record |
