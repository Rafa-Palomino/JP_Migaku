---
name: python-best-practices
description: "Use when writing, reviewing, refactoring, or debugging Python modules, packages, CLI tools, JSON-heavy workflows, local-model integrations, AI automation pipelines, data processing, or service code used in laboratory environments."
argument-hint: "Describe the Python task, module type, runtime, and validation goal."
user-invocable: true
disable-model-invocation: false
---
# Python Best Practices

## When To Use
- Build Python automation scripts, packages, or service components.
- Design data-processing or JSON transformation workflows.
- Integrate local models, adapters, or inference orchestration.
- Debug failures, improve testability, or harden configuration handling.

## Core Rules
- Prefer small modules with explicit responsibilities and clear import boundaries.
- Use type hints on public functions, methods, and data structures.
- Keep orchestration separate from domain logic, adapters, and serialization.
- Prefer explicit data models, validated dictionaries, or schemas over loose nested structures.
- Keep configuration loading centralized and avoid scattered environment reads.
- Raise specific exceptions or wrap them with context that preserves the cause.
- When emitting machine-readable logs, analytics payloads, or contracts, prefer one canonical schema over per-script synonyms for the same meaning.
- Prefer activating or extending existing proven components before introducing parallel implementations that duplicate the same behavior.

## Reliability
- Validate JSON, configuration, and model-related inputs as early as possible.
- Make external dependencies observable: timeouts, retries, partial failures, and fallback decisions should be logged.
- Avoid hidden global state for models, clients, or mutable caches unless clearly controlled.
- Design automation paths to be resumable or safely repeatable when possible.

## Project Structure
- Keep package layout predictable: entrypoints, domain logic, adapters, config, and tests.
- Use thin CLI or task runners that delegate to reusable functions.
- Separate synchronous orchestration from async or subprocess-heavy integrations if both exist.
- Prefer standard library features first unless a dependency clearly reduces complexity.

## Testing And Validation
- Validate edited code with the narrowest available test, script, or type-check command.
- Use pytest for behavior-focused tests where the repository supports it.
- Cover JSON parsing, edge conditions, and failure paths, not only happy paths.
- For automation code, test logic independently from network, filesystem, or model runtimes whenever possible.
- Add focused tests for contract mapping and timestamp handling when logs, reports, or append-only event streams are part of the behavior.
- Distinguish structural validation from real behavior validation when external models, services, or environment-dependent flows remain unexercised.

## Documentation
- Document module purpose, input contracts, side effects, and expected environment requirements.
- Capture assumptions around model availability, local paths, and resource usage.
- Keep examples short and runnable where practical.

## Procedure
1. Identify the owning module and the exact behavior to build or change.
2. Define types, contracts, and failure conditions before adding orchestration.
3. Isolate I/O, subprocess, or model calls behind narrow interfaces.
4. Add or update focused validation for the touched slice.
5. Summarize what changed, how it was checked, and any residual risks.
