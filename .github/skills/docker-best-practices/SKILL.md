---
name: docker-best-practices
description: "Use when writing, reviewing, refactoring, or debugging Dockerfiles, container images, docker-compose stacks, local container workflows, or containerized automation used in laboratory environments."
argument-hint: "Describe the Docker task, image or service type, runtime constraints, and validation goal."
user-invocable: true
disable-model-invocation: false
---
# Docker Best Practices

## When To Use
- Build or maintain Dockerfiles for applications, services, or automation tasks.
- Design local container workflows with Docker Compose or similar orchestration.
- Harden container images used for AI automation, scripts, or laboratory utilities.
- Debug build failures, runtime mismatches, networking, volumes, or container startup behavior.

## Core Rules
- Prefer small, purpose-built images with explicit base image tags.
- Use multi-stage builds when build-time tooling should not ship in the runtime image.
- Keep image responsibilities narrow: one service, one main process, one clear contract.
- Pin package versions when reproducibility matters, especially for CLI tools, Python dependencies, or OS packages.
- Avoid baking secrets, tokens, private keys, or machine-specific paths into images.
- Use `.dockerignore` to keep build contexts small and avoid leaking local artifacts.
- Prefer exec-form `ENTRYPOINT` and `CMD` instructions for predictable signal handling.

## Guardrails
- Treat Docker volumes as persisted user or service data by default.
- Do not delete volumes, recommend `docker compose down -v`, or suggest destructive volume cleanup unless the user explicitly asks to remove persisted data.

## Security And Reliability
- Run as a non-root user unless the workload has a clear operational reason not to.
- Minimize installed packages and remove build caches when they are not needed at runtime.
- Validate file ownership, permissions, ports, and writable directories explicitly.
- Keep environment variables centralized and document which are required versus optional.
- Treat mounted volumes, bind paths, and host networking as explicit operational dependencies.
- Make container startup deterministic: define health checks, required files, and startup commands clearly.

## Build And Runtime Design
- Order Dockerfile layers to maximize cache reuse for stable dependencies.
- Copy dependency manifests before application source when the ecosystem supports it.
- Prefer deterministic package install commands over ad hoc shell setup.
- Keep shell logic in scripts or entrypoints only when it improves clarity or reuse.
- Separate development conveniences from production/runtime behavior with explicit targets or compose overrides.

## Compose And Local Workflows
- Name services, networks, and volumes clearly so local troubleshooting stays simple.
- Expose only the ports that operators or dependent services actually need.
- Use health checks and dependency conditions carefully; prefer services that tolerate delayed readiness.
- Keep compose files declarative and move large setup logic into scripts or images.
- Document required local files, environment files, and persistent data paths.

## Testing And Validation
- Validate edited Dockerfiles with the narrowest relevant build command.
- Run at least one container startup path after meaningful runtime changes.
- Check image size, user identity, mounted paths, and exposed ports when those aspects matter.
- Prefer focused smoke tests over broad stack bring-up when only one service changed.

## Documentation
- Document image purpose, build context, entrypoint behavior, ports, and required environment variables.
- Record assumptions about host prerequisites, GPU access, filesystem mounts, or external services.
- Keep example build and run commands short, reproducible, and aligned with the checked-in configuration.

## Procedure
1. Identify whether the change belongs in the image, entrypoint, compose file, or runtime configuration.
2. Define the container contract: inputs, ports, volumes, user, and startup behavior.
3. Reduce image scope, dependencies, and privileges before adding convenience logic.
4. Validate with a focused build or startup command for the touched service.
5. Summarize operational assumptions, required environment, and residual risks.