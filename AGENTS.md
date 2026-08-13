Welcome agent!

Read the README.md file for a general introduction.

The project contains multiple modules with each a README and a docs/documentation folder. 

Do not commit agent-generated plans, specifications, or execution artifacts. Keep them outside the repository; `docs/superpowers/` is ignored.

When implementing fixes, you don't need to run the full test suite but only the one related to the 
module you are working on. If you only did changes in common code it is sufficient to only run the `jvmTest` task
through gradle. You don't need to do a full build as the tests already builds the relevant code.

For broad review-to-fix work:
- Treat a reviewed finding with source evidence and acceptance criteria as the investigation brief. Add a separate investigator only when root cause or intended behavior is still uncertain.
- Use at most one implementer and one independent reviewer per finding by default. Give subagents only the finding, owned paths, constraints, and acceptance tests; do not fork the full conversation unless required.
- Keep one compact progress ledger. Do not create a plan, brief, report, and review artifact for every finding.
- Verification ladder: implementer runs focused RED/GREEN; reviewer reviews the diff and evidence without repeating the full suite; controller runs the affected module suite once before commit; run root `jvmTest` once after all fixes.
- Do not run the same cache-bypassed module suite in implementer, reviewer, and controller unless a failure or flaky result requires reproduction.
- Keep Gradle output quiet and inspect test-result XML for counts/failures. Do not feed complete successful test logs into context.
- Before committing public API/config changes, check source and binary compatibility, serialized defaults, and existing call signatures.
- Search for lifecycle assumptions and affected call sites when changing eager/cold behavior, cancellation, listener ownership, or transaction boundaries.
- Run generated-document synchronization before the related implementation commit. Update review reports once at the end, not after every finding.
- Prefer bounded waits over repeated status polling or large agent-tree dumps. Report only changed status or blockers.

For commits:
- Use a concise subject followed by a multiline body for every substantive commit. The body must separately state what changed, why it was needed, and relevant compatibility, safety, or verification considerations. Do not use one-line commit messages for review fixes, behavior changes, CI changes, or documentation that records their disposition.
- Before committing, inspect staged and unstaged changes separately. Keep each commit concern-based and path-limited; preserve unrelated work in a dirty tree.

Style: telegraph. Drop filler/grammar. Min tokens (global AGENTS + replies).

When writing code:
- Always use imports and not fully qualified names
- Always try to write common code and not platform specific code where possible. 
