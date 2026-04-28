Always Respon in Chinese.

# AGENTS.md

## Purpose
This Java repository is maintained with human oversight and AI coding agents.
Optimize for correctness, explicitness, backward compatibility, and reviewability.

---

## Core Working Rules

### 1. Plan first for non-trivial work
Before editing files, first provide:
1. Task understanding
2. Relevant modules / packages
3. Assumptions
4. Missing information
5. Proposed implementation plan
6. Risks
7. Validation steps

Do not jump directly into code changes for medium or large tasks.

### 2. Respect module and layer boundaries
Follow existing architecture and package conventions.
Typical boundaries:
- controller / handler layer
- service / application layer
- domain / business logic
- repository / persistence layer
- configuration / integration layer

Do not bypass service boundaries unless the current codebase explicitly does so and the change requires it.

### 3. Minimize blast radius
Prefer small, focused edits.
Do not perform broad refactors unless explicitly requested or required for correctness.

### 4. Preserve compatibility
Assume public APIs, request/response contracts, DTOs, events, and persistence behavior are compatibility-sensitive unless stated otherwise.

### 5. Make every change verifiable
Every meaningful change should include:
- relevant unit tests
- integration tests where needed
- build verification
- lint / style / static analysis where available

---

## Java-Specific Engineering Rules

### Layering
- Controllers should handle transport concerns, not business rules.
- Business logic should live in services or domain components.
- Repositories should focus on persistence, not orchestration.
- Avoid putting domain logic into entity classes unless the codebase already uses rich domain models intentionally.

### Transactions
- Be careful with transaction boundaries.
- Do not casually move transactional logic across layers.
- If changing transaction behavior, call it out explicitly.

### DTOs and Entities
- Do not expose persistence entities directly through API layers unless the project already follows that pattern.
- Preserve mapping conventions already used in the repository.

### Exceptions
- Follow existing exception handling conventions.
- Do not swallow exceptions silently.
- Preserve observable behavior for error codes and error payloads unless the task requires a change.

### Dependency Management
Treat dependency changes as high-risk.
Request approval before:
- adding new dependencies
- upgrading framework versions
- changing BOM / parent versions
- changing plugin configuration

---

## Testing Expectations

Prefer this order:
1. format / style checks if configured
2. compile
3. unit tests
4. integration tests
5. package / build

Add or update tests for:
- service logic
- edge cases
- error handling
- serialization / deserialization behavior
- persistence behavior when relevant

When touching Spring components, consider:
- controller tests
- service tests
- repository tests
- SpringBootTest or sliced tests only when justified

---

## High-Risk Changes

Pause and explain before:
- database schema or migration changes
- transaction boundary changes
- auth / permission changes
- changes to request/response schemas
- message/event contract changes
- dependency upgrades
- configuration or deployment changes

Explain:
- what changes
- why it is needed
- what could break
- how it will be validated

---

## Preferred Workflow

### Phase 1: Understand
Identify:
- main entry points
- affected package(s)
- related interfaces
- downstream effects

### Phase 2: Plan
Provide a concise plan with expected files/modules to change.

### Phase 3: Implement
Apply the smallest correct change.
Preserve coding style and nearby conventions.

### Phase 4: Verify
Prefer repository-standard scripts first.
If not available, use Maven or Gradle project conventions.

### Phase 5: Report
Summarize:
- files changed
- behavior changed
- assumptions
- risks
- validation run
- harness improvements suggested

---

## Validation

Prefer:
```bash
./scripts/verify.sh
```

If unavailable, use the appropriate project command set, for example:

### Maven
```bash
mvn test
mvn verify
```

### Gradle
```bash
./gradlew test
./gradlew build
```


## Documentation Expectations

Update docs when changing:

- API behavior
- configuration
- environment variables
- build or developer workflow
- migration requirements

## Communication Style

For non-trivial tasks, structure responses as:

- Understanding
- Relevant Packages / Modules
- Assumptions
- Plan
- Implementation
- Validation
- Risks
- Harness improvements suggested

