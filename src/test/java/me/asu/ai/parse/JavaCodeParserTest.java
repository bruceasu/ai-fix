package me.asu.ai.parse;

import me.asu.ai.index.JavaProjectIndexer;
import me.asu.ai.analyze.JavaProjectAnalyzer;
import me.asu.ai.model.MethodInfo;
import me.asu.ai.model.ProjectSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * JavaCodeParser 接口的单元测试
 * 
 * 验证：
 * 1. 接口实现的正确性
 * 2. 多后端的可互换性
 * 3. 向后兼容性
 */
public class JavaCodeParserTest {

    private JavaProjectIndexer indexer;
    private JavaProjectAnalyzer analyzer;
    private Path testProject;

    @BeforeEach
    public void setUp() throws Exception {
        indexer = new JavaProjectIndexer();
        analyzer = new JavaProjectAnalyzer();
        
        // 创建测试项目目录结构
        testProject = Paths.get(System.getProperty("java.io.tmpdir"))
            .resolve("test-project-" + System.currentTimeMillis());
        
        if (!Files.exists(testProject)) {
            Files.createDirectories(testProject.resolve("src/main/java/me/asu/ai/test"));
        }
    }

    /**
     * 测试 1：验证 JavaProjectIndexer 实现 JavaCodeParser 接口
     */
    @Test
    public void testIndexerImplementsInterface() {
        assertTrue(indexer instanceof JavaCodeParser, "Indexer should implement JavaCodeParser");
        assertNotNull(indexer.getBackendName(), "Backend name should not be null");
        assertTrue(indexer.isAvailable(), "Indexer should be available");
        System.out.println("✓ Indexer correctly implements JavaCodeParser interface");
    }

    /**
     * 测试 2：验证 JavaProjectAnalyzer 实现 JavaCodeParser 接口
     */
    @Test
    public void testAnalyzerImplementsInterface() {
        assertTrue(analyzer instanceof JavaCodeParser, "Analyzer should implement JavaCodeParser");
        assertNotNull(analyzer.getBackendName(), "Backend name should not be null");
        assertTrue(analyzer.isAvailable(), "Analyzer should be available");
        System.out.println("✓ Analyzer correctly implements JavaCodeParser interface");
    }

    /**
     * 测试 3：验证 indexFile 方法
     */
    @Test
    public void testIndexFileMethod() throws Exception {
        String javaCode = """
package me.asu.ai.test;

public class SampleClass {
    public void method1() {
        System.out.println("Test");
    }
    
    public int method2() {
        return 42;
    }
}
""";
        
        Path sampleFile = testProject.resolve("src/main/java/me/asu/ai/test/SampleClass.java");
        Files.createDirectories(sampleFile.getParent());
        Files.writeString(sampleFile, javaCode);
        
        List<MethodInfo> methods = indexer.indexFile(sampleFile);
        
        assertNotNull(methods, "indexFile should return non-null list");
        assertFalse(methods.isEmpty(), "indexFile should return method info");
        assertEquals(2, methods.size(), "Should find 2 methods");
        
        System.out.println("✓ indexFile method test passed");
    }

    /**
     * 测试 4：验证 indexProject 方法
     */
    @Test
    public void testIndexProjectMethod() throws Exception {
        // 创建测试类
        String class1Code = """
package me.asu.ai.test;
public class Class1 {
    public void method1() {}
}
""";
        
        String class2Code = """
package me.asu.ai.test;
public class Class2 {
    public void method2() {}
    public void method3() {}
}
""";
        
        Path srcDir = testProject.resolve("src/main/java/me/asu/ai/test");
        Files.createDirectories(srcDir);
        
        Files.writeString(srcDir.resolve("Class1.java"), class1Code);
        Files.writeString(srcDir.resolve("Class2.java"), class2Code);
        
        List<MethodInfo> allMethods = indexer.indexProject(testProject);
        
        assertNotNull(allMethods, "indexProject should return non-null list");
        assertTrue(allMethods.size() >= 3, "Should find at least 3 methods");
        
        System.out.println("✓ indexProject method test passed - Found " + allMethods.size() + " methods");
    }

    /**
     * 测试 5：验证 analyzeProject 方法
     */
    @Test
    public void testAnalyzeProjectMethod() throws Exception {
        // 创建测试类
        String controllerCode = """
package me.asu.ai.test.controller;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    public void getUser() {}
}
""";
        
        String serviceCode = """
package me.asu.ai.test.service;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    public void createUser() {}
}
""";
        
        Files.createDirectories(testProject.resolve("src/main/java/me/asu/ai/test/controller"));
        Files.createDirectories(testProject.resolve("src/main/java/me/asu/ai/test/service"));
        
        Files.writeString(testProject.resolve("src/main/java/me/asu/ai/test/controller/UserController.java"), controllerCode);
        Files.writeString(testProject.resolve("src/main/java/me/asu/ai/test/service/UserService.java"), serviceCode);
        
        ProjectSummary summary = analyzer.analyzeProject(testProject);
        
        assertNotNull(summary, "analyzeProject should return non-null ProjectSummary");
        assertEquals("java", summary.primaryLanguage, "Primary language should be java");
        assertTrue(summary.javaFileCount >= 2, "Should find at least 2 Java files");
        
        System.out.println("✓ analyzeProject method test passed");
        System.out.println("  Java files: " + summary.javaFileCount);
        System.out.println("  Controllers: " + summary.controllerCount);
        System.out.println("  Services: " + summary.serviceCount);
    }

    /**
     * 测试 6：验证 getBackendName 方法
     */
    @Test
    public void testGetBackendName() {
        String indexerBackend = indexer.getBackendName();
        String analyzerBackend = analyzer.getBackendName();
        
        assertTrue(indexerBackend.contains("JavaParser"), "Should indicate JavaParser backend");
        assertTrue(analyzerBackend.contains("JavaParser"), "Should indicate JavaParser backend");
        
        System.out.println("✓ getBackendName test passed");
        System.out.println("  Indexer backend: " + indexerBackend);
        System.out.println("  Analyzer backend: " + analyzerBackend);
    }

    /**
     * 测试 7：验证 isAvailable 方法
     */
    @Test
    public void testIsAvailable() {
        assertTrue(indexer.isAvailable(), "JavaParser should always be available");
        assertTrue(analyzer.isAvailable(), "JavaParser should always be available");
        System.out.println("✓ isAvailable test passed");
    }

    /**
     * 测试 8：验证不支持的操作抛出异常
     */
    @Test
    public void testUnsupportedOperations() {
        assertThrows(UnsupportedOperationException.class, () -> {
            analyzer.indexFile(Paths.get("dummy.java"));
        }, "Analyzer should not support indexFile");
        
        assertThrows(UnsupportedOperationException.class, () -> {
            analyzer.indexProject(Paths.get("."));
        }, "Analyzer should not support indexProject");
        
        assertThrows(UnsupportedOperationException.class, () -> {
            indexer.analyzeProject(Paths.get("."));
        }, "Indexer should not support analyzeProject");
        
        System.out.println("✓ Unsupported operations test passed");
    }

    /**
     * 测试 9：验证向后兼容性 - build() 方法
     */
    @Test
    public void testBackwardCompatibility() throws Exception {
        // 创建测试类
        String testCode = """
package me.asu.ai.test;
public class LegacyClass {
    public void legacyMethod() {}
}
""";
        
        Path srcDir = testProject.resolve("src/main/java/me/asu/ai/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("LegacyClass.java"), testCode);
        
        // 使用旧接口 build() 方法
        List<MethodInfo> resultFromBuild = indexer.build(testProject);
        
        // 使用新接口 indexProject() 方法
        List<MethodInfo> resultFromIndexProject = indexer.indexProject(testProject);
        
        assertEquals(resultFromBuild.size(), resultFromIndexProject.size(), 
            "build() and indexProject() should return same results");
        
        System.out.println("✓ Backward compatibility test passed");
    }

    /**
     * 测试 10：验证 ProjectAnalyzer 向后兼容性 - analyze() 方法
     */
    @Test
    public void testAnalyzerBackwardCompatibility() throws Exception {
        // 创建测试类
        String testCode = """
package me.asu.ai.test;
public class AnalysisClass {
    public void method1() {}
}
""";
        
        Path srcDir = testProject.resolve("src/main/java/me/asu/ai/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("AnalysisClass.java"), testCode);
        
        // 使用旧接口 analyze() 方法
        ProjectSummary resultFromAnalyze = analyzer.analyze(testProject);
        
        // 使用新接口 analyzeProject() 方法
        ProjectSummary resultFromAnalyzeProject = analyzer.analyzeProject(testProject);
        
        assertEquals(resultFromAnalyze.javaFileCount, resultFromAnalyzeProject.javaFileCount,
            "analyze() and analyzeProject() should return same results");
        
        System.out.println("✓ Analyzer backward compatibility test passed");
    }
}
