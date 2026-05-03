package me.asu.ai.analyze;

import java.nio.file.Path;
import me.asu.ai.model.ProjectSummary;

public interface ProjectAnalyzer {

    ProjectSummary analyze(Path root) throws Exception;
}
