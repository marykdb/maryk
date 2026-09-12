Welcome agent!

Use README.md for project orientation when needed; read the affected module documentation for task-specific context.

The project contains multiple modules with each a README and a docs/documentation folder. 

Do not commit agent-generated plans, specifications, or execution artifacts. Keep them outside the repository; `docs/superpowers/` is ignored.

When implementing fixes, you don't need to run the full test suite but only the one related to the 
module you are working on. If you only did changes in common code it is sufficient to only run the `jvmTest` task
through gradle. You don't need to do a full build as the tests already builds the relevant code.

For broad review-to-fix work:
- Treat a reviewed finding with source evidence and acceptance criteria as the investigation brief. Add a separate investigator only when root cause or intended behavior is still uncertain.
- When delegation is useful and authorized, give each agent only its scope, owned paths, constraints, and acceptance criteria.
- Keep one compact progress ledger when the work needs tracking; avoid a separate artifact for every finding.
- Run focused regression checks and the affected module suite. Run root `jvmTest` when cross-module impact warrants it or the requested gate requires it.
- Reuse verification evidence when relevant inputs have not changed. Repeat checks only after relevant edits, failures, or suspected flakiness.
- Keep Gradle output quiet and inspect test-result XML for counts/failures. Do not feed complete successful test logs into context.
- Before committing public API/config changes, check source and binary compatibility, serialized defaults, and existing call signatures.
- Search for lifecycle assumptions and affected call sites when changing eager/cold behavior, cancellation, listener ownership, or transaction boundaries.
- Run generated-document synchronization before the related implementation commit. Update review reports once at the end, not after every finding.
- Prefer bounded waits over repeated status polling or large agent-tree dumps. Report only changed status or blockers.

For commits:
- Use a concise subject followed by a multiline body for every substantive commit. The body must separately state what changed, why it was needed, and relevant compatibility, safety, or verification considerations. Do not use one-line commit messages for review fixes, behavior changes, CI changes, or documentation that records their disposition.
- Before committing, inspect staged and unstaged changes separately. Keep each commit concern-based and path-limited; preserve unrelated work in a dirty tree.

Write concisely in complete, readable sentences.

When writing code:
- Always use imports and not fully qualified names
- Always try to write common code and not platform specific code where possible. 
