package me.asu.ai.analyze;

public final class ProjectAnalyzerFactory {

    private ProjectAnalyzerFactory() {
    }

    public static ProjectAnalyzer create(ProjectLanguage language) {
        return switch (language) {
            case GO -> new GoProjectAnalyzer();
            case PYTHON -> new PythonProjectAnalyzer();
            case JAVA, UNKNOWN -> new JavaProjectAnalyzer();
        };
    }
}
