package me.asu.ai.fix;

import me.asu.ai.config.AppConfig;
import me.asu.ai.util.Utils;

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
