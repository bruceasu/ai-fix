# ai-fix

面向 Java、Go、Python 项目的 AI 辅助分析与修复工具，当前以单个可执行 JAR 作为主要入口。

英文文档见 [README.en.md](README.en.md)。

架构边界说明见 [COMMAND_TOOL_SKILL_BOUNDARIES.md](COMMAND_TOOL_SKILL_BOUNDARIES.md)。

## 功能概览

- `index`：扫描源码并生成 `index.json`
- `analyze`：分析日志、异常、测试输出，返回结构化建议
- `understand`：生成项目摘要与项目理解报告
- `fix`：基于索引选择目标符号，生成补丁或修复建议
- `tool`：列出并执行内置或外部 tools
- `skill`：列出并执行内置或外部 skills
- `chat`：启动交互式 AI 会话，并在会话中调度 tool / skill
- `init`：交互式初始化配置文件和 prompt 模板

## 当前语言支持

- Java：`index`、`understand`、`fix`
- Go：`index`、`understand`，以及 `fix` 的 suggestion-only / dry-run 路径
- Python：`index`、`understand`，以及 `fix` 的 suggestion-only / dry-run 路径

说明：

- Java 仍然是最完整的能力路径
- Go / Python 已支持索引、理解、符号匹配、预览和建议模式
- 对 Go / Python，当前更推荐 `--suggest-only` 或 `--dry-run`

## 构建

建议先执行：

```powershell
.\setup-jdk25.ps1
```

或：

```bat
setup-jdk25.bat
```

推荐构建命令：

```bash
.\mvnw.cmd -s .mvn-local-settings.xml clean package
```

运行：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar
```

## CLI 命令

- `index`
- `analyze`
- `fix`
- `understand`
- `tool`
- `skill`
- `chat`
- `init`
- `help`

示例：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index .
```

```bash
mvn test 2>&1 | java -jar target/ai-fix-1.0-SNAPSHOT.jar analyze
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand .
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix --task "add null checks" --suggest-only
```

## index

`index` 会自动检测主语言，扫描 Java、Go、Python 源文件，并在当前工作目录写出 `index.json`。

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index /your/project
```

如果省略路径，则扫描当前目录：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index
```

也可以强制指定语言：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar index --language python /your/project
```

支持值：

- `auto`
- `java`
- `go`
- `python`

## understand

`understand` 会自动检测主语言，并生成：

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

也可以强制指定语言：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar understand \
  --language go \
  --source-root /your/project
```

当前行为：

- 总是先做本地静态分析
- 如果 LLM 可用，再生成项目报告
- 如果 LLM 不可用，则回退为本地 Markdown 报告

## analyze

`analyze` 是只读模式。它只分析输入并给出下一步建议，不改代码，也不做部署。

```bash
mvn test 2>&1 | java -jar target/ai-fix-1.0-SNAPSHOT.jar analyze
```

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar analyze "java.lang.OutOfMemoryError"
```

预期输出结构：

```text
【问题类型】
...
【可能原因】
1. ...
2. ...
【定位】
...
【建议】
1. ...
2. ...
```

## fix

`fix` 依赖当前工作目录中的 `index.json`。

最小示例：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --task "replace manual retry with framework retry"
```

### Git 行为

- 如果当前目录是 Git 仓库且工作区干净，`fix` 会在修改前自动创建新分支
- 如果工作区存在未提交改动，`fix` 会自动降级为 suggestion-only 模式
- suggestion-only 模式不会修改工作区文件
- `--suggest-only` 可以显式强制进入 suggestion-only

### 多语言目标过滤

Java 风格参数仍然可用：

- `--method`
- `--class`

语言无关参数：

- `--symbol`
- `--container`

推荐：

- Java：继续优先使用 `--method` 和 `--class`
- Go / Python：优先使用 `--symbol` 和 `--container`

Java 示例：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --class OrderService \
  --method createOrder \
  --task "add null checks"
```

Go 示例：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --container order.service \
  --symbol BuildReport \
  --task "add timeout handling" \
  --suggest-only
```

Python 示例：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --container app.service \
  --symbol build_report \
  --task "add null checks" \
  --suggest-only
```

dry-run 示例：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar fix \
  --symbol build_report \
  --task "refine error handling" \
  --dry-run
```

### 支持的 fix 参数

- `--task <text>`
- `--match <words>`
- `--method <methodName>`

## generate-exe

`generate-exe` 现在默认导出 Python 版本的固定 workflow 包，不再只限制为 tool-only skill。

当前导出规则：

- 文件系统中的 `external-command` tool 会被复制到输出目录，并在导出的 runner 中直接执行
- 内置 tool step 会被导出成固定的 manual override 输入位
- AI step 也会被导出成固定的 manual override 输入位
- 这样所有 skill 理论上都可以导出成固定程序，只是部分步骤需要人工预设，或者在运行时通过 CLI 参数传入

示例：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar generate-exe \
  --skill generate-import-control-and-run \
  --output D:/exports/generated-import-workflow \
  --config D:/03_projects/suk/ai-fix/ai-fix.properties
```

对于包含 AI step 的 skill，也可以导出，只是在运行导出的程序时要提供固定输出：

```bash
python main.py \
  --input-json "{\"text\":\"java.lang.NullPointerException\"}" \
  --step-overrides-json "{\"analysis\":\"[Problem Type]\\nNullPointerException\"}"
```

说明：

- Python 是默认导出目标，更适合跨平台分发和调度外部 tools
- 如果确实需要，也仍可导出 Go runner
- 当 workflow 依赖大量 copied tools、手工覆盖步骤或第三方脚本时，优先使用 Python runner 更稳

## workspace/tools 补充

当前仓库里的外部工具还包括：

- `youtube-transcript`
- `playwright-cli`

这个 tool 基于 `src/main/python/get-youtube-transcript/get-transcript.py` 重新包装，符合当前外部 tool 规范：

- 使用 Python 作为调度层
- 默认把输出、缓存、错误日志放在 tool 自己目录下
- 支持单个视频、多个视频、播放列表或频道视频页
- 支持 `--dry-run` 输出固定执行计划 JSON，方便 skill 或测试调用

另外还新增了一个浏览器自动化相关能力：

- `playwright-cli` tool
- `playwright-ui-smoke-test` skill

它们适合做基础 UI 冒烟测试：

- 打开页面
- 获取 snapshot
- 截图
- 让 AI 生成简洁的 UI 测试报告

`playwright-ui-smoke-test` 现在还支持把测试报告保存到文件：

- 显式传 `reportOutput` 时，写到指定路径
- 不传时，默认写到 `workspace/tools/playwright-cli/output/` 下
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

## 配置

配置来源优先级：

1. 命令行参数
2. 环境变量
3. `--config <path>`
4. `~/.config/ai-fix/ai-fix.properties`
5. 兼容旧路径 `~/.config/ai-fix.properties`
6. 运行中 JAR 同目录下的 `ai-fix.properties`

常用配置：

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
sessions.dir=C:/Users/yourname/.config/ai-fix/sessions

prompt.template.file=ai-fix-prompt.txt
prompt.fix-template.file=ai-fix-prompt-fix.txt
```

环境变量：

- `AI_FIX_PROVIDER`
- `AI_FIX_MODEL`
- `AI_FIX_HOME`
- `AI_FIX_TOOLS_DIR`
- `AI_FIX_SKILLS_DIR`
- `AI_FIX_SESSIONS_DIR`
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`
- `GROQ_API_KEY`
- `GROQ_BASE_URL`
- `GROQ_MODEL`
- `GROQ_MODE`
- `OLLAMA_BASE_URL`

说明：

- `GROQ_MODEL` 和 `GROQ_MODE` 都会映射到共享的 `model`
- 内置 classpath tools / skills 位于 `src/main/resources`
- 内置 tool / skill 名称是保留名
- 文件系统中同名定义会被忽略，不会覆盖内置能力
- 外部扩展请使用不同名称，例如 `echo-tool-extended`、`summarize-and-echo-extended`

## Tool / Skill 定义

运行时同时支持 JSON 和 YAML，当前内置 bundled tools / skills 已统一使用 YAML：

- `tool.json`
- `tool.yaml`
- `tool.yml`
- `skill.json`
- `skill.yaml`
- `skill.yml`

外部 `tool` / `skill` 的实现约定建议如下：

- 优先使用 Python 作为调度脚本，减少 Windows / Linux 差异
- 默认只使用定义文件所在目录中的资源
- 如果确实需要访问其它目录，应由该目录内脚本显式指定，而不是在 runtime 层隐式扩散
- 分发型工具建议把精简版可执行文件放在同目录的 `bin/` 下，把 `pgpass.conf`、`my.cnf` 这类本地配置也放在同目录，避免依赖源码树路径

推荐目录结构：

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

当前内置开发 tools 包括：

- `read-file`
- `list-files`
- `git-diff-reader`
- `project-summary-reader`
- `index-symbol-reader`
- `web-search`
- `web-fetch`
- `command-analyze`
- `command-index`
- `command-understand`
- `command-fix-suggest`

当前内置开发 skills 包括：

- `analyze-java-error`
- `analyze-test-failure`
- `search-doc-and-explain`
- `project-summary-and-explain`
- `find-entry-points`
- `explain-project-structure`
- `review-changed-files`
- `explain-file`
- `explain-symbol`

## Command Bridge

为保持核心命令和 `chat` / `tool` 体系的边界清晰，仓库提供了 4 个 bridge tools：

- `command-analyze`
- `command-index`
- `command-understand`
- `command-fix-suggest`

设计原则：

- `index`、`analyze`、`understand`、`fix` 仍然是一级稳定命令
- bridge tool 只是让 `chat` 或 skill 在不直接碰 shell 的情况下复用核心命令语义
- `command-fix-suggest` 只开放 suggestion-only 语义，不会修改文件

## init

`init` 用于交互式初始化：

- `ai-fix.properties`
- `ai-fix-prompt.txt`
- `ai-fix-prompt-fix.txt`

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar init
```

## chat

启动交互式会话：

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar chat \
  --provider groq \
  --model llama-3.1-8b-instant \
  --config C:/path/to/ai-fix.properties
```

会话内命令：

- `/store <name>`：保存 Markdown snapshot
- `/load <name>`：加载 Markdown snapshot
- `/new-skill <name>`：从当前会话生成 skill
- `/exit`：退出会话

当模型需要调 tool 或 skill 时，推荐输出结构化 YAML：

```yaml
action: tool
name: read-file
reason: 先读取源文件再回答。
expectedOutput: 用于分析的文件内容。
args:
  path: D:/your-project/src/main/java/com/example/OrderService.java
  maxChars: 6000
```

tool / skill 的执行结果现在也会以结构化对象形式返回，包含：

- `ok`
- `toolName` 或 `skillName`
- `output`
- `data`（tool）
- `result` 和 `summary`（skill）

如果用户明确想在 `chat` 里复用核心命令语义，可以使用 bridge tool，例如：

```yaml
action: tool
name: command-analyze
reason: 复用稳定的 analyze 命令语义来分析异常。
expectedOutput: 结构化诊断结果。
args:
  input: |
    java.lang.NullPointerException
    at com.example.OrderService.createOrder(OrderService.java:42)
```

```yaml
action: tool
name: command-fix-suggest
reason: 复用 fix 的建议模式，但不修改工作区文件。
expectedOutput: 针对目标符号的结构化修复建议。
args:
  task: 添加空值保护并保持现有行为兼容
  symbol: createOrder
  container: OrderService
  indexPath: D:/your-project/index.json
```

如果 `command-fix-suggest` 命中了多个候选，而你没有提供足够的 `container` 或 `file`，它会返回：

- `[candidates]`
- 候选符号
- 文件路径
- 行号范围

这时应继续补充 `container` 或 `file`，而不是让 AI 猜测目标。

## 故障排查

如果你看到这样的错误：

```text
Exception in thread "main" java.lang.Error: Unresolved compilation problem:
    InitCli cannot be resolved
```

通常不是当前源码树本身有问题。这个仓库里 `InitCli` 是存在的，而且打包后的 JAR 已验证可运行。

这类错误通常意味着你正在运行：

- 过期的 IDE 编译产物
- 未完整刷新的增量编译输出
- 部分损坏的类路径

建议处理步骤：

1. 清理 IDE 输出目录
2. 重新导入或刷新 Maven 工程
3. 在命令行重新构建
4. 优先运行打包后的 JAR，而不是旧的 IDE run configuration

推荐命令：

```bash
.\mvnw.cmd -s .mvn-local-settings.xml -DforkCount=0 test
.\mvnw.cmd -s .mvn-local-settings.xml -DskipTests package
java -jar target/ai-fix-1.0-SNAPSHOT.jar chat --help
```

## 已知限制

- `fix` 的完整自动补丁路径目前仍以 Java 最稳定
- Go / Python 的 patch 应用成功率仍需继续加强
- `index.json` 当前仍固定输出到当前工作目录
## 追加说明：显式 AI tool

现在内置 tools 中增加了 `llm`。

它的作用是：在 skill 编排里，明确声明“这一步就是调用 AI 做摘要、分析或提炼”，而不是只依赖通用的 `ai` step。

典型示例：

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

这样做的好处是：

- skill 编排意图更明确
- tool / skill 执行路径更统一
- 后续导出 workflow 时，更容易把“AI 参与点”单独识别出来

补充说明：

- 现在推荐在 skill 中使用 `toolName: llm`
- 旧的 `type: ai` 已视为 legacy 语法
- 运行 skill 时如果仍使用 `type: ai`，会明确报错并提示迁移到 `llm`

`llm` 现在还支持结构化输出格式：

- `format: text`
- `format: yaml`
- `format: json`

这样 skill 可以更稳定地拿到结构化结果，而不只是普通文本。

如果需要更严格的结果约束，还可以传：

- `requiredKeys: field1,field2`

这样在 `json` 或 `yaml` 模式下，如果模型输出里缺少指定字段，tool 会直接失败，而不是继续把不完整结果传下去。
## Additional note: local knowledge records

The tool now stores reusable local knowledge about analyzed problems and repair attempts under:

- `knowledge.dir`
- default: `~/.config/ai-fix/knowledge`

Typical contents:

- `records.jsonl`
- `records/<timestamp>-<slug>.md`

This is intended to help long-term self-learning by keeping:

- problem classifications
- analysis summaries
- repair schemes
- fix execution outcomes
