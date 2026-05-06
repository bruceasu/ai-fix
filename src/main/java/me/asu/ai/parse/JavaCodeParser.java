package me.asu.ai.parse;

import java.nio.file.Path;
import java.util.List;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;

/**
 * 统一的 Java 代码解析接口。
 *
 * 此接口定义了代码索引和项目分析的通用契约，允许多种解析器后端实现（JavaParser、tree-sitter 等）。
 * 业务逻辑不依赖具体解析器实现，便于未来切换或多后端支持。
 *
 * @since 1.0
 */
public interface JavaCodeParser {

    /**
     * 索引单个 Java 文件，提取方法、字段、注解等元数据。
     *
     * @param javaFile Java 源文件路径
     * @return 包含文件中所有方法/构造函数的元数据列表
     * @throws Exception 如果解析失败或文件读取错误
     */
    List<MethodInfo> indexFile(Path javaFile) throws Exception;

    /**
     * 索引整个项目，生成所有 Java 文件的方法元数据。
     *
     * @param projectRoot 项目根目录
     * @return 项目中所有 Java 文件的方法元数据列表
     * @throws Exception 如果遍历或解析失败
     */
    List<MethodInfo> indexProject(Path projectRoot) throws Exception;

    /**
     * 分析项目结构和统计信息。
     *
     * @param projectRoot 项目根目录
     * @return 项目总结，包含包数、类数、接口数、方法数、依赖等信息
     * @throws Exception 如果分析失败
     */
    ProjectSummary analyzeProject(Path projectRoot) throws Exception;

    /**
     * 返回此解析器后端的名称（用于日志和调试）。
     *
     * @return 后端名称，如 "JavaParser 3.28.1" 或 "tree-sitter Java"
     */
    String getBackendName();

    /**
     * 验证解析器是否可用（例如，检查必需的二进制文件或库）。
     *
     * @return 如果解析器可用则返回 true，否则返回 false
     */
    boolean isAvailable();
}
