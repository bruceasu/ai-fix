# ai-fix

AI-assisted Java patching tool with a shaded JAR entry point.

## What it does

- `index`: scan Java source files and generate `index.json`
- `fix`: load `index.json`, select matching methods, ask the LLM for a patch, and optionally apply it in Git
- `understand`: scan a Java project and generate a project understanding report plus structured summary
- `init`: interactively create config and prompt files under `~/.config`

## Build

Recommended environment setup:

```powershell
.\setup-jdk25.ps1
```

or:

```bat
setup-jdk25.bat
```

The setup scripts configure:

- `JAVA_HOME` to JDK 25
- `MAVEN_USER_HOME` to the project-local `.mvn-user-home`
- Maven wrapper usage with `.mvn-local-settings.xml`

Recommended build command:

```bash
.\mvnw.cmd -s .mvn-local-settings.xml clean package
```

After packaging, run the fat JAR with:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar
```

## CLI entry

The shaded JAR uses a unified entry class and supports these subcommands:

- `index`
- `fix`
- `understand`
- `init`
- `help`

Examples:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar help
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index src/main/java
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix --task "add retry"
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar init
```

## Generate index

`index` scans a source root and writes `index.json` into the current working directory.

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index /your/project
```

If the source root is omitted, the current directory is scanned:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index
```

Typical workflow:

1. Run `index` against the project you want to analyze.
2. Confirm `index.json` exists in the directory where you will run `fix`.
3. Run `fix` with filters to target the methods you want to change.

## Run fix

`fix` requires `index.json` to already exist.

Minimal example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --task "replace manual retry with Spring Retry"
```

Use an explicit config file:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --config /path/to/ai-fix.properties \
  --task "replace manual retry with Spring Retry"
```

## Configuration

Variable runtime settings can be placed in `ai-fix.properties`.
You can start from `ai-fix.properties.example`.

Load priority from high to low:

1. Command-line arguments
2. Environment variables
3. Explicit config file passed by `--config <path>`
4. `~/.config/ai-fix.properties`
5. `ai-fix.properties` in the same directory as the running JAR

Build/runtime helper files:

- `setup-jdk25.ps1`
- `setup-jdk25.bat`
- `.mvn-local-settings.xml`

You can also use:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar init
```

to interactively create or update the config and prompt files in `~/.config/`.

Prompt templates are configured as file paths, not inline strings.
If a prompt template path is relative, it is resolved relative to `~/.config/`.
Generated index and project summary files store source file paths relative to the output file's `projectRoot`, which defaults to `"."`.

Supported properties:

```properties
provider=openai
model=gpt-4.1

openai.api.key=your_openai_key
openai.base.url=https://api.openai.com/v1

groq.api.key=your_groq_key
groq.base.url=https://api.groq.com/openai/v1

ollama.base.url=http://localhost:11434

prompt.template.file=ai-fix-prompt.txt
prompt.fix-template.file=ai-fix-prompt-fix.txt
```

Supported environment variable mappings:

- `AI_FIX_PROVIDER` -> `provider`
- `AI_FIX_MODEL` -> `model`
- `OPENAI_API_KEY` -> `openai.api.key`
- `OPENAI_BASE_URL` -> `openai.base.url`
- `GROQ_API_KEY` -> `groq.api.key`
- `GROQ_BASE_URL` -> `groq.base.url`
- `OLLAMA_BASE_URL` -> `ollama.base.url`
- `AI_FIX_PROMPT_TEMPLATE_FILE` -> `prompt.template.file`
- `AI_FIX_PROMPT_FIX_TEMPLATE_FILE` -> `prompt.fix-template.file`

Explicit config file:

- `--config <path>` can be used by `fix` and `understand`
- absolute paths are used as-is
- relative paths are resolved from the current working directory

Command-line arguments currently override configuration for:

- `fix`: `--provider`, `--model`, `--config`, `--project-summary`
- `understand`: `--source-root`, `--output-json`, `--output-markdown`, `--provider`, `--model`, `--config`

Recommended fuzzy targeting:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --task "replace manual retry with Spring Retry" \
  --match "retry order service"
```

You can still narrow down with explicit filters when needed:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --package me.asu.ai.cli \
  --class AiFixCLI \
  --method printUsage \
  --task "refine help text"
```

Target by source file fragment:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --file src/main/java/me/asu/ai/cli/AiFixCLI.java \
  --method main \
  --task "simplify argument parsing"
```

Target by annotation:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --annotation Service \
  --task "add logging around external calls"
```

Target by called method name:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --call retry \
  --task "replace custom retry with framework retry"
```

Limit the number of matched methods:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --call retry \
  --limit 3 \
  --task "standardize retry handling"
```

Dry-run patch generation:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --method createOrder \
  --task "add null checks" \
  --dry-run
```

Create a branch and custom commit message:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --method createOrder \
  --task "refactor retry logic" \
  --branch ai-fix/retry-refactor \
  --commit-message "AI fix: refactor retry logic"
```

Auto-stash local changes before applying:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --call retry \
  --task "replace custom retry" \
  --stash
```

Use an explicit project summary context:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --project-summary /path/to/project-summary.json \
  --task "replace custom retry"
```

## Backward-compatible invocation

If the first argument starts with `--`, the JAR forwards the call directly to `AiFixCLI`.

So this still works:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar \
  --task "replace manual retry with Spring Retry" \
  --method createOrder
```

## Supported fix options

Current `fix` options parsed by `AiFixCLI`:

- `--task <text>`
- omit `--task` or pass `--task -` to enter the task interactively
- `--match <words>`
- `--method <methodName>`
- `--package <packageName>`
- `--class <className>`
- `--file <pathFragment>`
- `--call <methodCallName>`
- `--annotation <annotationName>`
- `--limit <number>`
- `--page-size <number>`
- `--preview-lines <number>`
- `--branch <branchName>`
- `--commit-message <message>`
- `--model <modelName>`
- `--config <path>`
- `--project-summary <path>`
- `--print-config`
- `--no-select`
- `--dry-run`
- `--stash`
- `--provider <provider>`
- `--config-debug`

## Provider examples

Default provider is OpenAI and uses environment-based configuration from the OpenAI Java SDK.
Default provider is OpenAI and now supports both properties files and environment variables.

OpenAI:

```bash
export OPENAI_API_KEY=your_key

java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --task "add logging" \
  --method createOrder
```

Groq:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --config /path/to/ai-fix.properties \
  --provider groq \
  --model llama-3.1-70b-versatile \
  --task "add logging" \
  --method createOrder
```

Ollama:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --provider ollama \
  --model qwen2.5-coder:7b \
  --task "add logging" \
  --method createOrder
```

## Run understand

`understand` scans a Java project and writes:

- `project-summary.json`: structured facts for later AI workflows
- `understand.md`: human-readable project understanding report

Minimal example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand
```

Specify a source root and output files:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand \
  --config /path/to/ai-fix.properties \
  --source-root /your/project \
  --output-json project-summary.json \
  --output-markdown understand.md
```

Current `understand` options:

- `--source-root <path>`
- `--output-json <path>`
- `--output-markdown <path>`
- `--provider <provider>`
- `--model <model>`
- `--config <path>`
- `--config-debug`

Behavior notes:

- local static analysis always runs first and writes `project-summary.json`
- if AI configuration is available, `understand.md` is generated by AI summarization over local project facts
- if AI configuration is missing or the AI call fails, the tool falls back to a local Markdown report

## Run init

`init` interactively manages these files under `~/.config/`:

- `ai-fix.properties`
- `ai-fix-prompt.txt`
- `ai-fix-prompt-fix.txt`

If a file already exists, `init` shows the current content and lets you:

- keep it
- delete it
- modify it

For modifications, you can choose:

- write the default template
- paste custom content ending with `EOF`

Example:

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar init
```

At the end, `init` prints example environment variable settings for:

- Windows PowerShell
- Windows permanent `setx`
- Linux/macOS `export`

## Runtime behavior

- `fix` expects to run inside a Git repository
- `fix` automatically reads `project-summary.json` from the current working directory when present
- `fix` can use `--project-summary <path>` to load a different summary file
- `fix` does not inject the whole summary JSON verbatim; it first compresses and filters the most relevant project context
- `fix` supports `--match "<words>"` for fuzzy matching across package, class, method, file, signature, annotations, and calls
- `fix` supports additional narrowing with `--package`, `--class`, and `--file`
- when multiple methods still match, `fix` prompts for an interactive selection unless `--no-select` is used
- during interactive selection, you can choose numbers like `1,3`, type more words to narrow the list again, enter `*` to use all currently matched results, enter `b` to go back to the previous filtered list, or enter `q` to quit
- when the candidate list is long, `fix` shows it in pages and supports `n` / `p` to move between pages, plus `g <page>` to jump directly
- `--page-size` controls how many candidates are shown per page during interactive selection
- `--preview-lines` controls how many code lines are shown in each candidate preview
- candidate lines include the method signature, a score, and a short code preview to better distinguish overloaded methods and explain ranking
- previews prefer a window around the first matched term, and fall back to the method start when no match is found in code lines
- preview lines include source line numbers so the snippet is easier to map back to the file
- matched terms are highlighted in candidate lines and previews using `[[term]]` style markers for terminal-friendly readability
- `--print-config` prints the resolved configuration and exits before any Git or LLM work starts
- if `--stash` is not used, the working tree must be clean
- if `--dry-run` is not used, the tool creates a branch automatically when `--branch` is omitted
- successful non-dry-run execution stages changes and creates a commit
- patch files such as `patch_<class>_<method>_attempt1.diff` are written to the working directory

## Known limitations

- `index.json` output path is currently fixed to the working directory
- `fix` reads `index.json` only from the working directory
- the codebase currently contains a duplicate/abnormal file name issue around `SimpleOpenAIClientImpl`, which may block full Maven compilation until cleaned up

## Recommended workflow

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index /path/to/project
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --match "retry order service" \
  --limit 1 \
  --task "replace manual retry with Spring Retry" \
  --dry-run
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --match "retry order service" \
  --limit 1 \
  --task "replace manual retry with Spring Retry" \
  --stash
```
