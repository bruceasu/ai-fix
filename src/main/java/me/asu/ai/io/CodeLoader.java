
package me.asu.ai.io;

import me.asu.ai.config.AppConfig;
import me.asu.ai.model.MethodInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CodeLoader {

    public static String loadMethod(MethodInfo m, AppConfig config) throws Exception {
        String projectRoot = m.projectRoot == null || m.projectRoot.isBlank() ? "." : m.projectRoot;
        Path root = Paths.get(projectRoot).toAbsolutePath().normalize();
        List<String> lines = Files.readAllLines(root.resolve(m.file).normalize());
        return String.join("\n",
                lines.subList(m.beginLine - 1, m.endLine));
    }
}
