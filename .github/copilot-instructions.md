# .github/copilot-instructions.md

Read `AGENTS.md` first.

## Spring Boot repository expectations

- plan before coding for non-trivial tasks
- keep controllers thin
- preserve service boundaries
- treat DTO and API changes as compatibility-sensitive
- call out transaction or persistence behavior changes explicitly
- keep PRs small and reviewable

## Validation
Use:
```bash
./scripts/verify.sh
```

when available.

Otherwise use repository-standard Maven or Gradle commands.

## Final summary should include
- summary
- affected modules
- validation run
- risks
- follow-ups
- harness improvements suggested