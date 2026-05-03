package me.asu.ai.index;

import me.asu.ai.analyze.ProjectLanguage;

public final class ProjectIndexerFactory {

    private ProjectIndexerFactory() {
    }

    public static ProjectIndexer create(ProjectLanguage language) {
        return switch (language) {
            case GO -> new GoProjectIndexer();
            case PYTHON -> new PythonProjectIndexer();
            case JAVA, UNKNOWN -> new JavaProjectIndexer();
        };
    }
}
