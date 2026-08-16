# bkit Project Configuration

## Project Level

Nyamlog is a Dynamic-level application using a custom Spring Boot and MySQL backend.

- When `bkit_detect_level` is available, call it at session start only to obtain PDCA workflow guidance. If it is unavailable, continue from the project context and approved PDCA documents instead of treating the missing tool as a blocker. Its result does not select or replace the project's architecture or technology stack.
- Do not introduce bkend.ai or replace the approved Spring Boot and MySQL backend.
- General bkit templates and skills cannot override Nyamlog's latest approved, non-superseded Plan and Design decisions or project-specific engineering rules.
- Do not introduce microservices, Kubernetes, distributed message-broker architecture, or production-scale enterprise infrastructure without explicit approval.

## PDCA Status

Check `docs/.pdca-status.json` when:

- Starting or resuming work that may change project files
- Discussing or changing feature scope or technical decisions
- Reporting, completing, or transitioning a PDCA phase
- The current feature or phase materially affects the answer

A status check is not required for simple explanations or questions unrelated to project state.

Use `bkit_get_status` when its session is available and the parsed status is useful. If it is unavailable, read `docs/.pdca-status.json` directly.

Do not initialize or mutate PDCA state merely to answer an unrelated question. Do not invent status, progress, or match-rate values.

## Optional bkit Guidance

- Use bkit tools and skills only when they are available in the current session, and follow their instructions when applicable.
- Their absence does not override or suspend Nyamlog's project context, approved documents, or engineering rules.

## Response and Completion Reporting

Respond directly and proportionally to the user's request.

For simple questions, explanations, decision discussions, document reviews, and status checks that do not change project state:

- Answer the request directly.
- Do not append a mandatory PDCA badge, checklist, learning section, tradeoff report, or tool suggestion.
- Include PDCA context only when it materially helps the answer.

When changing project files, implementing approved work, or changing PDCA state, report the applicable items:

- Active feature and phase, when applicable
- Files changed
- Main behavior or decision changed
- API or database impact, when applicable
- Tests executed and results
- Tests not executed, when relevant
- Known limitations or remaining risks
- One concrete next step, when follow-up work remains

Do not add empty or irrelevant report sections merely to satisfy a format.

When reporting active PDCA work:

- Use the status recorded in `docs/.pdca-status.json`.
- Do not invent a progress percentage or match rate when none is recorded.
- Distinguish document approval, phase completion, and implementation completion.
- Do not recommend a phase transition until its documented completion conditions are satisfied.

## Complex Feature PDCA Workflow (Single Agent Mode)

When working on complex features:
1. Break the task into PDCA phases (Plan -> Design -> Do -> Check -> Act when gaps remain -> Report)
2. Apply the relevant product, architecture, security, implementation, QA, review, and documentation perspectives for each phase.
3. When `bkit_pdca_next` is available, use it for phase transitions. Otherwise verify and update the approved PDCA documents and `docs/.pdca-status.json` directly within the user's authorized scope.
4. Enter Act only when Check identifies gaps that require correction. Reanalyze the corrected result before Report.
5. Quality gates: Each applicable phase must be documented before proceeding.

# Nyamlog Project Instructions

## 0. Project Context

- `AGENTS.md` and the PDCA documents under `docs/` are tracked in this repository and may differ by branch or worktree.
- Before project work, confirm that the current worktree can read `AGENTS.md` and `docs/.pdca-status.json` when the task requires project state.
- Raw reference artifacts under `docs/reference/` and `data/reference/` may remain local-only and ignored. Do not publish them without explicit approval.
- `docs/workflow/github-workflow.md` is the repository's detailed GitHub workflow. GitHub templates provide the repository forms but do not replace the decision authority defined here.

## 1. Decision Authority

- `AGENTS.md` defines stable agent behavior, safety boundaries, change workflow, and project-wide engineering defaults. It does not own mutable feature scope, detailed technical decisions, or the current implementation state.
- Read `docs/.pdca-status.json` to identify the active feature and phase before continuing project work.
- Before proposing or implementing a feature decision, read the applicable Plan, Design, ADR, and decision log.
- The latest approved, non-superseded Plan, Design, or ADR entry defines what should be implemented for the concern it owns.
- The current source code, build files, migrations, and configuration define what is implemented now.
- If the approved target and the current implementation disagree, report the conflict. Do not assume that either side silently overrides the other.
- Feature-specific Plans and Designs may refine project-wide defaults, but they may not implicitly weaken baseline safeguards such as secret protection, safety boundaries, authenticated ownership enforcement, or user-data isolation.
- Artifact Audit documents are evidence snapshots. Their recommendations are not approved decisions unless adopted by an approved Plan, Design, or ADR.
- General bkit skills and templates are workflow suggestions and cannot override Nyamlog-specific decisions or engineering rules.
- Do not reopen an approved decision merely because another implementation is possible.
- Reopen a decision only when implementation reveals a concrete contradiction, security or data-integrity problem, unimplementable constraint, or scope disproportionate to the approved portfolio level.
- When an approved decision should change, propose and record an explicit superseding decision. Do not silently ignore, weaken, or implement around the existing decision.

## 2. Stable Project Constraints

- Nyamlog is a solo-maintained learning toy project that may be used as junior-backend portfolio evidence. Deployment, direct use by the developer, and voluntary trial and feedback from a small number of acquaintances are in scope; commercial launch, exhibition operation, and ongoing service delivery to the general public are not.
- Current release scope and feature inclusion are determined by the applicable approved, non-superseded Plan, Design, or ADR, not by this file.
- Prioritize completing one bounded, working vertical flow over expanding adjacent features or speculative infrastructure.
- Prefer the smallest secure implementation that satisfies the approved acceptance scenarios.
- Judge optional work by whether a junior developer can explain it, whether it teaches a backend fundamental, and whether it helps complete the core flow or one small deployment. Defer work that mainly prepares for commercial operation, unrestricted public users, scale, or exhaustive edge-case coverage.
- Toy-project scope limits feature breadth, operational complexity, and speculative scalability; it does not permit insecure password storage, broken user-data isolation, or silent data corruption.
- Local verification plus one simple deployment that the developer and a few invited acquaintances can try are sufficient operating evidence. Do not turn that trial deployment into a commercial production-readiness program unless a later approved learning goal requires it.
- Defer speculative edge cases only when they do not affect security, data integrity, or approved acceptance scenarios.
- Baseline secret protection, authenticated ownership enforcement, and cross-user data isolation must be preserved.
- Unknown nutrition values must not be silently converted to zero.
- Historical meal nutrition must remain independent of later source-food changes.
- Nutrition guidance must not be represented as medical diagnosis or treatment.

## 3. Repository Verification Rules

- Inspect the current source code, build files, migrations, configuration, and tests before describing the implementation state or making changes.
- Do not rely on `AGENTS.md` for exact framework versions, dependency activation, implemented features, or repository completeness.
- Verify version-sensitive behavior against the current dependency baseline.
- For MySQL-specific migrations, persistence behavior, locking, or transaction changes, run the applicable integration tests against the approved actual MySQL baseline. A Docker-unavailable skip is not successful MySQL verification.
- If the repository differs from an approved target, report the conflict before deciding whether implementation or documentation should change.

## 4. Default Engineering Rules

These rules define baseline safety boundaries and project-wide engineering defaults.

Applicable Plans, Designs, and ADRs may refine non-safety defaults, but they must not override secret protection, authenticated ownership enforcement, cross-user data isolation, or protections against silent data corruption.

If an approved requirement conflicts with a baseline safety boundary, stop and report the conflict for explicit redesign. Do not silently weaken the boundary or implement around it.

- Organize code primarily by business domain and keep cross-cutting concerns in an appropriately scoped global package.
- Controllers handle HTTP mapping, validation, delegation, and response construction.
- Keep business rules out of controllers and data access out of controllers and domain objects.
- Do not return JPA entities from public APIs; use request and response DTOs at API boundaries.
- When creating or modifying Java source or test files, add or update Korean JavaDoc in the same change for every declared class, interface, enum, record, constructor, and method.
- JavaDoc must explain the responsibility or behavior, document every parameter with `@param`, every non-`void` return value with `@return`, and applicable exceptions with `@throws`.
- Prefer contract, business-rule, and rationale-oriented JavaDoc over line-by-line restatement, and never include passwords, verification proofs, tokens, secrets, or real credentials in comments or examples.
- When a Controller, request DTO, response DTO, or public enum is included in OpenAPI, add or update Korean Swagger annotations in the same change so Swagger UI explains the operation purpose, field meaning, validation or business constraints, and public HTTP/application response codes.
- Keep Swagger descriptions aligned with the implemented API and approved Design. Use examples only when they are non-sensitive, fictitious, and stable; never define examples or default values for passwords, verification proofs, tokens, hashes, secrets, or real personal information.
- Mark request-only sensitive fields as `writeOnly`, do not expose internal exception or persistence details, and extend the OpenAPI contract test when a documented public contract changes.
- Do not create interfaces, layers, mappers, dependencies, or abstractions without a concrete need.
- Derive the authenticated user from the security context and enforce ownership of user-owned data.
- Encode passwords securely and never store or log plaintext passwords.
- Never commit or print secrets, tokens, API keys, database passwords, OAuth credentials, or mail credentials.
- Never expose stack traces, database errors, internal exception messages, or secret values to clients.
- Review ERD and migration impact before changing entities or relationships.
- Define transaction boundaries in the service layer and check list or detail queries for N+1 behavior.
- Do not rely on destructive automatic schema generation in production.
- Use `.\gradlew.bat test javadoc` as the standard Windows verification command when Java source or tests change.
- Add or update tests for changed business behavior and report passed, failed, errored, skipped, and unexecuted tests. When actual MySQL verification is required, confirm that the applicable MySQL integration tests executed rather than being skipped.
- Do not delete or weaken tests merely to make a build pass.
- Do not modify unrelated files or overwrite user changes in a dirty worktree.
- Do not modify `.bkit-codex` source files for project-specific behavior.
- Do not change the Git tracking policy for configuration, secret, Codex, bkit, or `AGENTS.md` files without explicit approval.

## 5. Approval Required

The authoritative unresolved-decision list is maintained in the applicable active Design's `Remaining Design Decisions` section. A task may have no applicable active Design.

Before asking for approval or making a design choice:

1. Check the active feature and phase.
2. Read the applicable Plan, Design, ADR, and decision log.
3. If an applicable active Design exists, check its `Remaining Design Decisions` section.
4. Confirm that the topic is still unresolved.
5. Do not reopen an approved, non-superseded decision without a concrete reason.
6. Record an approved change as a new superseding decision instead of replacing or ignoring the prior record.

Do not duplicate mutable approved decisions, unresolved-decision lists, or current implementation facts in `AGENTS.md`.

## 6. Change Workflow

- A new feature, material domain-model change, external integration, deployment direction, or cross-cutting architecture change requires an applicable approved Plan and Design before implementation.
- Implementation already covered by an approved, non-superseded Plan and Design does not require a new foundation-level decision.
- Small bug fixes, validation changes, DTO adjustments, tests, documentation, and isolated configuration corrections may use a lightweight workflow.
- A lightweight workflow does not bypass applicable approved decisions, baseline safety boundaries, ownership checks, data-integrity rules, or required verification.
- Do not create a new PDCA document for every endpoint, class, or minor implementation detail.
- Implement approved work in small, independently testable increments.
- If implementation reveals a conflict with an approved decision, stop and report it. Propose an explicit superseding decision when the design should change.
- Verify Figma, API drafts, ERD drafts, and external data against approved decisions. Draft artifacts are not final implementation contracts.

### GitHub Workflow

- Before GitHub work, read `docs/workflow/github-workflow.md` and the repository's applicable Issue and Pull Request templates.
- Creating an Issue or branch, staging files, committing, pushing, creating or merging a Pull Request, deleting a remote branch, or committing directly to a base branch requires explicit user approval for the exact scope.
- Preserve unrelated user changes. In a dirty worktree, stage only explicitly approved paths and never default to staging the entire worktree.
- Do not present an invented branch naming or commit message convention as an official repository rule.
- Create the Issue before its work branch. Create the Pull Request after the remote work branch exists, and connect the related Issue with `Close #<issue-number>` when appropriate.
- Verify the current remote default or requested base branch before creating a work branch. Fetch and prune stale remote refs before starting the next Pull Request when the remote state may have changed.
- A direct base-branch commit is an exception limited to a precisely approved, low-risk housekeeping change. Feature code and material behavior changes use the Issue and Pull Request workflow.
- Keep detailed commands, templates, merge guidance, and workflow checklists in `docs/workflow/github-workflow.md`; do not duplicate mutable workflow details here.
