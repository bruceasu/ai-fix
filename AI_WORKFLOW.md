# Standard Workflow
Step 1 — Analysis
Ask AI to analyze code before modification.
Example:
Explain the order cancellation flow.
---
Step 2 — Planning
AI must produce a task plan.
Developer approves the plan.
---
Step 3 — Implementation
AI executes the approved plan.
---
Step 4 — Validation
AI runs tests and lint.
---
Step 5 — Review
AI summarizes changes.
---
# Safety Rules
AI must not:
- modify migrations
- change API contracts
- introduce new dependencies
without approval.
---
# Diff Size Rule
Prefer small diffs.
If change touches >5 files,
a plan is required.
---
# Testing Rule
New features require tests.
Bug fixes should include regression tests.
