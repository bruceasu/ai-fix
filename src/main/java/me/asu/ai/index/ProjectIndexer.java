package me.asu.ai.index;

import java.nio.file.Path;
import java.util.List;
import me.asu.ai.model.MethodInfo;

public interface ProjectIndexer {

    /**
     * @param root project root directory
     * @return list of method information
     * @throws Exception if any error occurs during indexing
     */
    List<MethodInfo> build(Path root) throws Exception;
}
