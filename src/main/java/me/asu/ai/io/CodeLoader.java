
package me.asu.ai.io;

import me.asu.ai.config.AppConfig;
import me.asu.ai.model.MethodInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CodeLoader {

    public static String loadMethod(MethodInfo m, AppConfig config) throws Exception {
        return loadSymbol(m, config);
    }

    public static String loadSymbol(MethodInfo m, AppConfig config) throws Exception {
        String projectRoot = m.projectRoot == null || m.projectRoot.isBlank() ? "." : m.projectRoot;
        Path root = Paths.get(projectRoot).toAbsolutePath().normalize();
        List<String> lines = Files.readAllLines(root.resolve(m.file).normalize());
        int start = Math.max(0, m.beginLine - 1);
        int endExclusive = Math.min(lines.size(), Math.max(m.endLine, m.beginLine));
        return String.join("\n", lines.subList(start, endExclusive));
    }
}
