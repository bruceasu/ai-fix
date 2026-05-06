package me.asu.ai.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MainCli {

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

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
            AiFixCli.main(args);
            return;
        }

        String[] forwardedArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (command) {
            case "analyze" -> AiAnalyzeCLI.main(forwardedArgs);
            case "fix" -> AiFixCli.main(forwardedArgs);
            case "index" -> IndexCli.main(forwardedArgs);
            case "tool" -> ToolCli.main(forwardedArgs);
            case "skill" -> SkillCli.main(forwardedArgs);
            case "knowledge" -> KnowledgeCli.main(forwardedArgs);
            case "generate-exe" -> GenerateExeCli.main(forwardedArgs);
            case "chat" -> ChatCli.main(forwardedArgs);
            case "understand" -> AiUnderstandCli.main(forwardedArgs);
            case "init" -> InitCli.main(forwardedArgs);
            case "match" -> MatchFunctionalityCli.main(forwardedArgs);
            default -> {
                System.out.println("Unknown command: " + command);
                System.out.println("Use 'help' to see available commands.");
                // fall back to chat mode for unrecognized commands
                ChatCli.main(forwardedArgs);
            }
        }
    }

    private static boolean isHelpCommand(String command) {
        return "help".equals(command) || "--help".equals(command) || "-h".equals(command);
    }

    private static void printCommandUsage(String command) {
        switch (command) {
            case "analyze" -> AiAnalyzeCLI.printUsage();
            case "fix" -> AiFixCli.printUsage();
            case "index" -> IndexCli.printUsage();
            case "tool" -> ToolCli.printUsage();
            case "skill" -> SkillCli.printUsage();
            case "knowledge" -> KnowledgeCli.printUsage();
            case "generate-exe" -> GenerateExeCli.printUsage();
            case "chat" -> ChatCli.printUsage();
            case "understand" -> AiUnderstandCli.printUsage();
            case "init" -> InitCli.printUsage();
            default -> printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar fix --task "<task>" [options]
                  java -jar ai-fix-<version>.jar analyze [text] [options]
                  java -jar ai-fix-<version>.jar index [source-root]
                  java -jar ai-fix-<version>.jar tool <subcommand> [options]
                  java -jar ai-fix-<version>.jar skill <subcommand> [options]
                  java -jar ai-fix-<version>.jar knowledge <subcommand> [options]
                  java -jar ai-fix-<version>.jar generate-exe [options]
                  java -jar ai-fix-<version>.jar chat [options]
                  java -jar ai-fix-<version>.jar understand [source-root] [options]
                  java -jar ai-fix-<version>.jar init

                Commands:
                  analyze     Analyze logs, exceptions, or test output
                  fix         Run AI patch generation and application flow
                  index       Build index.json from Java source files
                  tool        List or run registered tools
                  skill       List or run registered skills
                  knowledge   Browse recorded problem categories and repair experience
                  generate-exe Export a fixed workflow package from one skill
                  chat        Start an interactive AI session
                  understand  Generate project understanding report and summary
                  init        Interactively create config and prompt files

                Compatibility:
                  java -jar ai-fix-<version>.jar --task "<task>" [options]
                  This form is forwarded to the fix command.
                """);
    }
}
