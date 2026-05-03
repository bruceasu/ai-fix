package me.asu.ai.index;

import java.nio.file.Path;
import java.util.List;
import me.asu.ai.model.MethodInfo;

public interface ProjectIndexer {

    List<MethodInfo> build(Path root) throws Exception;
}
