package me.asu.ai.fix;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import me.asu.ai.config.AppConfig;
import me.asu.ai.io.CodeLoader;
import me.asu.ai.model.MethodInfo;

public class FixPreviewRenderer {

    private final AppConfig config;
    private final int previewLines;
    private final FixTargetMatcher matcher;
    private final FixTextSupport textSupport = new FixTextSupport();

    public FixPreviewRenderer(AppConfig config, int previewLines, FixTargetMatcher matcher) {
        this.config = config;
        this.previewLines = previewLines;
        this.matcher = matcher;
    }

    public String formatCandidateLine(int index, MethodInfo method, String currentQuery) {
        String scorePart = "";
        List<String> terms = textSupport.splitTerms(currentQuery);
        if (currentQuery != null && !currentQuery.isBlank()) {
            int score = matcher.scoreMethod(method, terms);
            scorePart = "  score=" + score;
        }
        return String.format(
                Locale.ROOT,
                "%d. %s#%s%s  signature=%s  package=%s  file=%s  lines=%d-%d",
                index,
                textSupport.highlightTerms(textSupport.safe(method.className), terms),
                textSupport.highlightTerms(textSupport.safe(method.methodName), terms),
                scorePart,
                textSupport.highlightTerms(textSupport.safe(method.signature), terms),
                textSupport.highlightTerms(textSupport.safe(textSupport.inferPackageName(method)), terms),
                textSupport.highlightTerms(textSupport.safe(method.file), terms),
                method.beginLine,
                method.endLine);
    }

    public String buildPreviewBlock(MethodInfo method, String currentQuery) {
        try {
            String code = CodeLoader.loadMethod(method, config);
            List<String> terms = textSupport.splitTerms(currentQuery);
            List<String> allLines = code.lines().collect(Collectors.toList());
            PreviewWindow window = selectPreviewWindow(allLines, terms);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < window.lines().size(); i++) {
                int absoluteLine = method.beginLine + window.startIndex() + i;
                String line = window.lines().get(i).stripTrailing();
                line = textSupport.highlightTerms(line, terms);
                sb.append(String.format(Locale.ROOT, "    %4d | %s", absoluteLine, line));
                if (i + 1 < window.lines().size()) {
                    sb.append("\n");
                }
            }
            if (allLines.size() > window.lines().size()) {
                sb.append("\n    ...");
            }
            return sb.toString();
        } catch (Exception e) {
            return "    [preview unavailable: " + e.getMessage() + "]";
        }
    }

    private PreviewWindow selectPreviewWindow(List<String> allLines, List<String> terms) {
        if (allLines.isEmpty()) {
            return new PreviewWindow(0, List.of());
        }
        if (allLines.size() <= previewLines) {
            return new PreviewWindow(0, allLines);
        }

        int hitLine = findFirstMatchingLine(allLines, terms);
        if (hitLine < 0) {
            int end = Math.min(previewLines, allLines.size());
            return new PreviewWindow(0, allLines.subList(0, end));
        }

        int before = Math.max(0, previewLines / 2);
        int start = Math.max(0, hitLine - before);
        int end = Math.min(allLines.size(), start + previewLines);
        start = Math.max(0, end - previewLines);
        return new PreviewWindow(start, allLines.subList(start, end));
    }

    private int findFirstMatchingLine(List<String> allLines, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < allLines.size(); i++) {
            String line = textSupport.safe(allLines.get(i)).toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (line.contains(term.toLowerCase(Locale.ROOT))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private record PreviewWindow(int startIndex, List<String> lines) {
    }
}
