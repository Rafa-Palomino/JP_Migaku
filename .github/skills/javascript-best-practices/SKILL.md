---
name: javascript-best-practices
description: "Use when writing, reviewing, refactoring, or debugging JavaScript modules, browser code, Node.js services, frontend state flows, JSON-heavy integrations, or automation glue in laboratory environments."
argument-hint: "Describe the JavaScript task, runtime (browser or Node), framework if any, and validation goal."
user-invocable: true
disable-model-invocation: false
---
# JavaScript Best Practices

## When To Use
- Build or maintain browser-side JavaScript, Node.js scripts, or service modules.
- Review frontend interaction logic, async flows, or API integration code.
- Debug state transitions, event handling, fetch logic, or JSON contract handling.
- Harden local automation or tool glue written in JavaScript.

## Core Rules
- Keep modules small and responsibilities explicit: UI wiring, domain logic, transport, and serialization should not blur together.
- Prefer explicit data shaping at boundaries instead of passing loose nested objects through the codebase.
- Use clear async control flow with `async` and `await`; do not hide sequencing inside deeply chained callbacks when simpler flow is possible.
- Validate JSON inputs, server responses, and user-provided data before using them in business logic.
- Prefer one canonical contract for machine-readable payloads rather than multiple synonymous field names.
- Avoid silent fallback behavior unless it is documented and operationally safe.
- Prefer extending proven existing modules or handlers over introducing parallel code paths that solve the same case again.

## Reliability
- Make fetch, filesystem, and process boundaries observable with explicit error handling and timeout strategy.
- Treat browser storage, in-memory caches, and mutable shared state as failure surfaces, not conveniences.

## Frontend And Service Design
- Separate rendering concerns from state mutation and network orchestration.
- Keep DOM selectors, event listeners, and mutation logic localized so behavior remains traceable.
- For Node.js services, isolate transport handlers from core domain logic and contract mapping.
- Prefer explicit retry and backoff decisions over implicit repeated calls hidden in helpers.

## Testing And Validation
- Validate the changed slice with the narrowest available script, unit test, integration test, or lint or type check.
- Test failure paths for async flows, contract mismatches, and partial responses, not only the happy path.
- Add focused checks when the code maps data into a contract, especially for timestamps, status fields, and validation evidence.
- Distinguish static or structural checks from real runtime validation when network, browser, or service behavior still has not been exercised.

## Documentation
- Document runtime assumptions, browser or Node dependencies, and environment requirements when they affect execution.
- Keep examples short and executable when possible.
- Call out any deliberate fallback, degraded mode, or resilience behavior that affects operations.

## Procedure
1. Identify the exact runtime surface: browser interaction, Node entrypoint, service handler, or shared module.
2. Define the contract at the boundary before changing orchestration.
3. Keep async operations, state mutation, and rendering concerns separated.
4. Add or run the narrowest validation for the touched behavior.
5. Summarize the real behavior change, validation result, and residual risks.