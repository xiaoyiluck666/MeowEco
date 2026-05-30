# Agent Instructions for MeowEco

## Serena Requirement

- If the task involves reading, analyzing, editing, generating, refactoring, reviewing, or searching code, you must use the Serena MCP tools for the code-related work.
- Treat any source file, build script, config tied to runtime behavior, or test file as code context. This includes files such as `*.java`, `*.kt`, `*.yml`, `*.yaml`, `*.gradle`, `*.kts`, `*.properties`, `*.xml`, `*.json`, and files under `src/`.
- Do not fall back to non-Serena code navigation or editing for code tasks unless Serena is unavailable or missing the required capability.
- If Serena is unavailable, say so explicitly, then use the best available fallback.

## Scope

- These instructions apply whenever code is detected in the request or workspace context.

## Changelog Requirement

- Every completed change must append an entry to `CHANGELOG.md`.
- Keep entries date-based (or release-based), and include at least `Added`/`Changed`/`Fixed` sections that reflect actual modifications.
- If a task does not change code or behavior, note it briefly under `Changed` as maintenance/documentation work.
