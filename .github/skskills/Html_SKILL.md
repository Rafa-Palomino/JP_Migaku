---
name: html-frontend-best-practices
description: "Use when creating, reviewing, or refactoring HTML, CSS, and lightweight frontend UI structure for forms, dashboards, local tools, agent interfaces, or browser-based laboratory workflows."
argument-hint: "Describe the page or UI surface, styling constraints, interaction model, and validation goal."
user-invocable: true
disable-model-invocation: false
---
# HTML And Frontend Best Practices

## When To Use
- Build or refine HTML and CSS for browser-based tools, forms, dashboards, or operational interfaces.
- Review structure, accessibility, information hierarchy, and responsive behavior.
- Improve lightweight frontend shells that coordinate with JavaScript or API-backed workflows.
- Debug layout, semantics, progressive enhancement, or UI states.

## Core Rules
- Start with semantic HTML and accessible structure before styling details.
- Make information hierarchy explicit with headings, sections, labels, and status regions.
- Prefer resilient layouts that still communicate clearly when scripts fail or data is delayed.
- Keep CSS intentional: define a visual system instead of stacking ad hoc overrides.
- Avoid generic placeholder UI text when the real task state, validation result, or next action is known.
- If the interface reflects machine-readable workflow state, keep labels and visible status aligned with the underlying contract.
- Prefer extending proven existing UI patterns or components over introducing parallel markup structures that solve the same interaction again.

## Accessibility And UX
- Use labels, button text, landmarks, and status messaging that are meaningful without surrounding context.
- Preserve keyboard accessibility and visible focus behavior.
- Ensure validation errors, success states, and blocked states are distinguishable and actionable.
- Keep user language consistent across controls, messages, and handoff text.

## Reliability
- Design empty, loading, success, and failure states explicitly; do not let the UI collapse into silence on partial data.
- If the page shows operational or agent results, surface both what failed and what worked well when that affects user trust or next steps.
- Prefer interfaces that can tolerate sparse backend data without rendering misleading placeholders.

## Responsive And Styling Guidance
- Design for desktop and mobile from the same information model.
- Use spacing, typography, and contrast intentionally to guide attention.
- Prefer reusable classes or tokens over duplicated one-off style fragments.
- Keep decorative complexity below the importance of clarity, validation, and task flow.

## Testing And Validation
- Validate the changed UI with the narrowest available browser check, component test, screenshot check, or markup inspection.
- Check semantic structure, keyboard flow, and responsive behavior after meaningful layout changes.
- Verify that visible status text matches the actual operational state for success, warning, and failure cases.
- Distinguish static or structural validation from real interaction validation when the rendered flow, user actions, or runtime data still have not been exercised.

## Documentation
- Document the intended user flow, important states, and any accessibility or responsiveness assumptions.
- Record notable constraints such as browser support, offline behavior, or required script dependencies.

## Procedure
1. Define the user task and the states the UI must communicate.
2. Build semantic structure and status messaging before visual polish.
3. Layer styling and responsiveness on top of a clear information hierarchy.
4. Validate the rendered states that matter most to the workflow.
5. Summarize what the UI now communicates better and any remaining gaps.
