# ai-fix

AI-assisted analysis and repair tool for Java, Go, and Python projects, packaged as a single runnable JAR.

For the Chinese document, see `README.md`.

Architecture note:

- core product boundaries are documented in [COMMAND_TOOL_SKILL_BOUNDARIES.md](D:/03_projects/suk/ai-fix/COMMAND_TOOL_SKILL_BOUNDARIES.md)
- keep `index`, `analyze`, `understand`, and `fix` as stable commands
- use tools as atomic capabilities and skills as orchestration wrappers

## What it does

- `index`: scan source code and generate `index.json`
- `analyze`: read logs, exceptions, or test output and return structured analysis
- `understand`: generate project summary data and a project understanding report
- `fix`: select target symbols from the index and generate patches or repair advice
- `tool`: list and run bundled or external tools
- `skill`: list and run bundled or external skills
- `knowledge`: browse recorded problem categories and repair experience
- `generate-exe`: export a fixed workflow package from any skill
- `chat`: start an interactive AI session with tool/skill execution
- `init`: interactively initialize config and prompt templates

## Current language support

- Java: `index`, `understand`, `fix`
- Go: `index`, `understand`, and `fix` suggestion/dry-run flows
- Python: `index`, `understand`, and `fix` suggestion/dry-run flows

Notes:

- Java is still the most complete path
- Go and Python already support indexing, understanding, symbol matching, preview, and suggestion mode
- For Go and Python, prefer `--suggest-only` or `--dry-run` for now

## Build

Recommended environment setup:

```powershell
.\setup-jdk25.ps1
```

or:

```bat
setup-jdk25.bat
```

Recommended build command:

```bash
.\mvnw.cmd -s .mvn-local-settings.xml clean package
```

Run the packaged JAR:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar
```

## CLI commands

- `index`
- `analyze`
- `fix`
- `understand`
- `tool`
- `skill`
- `knowledge`
- `chat`
- `init`

Examples:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index .
```

```bash
cat error.log | java -jar target/ai-fix-1.0-SNAPSHOT.jar analyze
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand .
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix --task "add null checks" --suggest-only
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar knowledge list
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar knowledge summarize
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar knowledge search --query "null" --source fix --status recorded
```

## index

`index` auto-detects the primary language and scans Java, Go, or Python source files, then writes `index.json` to the current working directory.

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index /your/project
```

If omitted, the current directory is scanned:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index
```

You can also force the language:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index --language python /your/project
```

Supported values:

- `auto`
- `java`
- `go`
- `python`

## understand

`understand` auto-detects the primary language and generates:

- `project-summary.json`
- `understand.md`

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand \
  --source-root /your/project \
  --output-json project-summary.json \
  --output-markdown understand.md
```

You can force the language:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand \
  --language go \
  --source-root /your/project
```

Current behavior:

- local static analysis always runs first
- if an LLM is available, the tool generates a project report
- otherwise it falls back to a local Markdown report

## analyze

`analyze` is read-only. It analyzes input and suggests next steps. It never edits code or deploys anything.

```bash
mvn test 2>&1 | java -jar target/ai-fix-1.0-SNAPSHOT.jar analyze
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar analyze "java.lang.OutOfMemoryError"
```

Expected output structure:

```text
[Problem Type]
...
[Possible Causes]
1. ...
2. ...
[Location]
...
[Suggestions]
1. ...
2. ...
```

## fix

`fix` requires `index.json` in the current working directory.

Minimal example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --task "replace manual retry with framework retry"
```

### Git behavior

- if the current directory is a Git repository and the working tree is clean, `fix` auto-creates a branch before modifying files
- if the working tree has uncommitted changes, `fix` falls back to suggestion-only mode
- suggestion-only mode never modifies the working tree
- `--suggest-only` explicitly forces suggestion-only mode

### Multi-language target filters

Java-style options are still supported:

- `--method`
- `--class`

Language-neutral options are now available:

- `--symbol`
- `--container`

Recommended usage:

- Java: keep using `--method` and `--class`
- Go/Python: prefer `--symbol` and `--container`

Java example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --class OrderService \
  --method createOrder \
  --task "add null checks"
```

Go example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --container order.service \
  --symbol BuildReport \
  --task "add timeout handling" \
  --suggest-only
```

Python example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --container app.service \
  --symbol build_report \
  --task "add null checks" \
  --suggest-only
```

dry-run example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --symbol build_report \
  --task "refine error handling" \
  --dry-run
```

### Supported fix options

- `--task <text>`
- `--match <words>`
- `--method <methodName>`
- `--symbol <symbolName>`
- `--package <packageName>`
- `--class <className>`
- `--container <name>`
- `--file <pathFragment>`
- `--call <methodCallName>`
- `--annotation <annotationName>`
- `--limit <number>`
- `--page-size <number>`
- `--preview-lines <number>`
- `--branch <branchName>`
- `--commit-message <message>`
- `--provider <provider>`
- `--model <modelName>`
- `--config <path>`
- `--project-summary <path>`
- `--suggest-only`
- `--dry-run`
- `--stash`
- `--no-select`
- `--print-config`
- `--config-debug`

## Configuration

Supported config sources:

1. command-line arguments
2. environment variables
3. `--config <path>`
4. `~/.config/ai-fix/ai-fix.properties`
5. legacy path `~/.config/ai-fix.properties`
6. `ai-fix.properties` next to the running JAR

Common properties:

```properties
provider=openai
model=gpt-4.1

openai.api.key=your_openai_key
openai.base.url=https://api.openai.com/v1

groq.api.key=your_groq_key
groq.base.url=https://api.groq.com/openai/v1

ollama.base.url=http://localhost:11434

app.home=C:/Users/yourname/.config/ai-fix
tools.dir=C:/Users/yourname/.config/ai-fix/tools
skills.dir=C:/Users/yourname/.config/ai-fix/skills
knowledge.dir=C:/Users/yourname/.config/ai-fix/knowledge

prompt.template.file=ai-fix-prompt.txt
prompt.fix-template.file=ai-fix-prompt-fix.txt
```

Environment variables:

- `AI_FIX_PROVIDER`
- `AI_FIX_MODEL`
- `AI_FIX_HOME`
- `AI_FIX_TOOLS_DIR`
- `AI_FIX_SKILLS_DIR`
- `AI_FIX_SESSIONS_DIR`
- `AI_FIX_KNOWLEDGE_DIR`
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `GROQ_API_KEY`
- `GROQ_BASE_URL`
- `GROQ_MODEL`
- `GROQ_MODE`
- `OLLAMA_BASE_URL`

Notes:

- `GROQ_MODEL` and `GROQ_MODE` both map to the shared `model` setting
- built-in classpath tools/skills live under `src/main/resources`
- issue and repair knowledge records are stored under `knowledge.dir`, defaulting to `~/.config/ai-fix/knowledge`
- built-in tool/skill names are reserved
- filesystem definitions with the same name are ignored
- use distinct external names such as `echo-tool-extended` or `summarize-and-echo-extended`

## Tool / Skill definitions

The runtime supports both JSON and YAML. Built-in bundled tools and skills are now stored in YAML by default:

- `tool.json`
- `tool.yaml`
- `tool.yml`
- `skill.json`
- `skill.yaml`
- `skill.yml`

Recommended conventions for external tools and skills:

- prefer Python as the orchestration script to reduce Windows/Linux differences
- by default, keep resource usage inside the same directory as the defining `tool.yaml` or `skill.yaml`
- if another directory must be used, let the local script opt into that path explicitly
- for distributable tools, keep bundled helper binaries under the local `bin/` directory and place local config files such as `pgpass.conf` or `my.cnf` next to the tool definition

Recommended layout:

```text
~/.config/ai-fix/
  ai-fix.properties
  tools/
    my-tool/
      tool.yaml
  skills/
    my-skill/
      skill.yaml
  sessions/
    demo-session.md
```

For this repository, you can also point external tools to the workspace-local directory:

```properties
tools.dir=D:/03_projects/suk/ai-fix/workspace/tools
python.scripts.dir=D:/03_projects/suk/ai-fix/src/main/python
```

`tools.dir` controls where tool definitions and tool implementations live. `python.scripts.dir` is the root for shared Python scheduler/runtime code.

Built-in developer tools now include:

- `read-file`
- `list-files`
- `git-diff-reader`
- `project-summary-reader`
- `index-symbol-reader`
- `web-search`
- `web-fetch`
- `llm`
- `command-analyze`
- `command-index`
- `command-understand`
- `command-fix-suggest`

Workspace-local database tools in this repository include:

- `mysql-client`
- `pgsql-client`
- `import-tool`
- `import-control-file-generator`
- `youtube-transcript`
- `playwright-cli`

Workspace-local developer skills can also compose those tools. For example:

- `generate-import-control-and-run`
- `youtube-transcript-and-summarize`
- `playwright-ui-smoke-test`

You can export a fixed workflow package from any skill:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar generate-exe \
  --skill generate-import-control-and-run \
  --output D:/exports/generated-import-workflow \
  --config D:/03_projects/suk/ai-fix/ai-fix.properties
```

Export behavior:

- Python is the default export target
- filesystem `external-command` tools are copied into the output package and run directly
- built-in tool steps, including `llm`, are exported as manual override steps
- legacy `ai` steps are no longer supported and should be migrated to `toolName: llm`
- the exported runner accepts `--step-overrides-json` so fixed outputs can be supplied manually or by another wrapper
- Go export is still available, but Python is the preferred portable runner when workflows depend on copied tools or flexible overrides

Example for a skill with manual steps:

```bash
python main.py \
  --input-json "{\"text\":\"java.lang.NullPointerException\"}" \
  --step-overrides-json "{\"analysis\":\"[Problem Type]\\nNullPointerException\"}"
```

You can inspect a tool definition from the CLI:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar tool describe --name import-tool --config D:/03_projects/suk/ai-fix/ai-fix.properties
```

Built-in developer skills now include:

- `analyze-java-error`
- `analyze-test-failure`
- `search-doc-and-explain`
- `project-summary-and-explain`
- `find-entry-points`
- `explain-project-structure`
- `review-changed-files`
- `explain-file`
- `explain-symbol`

## Skill documentation

Skills can now use a dual-file layout:

- `skill.yaml`: executable workflow definition
- `SKILL.md`: human-oriented description and reusable community guidance

A reusable starter template is available in:

- [SKILL_TEMPLATE.md](D:/03_projects/suk/ai-fix/SKILL_TEMPLATE.md)

The runtime still executes `skill.yaml`, but `skill describe` now prefers Markdown documentation when `SKILL.md` is present. `README.md` is also supported as a fallback for filesystem skills.

Example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar skill describe --name playwright-ui-smoke-test --config D:/03_projects/suk/ai-fix/ai-fix.properties
```

If you have a Markdown-first skill and want a starting YAML scaffold, generate one with:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar skill convert-markdown \
  --input D:/skills/my-skill/SKILL.md \
  --output-dir D:/skills/my-skill-generated
```

This command:

- first tries to use the configured AI provider to draft `skill.yaml`
- falls back to a local skeleton when AI conversion is unavailable or invalid
- copies the Markdown file to `SKILL.md`
- leaves a safe `llm` placeholder step when the local skeleton path is used

AI-generated drafts are also validated before they are written:

- top-level `arguments` must be a valid list
- every step must include `id`, `type`, `toolName`, and `outputKey`
- generated drafts are restricted to `type: tool`
- legacy `type: ai` is rejected
- `toolName: llm` must contain a non-empty `prompt`

To force local skeleton generation without AI:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar skill convert-markdown \
  --input D:/skills/my-skill/SKILL.md \
  --output-dir D:/skills/my-skill-generated \
  --no-ai
```

## Explicit AI tool in skills

Bundled tools now include `llm`, which lets a skill declare an explicit AI call as a tool step.

Typical usage:

```yaml
- id: summarize
  type: tool
  toolName: llm
  outputKey: summary
  arguments:
    systemPrompt: You are a concise technical summarizer.
    prompt: |
      Summarize the following content:

      ${webContent}
```

This is useful when you want a workflow to make it explicit that one step is "call the model for analysis or summarization", instead of using a generic `ai` step.

Structured output is also supported:

```yaml
- id: classify
  type: tool
  toolName: llm
  outputKey: classification
  arguments:
    format: json
    requiredKeys: problemType,severity
    prompt: |
      Return JSON with keys `problemType` and `severity`.
```

Supported values for `format`:

- `text`
- `yaml`
- `json`

## init

`init` interactively creates and updates:

- `ai-fix.properties`
- `ai-fix-prompt.txt`
- `ai-fix-prompt-fix.txt`

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar init
```

## chat

Start an interactive session:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar chat \
  --provider groq \
  --model llama-3.1-8b-instant \
  --config C:/path/to/ai-fix.properties
```

Inside chat:

- `/store <name>` saves a Markdown snapshot
- `/load <name>` loads a Markdown snapshot
- `/new-skill <name>` generates a new skill from the current session
- `/exit` exits the session

When the model needs to call a tool or skill, it should prefer a structured YAML action like:

```yaml
action: tool
name: read-file
reason: Read the source file before answering.
expectedOutput: File content for analysis.
args:
  path: D:/your-project/src/main/java/com/example/OrderService.java
  maxChars: 6000
```

Tool and skill execution results are now returned as structured objects, including:

- `ok`
- `toolName` or `skillName`
- `output`
- `data` for tools
- `result` and `summary` for skills

When a bridge tool or symbol lookup returns `[candidates]`, chat now stops the action loop and asks for clarification directly instead of relying only on prompt wording. This prevents the assistant from guessing when multiple symbols match the same name.

If the user explicitly wants core command behavior inside chat, the model can use bridge tools such as:

```yaml
action: tool
name: command-analyze
reason: Reuse the stable analyze command semantics inside chat.
expectedOutput: Structured diagnosis for the pasted exception.
args:
  input: |
    java.lang.NullPointerException
    at com.example.OrderService.createOrder(OrderService.java:42)
```

```yaml
action: tool
name: command-fix-suggest
reason: Reuse the fix command suggestion-only workflow without modifying files.
expectedOutput: Structured repair advice for the selected symbol.
args:
  task: Add null checks and preserve current behavior.
  symbol: createOrder
  container: OrderService
  indexPath: D:/your-project/index.json
```

The workspace-local database tools prefer native CLIs when they exist in the environment:

- `mysql-client` prefers `mysql`, then falls back to `src/main/go/mysql-go/bin/mysql-go.exe`
- `pgsql-client` prefers `psql`, then falls back to `src/main/go/psql-go/bin/psql-go.exe`

Example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar tool run \
  --name mysql-client \
  --args-json "{\"query\":\"SELECT 1\",\"database\":\"demo\",\"user\":\"reader\",\"tuplesOnly\":\"true\"}" \
  --confirm \
  --config D:/03_projects/suk/ai-fix/ai-fix.properties
```

Examples:

```text
Explain OrderService.java and show the risky parts.
```

```text
Use explain-symbol on createOrder in OrderService.
```

```text
Review my current git changes.
```

## Troubleshooting

If you see an error like:

```text
Exception in thread "main" java.lang.Error: Unresolved compilation problem:
    InitCli cannot be resolved
```

the current source tree is usually not the problem. In this repository, `InitCli` exists and the packaged JAR has been verified.

This error usually means you are running stale IDE output or a partially compiled classpath. Recommended recovery steps:

1. Clean the IDE build output directory
2. Re-import or refresh the Maven project
3. Rebuild the project from the command line
4. Run the packaged JAR instead of an old IDE run configuration

Recommended commands:

```bash
.\mvnw.cmd -s .mvn-local-settings.xml -DforkCount=0 test
.\mvnw.cmd -s .mvn-local-settings.xml -DskipTests package
java -jar target/ai-fix-1.0-SNAPSHOT.jar chat --help
```

## Known limitations

- full automatic patch application is still best on Java
- Go/Python patch application compatibility still needs more work
- `index.json` output is currently fixed to the current working directory
