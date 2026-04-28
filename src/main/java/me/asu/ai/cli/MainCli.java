package me.asu.ai.cli;

import java.util.Arrays;

public class MainCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0];
        if (isHelpCommand(command)) {
            if (args.length > 1) {
                printCommandUsage(args[1]);
            } else {
                printUsage();
            }
            return;
        }
        if (command.startsWith("--")) {
            AiFixCLI.main(args);
            return;
        }

        String[] forwardedArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (command) {
            case "fix" -> AiFixCLI.main(forwardedArgs);
            case "index" -> IndexCli.main(forwardedArgs);
            case "understand" -> AiUnderstandCLI.main(forwardedArgs);
            case "init" -> InitCli.main(forwardedArgs);
            default -> {
                System.out.println("Unknown command: " + command);
                printUsage();
            }
        }
    }

    private static boolean isHelpCommand(String command) {
        return "help".equals(command) || "--help".equals(command) || "-h".equals(command);
    }

    private static void printCommandUsage(String command) {
        switch (command) {
            case "fix" -> AiFixCLI.printUsage();
            case "index" -> IndexCli.printUsage();
            case "understand" -> AiUnderstandCLI.printUsage();
            case "init" -> InitCli.printUsage();
            default -> printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar fix --task "<task>" [options]
                  java -jar ai-fix-<version>.jar index [source-root]
                  java -jar ai-fix-<version>.jar understand [source-root] [options]
                  java -jar ai-fix-<version>.jar init

                Commands:
                  fix         Run AI patch generation and application flow
                  index       Build index.json from Java source files
                  understand  Generate project understanding report and summary
                  init        Interactively create config and prompt files

                Compatibility:
                  java -jar ai-fix-<version>.jar --task "<task>" [options]
                  This form is forwarded to the fix command.
                """);
    }
}
