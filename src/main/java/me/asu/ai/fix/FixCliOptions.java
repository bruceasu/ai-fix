package me.asu.ai.fix;

import me.asu.ai.config.AppConfig;

public class FixCliOptions {

    static final int DEFAULT_INTERACTIVE_PAGE_SIZE = 10;
    static final int DEFAULT_PREVIEW_LINES = 6;

    public String task;
    public String matchQuery = "";
    public String methodName;
    public String symbolName;
    public String packageFilter;
    public String classFilter;
    public String containerFilter;
    public String fileFilter;
    public String callFilter;
    public String annotationFilter;
    public String branch;
    public String commitMessage;
    public String projectSummaryPath;
    public String provider;
    public String model;
    public String verifyCmd;

    public int maxRetry = 2;
    public int limit = 3;
    public int pageSize = DEFAULT_INTERACTIVE_PAGE_SIZE;
    public int previewLines = DEFAULT_PREVIEW_LINES;
    public boolean dryRun = false;
    public boolean noSelect = false;
    public boolean printConfigOnly = false;
    public boolean useStash = false;
    public boolean suggestOnly = false;

    public static FixCliOptions parse(String[] args, AppConfig config) {
        FixCliOptions options = new FixCliOptions();
        options.provider = config.get("provider", "openai");
        options.model = config.get("model", "gpt-5-mini");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--task" -> options.task = args[++i];
                case "--match" -> options.matchQuery = options.matchQuery + " " + args[++i];
                case "--method" -> options.methodName = args[++i];
                case "--symbol" -> options.symbolName = args[++i];
                case "--package" -> options.packageFilter = args[++i];
                case "--class" -> options.classFilter = args[++i];
                case "--container" -> options.containerFilter = args[++i];
                case "--file" -> options.fileFilter = args[++i];
                case "--call" -> options.callFilter = args[++i];
                case "--annotation" -> options.annotationFilter = args[++i];
                case "--limit" -> options.limit = Integer.parseInt(args[++i]);
                case "--page-size" -> options.pageSize = Integer.parseInt(args[++i]);
                case "--preview-lines" -> options.previewLines = Integer.parseInt(args[++i]);
                case "--branch" -> options.branch = args[++i];
                case "--commit-message" -> options.commitMessage = args[++i];
                case "--model" -> options.model = args[++i];
                case "--provider" -> options.provider = args[++i];
                case "--verify-cmd" -> options.verifyCmd = args[++i];
                case "--project-summary" -> options.projectSummaryPath = args[++i];
                case "--config" -> i++;
                case "--dry-run" -> options.dryRun = true;
                case "--suggest-only" -> options.suggestOnly = true;
                case "--no-select" -> options.noSelect = true;
                case "--print-config" -> options.printConfigOnly = true;
                case "--stash" -> options.useStash = true;
                default -> options.matchQuery = options.matchQuery + " " + args[i];
            }
        }
        return options;
    }

    public void normalize() {
        if (pageSize <= 0) {
            System.out.println("--page-size must be greater than 0, use the default value: "
                    + DEFAULT_INTERACTIVE_PAGE_SIZE);
            pageSize = DEFAULT_INTERACTIVE_PAGE_SIZE;
        }
        if (previewLines <= 0) {
            System.out.println("--preview-lines must be greater than 0, use the default value: "
                    + DEFAULT_PREVIEW_LINES);
            previewLines = DEFAULT_PREVIEW_LINES;
        }
        if (matchQuery != null) {
            matchQuery = matchQuery.trim();
        }
    }

     public static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar ai-fix-<version>.jar fix --task "<task>" [options]

                Options:
                  --task <text>               Describe the change; omit or use - to enter interactively
                  --match <words>             Fuzzy match package/class/method/file words
                  --method <methodName>       Filter by method name or fragment
                  --symbol <symbolName>       Filter by language-neutral symbol name or fragment
                  --package <packageName>     Filter by package name or fragment
                  --class <className>         Filter by class name or fragment
                  --container <name>          Filter by language-neutral container/module/class name
                  --file <pathFragment>       Filter by source file path fragment
                  --call <methodCallName>     Filter by called method name
                  --annotation <annotation>   Filter by annotation name
                  --limit <number>            Limit matched methods
                  --page-size <number>        Interactive candidate count per page
                  --preview-lines <number>    Candidate preview line count
                  --branch <branchName>       Use a custom git branch
                  --commit-message <message>  Use a custom git commit message
                  --provider <provider>       openai | groq | ollama
                  --model <modelName>         Override configured model
                  --verify-cmd <command>      Override the default verification command
                  --config <path>             Load an explicit config file
                  --project-summary <path>    Load project summary context JSON
                  --no-select                 Do not prompt when multiple methods match
                  --suggest-only              Generate structured repair advice only, never modify files
                  --print-config              Print resolved config and exit
                  --dry-run                   Generate patch only, do not apply
                  --stash                     Auto-stash local changes before applying
                  --help                      Show this help
                  --config-debug              Print config loading summary

                Git behavior:
                  - when inside a Git repository and the working tree is clean, fix auto-creates a new branch before modifying files
                  - when uncommitted files are detected, fix does not modify the working tree and falls back to suggestion-only mode
                  - suggestion-only mode prints structured repair advice instead of applying a patch

                Interactive selection:
                  When multiple methods match, you can:
                  - enter numbers like 1 or 1,3 to choose candidates
                  - enter * to choose all currently matched results
                  - enter b to go back to the previous filtered list
                  - enter n / p to move to the next / previous page
                  - enter g <page> to jump to a specific page
                  - enter q to quit without selecting
                  - press Enter to use the first N matches
                  - enter more words to narrow the list again
                """);
    }

    
   
    public static boolean isConfigDebugEnabled(AppConfig config, String[] args) {
        for (String arg : args) {
            if ("--config-debug".equals(arg)) {
                return true;
            }
        }
        return "true".equalsIgnoreCase(config.get("config.debug"))
                || "1".equals(config.get("config.debug"));
    }


}
