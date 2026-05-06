# tree-sitter 迁移计划（修订版）

**日期**: 2026年5月6日  
**状态**: 规划阶段  
**决策**: 分阶段迁移，暂时保留 JavaParser，为 tree-sitter 迁移预留接口

---

## 1. 问题分析

### 原始问题
- JavaParser 3.28.1 对 Java 25 新特性支持不足
- 某些语法解析错误率高

### 根本原因调查
- JavaParser 是纯 Java 实现，维护新特性支持需要时间
- 某些边界情况可能 JavaParser 语法定义不完整

### tree-sitter 评估
✅ **优势**
- 官方 tree-sitter-java 语法活跃维护（v0.23.5, 2024年12月）
- 支持现代 Java 特性（记录、密封类、虚拟线程等）
- 增量解析，性能更优
- 成熟的 query 语言用于 AST 模式匹配

❌ **劣势**
- Java 绑定库维护状态不清（io.github.bonede:tree-sitter 库对应 GitHub 已删除）
- 引入外部二进制依赖（tree-sitter CLI）增加部署复杂度
- 跨平台兼容性需测试
- 学习曲线较陡

---

## 2. 修订策略：分层迁移

### 第 1 阶段：接口抽象（立即开始）
创建统一的 Java 代码解析接口，使业务逻辑不依赖具体解析器实现。

```java
// 新增接口，作为抽象层
public interface JavaCodeParser {
    List<MethodInfo> indexFile(Path javaFile) throws IOException;
    ProjectSummary analyzeProject(Path projectRoot) throws IOException;
}

// 现有实现（保留）
public class JavaParserBackend implements JavaCodeParser {
    // 当前 JavaParser 实现
}

// 未来实现（预留接口）
public class TreeSitterBackend implements JavaCodeParser {
    // 将来的 tree-sitter 实现
}
```

### 第 2 阶段：验证 JavaParser 兼容性（本周）
1. 测试 JavaParser 3.28.1 对 Java 25 代码的解析能力
2. 找出具体的兼容性问题
3. 尝试升级 JavaParser 到最新版本（目前 3.28.1 已是最新）
4. 如果有特定的解析失败，分析是否是语法定义问题

### 第 3 阶段：短期修复（可选）
如果 JavaParser 有已知的 Java 25 兼容性问题：
- 修复特定的语法解析规则
- 或创建兼容层处理未支持的特性
- 或升级到下一个主版本（如有发布）

### 第 4 阶段：长期迁移到 tree-sitter（Q3-Q4）
一旦第 2-3 阶段稳定：
1. 实现 TreeSitterBackend
2. 创建详细的 tree-sitter query 语句库
3. 充分测试和性能基准测试
4. 逐步替换 JavaParserBackend

---

## 3. 立即行动项

### 任务 3.1：创建 JavaCodeParser 接口
**文件**: `src/main/java/me/asu/ai/parse/JavaCodeParser.java`  
**目标**: 定义统一的 Java 代码解析接口  
**预期时间**: 30 分钟

### 任务 3.2：重构 JavaProjectIndexer
**文件**: [JavaProjectIndexer.java](src/main/java/me/asu/ai/index/JavaProjectIndexer.java)  
**目标**: 实现 JavaCodeParser 接口，支持可插拔解析器  
**预期时间**: 1 小时

### 任务 3.3：重构 JavaProjectAnalyzer  
**文件**: [JavaProjectAnalyzer.java](src/main/java/me/asu/ai/analyze/JavaProjectAnalyzer.java)  
**目标**: 实现 JavaCodeParser 接口，支持可插拔解析器  
**预期时间**: 1 小时

### 任务 3.4：创建 Java 25 兼容性测试
**文件**: `src/test/java/me/asu/ai/parse/Java25CompatibilityTest.java`  
**目标**: 测试 Java 25 新特性的解析能力  
**覆盖范围**:
- 记录（Records）
- 虚拟线程（Virtual Threads）
- 密封类（Sealed Classes）
- 模式匹配（Pattern Matching）

**预期时间**: 1.5 小时

### 任务 3.5：基准测试
**目标**: 性能对比（JavaParser vs 其他方案）  
**预期时间**: 1 小时

---

## 4. tree-sitter 迁移的具体实施（第 4 阶段）

### 4.1 TreeSitterBackend 实现步骤
1. 研究 tree-sitter-java 的 query 语言
2. 编写 tree-sitter query 库（文件、方法、注解等）
3. 实现 AST 遍历逻辑
4. 处理字节偏移 → 行列号转换
5. 实现错误处理和恢复机制

### 4.2 Query 示例
```sql
-- 捕获方法定义
(method_declaration
  (modifiers)? @modifiers
  (type_parameters)? @type_params
  (type) @return_type
  name: (identifier) @method_name
  parameters: (formal_parameters) @parameters
  (throws)? @throws
  body: (block)? @body)
  
-- 捕获记录定义（Java 14+）
(record_declaration
  name: (identifier) @record_name
  components: (record_formal_parameters) @components)

-- 捕获注解
(annotation
  name: (identifier) @annotation_name
  arguments: (annotation_argument_list)? @annotation_args)
```

### 4.3 性能优化考虑
- tree-sitter 增量解析能力
- 并发处理大型项目
- 缓存策略

---

## 5. 配置管理

### 环境变量或配置文件
```properties
# application.properties
java.code.parser.backend=javaparser  # 选项: javaparser, treesitter
java.code.parser.tree-sitter.path=/usr/bin/tree-sitter  # tree-sitter 二进制路径
```

### 依赖管理
- **当前**: JavaParser 3.28.1（保留）
- **备选**: io.github.bonede:tree-sitter 0.20.8（暂时注释）
- **未来**: 可能需要其他 tree-sitter Java 绑定库

---

## 6. 风险管理

### 风险 1：Java 25 特性不被支持
**概率**: 中等  
**影响**: 高  
**缓解**: 确认 JavaParser 3.28.1 是否真的有这个问题，可能已修复

### 风险 2：tree-sitter Java 绑定库不稳定
**概率**: 中等  
**影响**: 高  
**缓解**: 考虑使用 tree-sitter-cli + JSON 解析替代，或等待官方 Java 绑定

### 风险 3：性能下降
**概率**: 低  
**影响**: 中等  
**缓解**: 进行充分的性能基准测试，tree-sitter 通常性能更好

### 风险 4：跨平台兼容性
**概率**: 中等  
**影响**: 中等  
**缓解**: 在 Windows, Linux, macOS 上充分测试

---

## 7. 验收标准

✅ **完成条件**:
1. JavaCodeParser 接口定义清晰，业务逻辑依赖接口而非具体实现
2. Java 25 兼容性测试通过率 > 95%
3. 性能指标不低于当前 JavaParser 方案
4. 完整的文档说明如何切换解析器
5. tree-sitter 迁移路径清晰，文档详尽

---

## 8. 后续改进建议

1. **监控和告警** - 添加解析失败的监控，快速发现新问题
2. **性能优化** - 分析瓶颈，考虑缓存、并行化等
3. **扩展到其他语言** - Go、Python 等语言也采用统一接口
4. **开源贡献** - 如果发现 JavaParser 或 tree-sitter 的 bug，考虑向上游贡献修复

---

## 9. 时间表

| 阶段 | 任务 | 时间 | 优先级 |
|------|------|------|-------|
| 1 | 接口抽象 | 1-2 天 | 高 |
| 2 | JavaParser 兼容性验证 | 1 天 | 高 |
| 3 | 短期修复（如需） | 1-3 天 | 中 |
| 4 | tree-sitter 迁移（计划） | Q3-Q4 | 中 |

---

## 10. 决策记录

**日期**: 2026年5月6日  
**决策者**: AI Assistant  
**决策**: 
- 暂时保留 JavaParser 作为主要实现
- 创建统一的 JavaCodeParser 接口以支持多后端
- 验证 JavaParser 3.28.1 对 Java 25 的实际支持情况
- 计划 Q3-Q4 正式迁移到 tree-sitter

**原因**:
1. 降低短期风险，避免引入维护不善的库
2. 允许灵活的过渡路径
3. 保持工程质量和系统稳定性
4. 为长期改进奠定基础
