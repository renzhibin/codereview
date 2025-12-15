# JavaParser 安全分析 Demo

这是一个使用 [JavaParser](https://github.com/javaparser/javaparser) 库分析 Java 代码的演示项目，专门用于分析 `springboot-vuln-demo` 项目中的潜在安全漏洞。

## 功能特性

### 1. SecurityAnalyzer - 安全漏洞分析器
自动检测以下安全问题：
- ✅ **IDOR漏洞** (Insecure Direct Object Reference)
  - 检测使用 `@PathVariable` 但缺少授权检查的方法
  - 识别可能被未授权用户访问的资源
  
- ✅ **缺少授权检查**
  - 检测敏感操作（delete、update、modify）缺少授权注解
  - 验证是否存在运行时权限检查
  
- ✅ **SQL注入风险**
  - 检测使用字符串拼接构建SQL查询的代码
  - 识别潜在的SQL注入漏洞点
  
- ✅ **路径遍历漏洞**
  - 检测文件操作方法
  - 提醒需要验证文件路径

### 2. ASTVisualizer - AST 可视化工具
可视化展示 Java 源代码的抽象语法树结构：
- 📦 包和导入信息
- 🏛️ 类和接口定义
- 📋 字段声明
- 🔧 方法签名和注解
- 📊 代码结构概览

### 3. MethodAnalyzer - 方法复杂度分析器
分析方法的质量指标：
- **圈复杂度** (Cyclomatic Complexity)
- **代码行数** (Lines of Code)
- **参数数量**
- **方法调用次数**
- **API端点识别**

## 项目结构

```
javaparsedemo/
├── pom.xml                                    # Maven配置文件
├── README.md                                  # 项目说明文档
└── src/
    └── main/
        └── java/
            └── com/
                └── security/
                    └── analyzer/
                        ├── SecurityAnalyzer.java      # 主安全分析器
                        ├── ASTVisualizer.java         # AST可视化工具
                        └── MethodAnalyzer.java        # 方法复杂度分析器
```

## 技术栈

- **Java**: 17
- **JavaParser**: 3.27.1
  - `javaparser-core`: 核心解析功能
  - `javaparser-symbol-solver-core`: 符号解析和类型推断
- **Maven**: 构建工具
- **SLF4J**: 日志框架

## 快速开始

### 1. 编译项目

```bash
cd javaparsedemo
mvn clean compile
```

### 2. 运行安全分析器

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"
```

### 3. 运行 AST 可视化工具

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.ASTVisualizer"
```

### 4. 运行方法复杂度分析器

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer"
```

## 使用示例

### 安全分析输出示例

```
=== JavaParser 安全分析工具 ===

正在分析项目: ../springboot-vuln-demo/src/main/java

分析文件: UserController.java
分析文件: OrderController.java
分析文件: User.java

=== 安全分析报告 ===

发现 3 个潜在安全问题:

严重程度统计:
  高: 2
  中: 1
  低: 0

详细问题列表:

1. [高] IDOR漏洞
   文件: OrderController.java
   方法: getOrder (行号: 25)
   描述: 方法使用了PathVariable但缺少授权检查，可能存在IDOR漏洞

2. [高] IDOR漏洞
   文件: UserController.java
   方法: updateUser (行号: 42)
   描述: 方法使用了PathVariable但缺少授权检查，可能存在IDOR漏洞

3. [中] 缺少授权检查
   文件: OrderController.java
   方法: deleteOrder (行号: 58)
   描述: 敏感操作方法缺少授权注解或运行时权限检查

=== 分析完成 ===
```

### 方法分析输出示例

```
=== 方法分析报告 ===

共分析 15 个方法

复杂度最高的前10个方法:

文件                           方法名                         复杂度      行数        参数数      调用数    
----------------------------------------------------------------------------------------------------
OrderController.java          getOrder                      5          12         1          3         
UserController.java           updateUser                    4          15         2          4         
DataInitializer.java          init                          3          20         0          8         

统计信息:
  API端点数量: 8
  平均复杂度: 2.35
  平均方法长度: 12.8
```

## JavaParser 核心概念

### 1. CompilationUnit (编译单元)
代表一个完整的 Java 源文件，包含包声明、导入语句和类型声明。

```java
CompilationUnit cu = StaticJavaParser.parse(filePath);
```

### 2. Visitor 模式
JavaParser 使用 Visitor 模式遍历 AST：

```java
cu.accept(new VoidVisitorAdapter<Void>() {
    @Override
    public void visit(MethodDeclaration method, Void arg) {
        // 处理方法声明
    }
}, null);
```

### 3. 常用 AST 节点类型
- `ClassOrInterfaceDeclaration`: 类或接口声明
- `MethodDeclaration`: 方法声明
- `FieldDeclaration`: 字段声明
- `MethodCallExpr`: 方法调用表达式
- `AnnotationExpr`: 注解表达式

## 扩展开发

### 添加新的安全检查规则

1. 在 `SecurityAnalyzer.java` 中添加新方法：

```java
private void checkNewVulnerability(CompilationUnit cu, Path filePath) {
    cu.accept(new VoidVisitorAdapter<Void>() {
        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);
            // 实现你的检查逻辑
            if (/* 发现问题 */) {
                issues.add(new SecurityIssue(
                    "漏洞类型",
                    "严重程度",
                    filePath.getFileName().toString(),
                    method.getNameAsString(),
                    method.getBegin().get().line,
                    "问题描述"
                ));
            }
        }
    }, null);
}
```

2. 在 `analyzeFile` 方法中调用：

```java
private void analyzeFile(Path filePath) {
    try {
        CompilationUnit cu = StaticJavaParser.parse(filePath);
        checkIDORVulnerability(cu, filePath);
        checkNewVulnerability(cu, filePath);  // 添加新检查
    } catch (IOException e) {
        // ...
    }
}
```

## 参考资料

- [JavaParser 官方文档](https://javaparser.org/)
- [JavaParser GitHub](https://github.com/javaparser/javaparser)
- [JavaParser API 文档](https://www.javadoc.io/doc/com.github.javaparser/javaparser-core/latest/index.html)
- [JavaParser 示例项目](https://github.com/javaparser/javaparser-maven-sample)

## License

本项目仅用于教育和演示目的。

