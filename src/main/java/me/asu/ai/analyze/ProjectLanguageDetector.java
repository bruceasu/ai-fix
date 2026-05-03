package me.asu.ai.analyze;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectLanguageDetector {

    private ProjectLanguageDetector() {
    }

    public static ProjectLanguage detect(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalizedRoot.resolve("go.mod"))) {
            return ProjectLanguage.GO;
        }
        if (Files.isRegularFile(normalizedRoot.resolve("pyproject.toml"))
                || Files.isRegularFile(normalizedRoot.resolve("requirements.txt"))
                || Files.isRegularFile(normalizedRoot.resolve("setup.py"))) {
            return ProjectLanguage.PYTHON;
        }
        if (Files.isRegularFile(normalizedRoot.resolve("pom.xml"))
                || Files.isRegularFile(normalizedRoot.resolve("build.gradle"))
                || Files.isRegularFile(normalizedRoot.resolve("build.gradle.kts"))) {
            return ProjectLanguage.JAVA;
        }

        int javaCount = countByExtension(normalizedRoot, ".java");
        int goCount = countByExtension(normalizedRoot, ".go");
        int pyCount = countByExtension(normalizedRoot, ".py");

        if (javaCount >= goCount && javaCount >= pyCount && javaCount > 0) {
            return ProjectLanguage.JAVA;
        }
        if (goCount >= javaCount && goCount >= pyCount && goCount > 0) {
            return ProjectLanguage.GO;
        }
        if (pyCount > 0) {
            return ProjectLanguage.PYTHON;
        }
        return ProjectLanguage.UNKNOWN;
    }

    public static ProjectLanguage parseOrDetect(String languageName, Path root) throws IOException {
        if (languageName == null || languageName.isBlank() || "auto".equalsIgnoreCase(languageName)) {
            return detect(root);
        }
        return switch (languageName.trim().toLowerCase()) {
            case "java" -> ProjectLanguage.JAVA;
            case "go", "golang" -> ProjectLanguage.GO;
            case "python", "py" -> ProjectLanguage.PYTHON;
            default -> throw new IllegalArgumentException("Unsupported language: " + languageName);
        };
    }

    private static int countByExtension(Path root, String extension) throws IOException {
        try (var stream = Files.walk(root)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(extension))
                    .limit(2000)
                    .count();
        }
    }
}
