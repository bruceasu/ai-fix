package me.asu.ai.cli;

import me.asu.ai.config.AppConfig;
import me.asu.ai.tool.ToolCatalogService;
import me.asu.ai.tool.ToolDefinition;
import me.asu.ai.tool.ToolExecutionResult;
import me.asu.ai.tool.ToolExecutor;

public class ToolCli {

    public static void main(String[] args) throws Exception {
        String configPath = findOptionValue(args, "--config");
        AppConfig config = AppConfig.load(configPath);
        ToolCatalogService catalog = new ToolCatalogService(config);
        ToolExecutor executor = new ToolExecutor(catalog, config);

        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        switch (args[0]) {
            case "list" -> {
                for (ToolDefinition tool : catalog.all()) {
                    System.out.println(tool.name + " - " + tool.description);
                }
            }
            case "describe" -> {
                String name = findOptionValue(args, "--name");
                if (name == null || name.isBlank()) {
                    System.out.println("Missing required option: --name");
                    return;
                }
                printToolDetails(catalog.getRequired(name));
            }
            case "run" -> {
                String name = findOptionValue(args, "--name");
                String argsJson = findOptionValue(args, "--args-json");
                boolean confirmed = containsFlag(args, "--confirm");
                ToolExecutionResult result = executor.execute(name, argsJson, confirmed);
                if (!result.ok()) {
                    System.out.println("Tool failed: " + result.error());
                    return;
                }
                System.out.println(result.output());
            }
            default -> printUsage();
        }
    }

    public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar tool list
                  java -jar ai-fix-<version>.jar tool describe --name <toolName> [--config <path>]
                  java -jar ai-fix-<version>.jar tool run --name <toolName> [--args-json <json>] [--confirm] [--config <path>]
                """);
    }

    private static void printToolDetails(ToolDefinition tool) {
        System.out.println("Name: " + tool.name);
        System.out.println("Description: " + safe(tool.description));
        System.out.println("Auto execute allowed: " + tool.autoExecuteAllowed);
        System.out.println("Type: " + safe(tool.tool.type));
        if (tool.tool.command != null && !tool.tool.command.isBlank()) {
            System.out.println("Builtin command: " + tool.tool.command);
        }
        if (tool.tool.program != null && !tool.tool.program.isBlank()) {
            System.out.println("Program: " + tool.tool.program);
        }
        if (tool.tool.workingDirectory != null && !tool.tool.workingDirectory.isBlank()) {
            System.out.println("Working directory: " + tool.tool.workingDirectory);
        }
        System.out.println("Home: " + safe(tool.toolHome));
        System.out.println("Arguments:");
        if (tool.arguments == null || tool.arguments.isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (var argument : tool.arguments) {
                String defaultValue = argument.defaultValue == null || argument.defaultValue.isBlank()
                        ? ""
                        : " default=" + argument.defaultValue;
                System.out.println("  - " + argument.name
                        + " : " + safe(argument.type)
                        + " required=" + argument.required
                        + defaultValue);
                if (argument.description != null && !argument.description.isBlank()) {
                    System.out.println("    " + argument.description);
                }
            }
        }
        if (tool.tool.args != null && !tool.tool.args.isEmpty()) {
            System.out.println("Runtime args:");
            for (String arg : tool.tool.args) {
                System.out.println("  - " + arg);
            }
        }
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

    private static String safe(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }
}
