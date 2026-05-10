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
   

        String[] forwardedArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (command) {
            case "knowledge" -> KnowledgeCli.main(forwardedArgs);
            case "generate-exe" -> GenerateExeCli.main(forwardedArgs);
            case "chat" -> ChatCli.main(forwardedArgs);
            case "init" -> InitCli.main(forwardedArgs);
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
            case "knowledge" -> KnowledgeCli.printUsage();
            case "generate-exe" -> GenerateExeCli.printUsage();
            case "chat" -> ChatCli.printUsage();
            case "init" -> InitCli.printUsage();
            default -> printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar chat [options]
                  java -jar ai-fix-<version>.jar knowledge <subcommand> [options]
                  java -jar ai-fix-<version>.jar generate-exe [options]
                  java -jar ai-fix-<version>.jar init

                Commands:
                  chat        Start an interactive DPEF Agent session (Recommended)
                  knowledge   Browse recorded problem categories and repair experience
                  generate-exe Export a skill as a standalone executable package
                  init        Interactively create config and prompt files

                """);
    }
}
