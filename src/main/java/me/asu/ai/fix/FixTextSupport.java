package me.asu.ai.fix;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import me.asu.ai.model.MethodInfo;

public class FixTextSupport {

    public String inferPackageName(MethodInfo method) {
        if (method != null && method.packageName != null && !method.packageName.isBlank()) {
            return method.packageName;
        }
        if (method == null || method.file == null || method.file.isBlank()) {
            return "";
        }
        String normalized = normalizePath(method.file);
        int srcIndex = normalized.indexOf("/java/");
        String packagePath = srcIndex >= 0
                ? normalized.substring(srcIndex + "/java/".length())
                : normalized;
        int slashIndex = packagePath.lastIndexOf('/');
        if (slashIndex < 0) {
            return "";
        }
        return packagePath.substring(0, slashIndex).replace('/', '.');
    }

    public String displayContainer(MethodInfo method) {
        if (method == null) {
            return "";
        }
        if (method.containerName != null && !method.containerName.isBlank()) {
            return method.containerName;
        }
        if (method.className != null && !method.className.isBlank()) {
            return method.className;
        }
        return inferPackageName(method);
    }

    public String displaySymbol(MethodInfo method) {
        if (method == null) {
            return "";
        }
        if (method.symbolName != null && !method.symbolName.isBlank()) {
            return method.symbolName;
        }
        return safe(method.methodName);
    }

    public String displayQualifiedTarget(MethodInfo method) {
        String container = displayContainer(method);
        String symbol = displaySymbol(method);
        if (container.isBlank()) {
            return symbol;
        }
        if (symbol.isBlank()) {
            return container;
        }
        return container + "#" + symbol;
    }

    public List<String> splitTerms(String matchQuery) {
        return List.of(matchQuery.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .stream()
                .filter(term -> !term.isBlank())
                .collect(Collectors.toList());
    }

    public String highlightTerms(String text, List<String> terms) {
        if (text == null || text.isEmpty() || terms == null || terms.isEmpty()) {
            return safe(text);
        }
        String highlighted = text;
        List<String> sortedTerms = terms.stream()
                .filter(term -> term != null && !term.isBlank())
                .distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(Collectors.toList());
        for (String term : sortedTerms) {
            highlighted = highlightTerm(highlighted, term);
        }
        return highlighted;
    }

    public String safe(String value) {
        return value == null ? "" : value;
    }

    private String highlightTerm(String text, String term) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerTerm = term.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int found = lowerText.indexOf(lowerTerm, index);
            if (found < 0) {
                sb.append(text.substring(index));
                break;
            }
            sb.append(text, index, found);
            sb.append("[[").append(text, found, found + term.length()).append("]]");
            index = found + term.length();
        }
        return sb.toString();
    }

    private String normalizePath(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }
}
