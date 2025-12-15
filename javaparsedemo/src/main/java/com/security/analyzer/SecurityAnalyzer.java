package com.security.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * JavaParser 安全分析器
 * 用于分析 SpringBoot 项目中的安全漏洞
 */
public class SecurityAnalyzer {
    
    private final List<SecurityIssue> issues = new ArrayList<>();
    private final Path projectPath;
    
    public SecurityAnalyzer(String projectPath) {
        this.projectPath = Paths.get(projectPath);
    }
    
    public static void main(String[] args) {
        System.out.println("=== JavaParser 安全分析工具 ===\n");
        
        // 默认分析 springboot-vuln-demo 项目
        String targetProject = "../springboot-vuln-demo/src/main/java";
        if (args.length > 0) {
            targetProject = args[0];
        }
        
        SecurityAnalyzer analyzer = new SecurityAnalyzer(targetProject);
        analyzer.analyze();
        analyzer.printReport();
    }
    
    /**
     * 分析整个项目
     */
    public void analyze() {
        System.out.println("正在分析项目: " + projectPath.toAbsolutePath() + "\n");
        
        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(this::analyzeFile);
        } catch (IOException e) {
            System.err.println("错误: 无法读取项目目录 - " + e.getMessage());
        }
    }
    
    /**
     * 分析单个Java文件
     */
    private void analyzeFile(Path filePath) {
        try {
            System.out.println("分析文件: " + filePath.getFileName());
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            
            // 执行各种安全检查
            checkIDORVulnerability(cu, filePath);
            checkMissingAuthorization(cu, filePath);
            checkSQLInjection(cu, filePath);
            checkPathTraversal(cu, filePath);
            checkXSS(cu, filePath);
            checkHardcodedCredentials(cu, filePath);
            
        } catch (IOException e) {
            System.err.println("  错误: 无法解析文件 - " + e.getMessage());
        }
    }
    
    /**
     * 检查 IDOR 漏洞
     * 查找使用 @PathVariable 但缺少授权检查的方法
     */
    private void checkIDORVulnerability(CompilationUnit cu, Path filePath) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration method, Void arg) {
                super.visit(method, arg);
                
                boolean hasPathVariable = method.getParameters().stream()
                    .anyMatch(param -> param.getAnnotationByName("PathVariable").isPresent());
                
                if (hasPathVariable) {
                    // 检查是否有授权相关注解
                    boolean hasAuthAnnotation = method.getAnnotations().stream()
                        .anyMatch(ann -> {
                            String name = ann.getNameAsString();
                            return name.contains("PreAuthorize") || 
                                   name.contains("Secured") ||
                                   name.contains("RolesAllowed");
                        });
                    
                    // 检查方法体内是否有权限检查
                    boolean hasRuntimeCheck = false;
                    if (method.getBody().isPresent()) {
                        BlockStmt body = method.getBody().get();
                        String bodyStr = body.toString();
                        hasRuntimeCheck = bodyStr.contains("checkPermission") ||
                                        bodyStr.contains("hasAccess") ||
                                        bodyStr.contains("isAuthorized") ||
                                        bodyStr.contains("SecurityContext") ||
                                        bodyStr.contains("getCurrentUser");
                    }
                    
                    if (!hasAuthAnnotation && !hasRuntimeCheck) {
                        issues.add(new SecurityIssue(
                            "IDOR漏洞",
                            "高",
                            filePath.getFileName().toString(),
                            method.getNameAsString(),
                            method.getBegin().get().line,
                            "方法使用了PathVariable但缺少授权检查，可能存在IDOR漏洞。" +
                            "攻击者可能通过修改URL参数访问未授权的资源。"
                        ));
                    }
                }
            }
        }, null);
    }
    
    /**
     * 检查缺少授权检查的敏感操作
     */
    private void checkMissingAuthorization(CompilationUnit cu, Path filePath) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration method, Void arg) {
                super.visit(method, arg);
                
                String methodName = method.getNameAsString().toLowerCase();
                boolean isSensitiveOperation = methodName.contains("delete") ||
                                             methodName.contains("update") ||
                                             methodName.contains("modify") ||
                                             methodName.contains("remove") ||
                                             methodName.contains("admin");
                
                if (isSensitiveOperation) {
                    boolean hasAuthAnnotation = method.getAnnotations().stream()
                        .anyMatch(ann -> {
                            String name = ann.getNameAsString();
                            return name.contains("PreAuthorize") || 
                                   name.contains("Secured") ||
                                   name.contains("RolesAllowed");
                        });
                    
                    if (!hasAuthAnnotation) {
                        issues.add(new SecurityIssue(
                            "缺少授权检查",
                            "中",
                            filePath.getFileName().toString(),
                            method.getNameAsString(),
                            method.getBegin().get().line,
                            "敏感操作方法缺少授权注解或运行时权限检查。" +
                            "建议添加@PreAuthorize等注解或在方法内进行权限验证。"
                        ));
                    }
                }
            }
        }, null);
    }
    
    /**
     * 检查 SQL 注入风险
     */
    private void checkSQLInjection(CompilationUnit cu, Path filePath) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                super.visit(methodCall, arg);
                
                String methodName = methodCall.getNameAsString();
                
                // 检查常见的SQL执行方法
                if (methodName.equals("executeQuery") || 
                    methodName.equals("executeUpdate") ||
                    methodName.equals("execute") ||
                    methodName.equals("createQuery")) {
                    
                    // 检查参数是否包含字符串拼接
                    methodCall.getArguments().forEach(expr -> {
                        if (expr instanceof BinaryExpr) {
                            BinaryExpr binExpr = (BinaryExpr) expr;
                            if (binExpr.getOperator() == BinaryExpr.Operator.PLUS) {
                                issues.add(new SecurityIssue(
                                    "SQL注入风险",
                                    "高",
                                    filePath.getFileName().toString(),
                                    "N/A",
                                    methodCall.getBegin().get().line,
                                    "检测到SQL语句使用字符串拼接，可能存在SQL注入风险。" +
                                    "建议使用PreparedStatement或参数化查询。"
                                ));
                            }
                        }
                    });
                }
            }
        }, null);
    }
    
    /**
     * 检查路径遍历漏洞
     */
    private void checkPathTraversal(CompilationUnit cu, Path filePath) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr expr, Void arg) {
                super.visit(expr, arg);
                
                String typeName = expr.getTypeAsString();
                if (typeName.equals("File") || typeName.equals("FileInputStream") ||
                    typeName.equals("FileOutputStream") || typeName.contains("Path")) {
                    
                    // 检查是否直接使用了用户输入
                    if (!expr.getArguments().isEmpty()) {
                        issues.add(new SecurityIssue(
                            "路径遍历风险",
                            "中",
                            filePath.getFileName().toString(),
                            "N/A",
                            expr.getBegin().get().line,
                            "检测到文件操作，请确保对文件路径进行验证和规范化，" +
                            "防止路径遍历攻击（如: ../../etc/passwd）。"
                        ));
                    }
                }
            }
        }, null);
    }
    
    /**
     * 检查 XSS 风险
     */
    private void checkXSS(CompilationUnit cu, Path filePath) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration method, Void arg) {
                super.visit(method, arg);
                
                // 检查是否直接返回用户输入
                boolean returnsString = method.getTypeAsString().equals("String");
                boolean hasRequestParam = method.getParameters().stream()
                    .anyMatch(param -> param.getAnnotationByName("RequestParam").isPresent() ||
                                      param.getAnnotationByName("RequestBody").isPresent());
                
                if (returnsString && hasRequestParam) {
                    // 检查是否有HTML转义
                    boolean hasEscaping = false;
                    if (method.getBody().isPresent()) {
                        String bodyStr = method.getBody().get().toString();
                        hasEscaping = bodyStr.contains("HtmlUtils.htmlEscape") ||
                                    bodyStr.contains("StringEscapeUtils") ||
                                    bodyStr.contains("@ResponseBody");
                    }
                    
                    if (!hasEscaping) {
                        issues.add(new SecurityIssue(
                            "XSS风险",
                            "中",
                            filePath.getFileName().toString(),
                            method.getNameAsString(),
                            method.getBegin().get().line,
                            "方法返回字符串且接收用户输入，可能存在XSS风险。" +
                            "建议对输出进行HTML转义处理。"
                        ));
                    }
                }
            }
        }, null);
    }
    
    /**
     * 检查硬编码凭证
     */
    private void checkHardcodedCredentials(CompilationUnit cu, Path filePath) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarator var, Void arg) {
                super.visit(var, arg);
                
                String varName = var.getNameAsString().toLowerCase();
                if (varName.contains("password") || varName.contains("secret") ||
                    varName.contains("token") || varName.contains("key")) {
                    
                    if (var.getInitializer().isPresent()) {
                        Expression init = var.getInitializer().get();
                        if (init instanceof StringLiteralExpr) {
                            StringLiteralExpr strExpr = (StringLiteralExpr) init;
                            String value = strExpr.getValue();
                            if (!value.isEmpty() && !value.startsWith("${")) {
                                issues.add(new SecurityIssue(
                                    "硬编码凭证",
                                    "高",
                                    filePath.getFileName().toString(),
                                    "N/A",
                                    var.getBegin().get().line,
                                    "检测到硬编码的密码、密钥或令牌: " + varName + 
                                    "。建议使用环境变量或配置文件存储敏感信息。"
                                ));
                            }
                        }
                    }
                }
            }
        }, null);
    }
    
    /**
     * 打印分析报告
     */
    public void printReport() {
        System.out.println("\n=== 安全分析报告 ===\n");
        
        if (issues.isEmpty()) {
            System.out.println("✅ 未发现安全问题!");
            return;
        }
        
        System.out.println("发现 " + issues.size() + " 个潜在安全问题:\n");
        
        // 统计严重程度
        Map<String, Long> severityCount = new HashMap<>();
        for (SecurityIssue issue : issues) {
            severityCount.merge(issue.severity, 1L, Long::sum);
        }
        
        System.out.println("严重程度统计:");
        System.out.println("  高: " + severityCount.getOrDefault("高", 0L));
        System.out.println("  中: " + severityCount.getOrDefault("中", 0L));
        System.out.println("  低: " + severityCount.getOrDefault("低", 0L));
        System.out.println();
        
        // 按严重程度排序
        issues.sort((a, b) -> {
            Map<String, Integer> priority = new HashMap<>();
            priority.put("高", 3);
            priority.put("中", 2);
            priority.put("低", 1);
            return priority.getOrDefault(b.severity, 0).compareTo(
                   priority.getOrDefault(a.severity, 0));
        });
        
        System.out.println("详细问题列表:\n");
        for (int i = 0; i < issues.size(); i++) {
            SecurityIssue issue = issues.get(i);
            System.out.println((i + 1) + ". [" + issue.severity + "] " + issue.type);
            System.out.println("   文件: " + issue.fileName);
            if (!issue.methodName.equals("N/A")) {
                System.out.println("   方法: " + issue.methodName + " (行号: " + issue.lineNumber + ")");
            } else {
                System.out.println("   行号: " + issue.lineNumber);
            }
            System.out.println("   描述: " + issue.description);
            System.out.println();
        }
        
        System.out.println("=== 分析完成 ===");
    }
    
    /**
     * 安全问题数据类
     */
    static class SecurityIssue {
        String type;        // 问题类型
        String severity;    // 严重程度
        String fileName;    // 文件名
        String methodName;  // 方法名
        int lineNumber;     // 行号
        String description; // 描述
        
        SecurityIssue(String type, String severity, String fileName, 
                     String methodName, int lineNumber, String description) {
            this.type = type;
            this.severity = severity;
            this.fileName = fileName;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
            this.description = description;
        }
    }
}
