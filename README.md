# ai-fix: DPEF Agent

`ai-fix` 是一个基于 **任务分解、计划、执行、反馈 (DPEF)** 闭环设计的 AI Agent。它通过调度一系列原子化的 **Tools** 和 **Skills** 来自动分析、定位并修复 Java、Go、Python 项目中的问题。

英文文档见 [README.en.md](README.en.md)。

## 核心理念：DPEF 闭环

- **Decomposition (任务分解)**：将复杂的工程目标分解为可执行的子任务。
- **Planning (计划)**：基于项目上下文生成动态或静态的执行计划。
- **Execution (执行)**：调用 Tools (原子工具) 或 Skills (复合方案) 完成子任务。
- **Feedback (反馈)**：分析执行结果，并根据反馈调整后续计划或进行下一轮推理。

## 核心架构

项目不再提供固定的黑盒命令，而是通过高度灵活的 Agent 接口运行：

- **Chat (交互式 Agent)**：主要的交互入口。你可以通过自然语言下达复杂指令，Agent 会自动分解任务并调度工具。
- **Skill (静态方案)**：预定义的多步骤工作流，用于处理常见的标准化任务（如分析测试失败、理解项目结构等）。
- **Tool (原子能力)**：底层的单功能插件（如读写文件、Git 操作、Web 搜索、LLM 推理等）。
- **Knowledge (本地知识库)**：记录历史分析和修复经验，支持 RAG（检索增强生成）以提升未来决策的准确性。

## 快速开始

### 构建

```bash
.\mvnw.cmd -s .mvn-local-settings.xml clean package
```

### 运行交互式 Agent (推荐)

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar chat --provider openai --model gpt-4o
```

在 Chat 模式下，你可以输入：
- "帮我分析一下为什么这个测试用例失败了"
- "在 OrderService 中找出所有缺失空值检查的方法并给出修复建议"
- "解释一下这个项目的整体架构"

Agent 会自动拆解任务，可能执行的动作包括：`list-files` -> `read-file` -> `analyze-java-error` 等。

## CLI 常用命令

- `chat`：启动交互式 DPEF Agent。
- `skill`：列出并执行预定义工作流。
- `tool`：列出并运行原子工具。
- `knowledge`：管理本地积累的问题修复知识。
- `generate-exe`：将一个 Skill 导出为独立的可执行程序包。
- `init`：初始化配置文件和 Prompt 模板。

示例：

```bash
# 列出所有可用技能
java -jar target/ai-fix-1.0-SNAPSHOT.jar skill list

# 执行指定技能
java -jar target/ai-fix-1.0-SNAPSHOT.jar skill run explain-symbol --args "name=createOrder"
```

## 开发者扩展

你可以非常容易地添加自己的 Tool 或 Skill：

- **添加 Tool**：在 `workspace/tools/` 下创建一个目录，包含 `tool.yaml` 和对应的脚本。
- **添加 Skill**：在 `workspace/skills/` 下创建一个目录，包含 `skill.yaml`。你可以通过 `/new-skill` 命令从一段成功的 Chat 会话中自动生成 Skill 定义。

## 配置与定制

配置文件：`ai-fix.properties`
Prompt 模板：位于 `prompt/` 目录下的 `.txt` 文件。

你可以通过修改 Prompt 模板来调整 Agent 的思考风格和反馈逻辑。

## 故障排查

建议优先运行打包后的 JAR，以确保依赖完整。

```bash
java -jar target/ai-fix-1.0-SNAPSHOT.jar chat --help
```
