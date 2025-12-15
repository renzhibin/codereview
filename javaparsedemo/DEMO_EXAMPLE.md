# JavaParser Demo 实战示例

## 运行结果展示

### 1. 安全漏洞分析器 (SecurityAnalyzer)

```bash
cd javaparsedemo
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"
```

**实际输出：**

```
=== JavaParser 安全分析工具 ===

正在分析项目: ../springboot-vuln-demo/src/main/java

分析文件: OrderController.java
分析文件: UserController.java
分析文件: User.java
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
   描述: 方法使用了PathVariable但缺少授权检查，可能存在IDOR漏洞。
        攻击者可能通过修改URL参数访问未授权的资源。

2. [高] IDOR漏洞
   文件: UserController.java
   方法: getUserById (行号: 23)
   描述: 方法使用了PathVariable但缺少授权检查，可能存在IDOR漏洞。

3. [中] 缺少授权检查
   文件: OrderController.java
   方法: deleteOrder (行号: 77)
   描述: 敏感操作方法缺少授权注解或运行时权限检查。
```

## 检测到的具体问题

### 问题1: IDOR漏洞 - OrderController.getOrderById

**问题代码：**
```java
@GetMapping("/orders/{id}")
public Order getOrderById(@PathVariable Long id) {
    return orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found"));
}
```

**问题分析：**
- ❌ 使用 `@PathVariable` 直接接收订单ID
- ❌ 没有任何授权检查
- ❌ 任何用户都可以通过修改URL访问其他用户的订单

**修复方案1 - 使用注解：**
```java
@GetMapping("/orders/{id}")
@PreAuthorize("@orderSecurity.canAccess(#id, authentication)")
public Order getOrderById(@PathVariable Long id) {
    return orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found"));
}
```

**修复方案2 - 运行时检查：**
```java
@GetMapping("/orders/{id}")
public Order getOrderById(@PathVariable Long id, Authentication authentication) {
    Order order = orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found"));
    
    // 检查当前用户是否有权访问该订单
    String currentUsername = authentication.getName();
    if (!order.getUser().getUsername().equals(currentUsername)) {
        throw new AccessDeniedException("无权访问该订单");
    }
    
    return order;
}
```

### 问题2: 缺少授权检查 - UserController.updateUserRole

**问题代码：**
```java
@PutMapping("/users/{id}/role")
public User updateUserRole(@PathVariable Long id, @RequestParam String role) {
    User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
    user.setRole(role);
    return userRepository.save(user);
}
```

**问题分析：**
- ❌ 敏感操作：修改用户角色
- ❌ 没有管理员权限检查
- ❌ 任何用户都可以将自己提升为管理员

**修复方案：**
```java
@PutMapping("/users/{id}/role")
@PreAuthorize("hasRole('ADMIN')")
public User updateUserRole(@PathVariable Long id, @RequestParam String role) {
    User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
    user.setRole(role);
    return userRepository.save(user);
}
```

## JavaParser 核心代码解析

### 如何检测 IDOR 漏洞

```java
private void checkIDORVulnerability(CompilationUnit cu, Path filePath) {
    cu.accept(new VoidVisitorAdapter<Void>() {
        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);
            
            // 1. 检查方法是否使用了 @PathVariable
            boolean hasPathVariable = method.getParameters().stream()
                .anyMatch(param -> param.getAnnotationByName("PathVariable").isPresent());
            
            if (hasPathVariable) {
                // 2. 检查是否有授权注解
                boolean hasAuthAnnotation = method.getAnnotations().stream()
                    .anyMatch(ann -> {
                        String name = ann.getNameAsString();
                        return name.contains("PreAuthorize") || 
                               name.contains("Secured") ||
                               name.contains("RolesAllowed");
                    });
                
                // 3. 检查方法体内是否有运行时权限检查
                boolean hasRuntimeCheck = false;
                if (method.getBody().isPresent()) {
                    String bodyStr = method.getBody().get().toString();
                    hasRuntimeCheck = bodyStr.contains("checkPermission") ||
                                    bodyStr.contains("hasAccess") ||
                                    bodyStr.contains("getCurrentUser");
                }
                
                // 4. 如果两者都没有，则报告漏洞
                if (!hasAuthAnnotation && !hasRuntimeCheck) {
                    issues.add(new SecurityIssue(
                        "IDOR漏洞",
                        "高",
                        filePath.getFileName().toString(),
                        method.getNameAsString(),
                        method.getBegin().get().line,
                        "方法使用了PathVariable但缺少授权检查"
                    ));
                }
            }
        }
    }, null);
}
```

### 如何检测 SQL 注入

```java
private void checkSQLInjection(CompilationUnit cu, Path filePath) {
    cu.accept(new VoidVisitorAdapter<Void>() {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            String methodName = methodCall.getNameAsString();
            
            // 检查常见的SQL执行方法
            if (methodName.equals("executeQuery") || 
                methodName.equals("executeUpdate") ||
                methodName.equals("createQuery")) {
                
                // 检查参数是否包含字符串拼接
                methodCall.getArguments().forEach(expr -> {
                    if (expr instanceof BinaryExpr) {
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        if (binExpr.getOperator() == BinaryExpr.Operator.PLUS) {
                            // 发现字符串拼接构建SQL
                            issues.add(new SecurityIssue(...));
                        }
                    }
                });
            }
        }
    }, null);
}
```

## 运行其他分析器

### AST 可视化工具

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.ASTVisualizer"
```

**输出示例：**
```
================================================================================
📄 文件: UserController.java
================================================================================

📦 包: com.example.controller

📥 导入:
  - org.springframework.web.bind.annotation.RestController
  - org.springframework.web.bind.annotation.RequestMapping
  - com.example.repository.UserRepository

🏛️  类: UserController
  注解:
    @RestController
    @RequestMapping
  
  📋 字段: UserRepository userRepository
    注解: @Autowired 
  
  🔧 方法: getUserById(Long id): User
    注解: @GetMapping 
    修饰符: [public]
    语句数: 3
```

### 方法复杂度分析器

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer"
```

**输出示例：**
```
=== 方法分析报告 ===

共分析 15 个方法

复杂度最高的前10个方法:

文件                           方法名                         复杂度      行数        参数数      调用数    
--------------------------------------------------------------------------------------------------------------
OrderController.java          getOrderById                  2          5          1          2         [API]
UserController.java           updateUserRole                2          6          2          3         [API]
DataInitializer.java          init                          1          20         0          8         

统计信息:
  API端点数量: 8
  平均复杂度: 1.8
  平均方法长度: 8.5 行
  高复杂度方法 (CC > 10): 0
  长方法 (> 50行): 0
  参数过多的方法 (> 5个): 0
  缺少Javadoc的API: 8

💡 质量建议:
  - 有 8 个API缺少文档注释
```

## 使用场景

### 场景1: CI/CD 集成

在 GitHub Actions 中使用：

```yaml
name: Security Scan

on: [push, pull_request]

jobs:
  security-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up JDK 8
        uses: actions/setup-java@v2
        with:
          java-version: '8'
          
      - name: Run Security Analysis
        run: |
          cd javaparsedemo
          mvn clean compile
          mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"
```

### 场景2: 代码审查辅助

在代码审查时快速识别潜在安全问题：

```bash
# 分析特定目录
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" \
  -Dexec.args="/path/to/your/controllers"
  
# 将结果保存到文件
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" \
  > security-report-$(date +%Y%m%d).txt
```

### 场景3: 开发时实时检查

在IDE中配置Maven运行配置，一键运行分析。

## 扩展示例

### 添加自定义检查规则

```java
/**
 * 检查敏感数据硬编码
 */
private void checkHardcodedSensitiveData(CompilationUnit cu, Path filePath) {
    cu.accept(new VoidVisitorAdapter<Void>() {
        @Override
        public void visit(VariableDeclarator var, Void arg) {
            super.visit(var, arg);
            
            String varName = var.getNameAsString().toLowerCase();
            
            // 检查可能包含敏感信息的变量名
            if (varName.contains("apikey") || 
                varName.contains("secret") ||
                varName.contains("token")) {
                
                if (var.getInitializer().isPresent()) {
                    Expression init = var.getInitializer().get();
                    
                    // 检查是否硬编码了字符串值
                    if (init instanceof StringLiteralExpr) {
                        StringLiteralExpr strExpr = (StringLiteralExpr) init;
                        String value = strExpr.getValue();
                        
                        // 排除配置占位符
                        if (!value.startsWith("${") && !value.isEmpty()) {
                            issues.add(new SecurityIssue(
                                "硬编码敏感数据",
                                "高",
                                filePath.getFileName().toString(),
                                "N/A",
                                var.getBegin().get().line,
                                "检测到硬编码的敏感信息: " + varName
                            ));
                        }
                    }
                }
            }
        }
    }, null);
}
```

## JavaParser 优势

1. **精确的AST分析**
   - 深入理解代码结构，而不仅仅是文本匹配
   - 可以分析代码的语义和上下文

2. **灵活的扩展性**
   - 基于Visitor模式，易于添加新的检查规则
   - 可以组合多个检查器

3. **高性能**
   - 纯Java实现，无需外部依赖
   - 可以并行处理多个文件

4. **类型感知**
   - 结合Symbol Solver可以进行类型推断
   - 理解继承关系和接口实现

## 总结

这个JavaParser demo展示了如何：

✅ 使用JavaParser解析Java源代码  
✅ 遍历AST进行安全漏洞检测  
✅ 识别IDOR、SQL注入等常见漏洞  
✅ 分析代码质量和复杂度  
✅ 可视化代码结构  

适用于：
- 🔒 安全代码审查
- 📊 代码质量分析
- 🎓 学习Java AST
- 🛠️ 构建自定义代码分析工具

