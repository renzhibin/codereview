# JavaParser 使用指南

## 目录
- [快速开始](#快速开始)
- [工具说明](#工具说明)
- [使用示例](#使用示例)
- [自定义分析](#自定义分析)
- [常见问题](#常见问题)

## 快速开始

### 方法一: 使用脚本运行（推荐）

```bash
cd javaparsedemo
chmod +x run-analysis.sh
./run-analysis.sh
```

然后按照提示选择要运行的分析器。

### 方法二: 使用Maven命令

#### 1. 安全漏洞分析器
```bash
cd javaparsedemo
mvn clean compile
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"
```

#### 2. AST可视化工具
```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.ASTVisualizer"
```

#### 3. 方法复杂度分析器
```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer"
```

### 方法三: 指定自定义目录

```bash
# 分析指定目录
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" \
  -Dexec.args="/path/to/your/java/project"
```

## 工具说明

### 1. SecurityAnalyzer - 安全漏洞分析器

**功能**: 自动检测Java代码中的常见安全漏洞

**检测项**:
- ✅ IDOR漏洞 (不安全的直接对象引用)
- ✅ 缺少授权检查
- ✅ SQL注入风险
- ✅ 路径遍历漏洞
- ✅ XSS跨站脚本攻击
- ✅ 硬编码凭证

**输出示例**:
```
=== 安全分析报告 ===

发现 5 个潜在安全问题:

严重程度统计:
  高: 3
  中: 2
  低: 0

详细问题列表:

1. [高] IDOR漏洞
   文件: UserController.java
   方法: getUser (行号: 25)
   描述: 方法使用了PathVariable但缺少授权检查...
```

### 2. ASTVisualizer - AST可视化工具

**功能**: 可视化展示Java源代码的抽象语法树结构

**显示内容**:
- 📦 包和导入信息
- 🏛️ 类、接口、枚举定义
- 📋 字段声明
- 🔧 方法和构造方法
- 🎯 注解信息
- 📊 继承和实现关系

**输出示例**:
```
================================================================================
📄 文件: UserController.java
================================================================================

📦 包: com.example.controller

📥 导入:
  - org.springframework.web.bind.annotation.RestController
  - com.example.service.UserService

🏛️  类: UserController
  注解:
    @RestController
    @RequestMapping

  🔧 方法: getUser(Long id): User
    注解: @GetMapping 
    修饰符: [public]
    语句数: 5
```

### 3. MethodAnalyzer - 方法复杂度分析器

**功能**: 分析方法的质量指标

**分析指标**:
- 圈复杂度 (Cyclomatic Complexity)
- 代码行数 (LOC)
- 参数数量
- 方法调用次数
- API端点识别

**输出示例**:
```
=== 方法分析报告 ===

共分析 25 个方法

复杂度最高的前10个方法:

文件                           方法名                         复杂度      行数        参数数      调用数    
--------------------------------------------------------------------------------------------------------------
UserController.java           processUserData               8          45         3          12        [API]
OrderService.java             calculateTotal                6          32         2          8         

统计信息:
  API端点数量: 8
  平均复杂度: 3.2
  平均方法长度: 15.6 行
  高复杂度方法 (CC > 10): 2
  长方法 (> 50行): 1
```

## 使用示例

### 示例1: 检测IDOR漏洞

假设有以下代码:

```java
@RestController
public class OrderController {
    
    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderService.findById(id);
    }
}
```

**SecurityAnalyzer** 会报告:
```
[高] IDOR漏洞
方法使用了PathVariable但缺少授权检查，可能存在IDOR漏洞。
攻击者可能通过修改URL参数访问未授权的资源。
```

**修复方案**:
```java
@GetMapping("/orders/{id}")
@PreAuthorize("@orderSecurity.canAccess(#id)")  // 添加授权检查
public Order getOrder(@PathVariable Long id) {
    return orderService.findById(id);
}
```

### 示例2: 检测SQL注入

假设有以下代码:

```java
public List<User> findUsers(String name) {
    String sql = "SELECT * FROM users WHERE name = '" + name + "'";
    return jdbcTemplate.query(sql, userMapper);
}
```

**SecurityAnalyzer** 会报告:
```
[高] SQL注入风险
检测到SQL语句使用字符串拼接，可能存在SQL注入风险。
建议使用PreparedStatement或参数化查询。
```

**修复方案**:
```java
public List<User> findUsers(String name) {
    String sql = "SELECT * FROM users WHERE name = ?";
    return jdbcTemplate.query(sql, new Object[]{name}, userMapper);
}
```

### 示例3: 分析方法复杂度

对于复杂的方法:

```java
public void processOrder(Order order) {
    if (order != null) {
        if (order.getStatus() == Status.PENDING) {
            for (Item item : order.getItems()) {
                if (item.getPrice() > 100) {
                    // 处理逻辑
                }
            }
        } else if (order.getStatus() == Status.PROCESSING) {
            // 其他逻辑
        }
    }
}
```

**MethodAnalyzer** 会显示:
```
方法: processOrder
圈复杂度: 5
行数: 15
```

建议重构为:
```java
public void processOrder(Order order) {
    validateOrder(order);
    if (isPending(order)) {
        processItems(order.getItems());
    } else if (isProcessing(order)) {
        handleProcessing(order);
    }
}

private void processItems(List<Item> items) {
    items.stream()
        .filter(item -> item.getPrice() > 100)
        .forEach(this::handleExpensiveItem);
}
```

## 自定义分析

### 添加新的安全检查规则

在 `SecurityAnalyzer.java` 中添加新方法:

```java
/**
 * 检查XXX漏洞
 */
private void checkCustomVulnerability(CompilationUnit cu, Path filePath) {
    cu.accept(new VoidVisitorAdapter<Void>() {
        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);
            
            // 你的检查逻辑
            if (/* 发现问题 */) {
                issues.add(new SecurityIssue(
                    "漏洞类型",
                    "严重程度 (高/中/低)",
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

然后在 `analyzeFile` 方法中调用:

```java
private void analyzeFile(Path filePath) {
    try {
        CompilationUnit cu = StaticJavaParser.parse(filePath);
        
        checkIDORVulnerability(cu, filePath);
        checkMissingAuthorization(cu, filePath);
        checkCustomVulnerability(cu, filePath);  // 添加你的检查
        
    } catch (IOException e) {
        // ...
    }
}
```

### 自定义复杂度阈值

在 `MethodAnalyzer.java` 中修改:

```java
// 原代码
long highComplexity = allMetrics.stream()
    .filter(m -> m.cyclomaticComplexity > 10)
    .count();

// 修改为自定义阈值
long highComplexity = allMetrics.stream()
    .filter(m -> m.cyclomaticComplexity > 15)  // 改为15
    .count();
```

## 常见问题

### Q1: 编译失败怎么办？

**A**: 确保你的环境满足以下要求:
- JDK 17或更高版本
- Maven 3.6+

检查Java版本:
```bash
java -version
mvn -version
```

### Q2: 如何分析其他项目？

**A**: 传递项目路径作为参数:
```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" \
  -Dexec.args="/path/to/your/project/src/main/java"
```

### Q3: 分析器报告的问题都是真实的吗？

**A**: 不一定。这些是静态分析工具，可能会产生误报(False Positive)。你需要:
1. 手动审查每个报告的问题
2. 理解上下文和业务逻辑
3. 结合其他安全工具验证

### Q4: 可以集成到CI/CD吗？

**A**: 可以。在CI配置文件中添加:

```yaml
# .github/workflows/security-scan.yml
- name: Security Analysis
  run: |
    cd javaparsedemo
    mvn clean compile
    mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"
```

### Q5: 如何导出分析结果？

**A**: 修改 `printReport` 方法，将结果写入文件:

```java
public void printReport() {
    try (PrintWriter writer = new PrintWriter("security-report.txt")) {
        writer.println("=== 安全分析报告 ===");
        // ... 写入报告内容
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

## JavaParser核心API参考

### 常用类

```java
// 解析Java文件
CompilationUnit cu = StaticJavaParser.parse(filePath);

// 遍历所有类
cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
    System.out.println("Class: " + cls.getNameAsString());
});

// 遍历所有方法
cu.findAll(MethodDeclaration.class).forEach(method -> {
    System.out.println("Method: " + method.getNameAsString());
});

// 查找特定注解
method.getAnnotationByName("GetMapping").ifPresent(ann -> {
    System.out.println("Found GetMapping");
});

// 获取方法参数
method.getParameters().forEach(param -> {
    System.out.println("Param: " + param.getNameAsString());
});
```

### Visitor模式

```java
cu.accept(new VoidVisitorAdapter<Void>() {
    @Override
    public void visit(MethodDeclaration method, Void arg) {
        super.visit(method, arg);
        // 处理每个方法
    }
    
    @Override
    public void visit(MethodCallExpr methodCall, Void arg) {
        super.visit(methodCall, arg);
        // 处理每个方法调用
    }
}, null);
```

## 更多资源

- [JavaParser官方文档](https://javaparser.org/)
- [JavaParser GitHub](https://github.com/javaparser/javaparser)
- [OWASP安全编码规范](https://owasp.org/www-project-secure-coding-practices-quick-reference-guide/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)

## License

本项目仅用于教育和演示目的。

