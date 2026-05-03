package me.asu.ai.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.asu.ai.skill.SkillCatalogService;
import me.asu.ai.skill.SkillDefinition;
import me.asu.ai.skill.SkillStepDefinition;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolDefinition;

public class WorkflowExporter {

    private final SkillCatalogService skillCatalogService;
    private final ToolCatalogService toolCatalogService;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorkflowExporter(SkillCatalogService skillCatalogService, ToolCatalogService toolCatalogService) {
        this.skillCatalogService = skillCatalogService;
        this.toolCatalogService = toolCatalogService;
    }

    public ExportResult export(String skillName, Path outputDir, String target) throws Exception {
        SkillDefinition skill = skillCatalogService.getRequired(skillName);

        Path normalizedOutput = outputDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedOutput);
        Files.createDirectories(normalizedOutput.resolve("tools"));

        Map<String, Object> workflow = new LinkedHashMap<>();
        Map<String, Object> skillNode = new LinkedHashMap<>();
        skillNode.put("name", skill.name);
        skillNode.put("description", skill.description);
        skillNode.put("arguments", skill.arguments);
        List<Map<String, Object>> steps = new ArrayList<>();
        Map<String, Object> toolsNode = new LinkedHashMap<>();
        List<Map<String, Object>> manualSteps = new ArrayList<>();

        for (SkillStepDefinition step : skill.steps) {
            Map<String, Object> stepNode = buildStepNode(step, normalizedOutput, toolsNode, manualSteps);
            steps.add(stepNode);
        }

        skillNode.put("steps", steps);
        workflow.put("skill", skillNode);
        workflow.put("tools", toolsNode);
        workflow.put("manualSteps", manualSteps);

        Path workflowFile = normalizedOutput.resolve("workflow.json");
        Files.writeString(workflowFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow), StandardCharsets.UTF_8);

        boolean exportPython = "both".equals(target) || "python".equals(target);
        boolean exportGo = "both".equals(target) || "go".equals(target);
        if (exportPython) {
            Files.writeString(normalizedOutput.resolve("main.py"), pythonTemplate(), StandardCharsets.UTF_8);
        }
        if (exportGo) {
            Files.writeString(normalizedOutput.resolve("main.go"), goTemplate(), StandardCharsets.UTF_8);
        }
        Files.writeString(normalizedOutput.resolve("README.md"), readmeTemplate(skill.name, target), StandardCharsets.UTF_8);

        return new ExportResult(normalizedOutput, workflowFile, exportPython, exportGo, !manualSteps.isEmpty());
    }

    private Map<String, Object> buildStepNode(
            SkillStepDefinition step,
            Path normalizedOutput,
            Map<String, Object> toolsNode,
            List<Map<String, Object>> manualSteps) throws IOException {
        String outputKey = step.outputKey == null || step.outputKey.isBlank() ? step.id : step.outputKey;
        Map<String, Object> stepNode = new LinkedHashMap<>();
        stepNode.put("id", step.id);
        stepNode.put("type", step.type);
        stepNode.put("optional", step.optional);
        stepNode.put("outputKey", outputKey);
        stepNode.put("arguments", step.arguments);

        if ("tool".equals(step.type) && step.toolName != null && !step.toolName.isBlank()) {
            ToolDefinition tool = toolCatalogService.getRequired(step.toolName);
            if (isExportableExternalTool(tool)) {
                Path copiedToolDir = copyToolDirectory(tool, normalizedOutput.resolve("tools").resolve(tool.name));
                stepNode.put("executionMode", "external-tool");
                stepNode.put("toolName", step.toolName);

                Map<String, Object> toolNode = new LinkedHashMap<>();
                toolNode.put("name", tool.name);
                toolNode.put("description", tool.description);
                toolNode.put("program", tool.tool.program);
                toolNode.put("args", tool.tool.args);
                toolNode.put("workingDirectory", tool.tool.workingDirectory == null || tool.tool.workingDirectory.isBlank()
                        ? "."
                        : tool.tool.workingDirectory);
                toolNode.put("toolDir", normalizedOutput.relativize(copiedToolDir).toString().replace('\\', '/'));
                toolsNode.put(tool.name, toolNode);
                return stepNode;
            }

            stepNode.put("executionMode", "manual-tool");
            stepNode.put("toolName", step.toolName);
            stepNode.put("manualReason", manualReasonForTool(tool));
            manualSteps.add(manualStepNode(step, outputKey, "manual-tool", manualReasonForTool(tool)));
            return stepNode;
        }

        if ("ai".equals(step.type)) {
            stepNode.put("executionMode", "manual-unsupported");
            stepNode.put("manualReason", "Legacy ai steps are no longer supported. Migrate this step to toolName=llm before export.");
            manualSteps.add(manualStepNode(step, outputKey, "manual-unsupported", "Legacy ai steps are no longer supported. Migrate this step to toolName=llm before export."));
            return stepNode;
        }

        stepNode.put("executionMode", "manual-unsupported");
        stepNode.put("manualReason", "Unsupported step type for automatic export.");
        manualSteps.add(manualStepNode(step, outputKey, "manual-unsupported", "Unsupported step type for automatic export."));
        return stepNode;
    }

    private Map<String, Object> manualStepNode(SkillStepDefinition step, String outputKey, String mode, String reason) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", step.id);
        node.put("type", step.type);
        node.put("executionMode", mode);
        node.put("outputKey", outputKey);
        node.put("reason", reason);
        node.put("optional", step.optional);
        node.put("arguments", step.arguments);
        if (step.toolName != null && !step.toolName.isBlank()) {
            node.put("toolName", step.toolName);
        }
        if (step.provider != null && !step.provider.isBlank()) {
            node.put("provider", step.provider);
        }
        if (step.systemPrompt != null && !step.systemPrompt.isBlank()) {
            node.put("systemPrompt", step.systemPrompt);
        }
        if (step.userPrompt != null && !step.userPrompt.isBlank()) {
            node.put("userPrompt", step.userPrompt);
        }
        return node;
    }

    private boolean isExportableExternalTool(ToolDefinition tool) {
        return tool.tool != null
                && "external-command".equalsIgnoreCase(tool.tool.type)
                && tool.toolHome != null
                && !tool.toolHome.startsWith("classpath:");
    }

    private String manualReasonForTool(ToolDefinition tool) {
        if (tool.tool == null) {
            return "Tool runtime is missing, so this step must be supplied manually.";
        }
        if (tool.toolHome != null && tool.toolHome.startsWith("classpath:")) {
            return "Built-in tools are not copied into exported workflow packages. Supply this step output manually.";
        }
        return "Only filesystem external-command tools can run directly in exported workflows. Supply this step output manually.";
    }

    private Path copyToolDirectory(ToolDefinition tool, Path destinationDir) throws IOException {
        Path sourceDefinition = Path.of(tool.toolHome).toAbsolutePath().normalize();
        Path sourceDir = Files.isDirectory(sourceDefinition) ? sourceDefinition : sourceDefinition.getParent();
        Files.createDirectories(destinationDir);
        try (var stream = Files.walk(sourceDir)) {
            stream.forEach(path -> {
                try {
                    Path relative = sourceDir.relativize(path);
                    Path target = destinationDir.resolve(relative.toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to copy tool file: " + path, e);
                }
            });
        }
        return destinationDir;
    }

    private String readmeTemplate(String skillName, String target) {
        return """
                # Exported Workflow

                This package was generated from the ai-fix skill `%s`.

                Generated targets:
                - %s

                Files:
                - `workflow.json`: normalized workflow manifest
                - `tools/`: copied external tool directories
                - `main.py`: Python runner when requested
                - `main.go`: Go runner when requested

                Export model:
                - filesystem `external-command` tools are copied and executed directly
                - built-in tools are exported as manual override steps
                - legacy `ai` steps are unsupported and should be migrated to the `llm` tool first
                - use `--step-overrides-json` to provide fixed outputs for manual steps

                Run examples:

                ```bash
                python main.py --input-json "{}" --step-overrides-json "{}"
                ```

                ```bash
                go run . --input-json "{}" --step-overrides-json "{}"
                ```
                """.formatted(skillName, target);
    }

    private String pythonTemplate() {
        return """
                import argparse
                import json
                import os
                import subprocess
                import sys
                from pathlib import Path

                ROOT = Path(__file__).resolve().parent
                WORKFLOW = json.loads((ROOT / "workflow.json").read_text(encoding="utf-8"))

                def resolve_template(value, context):
                    text = "" if value is None else str(value)
                    input_map = context.get("input", {})
                    for key, data in input_map.items():
                        text = text.replace("${input." + key + "}", str(data))
                    for key, data in context.items():
                        if key == "input":
                            continue
                        text = text.replace("${" + key + "}", str(data))
                    return text

                def select_program(program):
                    if str(program).lower() == "python":
                        if os.name == "nt":
                            for candidate in (["python"], ["py", "-3"]):
                                try:
                                    result = subprocess.run(candidate + ["--version"], capture_output=True, text=True)
                                    if result.returncode in (0, 1):
                                        return candidate
                                except OSError:
                                    pass
                        else:
                            for candidate in (["python3"], ["python"]):
                                try:
                                    result = subprocess.run(candidate + ["--version"], capture_output=True, text=True)
                                    if result.returncode in (0, 1):
                                        return candidate
                                except OSError:
                                    pass
                        raise SystemExit("No Python launcher found")
                    return [program]

                def resolve_program(program, tool_dir):
                    if str(program).lower() == "python":
                        return select_program(program)
                    candidate = Path(program)
                    if candidate.is_absolute():
                        return [str(candidate)]
                    if "/" in str(program) or "\\\\" in str(program) or str(program).startswith("."):
                        return [str((tool_dir / candidate).resolve())]
                    return [program]

                def get_override(step, overrides):
                    output_key = step.get("outputKey") or step.get("id")
                    if output_key in overrides:
                        return overrides[output_key]
                    step_id = step.get("id")
                    if step_id in overrides:
                        return overrides[step_id]
                    return None

                def handle_manual_step(step, context, overrides):
                    override = get_override(step, overrides)
                    if override is None:
                        reason = step.get("manualReason", "manual override required")
                        key = step.get("outputKey") or step.get("id")
                        raise SystemExit(
                            "Missing manual override for step '%s' (outputKey=%s). Reason: %s. "
                            "Pass --step-overrides-json with a value for '%s' or '%s'."
                            % (step.get("id"), key, reason, key, step.get("id"))
                        )
                    context[step["outputKey"]] = override

                def main():
                    parser = argparse.ArgumentParser()
                    parser.add_argument("--input-json", default="{}")
                    parser.add_argument("--step-overrides-json", default="{}")
                    args = parser.parse_args()
                    context = {"input": json.loads(args.input_json or "{}")}
                    overrides = json.loads(args.step_overrides_json or "{}")

                    for step in WORKFLOW["skill"]["steps"]:
                        mode = step.get("executionMode", "")
                        if mode.startswith("manual"):
                            handle_manual_step(step, context, overrides)
                            continue

                        tool = WORKFLOW["tools"][step["toolName"]]
                        tool_dir = ROOT / tool["toolDir"]
                        command = resolve_program(tool["program"], tool_dir)
                        for token in tool.get("args", []):
                            rendered = resolve_template(token, context)
                            if "/" in rendered or "\\\\" in rendered or rendered.startswith("."):
                                rendered = str((tool_dir / rendered).resolve()) if not Path(rendered).is_absolute() else str(Path(rendered))
                            command.append(rendered)
                        completed = subprocess.run(command, cwd=str((tool_dir / tool.get("workingDirectory", ".")).resolve()), capture_output=True, text=True)
                        if completed.returncode != 0:
                            if step.get("optional"):
                                context[step["outputKey"]] = ""
                                continue
                            sys.stderr.write(completed.stderr or completed.stdout)
                            raise SystemExit(completed.returncode)
                        context[step["outputKey"]] = (completed.stdout or "").strip()

                    output_key = WORKFLOW["skill"]["steps"][-1]["outputKey"] if WORKFLOW["skill"]["steps"] else ""
                    result = {
                        "skill": WORKFLOW["skill"]["name"],
                        "outputKey": output_key,
                        "output": context.get(output_key, ""),
                        "context": {k: v for k, v in context.items() if k != "input"}
                    }
                    print(json.dumps(result, ensure_ascii=False, indent=2))

                if __name__ == "__main__":
                    main()
                """;
    }

    private String goTemplate() {
        return """
                package main

                import (
                  "encoding/json"
                  "flag"
                  "fmt"
                  "os"
                  "os/exec"
                  "path/filepath"
                  "runtime"
                  "strings"
                )

                type workflow struct {
                  Skill struct {
                    Name string `json:"name"`
                    Steps []step `json:"steps"`
                  } `json:"skill"`
                  Tools map[string]tool `json:"tools"`
                }

                type step struct {
                  Id string `json:"id"`
                  Type string `json:"type"`
                  Optional bool `json:"optional"`
                  ToolName string `json:"toolName"`
                  OutputKey string `json:"outputKey"`
                  Arguments map[string]string `json:"arguments"`
                  ExecutionMode string `json:"executionMode"`
                  ManualReason string `json:"manualReason"`
                }

                type tool struct {
                  Program string `json:"program"`
                  Args []string `json:"args"`
                  WorkingDirectory string `json:"workingDirectory"`
                  ToolDir string `json:"toolDir"`
                }

                func resolveTemplate(text string, context map[string]string, input map[string]string) string {
                  resolved := text
                  for k, v := range input {
                    resolved = strings.ReplaceAll(resolved, "${input."+k+"}", v)
                  }
                  for k, v := range context {
                    resolved = strings.ReplaceAll(resolved, "${"+k+"}", v)
                  }
                  return resolved
                }

                func selectPython() ([]string, error) {
                  candidates := [][]string{{"python3"}, {"python"}}
                  if runtime.GOOS == "windows" {
                    candidates = [][]string{{"python"}, {"py", "-3"}}
                  }
                  for _, candidate := range candidates {
                    cmd := exec.Command(candidate[0], append(candidate[1:], "--version")...)
                    if err := cmd.Run(); err == nil {
                      return candidate, nil
                    }
                  }
                  return nil, fmt.Errorf("no Python launcher found")
                }

                func resolveProgram(program string, toolDir string) ([]string, error) {
                  if strings.EqualFold(program, "python") {
                    return selectPython()
                  }
                  if filepath.IsAbs(program) {
                    return []string{program}, nil
                  }
                  if strings.Contains(program, "/") || strings.Contains(program, "\\\\") || strings.HasPrefix(program, ".") {
                    return []string{filepath.Clean(filepath.Join(toolDir, program))}, nil
                  }
                  return []string{program}, nil
                }

                func getOverride(step step, overrides map[string]string) (string, bool) {
                  if value, ok := overrides[step.OutputKey]; ok {
                    return value, true
                  }
                  if value, ok := overrides[step.Id]; ok {
                    return value, true
                  }
                  return "", false
                }

                func main() {
                  inputJSON := flag.String("input-json", "{}", "input json")
                  overrideJSON := flag.String("step-overrides-json", "{}", "manual step overrides json")
                  flag.Parse()
                  root, _ := os.Getwd()
                  workflowBytes, err := os.ReadFile(filepath.Join(root, "workflow.json"))
                  if err != nil {
                    panic(err)
                  }
                  var wf workflow
                  if err := json.Unmarshal(workflowBytes, &wf); err != nil {
                    panic(err)
                  }
                  input := map[string]string{}
                  if err := json.Unmarshal([]byte(*inputJSON), &input); err != nil {
                    panic(err)
                  }
                  overrides := map[string]string{}
                  if err := json.Unmarshal([]byte(*overrideJSON), &overrides); err != nil {
                    panic(err)
                  }
                  context := map[string]string{}
                  for _, step := range wf.Skill.Steps {
                    if strings.HasPrefix(step.ExecutionMode, "manual") {
                      override, ok := getOverride(step, overrides)
                      if !ok {
                        fmt.Fprintf(os.Stderr, "missing manual override for step '%s' (outputKey=%s): %s\n", step.Id, step.OutputKey, step.ManualReason)
                        os.Exit(1)
                      }
                      context[step.OutputKey] = override
                      continue
                    }

                    tool := wf.Tools[step.ToolName]
                    toolDir := filepath.Join(root, filepath.FromSlash(tool.ToolDir))
                    command, err := resolveProgram(tool.Program, toolDir)
                    if err != nil {
                      panic(err)
                    }
                    for _, token := range tool.Args {
                      rendered := resolveTemplate(token, context, input)
                      if filepath.IsAbs(rendered) {
                        command = append(command, rendered)
                      } else if strings.Contains(rendered, "/") || strings.Contains(rendered, "\\\\") || strings.HasPrefix(rendered, ".") {
                        command = append(command, filepath.Clean(filepath.Join(toolDir, rendered)))
                      } else {
                        command = append(command, rendered)
                      }
                    }
                    workingDir := filepath.Join(toolDir, filepath.FromSlash(tool.WorkingDirectory))
                    cmd := exec.Command(command[0], command[1:]...)
                    cmd.Dir = workingDir
                    output, err := cmd.CombinedOutput()
                    if err != nil {
                      if step.Optional {
                        context[step.OutputKey] = ""
                        continue
                      }
                      fmt.Fprint(os.Stderr, string(output))
                      os.Exit(1)
                    }
                    context[step.OutputKey] = strings.TrimSpace(string(output))
                  }

                  outputKey := ""
                  output := ""
                  if len(wf.Skill.Steps) > 0 {
                    outputKey = wf.Skill.Steps[len(wf.Skill.Steps)-1].OutputKey
                    output = context[outputKey]
                  }
                  result := map[string]any{
                    "skill": wf.Skill.Name,
                    "outputKey": outputKey,
                    "output": output,
                    "context": context,
                  }
                  bytes, err := json.MarshalIndent(result, "", "  ")
                  if err != nil {
                    panic(err)
                  }
                  fmt.Println(string(bytes))
                }
                """;
    }

    public record ExportResult(Path outputDir, Path workflowFile, boolean pythonGenerated, boolean goGenerated, boolean hasManualSteps) {
    }
}


