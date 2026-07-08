# Project Guidelines

## Scope
This repository template is intended for projects focused on AI automation, local-model laboratory work, PowerShell, Python, and JSON-based configuration or contracts.

## Delivery Rules
- Prefer small, reversible changes over broad rewrites.
- Fix root causes when feasible; avoid cosmetic patches that leave the failure mode intact.
- Prefer extending proven existing components or flows over introducing parallel rewrites when the existing path already solves most of the problem.
- Keep automation deterministic: define inputs, outputs, side effects, and rollback expectations.
- Preserve existing user changes unless the task explicitly requires modifying them.
- Understand the project context, goals, constraints, and expected behavior before making decisions that depend on them.
- If the project or request is not understood well enough to proceed safely, ask the user clarifying questions before implementing or recommending changes.
- Document assumptions, prerequisites, and operating constraints when they affect execution.
- Before modifying a code path that was previously reverted or documented as problematic (in git history, ADRs, or operation logs), locate and review that prior decision. A documented revert is a hard blocker, not a negotiable opinion; escalate to the user instead of reinterpreting it.

## Architecture
- Separate orchestration code from domain logic, adapters, and configuration.
- Keep model-specific code behind explicit interfaces or adapters so providers can be swapped.
- Treat prompts, schemas, and automation policies as versioned artifacts when they influence behavior.
- Prefer JSON for machine-readable configuration, contracts, and intermediate results.
- Compare plausible solution options against explicit operational criteria such as reliability, complexity, runtime cost, validation path, and rollback shape before locking an architectural direction.
- Keep long-running or destructive operations behind explicit confirmation or dry-run paths.

## Configuration And Data
- Validate JSON inputs early and fail with actionable error messages.
- Avoid hard-coded secrets, endpoints, model paths, or environment-specific absolute paths.
- Prefer explicit configuration objects over scattered environment-variable reads.
- Make defaults safe for local laboratories and non-production environments.

## Reliability
- Add focused validation after meaningful edits: the narrowest test, lint, type-check, or execution path that can falsify the change.
- Distinguish syntax or startup validation from real behavior validation; when a change is not exercised through the real path, state that limit explicitly instead of overstating confidence.
- When validation coverage is only syntactic or structural (parse, compile, import, startup), state that explicitly and list which runtime behaviors were not covered. Do not mark work as completed when behavior-level validation has not been performed.
- Use structured logging for automation paths; log decisions, failures, retries, and external calls.
- Make scripts idempotent when practical, especially for filesystem, provisioning, or model-management tasks.
- Handle external dependency failures explicitly: timeouts, unavailable models, malformed data, and partial writes.

## Agent Observability
- When an agent performs meaningful work, emit one structured operation log entry to `.artifacts/agent-logs/<YYYY-MM-DD>.ndjson` unless the user explicitly asks not to persist logs.
- Treat the log entry as an operational artifact, not as narrative documentation. Keep it concise, machine-readable, and append-only.
- Use JSON Lines format: one JSON object per line, UTF-8 encoded, with stable field names that can be aggregated later.
- Treat `.github/contracts/agent-operation-log.schema.json` as the canonical field contract; prefer mapping richer context into existing fields over inventing alternate top-level names.
- For meaningful work, populate the core evidence fields when known: `run_id`, `agent`, `task_summary`, `status`, `actions`, `files_touched`, `validation`, and `risks`.
- Log enough evidence to reconstruct what happened: request summary, selected agent role, key actions, tools used, files touched, validation steps, outcome, and residual risks.
- Emit meaningful intermediate checkpoints when they add diagnostic value: user-prompt intake, tool start/finish, subagent start/finish, and compaction boundaries.
- Enrich lifecycle events before writing them: prefer the resolved agent role, a concrete task summary, the dominant request class, the primary tool or operation, and the most relevant files or artifacts instead of generic placeholders such as `unknown` or `Stop event`.
- Do not preserve a generic stop-event closure when the task outcome, validation, or changed surface is already known; emit the richer completion state instead.
- Include optimization-oriented telemetry when available: model/provider/deployment, token usage, requested max tokens, duration, tool counts, progress percentage, retry counts, and estimated cost.
- Keep the agent's working language and terminology aligned with the user's chosen language unless the user explicitly asks to switch; avoid mixed-language summaries or handoff notes.
- When the user proposes a solution or architectural direction that is better than the agent's earlier proposal, adopt it and log that explicitly with the repository contract: record that the adopted solution came from the user, summarize both proposals, and explain why the user's proposal won.
- When the user, validation, or downstream results confirm that something worked well, log one concise `positive_feedback` block with the signal source and the specific behavior worth reinforcing.
- When some context is inferred rather than explicitly provided by the runtime payload, keep the best available value and record the uncertainty in `risks` instead of dropping to empty or low-value fields.
- Partition daily NDJSON files using the same timestamp written into the entry; do not derive the filename from a different clock source.
- If downstream or manual producers emit incompatible log shapes, treat that as normalization work to be fixed at the producer; do not copy the incompatible shape into new entries.
- Exclude observability artifacts from operational evidence unless the task genuinely modified the logging system itself; `files_touched` should favor business or source files over `.artifacts/agent-logs/*`.
- Never log secrets, tokens, credentials, private datasets, full prompt dumps containing sensitive material, or large file contents. Summarize sensitive context instead.
- Prefer one rich final summary plus a small number of high-signal intermediate checkpoints over many low-information lifecycle duplicates.
- If no files were edited and no commands or tools were run, log only if the task still produced a decision, recommendation, or review outcome that should be auditable.
- Prefer the repository contract in `.github/contracts/agent-operation-log.schema.json` when creating or validating entries.

## Branching and Stability
- When the user confirms the system is stable, treat that as a stability gate: structural changes to core code paths require a feature branch before any files are modified.
- Use the branch pattern `agent/<task-slug>-YYYY-MM-DD`. Create the branch immediately, before the first edit.
- Commit work in progress to that branch. Do not push directly to the default branch for structural changes without explicit user confirmation.
- If a suitable branch already exists for the task, continue on it rather than creating a parallel one.
- After completing and validating work on the branch, surface it to the user for review before merging.

## Documentation
- Document architecture decisions, workflows, and operational hazards close to the code they affect.
- Summaries should explain why the change exists, what it touches, and how it was verified.
- Keep generated artifacts, caches, and machine output separate from source and documentation.

## Security And Safety
- Never commit credentials, tokens, private datasets, or local model secrets.
- Avoid destructive commands by default; require clear intent for deletion, overwrite, or reset flows.
- Minimize privilege assumptions and prefer least-privilege execution.
- Treat local-model laboratory outputs as untrusted until validated.

## Language-Specific Guidance
Detailed PowerShell and Python guidance lives in the skills under `.github/skills/`. Keep this file limited to rules that apply across the whole project.
