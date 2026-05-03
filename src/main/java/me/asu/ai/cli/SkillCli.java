package me.asu.ai.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import me.asu.ai.config.AppConfig;
import me.asu.ai.skill.SkillCatalogService;
import me.asu.ai.skill.SkillDefinition;
import me.asu.ai.skill.SkillExecutionResult;
import me.asu.ai.skill.SkillMarkdownSupport;
import me.asu.ai.skill.SkillOrchestrator;
import me.asu.ai.skill.SkillStepDefinition;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolArgumentDefinition;
import me.asu.ai.tool.ToolExecutor;

public class SkillCli {

    public static void main(String[] args) throws Exception {
        String configPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(configPath);
        SkillCatalogService skillCatalog = new SkillCatalogService(config);
        ToolExecutor toolExecutor = new ToolExecutor(new ToolCatalogService(config), config);

        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        switch (args[0]) {
            case "list" -> {
                for (SkillDefinition skill : skillCatalog.all()) {
                    System.out.println(skill.name + " - " + skill.description);
                }
            }
            case "describe" -> describeSkill(args, skillCatalog);
            case "convert-markdown" -> convertMarkdown(args, toolExecutor, config);
            case "run" -> runSkill(args, skillCatalog, toolExecutor, config);
            default -> printUsage();
        }
    }

    private static void describeSkill(String[] args, SkillCatalogService skillCatalog) {
        String name = findOptionValue(args, "--name");
        if (name == null || name.isBlank()) {
            System.out.println("Missing required option: --name");
            return;
        }
        SkillDefinition skill = skillCatalog.getRequired(name);
        System.out.println("Name: " + skill.name);
        System.out.println("Description: " + skill.description);
        System.out.println("Auto execute allowed: " + skill.autoExecuteAllowed);
        System.out.println("Home: " + skill.skillHome);
        if (skill.markdownHome != null && !skill.markdownHome.isBlank()) {
            System.out.println("Documentation: " + skill.markdownHome);
        }
        System.out.println("Arguments:");
        if (skill.arguments == null || skill.arguments.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (ToolArgumentDefinition argument : skill.arguments) {
                String defaultText = argument.defaultValue == null || argument.defaultValue.isBlank()
                        ? ""
                        : " default=" + argument.defaultValue;
                System.out.println("  - " + argument.name + " [" + argument.type + "] "
                        + argument.description + defaultText);
            }
        }
        System.out.println("Steps:");
        if (skill.steps == null || skill.steps.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (SkillStepDefinition step : skill.steps) {
                StringBuilder line = new StringBuilder("  - ")
                        .append(step.id)
                        .append(" type=")
                        .append(step.type);
                if (step.toolName != null && !step.toolName.isBlank()) {
                    line.append(" tool=").append(step.toolName);
                }
                if (step.outputKey != null && !step.outputKey.isBlank()) {
                    line.append(" outputKey=").append(step.outputKey);
                }
                if (step.optional) {
                    line.append(" optional=true");
                }
                System.out.println(line);
            }
        }
        if (skill.markdownDescription != null && !skill.markdownDescription.isBlank()) {
            System.out.println("Markdown Description:");
            System.out.println(skill.markdownDescription.strip());
        }
    }

    private static void runSkill(
            String[] args,
            SkillCatalogService skillCatalog,
            ToolExecutor toolExecutor,
            AppConfig config) throws Exception {
        SkillOrchestrator orchestrator = new SkillOrchestrator(skillCatalog, toolExecutor, config);

        String name = findOptionValue(args, "--name");
        String inputJson = findOptionValue(args, "--input-json");
        String provider = findOptionValue(args, "--provider");
        String model = findOptionValue(args, "--model");
        String emitYaml = findOptionValue(args, "--emit-yaml");
        if (provider == null || provider.isBlank()) {
            provider = config.get("provider", AppConfig.DEFAULT_PROVIDER);
        }
        if (model == null || model.isBlank()) {
            model = config.get("model", AppConfig.DEFAULT_MODEL);
        }
        boolean confirmed = containsFlag(args, "--confirm");

        SkillExecutionResult result = orchestrator.execute(name, inputJson, confirmed, provider, model, emitYaml);
        if (!result.ok()) {
            System.out.println("Skill failed: " + result.error());
            return;
        }
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.toMap()));
    }

    static void convertMarkdown(String[] args, ToolExecutor toolExecutor, AppConfig config) throws Exception {
        String input = findOptionValue(args, "--input");
        String outputDir = findOptionValue(args, "--output-dir");
        String name = findOptionValue(args, "--name");
        String provider = findOptionValue(args, "--provider");
        String model = findOptionValue(args, "--model");
        boolean noAi = containsFlag(args, "--no-ai");
        if (input == null || input.isBlank()) {
            System.out.println("Missing required option: --input");
            return;
        }
        if (outputDir == null || outputDir.isBlank()) {
            System.out.println("Missing required option: --output-dir");
            return;
        }
        Path inputPath = Path.of(input).toAbsolutePath().normalize();
        Path outputPath = Path.of(outputDir).toAbsolutePath().normalize();
        String effectiveProvider = provider == null || provider.isBlank()
                ? config.get("provider", AppConfig.DEFAULT_PROVIDER)
                : provider;
        String effectiveModel = model == null || model.isBlank()
                ? config.get("model", AppConfig.DEFAULT_MODEL)
                : model;
        SkillMarkdownSupport.ConversionResult result = SkillMarkdownSupport.convertWithAiAssistance(
                inputPath,
                outputPath,
                name,
                toolExecutor,
                effectiveProvider,
                effectiveModel,
                !noAi);
        System.out.println("Generated skill output:");
        System.out.println("  Mode: " + (result.aiGenerated() ? "ai-assisted" : "local-skeleton"));
        System.out.println("  Name: " + result.skillName());
        System.out.println("  SKILL.md: " + result.markdownFile());
        System.out.println("  skill.yaml: " + result.yamlFile());
        if (result.note() != null && !result.note().isBlank()) {
            System.out.println("  Note: " + result.note());
        }
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar skill list
                  java -jar ai-fix-<version>.jar skill describe --name <skillName> [--config <path>]
                  java -jar ai-fix-<version>.jar skill run --name <skillName> [--input-json <json>] [--provider <provider>] [--model <model>] [--emit-yaml <path>] [--confirm] [--config <path>]
                  java -jar ai-fix-<version>.jar skill convert-markdown --input <markdownFile> --output-dir <skillDir> [--name <skillName>] [--provider <provider>] [--model <model>] [--no-ai]
                """);
    }

    private static String findOptionValue(String[] args, String optionName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (optionName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static boolean containsFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
