# Command, Tool, and Skill Boundaries

This repository uses three layers on purpose:

## Commands

Commands are the stable product entry points.

Use commands when the operation:

- has a clear input/output contract
- is useful in shell scripts, pipes, or CI
- needs explicit options and safety boundaries
- is part of the main product surface

Current core commands:

- `index`
- `analyze`
- `understand`
- `fix`
- `tool`
- `skill`
- `chat`
- `init`

Examples:

- `index` writes `index.json`
- `analyze` reads stdin or text and returns structured diagnosis
- `understand` writes `project-summary.json` and a Markdown report
- `fix` applies safety checks, Git rules, dry-run, and suggestion-only behavior

## Tools

Tools are the smallest executable capabilities.

Use tools when the operation:

- reads or fetches one thing
- has a narrow and reusable responsibility
- is easy to combine inside a skill or chat workflow

Examples:

- `read-file`
- `list-files`
- `git-diff-reader`
- `project-summary-reader`
- `index-symbol-reader`
- `web-search`
- `web-fetch`

## Skills

Skills are reusable workflows built on top of AI steps and tools.

Use skills when the operation:

- combines multiple steps
- needs AI reasoning plus one or more tools
- should be callable from `chat`
- is specific to a developer workflow rather than a stable top-level contract

Examples:

- `analyze-java-error`
- `analyze-test-failure`
- `search-doc-and-explain`
- `project-summary-and-explain`
- `find-entry-points`
- `explain-project-structure`
- `review-changed-files`
- `explain-file`
- `explain-symbol`

## Recommended Rule

Do not replace core commands with skills.

Recommended model:

- commands are the stable user-facing interface
- tools are the atomic capabilities
- skills are the orchestration layer

Inside `chat`, prefer built-in skills for common developer workflows.
Outside `chat`, prefer core commands for deterministic tasks and automation.
