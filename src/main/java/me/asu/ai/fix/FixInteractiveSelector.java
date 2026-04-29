package me.asu.ai.fix;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import me.asu.ai.config.AppConfig;
import me.asu.ai.model.MethodInfo;

public class FixInteractiveSelector {

    private final FixTargetMatcher matcher;
    private final int pageSize;
    private final FixPreviewRenderer renderer;

    public FixInteractiveSelector(AppConfig config, int pageSize, int previewLines, FixTargetMatcher matcher) {
        this.pageSize = pageSize;
        this.matcher = matcher;
        this.renderer = new FixPreviewRenderer(config, previewLines, matcher);
    }

    public List<MethodInfo> selectTargets(List<MethodInfo> targets, int limit, String matchQuery) {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        FixSelectionState state = new FixSelectionState(targets, pageSize, matchQuery);

        while (true) {
            state.clampPage();
            int totalPages = state.totalPages();
            List<MethodInfo> visibleTargets = state.visibleTargets();

            System.out.println("Matched multiple methods. Select target numbers separated by comma.");
            System.out.println("Showing page " + (state.currentPage() + 1) + "/" + totalPages
                    + ", items " + (state.pageStart() + 1) + "-" + state.pageEnd()
                    + " of " + state.currentTargets().size());
            for (int i = 0; i < visibleTargets.size(); i++) {
                int displayIndex = state.pageStart() + i + 1;
                System.out.println(renderer.formatCandidateLine(displayIndex, visibleTargets.get(i), state.currentQuery()));
                System.out.println(renderer.buildPreviewBlock(visibleTargets.get(i), state.currentQuery()));
            }
            System.out.println("Press Enter to use the first " + Math.min(limit, state.currentTargets().size()) + " matches.");
            System.out.println("Type * to use all currently matched results.");
            System.out.println("Type b to go back to the previous filtered list.");
            if (totalPages > 1) {
                System.out.println("Type n for next page, p for previous page.");
                System.out.println("Type g <page> to jump to a specific page.");
            }
            System.out.println("Type q to quit without selecting.");
            System.out.println("Or type more keywords to narrow the list, for example: cli main usage");
            System.out.print("Selection or filter: ");

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                return state.currentTargets().stream().limit(limit).collect(Collectors.toList());
            }
            if ("q".equalsIgnoreCase(line)) {
                return List.of();
            }
            if ("*".equals(line)) {
                return new ArrayList<>(state.currentTargets());
            }
            if ("b".equalsIgnoreCase(line)) {
                if (!state.canGoBack()) {
                    System.out.println("Already at the initial result set.");
                    continue;
                }
                state.goBack();
                continue;
            }
            if ("n".equalsIgnoreCase(line)) {
                if (state.isLastPage()) {
                    System.out.println("Already at the last page.");
                } else {
                    state.nextPage();
                }
                continue;
            }
            if ("p".equalsIgnoreCase(line)) {
                if (state.isFirstPage()) {
                    System.out.println("Already at the first page.");
                } else {
                    state.previousPage();
                }
                continue;
            }
            if (isGotoPageCommand(line)) {
                int targetPage = parseGotoPage(line);
                if (!state.jumpToPage(targetPage)) {
                    System.out.println("Page out of range: " + targetPage);
                }
                continue;
            }

            if (looksLikeSelection(line)) {
                List<MethodInfo> selected = parseSelection(line, state.currentTargets(), limit);
                if (!selected.isEmpty()) {
                    return selected;
                }
                System.out.println("No valid selection. Try again.");
                continue;
            }

            List<MethodInfo> refined = matcher.applyMatchQuery(state.currentTargets(), line);
            if (refined.isEmpty()) {
                System.out.println("No matches for filter: " + line);
                continue;
            }
            state.refine(refined, line);
            if (state.currentTargets().size() == 1) {
                System.out.println("Refined to a single match.");
                return state.currentTargets();
            }
        }
    }

    private List<MethodInfo> parseSelection(String line, List<MethodInfo> targets, int limit) {
        List<MethodInfo> selected = new ArrayList<>();
        for (String token : line.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int index = Integer.parseInt(trimmed);
            if (index < 1 || index > targets.size()) {
                System.out.println("Skip invalid selection: " + trimmed);
                continue;
            }
            MethodInfo candidate = targets.get(index - 1);
            if (!selected.contains(candidate)) {
                selected.add(candidate);
            }
        }
        return selected.stream().limit(limit).collect(Collectors.toList());
    }

    private boolean looksLikeSelection(String line) {
        for (String token : line.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.matches("\\d+")) {
                return false;
            }
        }
        return true;
    }

    private boolean isGotoPageCommand(String line) {
        return line != null && line.trim().matches("(?i)g\\s+\\d+");
    }

    private int parseGotoPage(String line) {
        String[] parts = line.trim().split("\\s+");
        return Integer.parseInt(parts[1]);
    }
}
