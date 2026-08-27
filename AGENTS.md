# Nyamlog Project Instructions

## 1. Project Context and Decision Authority

Nyamlog is a Dynamic-level, solo-maintained learning and junior-backend portfolio project built with Spring Boot and MySQL.

- Direct developer use, one small deployment, and limited voluntary trials are in scope. Commercial operation, unrestricted public service, and speculative production-scale infrastructure are not.
- Do not introduce bkend.ai, microservices, Kubernetes, distributed message brokers, or enterprise infrastructure without explicit approval.

- `AGENTS.md` defines stable workflow and safety rules. `docs/.pdca-status.json` identifies the active feature and phase. The latest approved, non-superseded Plan, Design, or ADR defines the target; source code, migrations, configuration, and tests define the current implementation.
- Read authority files from the current branch or worktree. If the approved target and implementation differ, report the conflict instead of silently choosing one.
- Feature documents may refine non-safety defaults but cannot weaken secret protection, authenticated ownership, user-data isolation, or protection against silent data corruption.
- Artifact Audits and general templates are references, not approved decisions. Reopen a decision only for a concrete contradiction, security or data-integrity problem, unimplementable constraint, or scope disproportionate to this project, and record the change as an explicit superseding decision.
- Raw artifacts under `docs/reference/` and `data/reference/` may be local-only or ignored. Do not publish them without explicit approval.

## 2. Scope and Safety Boundaries

- Prefer the smallest secure implementation that completes one bounded vertical flow and can be explained by a junior developer.
- Defer work aimed mainly at commercial operations, unrestricted public users, scale, or exhaustive edge cases. Do not defer issues affecting security, data integrity, or approved acceptance scenarios.
- Never commit, print, log, or expose passwords, tokens, API keys, database credentials, mail credentials, hashes, verification values, or other secrets. Do not expose stack traces, database errors, or internal exception details to clients.
- Store passwords with an approved secure encoder; never store or log plaintext passwords.
- Derive the authenticated user from the SecurityContext and enforce ownership and cross-user isolation for user-owned data.
- Do not silently corrupt data. Unknown nutrition values must not become zero, historical meal nutrition must remain independent of later source-food changes, and nutrition guidance must not be represented as diagnosis or treatment.

## 3. Engineering and Verification

- Inspect the current source, build files, migrations, configuration, and tests before describing implementation state or making changes. Verify version-sensitive behavior against the current dependency baseline.
- Organize code primarily by business domain. Controllers handle HTTP mapping, validation, delegation, and response construction; keep business rules and data access out of controllers and domain objects. Do not return JPA entities from public APIs.
- Do not add interfaces, layers, mappers, dependencies, or abstractions without a concrete need.
- Define transaction boundaries in the service layer, check list and detail queries for N+1 behavior, and review ERD and migration impact before entity or relationship changes. Do not rely on destructive automatic schema generation in production.

### JavaDoc

- When Java source or tests change, write or update Korean JavaDoc where it carries contract or rationale value: public types, major service responsibilities, public API contracts, security or transaction behavior, and non-obvious rules.
- JavaDoc is not required for self-explanatory constructors, accessors, record components, simple conversion methods, private helpers, or test helpers.
- Add `@param`, `@return`, and `@throws` only when names and types do not sufficiently explain the contract. Never place sensitive values in documentation or examples.

### Swagger and OpenAPI

- For public API changes, document the operation purpose, required input constraints, major success and failure outcomes, and sensitive-field boundary.
- Do not repeat the same policy sentence across Controller, DTO, and test. Keep examples fictitious and non-sensitive; do not define examples or defaults for passwords, proofs, tokens, hashes, secrets, or real personal data.
- Mark request-only sensitive fields as `writeOnly` and do not expose internal exception or persistence details.
- OpenAPI contract tests verify paths, fields, status codes, security schemes, and non-exposure of sensitive values rather than exact description wording.

### Verification

- Use `.\gradlew.bat test javadoc` as the standard Windows verification command when Java source or tests change.
- Add or update tests for changed behavior and report passed, failed, errored, skipped, and unexecuted tests. Never delete or weaken tests merely to make a build pass.
- For MySQL-specific migrations, persistence, locking, or transaction behavior, run the applicable integration tests against the approved actual MySQL baseline. A Docker-unavailable skip is not successful MySQL verification.
- Do not modify unrelated files, overwrite user changes, change `.bkit-codex` source files, or alter tracking policy for configuration, secrets, Codex, bkit, or `AGENTS.md` without explicit approval.

## 4. Lean PDCA and Review

- Check `docs/.pdca-status.json` before changing project files, discussing feature scope or material technical decisions, transitioning a phase, or reporting PDCA completion. Do not mutate it for unrelated questions, draft comments, wording-only review, or reversible implementation details.
- bkit may assist PDCA progress when available. Its absence is not a blocker; read repository documents directly. bkit output and templates cannot override approved Nyamlog decisions or these project rules.
- Manage one bounded vertical feature with one concise Plan and one consolidated Design. Do not create a document or approval gate for every endpoint, class, policy value, or test case.
- Plan records purpose, scope, acceptance scenarios, and material risks. Design records decisions expensive or unsafe to reverse, including public API and validation, database constraints, security and ownership, transaction and concurrency outcomes, external-failure outcomes, and representative verification.
- Document only decisions required by the current implementation. Exclude unrequested scale, distributed-operation, and future-expansion design.
- Do not fix class names, Bean configuration, library APIs, internal methods, SQL text, package paths, or test names in Design unless they change an approved contract. Avoid repeating one decision across sections.
- Aim for roughly 150 lines in Design; this is a readability target, not a hard limit.
- Analysis records actual gaps and verification evidence. Report is a concise completion record and does not repeat Plan or Design.
- Whole-Design approval and implementation authorization are separate unless the user explicitly approves both scopes together. After implementation authorization, reversible internal choices inside the approved Design do not require renewed approval.
- Enter Act only for gaps found during Check. Update PDCA status for phase transitions and material approved decisions, and never invent progress or match-rate values.
- Stop for explicit redesign when implementation reveals an approved-design conflict, a security or data-integrity problem, an unimplementable requirement, or material scope expansion.

Review severity has the following single meaning throughout the repository:

- **P1:** Data corruption or loss, authentication or authorization bypass, cross-user data exposure or isolation failure, secret exposure, or another critical security or integrity failure.
- **P2:** A problem that breaks the core flow, public API or schema, transaction or external-failure outcome, or the ability to verify completion.
- **P3:** A non-blocking improvement to naming, wording, internal structure, optional tests, or optimization.
- Only P1 and P2 block approval. P3 may be suggested or tracked in an implementation note or follow-up Issue, but must not be an approval condition.

## 5. GitHub Workflow and Approval

- Before GitHub work, read `docs/workflow/github-workflow.md` and the repository's applicable Issue and Pull Request templates.
- Creating or editing an Issue, creating a branch or worktree, staging, committing, pushing, creating or merging a Pull Request, deleting a remote branch, or committing directly to a base branch requires explicit user approval for the exact scope.
- Related Git actions may be approved together when the user explicitly approves the complete scope, such as branch and worktree creation, or approved-path staging, commit, push, and Pull Request creation.
- Direct base-branch commits and Pull Request merges always require separate approval.
- Preserve unrelated user changes. In a dirty worktree, stage only explicitly approved paths and never default to staging the entire worktree.
- Do not present an invented branch naming or commit message convention as an official repository rule.
- Create the Issue before its work branch. Create the Pull Request after the remote work branch exists, and connect the related Issue with `Close #<issue-number>` when appropriate.
- Verify the current remote default or requested base branch before creating a work branch. Fetch and prune stale remote refs before starting the next Pull Request when the remote state may have changed.
- A direct base-branch commit is an exception limited to a precisely approved, low-risk housekeeping change. Feature code and material behavior changes use the Issue and Pull Request workflow.
- Keep detailed commands, templates, merge guidance, and workflow checklists in `docs/workflow/github-workflow.md`; do not duplicate mutable workflow details here.

## 6. Response and Completion Reporting

- Respond proportionally. Simple explanations and reviews do not need a PDCA badge, checklist, learning section, or tool suggestion.
- After changing project files or PDCA state, report the applicable feature and phase, changed files, behavior and API or database impact, tests and results, known risks, and one concrete next step.
- Use recorded PDCA status, distinguish document approval from phase or implementation completion, and do not recommend a phase transition until its completion conditions are satisfied.
