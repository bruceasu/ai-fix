package me.asu.ai.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import me.asu.ai.model.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

/**
 * Java 25 兼容性测试
 * 
 * 验证 JavaParser 3.28.1 对 Java 25 新特性的解析能力
 */
public class Java25CompatibilityTest {

    private JavaProjectIndexer indexer;
    private Path testResourcesDir;

    @BeforeEach
    public void setUp() {
        indexer = new JavaProjectIndexer();
        testResourcesDir = Paths.get("src/test/resources/java25-samples");
        
        // 创建测试资源目录（如不存在）
        if (!Files.exists(testResourcesDir)) {
            try {
                Files.createDirectories(testResourcesDir);
            } catch (Exception e) {
                System.err.println("Warning: Could not create test resources directory: " + e.getMessage());
            }
        }
    }

    /**
     * 测试 1：验证 Record 类型的解析
     * Java 14+ 特性
     */
    @Test
    public void testRecordParsing() throws Exception {
        String recordCode = """
package me.asu.ai.test;

public record Person(String name, int age) {
    public Person {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative");
        }
    }
    
    public String displayInfo() {
        return name + " is " + age + " years old";
    }
}
""";
        
        Path recordFile = createTempJavaFile("TestRecord", recordCode);
        List<MethodInfo> methods = indexer.indexFile(recordFile);
        
        assertNotNull(methods, "Methods list should not be null");
        assertFalse(methods.isEmpty(), "Record should have generated method info");
        
        // 验证是否能识别到方法
        boolean hasDisplayInfo = methods.stream()
            .anyMatch(m -> "displayInfo".equals(m.methodName));
        assertTrue(hasDisplayInfo, "Should parse displayInfo method from record");
        
        System.out.println("✓ Record parsing test passed");
    }

    /**
     * 测试 2：验证 Sealed Class 的解析
     * Java 15+ 特性
     */
    @Test
    public void testSealedClassParsing() throws Exception {
        String sealedCode = """
package me.asu.ai.test;

public sealed class Shape permits Circle, Rectangle, Triangle {
    protected double area;
    
    abstract double calculateArea();
    
    public void printArea() {
        System.out.println("Area: " + area);
    }
}

final class Circle extends Shape {
    private double radius;
    
    @Override
    double calculateArea() {
        return Math.PI * radius * radius;
    }
}

final class Rectangle extends Shape {
    private double width, height;
    
    @Override
    double calculateArea() {
        return width * height;
    }
}

final class Triangle extends Shape {
    private double base, height;
    
    @Override
    double calculateArea() {
        return 0.5 * base * height;
    }
}
""";
        
        Path sealedFile = createTempJavaFile("TestSealed", sealedCode);
        List<MethodInfo> methods = indexer.indexFile(sealedFile);
        
        assertNotNull(methods, "Methods list should not be null");
        
        // 验证是否能识别到密封类中的方法
        boolean hasPrintArea = methods.stream()
            .anyMatch(m -> "printArea".equals(m.methodName));
        assertTrue(hasPrintArea, "Should parse printArea method from sealed class");
        
        // 验证是否能识别到子类中的方法
        boolean hasCalculateArea = methods.stream()
            .anyMatch(m -> "calculateArea".equals(m.methodName));
        assertTrue(hasCalculateArea, "Should parse calculateArea methods from sealed subclasses");
        
        System.out.println("✓ Sealed class parsing test passed");
    }

    /**
     * 测试 3：验证 Pattern Matching 的解析
     * Java 16+ 特性（增强中）
     */
    @Test
    public void testPatternMatchingParsing() throws Exception {
        String patternCode = """
package me.asu.ai.test;

public class PatternMatcher {
    
    public String describeObject(Object obj) {
        return switch (obj) {
            case Integer i && i > 0 -> "Positive integer: " + i;
            case Integer i -> "Non-positive integer: " + i;
            case String s && !s.isEmpty() -> "Non-empty string: " + s;
            case String s -> "Empty string";
            case null -> "Null object";
            default -> "Unknown object type";
        };
    }
    
    public int getLength(Object obj) {
        if (obj instanceof String s) {
            return s.length();
        } else if (obj instanceof Integer i) {
            return i;
        }
        return -1;
    }
}
""";
        
        Path patternFile = createTempJavaFile("TestPattern", patternCode);
        List<MethodInfo> methods = indexer.indexFile(patternFile);
        
        assertNotNull(methods, "Methods list should not be null");
        
        // 验证是否能识别到使用 pattern matching 的方法
        boolean hasDescribeObject = methods.stream()
            .anyMatch(m -> "describeObject".equals(m.methodName));
        assertTrue(hasDescribeObject, "Should parse method with pattern matching");
        
        boolean hasGetLength = methods.stream()
            .anyMatch(m -> "getLength".equals(m.methodName));
        assertTrue(hasGetLength, "Should parse method with instanceof pattern");
        
        System.out.println("✓ Pattern matching parsing test passed");
    }

    /**
     * 测试 4：验证 Text Blocks 的解析
     * Java 13+ 特性
     */
    @Test
    public void testTextBlocksParsing() throws Exception {
        String textBlockCode = """
package me.asu.ai.test;

public class TextBlockExample {
    
    private static final String JSON = \"\"\"
        {
            "name": "John",
            "age": 30,
            "city": "New York"
        }
        \"\"\";
    
    public String getJsonQuery() {
        return \"\"\"
            SELECT * FROM users
            WHERE age > 18
            ORDER BY name
            \"\"\";
    }
    
    public String getXmlContent() {
        return \"\"\"
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <user>John</user>
                <age>30</age>
            </root>
            \"\"\";
    }
}
""";
        
        Path textBlockFile = createTempJavaFile("TestTextBlock", textBlockCode);
        List<MethodInfo> methods = indexer.indexFile(textBlockFile);
        
        assertNotNull(methods, "Methods list should not be null");
        
        // 验证是否能识别到使用 text blocks 的方法
        boolean hasGetJsonQuery = methods.stream()
            .anyMatch(m -> "getJsonQuery".equals(m.methodName));
        assertTrue(hasGetJsonQuery, "Should parse method with text blocks");
        
        System.out.println("✓ Text blocks parsing test passed");
    }

    /**
     * 测试 5：综合测试 - Java 25 特性混合使用
     */
    @Test
    public void testJava25MixedFeaturesComplexClass() throws Exception {
        String complexCode = """
package me.asu.ai.test;

import java.util.Optional;
import java.util.List;

public sealed class DataProcessor permits JsonProcessor, XmlProcessor {
    
    public record ProcessResult(String status, Object data, long timestamp) {
        public ProcessResult {
            if (timestamp < 0) {
                throw new IllegalArgumentException("Invalid timestamp");
            }
        }
    }
    
    abstract ProcessResult process(String input);
    
    public String analyzeResult(ProcessResult result) {
        return switch (result.status) {
            case "success" -> "Processing completed: " + result.data;
            case "error" -> "Processing failed";
            case null -> "Unknown status";
            default -> "Unhandled status: " + result.status;
        };
    }
}

final class JsonProcessor extends DataProcessor {
    @Override
    ProcessResult process(String input) {
        return new DataProcessor.ProcessResult("success", "{...}", System.currentTimeMillis());
    }
}

final class XmlProcessor extends DataProcessor {
    @Override
    ProcessResult process(String input) {
        return new DataProcessor.ProcessResult("success", "<.../>", System.currentTimeMillis());
    }
}
""";
        
        Path complexFile = createTempJavaFile("TestComplex", complexCode);
        List<MethodInfo> methods = indexer.indexFile(complexFile);
        
        assertNotNull(methods, "Methods list should not be null");
        assertFalse(methods.isEmpty(), "Complex class should generate method info");
        
        // 验证关键方法都被识别
        boolean hasProcess = methods.stream()
            .anyMatch(m -> "process".equals(m.methodName));
        assertTrue(hasProcess, "Should parse process method");
        
        boolean hasAnalyzeResult = methods.stream()
            .anyMatch(m -> "analyzeResult".equals(m.methodName));
        assertTrue(hasAnalyzeResult, "Should parse analyzeResult method");
        
        System.out.println("✓ Complex Java 25 features test passed");
        System.out.println("  Total methods found: " + methods.size());
    }

    /**
     * 辅助方法：创建临时 Java 文件
     */
    private Path createTempJavaFile(String className, String content) throws Exception {
        Path file = testResourcesDir.resolve(className + ".java");
        Files.write(file, content.getBytes());
        return file;
    }

    /**
     * 验证基本的索引功能
     */
    @Test
    public void testBackendAvailability() {
        assertTrue(indexer.isAvailable(), "JavaParser backend should be available");
        assertEquals("JavaParser 3.28.1", indexer.getBackendName(), "Backend name should be JavaParser 3.28.1");
        System.out.println("✓ Backend availability test passed - Backend: " + indexer.getBackendName());
    }
}
