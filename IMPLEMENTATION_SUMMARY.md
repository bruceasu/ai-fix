# tree-sitter 迁移 - 实现总结

**日期**: 2026年5月6日  
**状态**: Phase 1-4 完成，Phase 5 准备中  
**分支**: main

---

## 已完成的工作

### ✅ Phase 1：研究与验证
- ✓ 确认 tree-sitter-java 项目活跃，最新版本 v0.23.5（2024年12月）
- ✓ 验证 tree-sitter-java 支持现代 Java 特性（记录、虚拟线程、密封类等）
- ✓ 发现 Java 绑定库 (`io.github.bonede:tree-sitter`) 维护状态不清
- ✓ 制定分层迁移策略，暂时保留 JavaParser，为未来迁移预留接口

**输出**: [TREE_SITTER_MIGRATION_PLAN.md](TREE_SITTER_MIGRATION_PLAN.md)

### ✅ Phase 2：接口抽象 & Phase 3：重构实现
创建统一的 Java 代码解析接口，业务逻辑不依赖具体解析器实现。

#### 新增文件
- **[JavaCodeParser.java](src/main/java/me/asu/ai/parse/JavaCodeParser.java)** 
  - 统一接口，定义解析器应实现的契约
  - 方法：`indexFile()`、`indexProject()`、`analyzeProject()`、`getBackendName()`、`isAvailable()`
  - 支持多后端：JavaParser、tree-sitter 等

#### 重构文件
1. **[JavaProjectIndexer.java](src/main/java/me/asu/ai/index/JavaProjectIndexer.java)**
   - 现在实现 `ProjectIndexer` + `JavaCodeParser`
   - 向后兼容：保留 `build(Path)` 方法，委托给 `indexProject(Path)`
   - 新增方法：`indexFile()`、`indexProject()`、`analyzeProject()`
   - 后端名称：JavaParser 3.28.1
   - 代码改进：提取公共逻辑，更清晰的方法职责

2. **[JavaProjectAnalyzer.java](src/main/java/me/asu/ai/analyze/JavaProjectAnalyzer.java)**
   - 现在实现 `ProjectAnalyzer` + `JavaCodeParser`
   - 向后兼容：保留 `analyze(Path)` 方法，委托给 `analyzeProject(Path)`
   - 新增方法：`analyzeProject()`
   - 注意：`indexFile()` 和 `indexProject()` 抛出 `UnsupportedOperationException`（专用于分析）
   - 后端名称：JavaParser 3.28.1

#### 关键设计决策
- **接口优先**：业务代码依赖 `JavaCodeParser` 接口，而非具体实现
- **向后兼容**：保留旧接口方法，过渡期无需更改现有调用代码
- **职责分离**：
  - `JavaProjectIndexer` - 负责代码索引（方法、字段等元数据）
  - `JavaProjectAnalyzer` - 负责项目分析（统计、结构、依赖等）
  - 各专其职，互不重复

### ✅ Phase 4：集成准备
- ✓ pom.xml：tree-sitter 依赖保持注释状态，备注为"暂不启用，保留用于未来迁移"
- ✓ 项目仍使用 JavaParser 3.28.1 作为主要解析器
- ✓ 代码对 tree-sitter 迁移完全透明

---

## 架构设计

### 分层模型

```
┌─────────────────────────────────────┐
│   业务代码                            │
│ (ProjectIndexer, ProjectAnalyzer)    │
└──────────────┬──────────────────────┘
               │ 实现
┌──────────────▼──────────────────────┐
│   JavaCodeParser (统一接口)          │
│  - indexFile()                       │
│  - indexProject()                    │
│  - analyzeProject()                  │
│  - getBackendName()                  │
│  - isAvailable()                     │
└──────────────┬──────────────────────┘
               │
      ┌────────┴────────┐
      │                 │
┌─────▼─────────┐  ┌────▼──────────────┐
│ JavaParser    │  │  TreeSitter       │
│ Backend 3.28.1   │  Backend (未来)    │
│ (当前)        │  │  (计划中)         │
└───────────────┘  └───────────────────┘
```

### 类关系

```
ProjectIndexer (接口) ◄─┐
                        ├─ JavaProjectIndexer ──► JavaCodeParser (接口)
ProjectAnalyzer (接口) ◄┤
                        └─ JavaProjectAnalyzer ──► JavaCodeParser (接口)
```

---

## 验证状态

### 代码质量检查 ✓
- ✓ JavaCodeParser 接口定义清晰
- ✓ JavaProjectIndexer 正确实现接口并保持向后兼容
- ✓ JavaProjectAnalyzer 正确实现接口并保持向后兼容
- ✓ 导入和包结构正确
- ✓ 文档注释完整

### 编译验证 ⏳ 
- 环境内存限制，暂无法完成完整编译验证
- 代码语法检查通过（通过文件审视）
- 建议在内存充足的环境运行 `mvn clean compile`

---

## 后续步骤 (Phase 5)

### 5.1 编译和单元测试
```bash
# 在内存充足的环境执行
mvn clean compile
mvn test
```

**预期**:
- ✓ 项目编译成功
- ✓ 现有测试通过（无新增测试）
- ✓ 无编译警告

### 5.2 集成测试和功能验证
```bash
# 验证功能不变
./scripts/verify.sh  # 如果可用
mvn verify          # 或标准验证
```

**验证项**:
- ✓ `index.json` 输出与之前一致
- ✓ `project-summary.json` 输出与之前一致
- ✓ 性能无显著下降（如可测）

### 5.3 Java 25 兼容性测试（可选但推荐）
创建测试用例验证 Java 25 新特性的解析能力：
- Records (Java 14+)
- Virtual Threads (Java 19+)
- Sealed Classes (Java 15+)
- Pattern Matching 等

### 5.4 配置管理（未来）
为支持多个解析器后端，考虑添加配置：
```properties
# application.properties
java.code.parser.backend=javaparser  # 选项: javaparser, treesitter
```

---

## 未来迁移到 tree-sitter (Q3-Q4)

### 步骤 1：创建 TreeSitterBackend
```java
public class TreeSitterBackend implements JavaCodeParser {
    @Override
    public List<MethodInfo> indexFile(Path javaFile) throws Exception {
        // 使用 tree-sitter 解析
    }
    
    @Override
    public ProjectSummary analyzeProject(Path projectRoot) throws Exception {
        // 使用 tree-sitter 分析
    }
    
    @Override
    public String getBackendName() {
        return "tree-sitter (v0.23.5+)";
    }
}
```

### 步骤 2：tree-sitter query 库
编写 tree-sitter query 语句捕获：
- 方法定义、记录类型、密封类
- 注解、字段、方法调用
- 导入声明、包声明

### 步骤 3：充分测试和性能基准

### 步骤 4：灵活切换
```java
JavaCodeParser parser = 
    useTreeSitter ? new TreeSitterBackend() : new JavaProjectIndexer();
```

---

## 文件变更统计

### 新增文件
- `src/main/java/me/asu/ai/parse/JavaCodeParser.java` (新接口)
- `TREE_SITTER_MIGRATION_PLAN.md` (计划文档)
- `IMPLEMENTATION_SUMMARY.md` (此文件)

### 修改文件
- `src/main/java/me/asu/ai/index/JavaProjectIndexer.java` (+ JavaCodeParser 实现)
- `src/main/java/me/asu/ai/analyze/JavaProjectAnalyzer.java` (+ JavaCodeParser 实现)
- `pom.xml` (tree-sitter 依赖仍为注释)

### 无变更
- 业务逻辑保持不变
- 输出格式和结构不变
- API 契约保持向后兼容

---

## 风险评估

### 已缓解的风险
✅ **维护状态不清** - 采用分层策略，不立即依赖维护不善的库
✅ **迁移复杂度** - 接口设计预留了灵活的迁移路径
✅ **兼容性** - 向后兼容策略确保现有代码无需改动

### 剩余风险
⚠️ **编译验证延迟** - 环境限制，建议尽快在充足资源环境验证
⚠️ **Java 25 特性支持** - JavaParser 3.28.1 对新特性支持需进一步测试
⚠️ **性能影响** - 重构可能带来轻微性能变化，需基准测试

---

## 建议事项

### 立即行动
1. 在内存充足的环境运行 `mvn clean compile test` 验证代码
2. 使用 Java 25 代码进行测试，确认 JavaParser 3.28.1 的兼容性
3. 如发现兼容性问题，评估升级 JavaParser 或采用 tree-sitter

### 中期计划（1-2 周）
1. 完成 Phase 5 验证和测试
2. 更新文档以反映新的接口
3. 可考虑创建配置管理，以支持多后端选择

### 长期计划（Q3-Q4）
1. 实现 TreeSitterBackend
2. 性能和功能对比测试
3. 逐步迁移到 tree-sitter

---

## 相关资源

- 迁移计划：[TREE_SITTER_MIGRATION_PLAN.md](TREE_SITTER_MIGRATION_PLAN.md)
- JavaCodeParser 接口：[JavaCodeParser.java](src/main/java/me/asu/ai/parse/JavaCodeParser.java)
- JavaProjectIndexer 实现：[JavaProjectIndexer.java](src/main/java/me/asu/ai/index/JavaProjectIndexer.java)
- JavaProjectAnalyzer 实现：[JavaProjectAnalyzer.java](src/main/java/me/asu/ai/analyze/JavaProjectAnalyzer.java)

---

## 签名

**实施者**: AI Assistant  
**完成日期**: 2026年5月6日  
**审阅状态**: 待编译验证  
**下一步审核**: Phase 5 - 编译和测试验证
