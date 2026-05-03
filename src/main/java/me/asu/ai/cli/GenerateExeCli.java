package me.asu.ai.cli;

import java.nio.file.Path;
import me.asu.ai.config.AppConfig;
import me.asu.ai.export.WorkflowExporter;
import me.asu.ai.skill.SkillCatalogService;
import me.asu.ai.tool.ToolCatalogService;

public class GenerateExeCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || containsHelpFlag(args)) {
            printUsage();
            return;
        }
        String configPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(configPath);
        String skillName = findOptionValue(args, "--skill");
        String output = findOptionValue(args, "--output");
        String target = findOptionValue(args, "--target");
        if (target == null || target.isBlank()) {
            target = "python";
        }
        if (skillName == null || skillName.isBlank()) {
            System.out.println("Missing required option: --skill");
            return;
        }
        if (output == null || output.isBlank()) {
            System.out.println("Missing required option: --output");
            return;
        }
        if (!"python".equals(target) && !"go".equals(target) && !"both".equals(target)) {
            System.out.println("Unsupported target: " + target + ". Use python, go, or both.");
            return;
        }

        WorkflowExporter exporter = new WorkflowExporter(
                new SkillCatalogService(config),
                new ToolCatalogService(config));
        WorkflowExporter.ExportResult result = exporter.export(skillName, Path.of(output), target);

        System.out.println("Exported workflow package:");
        System.out.println("- skill: " + skillName);
        System.out.println("- output: " + result.outputDir().toString().replace('\\', '/'));
        System.out.println("- workflow: " + result.workflowFile().toString().replace('\\', '/'));
        System.out.println("- python: " + result.pythonGenerated());
        System.out.println("- go: " + result.goGenerated());
        System.out.println("- manual overrides required: " + result.hasManualSteps());
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar generate-exe --skill <skillName> --output <dir> [--target <python|go|both>] [--config <path>]

                Notes:
                  - Python is the default export target
                  - filesystem external-command tools are copied into the output package
                  - built-in tool steps, including llm tool steps, are exported as manual override slots
                  - legacy ai steps are no longer supported and should be migrated to toolName=llm before export
                  - use --step-overrides-json in the exported runner to provide fixed step outputs
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

    private static boolean containsHelpFlag(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
