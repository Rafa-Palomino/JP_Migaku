---
# Copy this file to the target project as: .github/copilot-instructions.md
# Or as a scoped override: .instructions.md (with applyTo) in the repo root or subfolder.
#
# Use applyTo to scope these instructions to specific file types or directories.
# Examples:
#   applyTo: "src/**"                    → only files under src/
#   applyTo: "**/*.py"                   → only Python files
#   applyTo: "**"                        → all files in the repo (default)
applyTo: "**"
---

# Project Guidelines

## Scope
<!-- What this project does and what languages/stacks are in scope. -->
<!-- Example: FastAPI service + PostgreSQL, Python 3.11, Docker. -->

## Architecture
<!-- Key design decisions that agents must respect. -->
<!-- Example: -->
<!-- - Domain-driven layout: interface/, application/, infrastructure/, core/ -->
<!-- - All GPU operations run on a dedicated single-thread executor; never use ThreadPoolExecutor(None). -->
<!-- - Database access is always async via SQLAlchemy async sessions. -->

## Stability Gates
<!-- Code paths that are confirmed stable and must not be modified without a feature branch + user confirmation. -->
<!-- Example: -->
<!-- - Generation pipeline: src/<project>/interface/services/ -->
<!-- - Database connection management: src/<project>/infrastructure/database.py -->

## Known Reverts and Hard Blockers
<!-- Mechanisms or patterns that were tried, failed, and must NOT be reintroduced. -->
<!-- Format: <mechanism> — reverted <date> — reason — do NOT reapply without user confirmation. -->
<!-- Example: -->
<!-- - run_in_executor(None, ...) for CUDA operations — reverted 2026-05-11 — causes memory fragmentation and OOMKill — hard blocker -->

## Validated Patterns
<!-- Mechanisms or patterns confirmed to work correctly in this project. Prefer these over alternatives. -->
<!-- Example: -->
<!-- - Single-thread executor (ThreadPoolExecutor(max_workers=1)) for CUDA — validated 2026-05-16 -->
<!-- - Alembic for schema migrations; no raw DDL in application code -->

## Constraints
<!-- Operational limits agents must treat as fixed unless the user explicitly overrides them. -->
<!-- Example: -->
<!-- - Max concurrent GPU operations: 2 (enforced by asyncio.Semaphore) -->
<!-- - Do not drop or truncate any table without an explicit user instruction. -->

## Validation Requirements
<!-- What validation level is required before marking work as completed. -->
<!-- Example: -->
<!-- - Python syntax check is not sufficient; behavior-level validation (docker run + /health endpoint) required for service changes. -->
<!-- - Database migrations must be tested with alembic upgrade head on a local instance before commit. -->

## File Layout
<!-- Brief map of the key directories so agents orient quickly. -->
<!-- Example: -->
<!-- src/<project>/interface/   — HTTP routes, request/response models -->
<!-- src/<project>/application/ — use cases, orchestrators -->
<!-- src/<project>/infrastructure/ — DB, external APIs, storage -->
<!-- src/<project>/core/        — shared utilities, GPU manager, config -->
<!-- tests/                     — pytest suite -->
<!-- .artifacts/                — generated reports, agent logs, exports -->

## Standardization
<!-- Canonical paths that all agents must use consistently. Do not invent alternate locations. -->

### Documentation paths
<!-- docs/                      — human-readable architecture decisions, workflows, and operational notes -->
<!-- docs/adr/                  — Architecture Decision Records (ADR_*.md) -->
<!-- .artifacts/                — machine-generated output: reports, exports, analysis results -->
<!-- .artifacts/agent-logs/     — operational NDJSON logs (one file per day: YYYY-MM-DD.ndjson) -->

### Naming conventions
<!-- ADRs:       ADR_<TOPIC>.md                 (e.g. ADR_SAFE_SEED_FALLBACK_SYSTEM.md) -->
<!-- Agent logs: YYYY-MM-DD.ndjson              (date-partitioned, append-only) -->
<!-- Branches:   agent/<task-slug>-YYYY-MM-DD   (created before any structural edit) -->

### Placement rules
<!-- - Never write generated output into docs/; use .artifacts/ instead. -->
<!-- - Never write source code into .artifacts/. -->
<!-- - Agent logs go exclusively in .artifacts/agent-logs/; never in docs/ or src/. -->
