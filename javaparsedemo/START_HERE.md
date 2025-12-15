# 🚀 从这里开始

欢迎使用 **JavaParser 安全分析工具**！

## ⚡ 快速开始（30秒）

```bash
cd javaparsedemo
./quick-start.sh
```

就这么简单！脚本会自动编译并运行安全分析。

## 📚 项目文档导航

### 🆕 新手入门
1. **START_HERE.md** ← 你在这里
2. **README.md** - 项目概述和功能介绍
3. **quick-start.sh** - 一键启动脚本

### 📖 详细文档
4. **USAGE.md** - 完整使用指南和API参考
5. **DEMO_EXAMPLE.md** - 实战示例和输出演示
6. **PROJECT_SUMMARY.md** - 项目技术总结

### 🔧 运行脚本
7. **quick-start.sh** - 快速启动（推荐）
8. **run-analysis.sh** - 交互式菜单

## 🎯 三个核心工具

### 1️⃣ 安全漏洞分析器 (SecurityAnalyzer)

**作用：** 自动检测6类常见安全漏洞

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"
```

**检测项：**
- IDOR漏洞
- SQL注入
- XSS风险
- 缺少授权检查
- 硬编码凭证
- 路径遍历

**实际效果：** 在springboot-vuln-demo中检测到9个安全问题

---

### 2️⃣ AST可视化工具 (ASTVisualizer)

**作用：** 可视化展示Java代码的抽象语法树

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.ASTVisualizer"
```

**显示内容：**
- 类和方法结构
- 注解信息
- 字段声明
- 继承关系

---

### 3️⃣ 方法复杂度分析器 (MethodAnalyzer)

**作用：** 分析代码质量和复杂度

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer"
```

**分析指标：**
- 圈复杂度
- 代码行数
- 参数数量
- API端点识别

## 🎬 演示视频（文字版）

```
$ ./quick-start.sh

╔════════════════════════════════════════════════════════╗
║     JavaParser 安全分析工具 - 快速开始              ║
╚════════════════════════════════════════════════════════╝

✅ 检测到Maven: Apache Maven 3.8.6

📦 步骤1/3: 编译项目...
✅ 编译成功

🔍 步骤2/3: 运行安全漏洞分析...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

=== JavaParser 安全分析工具 ===

正在分析项目: ../springboot-vuln-demo/src/main/java

分析文件: OrderController.java
分析文件: UserController.java
...

=== 安全分析报告 ===

发现 9 个潜在安全问题:

严重程度统计:
  高: 6
  中: 3
  低: 0

详细问题列表:

1. [高] IDOR漏洞
   文件: OrderController.java
   方法: getOrderById (行号: 22)
   描述: 方法使用了PathVariable但缺少授权检查...
```

## 💡 常见使用场景

### 场景1：代码审查前快速扫描
```bash
# 分析整个项目
./quick-start.sh

# 查看报告，重点关注"高"和"中"级别问题
```

### 场景2：CI/CD集成
```bash
# 在CI脚本中添加
cd javaparsedemo
mvn clean compile
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"
```

### 场景3：学习JavaParser
```bash
# 查看源代码
cat src/main/java/com/security/analyzer/SecurityAnalyzer.java

# 学习如何检测IDOR漏洞
cat DEMO_EXAMPLE.md
```

### 场景4：分析特定目录
```bash
# 只分析controller目录
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" \
  -Dexec.args="/path/to/your/controllers"
```

## ❓ 常见问题

### Q: 编译失败怎么办？
**A:** 确保你有Java 8+和Maven 3.6+
```bash
java -version    # 应该显示 1.8 或更高
mvn -version     # 应该显示 3.6 或更高
```

### Q: 如何分析其他项目？
**A:** 修改SecurityAnalyzer.java中的默认路径，或者传递参数：
```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" \
  -Dexec.args="/your/project/src/main/java"
```

### Q: 检测到的问题都是真实的吗？
**A:** 静态分析工具可能产生误报，需要人工审查确认。

### Q: 可以添加自定义检查规则吗？
**A:** 可以！查看`DEMO_EXAMPLE.md`中的"扩展示例"部分。

## 🎓 学习路径

### 初级（了解功能）
1. ✅ 运行`quick-start.sh`查看效果
2. ✅ 阅读`README.md`了解项目
3. ✅ 查看`DEMO_EXAMPLE.md`理解输出

### 中级（理解原理）
4. 📖 阅读`USAGE.md`学习API
5. 📖 查看源代码理解实现
6. 📖 阅读`PROJECT_SUMMARY.md`掌握技术细节

### 高级（扩展定制）
7. 🔧 添加自定义检查规则
8. 🔧 集成到CI/CD流程
9. 🔧 优化检测逻辑减少误报

## 📊 项目数据

- **代码行数**: ~1200行（3个分析器）
- **检测规则**: 6类安全漏洞
- **文档**: 6份完整文档
- **脚本**: 2个启动脚本
- **Java版本**: 8+
- **JavaParser版本**: 3.25.10

## 🎯 下一步？

选择你感兴趣的：

- 🔍 **立即体验** → 运行`./quick-start.sh`
- 📚 **深入学习** → 阅读`USAGE.md`
- 🎓 **查看示例** → 阅读`DEMO_EXAMPLE.md`
- 🔧 **了解技术** → 阅读`PROJECT_SUMMARY.md`
- 💻 **查看代码** → 浏览`src/main/java/`

## 📞 需要帮助？

1. 查看相应文档（见上方导航）
2. 阅读常见问题部分
3. 查看JavaParser官方文档: https://javaparser.org/

## 🌟 核心优势

✅ **开箱即用** - 一键启动，无需配置  
✅ **精准检测** - 基于AST的语义分析  
✅ **易于扩展** - 清晰的代码结构  
✅ **文档完善** - 详细的使用说明  
✅ **实战验证** - 已检测出真实漏洞  

---

**准备好了吗？** 运行以下命令开始你的JavaParser之旅：

```bash
./quick-start.sh
```

祝你使用愉快！🎉

