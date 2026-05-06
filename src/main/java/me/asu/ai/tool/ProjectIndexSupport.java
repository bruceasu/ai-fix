package me.asu.ai.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.asu.ai.fix.FixTextSupport;
import me.asu.ai.io.CodeLoader;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.util.Utils;

public class ProjectIndexSupport {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FixTextSupport textSupport = new FixTextSupport();

    public String explainSymbolInput(String indexPath, String symbol, String container) throws Exception {
        return explainSymbolInput(indexPath, symbol, container, 5);
    }

    public String explainSymbolInput(String indexPath, String symbol, String container, int limit) throws Exception {
        Path path;
        if (indexPath == null || indexPath.isBlank() || "index.json".equals(indexPath)) {
            path = Utils.findFileUpwards("index.json");
        } else {
            path = Path.of(indexPath).toAbsolutePath().normalize();
        }

        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("index.json not found. Run 'index' command first.");
        }

        List<MethodInfo> methods = mapper.readValue(path.toFile(), new TypeReference<List<MethodInfo>>() {
        });
        List<MethodInfo> matches = methods.stream()
                .filter(method -> matches(method, symbol, container))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No matching symbol found in index: " + symbol);
        }
        if (matches.size() > 1 && (container == null || container.isBlank())) {
            return formatCandidates(matches, limit);
        }
        MethodInfo target = matches.get(0);

        String code = CodeLoader.loadSymbol(target, null);
        return """
                [target]
                language=%s
                container=%s
                symbol=%s
                file=%s:%d-%d

                [code]
                %s
                """.formatted(
                textSupport.safe(target.language),
                textSupport.displayContainer(target),
                textSupport.displaySymbol(target),
                textSupport.safe(target.file),
                target.beginLine,
                target.endLine,
                code);
    }

    private String formatCandidates(List<MethodInfo> matches, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("[candidates]\n");
        for (int i = 0; i < Math.min(Math.max(1, limit), matches.size()); i++) {
            MethodInfo method = matches.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(textSupport.displayQualifiedTarget(method))
                    .append(" | ")
                    .append(textSupport.safe(method.file))
                    .append(":")
                    .append(method.beginLine)
                    .append("-")
                    .append(method.endLine)
                    .append("\n");
        }
        if (matches.size() > limit) {
            sb.append("... and ").append(matches.size() - limit).append(" more candidates\n");
        }
        sb.append("\nProvide container to narrow the target.");
        return sb.toString();
    }

    private boolean matches(MethodInfo method, String symbol, String container) {
        boolean symbolMatch = symbol == null || symbol.isBlank()
                || textSupport.displaySymbol(method).equalsIgnoreCase(symbol)
                || textSupport.displayQualifiedTarget(method).equalsIgnoreCase(symbol);
        boolean containerMatch = container == null || container.isBlank()
                || textSupport.displayContainer(method).equalsIgnoreCase(container);
        return symbolMatch && containerMatch;
    }
}
