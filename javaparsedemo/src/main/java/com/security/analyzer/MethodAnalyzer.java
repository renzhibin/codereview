package com.security.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 方法复杂度分析器
 * 分析方法的质量指标和复杂度
 */
public class MethodAnalyzer {
    
    private final List<MethodMetrics> allMetrics = new ArrayList<>();
    
    /**
     * 重复字符串 (Java 8兼容)
     */
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    public static void main(String[] args) {
        System.out.println("=== JavaParser 方法复杂度分析器 ===\n");
        
        String targetProject = "../springboot-vuln-demo/src/main/java";
        if (args.length > 0) {
            targetProject = args[0];
        }
        
        MethodAnalyzer analyzer = new MethodAnalyzer();
        analyzer.analyzeProject(targetProject);
        analyzer.printReport();
    }
    
    /**
     * 分析整个项目
     */
    public void analyzeProject(String projectPath) {
        Path path = Paths.get(projectPath);
        System.out.println("正在分析项目: " + path.toAbsolutePath() + "\n");
        
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(this::analyzeFile);
        } catch (IOException e) {
            System.err.println("错误: 无法读取项目目录 - " + e.getMessage());
        }
    }
    
    /**
     * 分析单个文件
     */
    private void analyzeFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodDeclaration method, Void arg) {
                    super.visit(method, arg);
                    
                    MethodMetrics metrics = new MethodMetrics();
                    metrics.fileName = filePath.getFileName().toString();
                    metrics.methodName = method.getNameAsString();
                    metrics.lineNumber = method.getBegin().get().line;
                    metrics.parameterCount = method.getParameters().size();
                    
                    // 计算圈复杂度
                    metrics.cyclomaticComplexity = calculateCyclomaticComplexity(method);
                    
                    // 计算代码行数
                    metrics.linesOfCode = calculateLOC(method);
                    
                    // 计算方法调用次数
                    metrics.methodCallCount = countMethodCalls(method);
                    
                    // 检查是否是API端点
                    metrics.isAPIEndpoint = isAPIEndpoint(method);
                    
                    // 检查是否有注释
                    metrics.hasJavadoc = method.getJavadoc().isPresent();
                    
                    allMetrics.add(metrics);
                }
            }, null);
            
        } catch (IOException e) {
            System.err.println("错误: 无法解析文件 " + filePath.getFileName() + " - " + e.getMessage());
        }
    }
    
    /**
     * 计算圈复杂度 (Cyclomatic Complexity)
     * CC = 决策点数量 + 1
     */
    private int calculateCyclomaticComplexity(MethodDeclaration method) {
        if (!method.getBody().isPresent()) {
            return 1;
        }
        
        final int[] complexity = {1}; // 基础复杂度为1
        
        method.getBody().get().accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(IfStmt stmt, Void arg) {
                complexity[0]++;
                super.visit(stmt, arg);
            }
            
            @Override
            public void visit(WhileStmt stmt, Void arg) {
                complexity[0]++;
                super.visit(stmt, arg);
            }
            
            @Override
            public void visit(ForStmt stmt, Void arg) {
                complexity[0]++;
                super.visit(stmt, arg);
            }
            
            @Override
            public void visit(ForEachStmt stmt, Void arg) {
                complexity[0]++;
                super.visit(stmt, arg);
            }
            
            @Override
            public void visit(DoStmt stmt, Void arg) {
                complexity[0]++;
                super.visit(stmt, arg);
            }
            
            @Override
            public void visit(SwitchEntry stmt, Void arg) {
                // switch的每个case增加复杂度
                if (!stmt.getLabels().isEmpty()) {
                    complexity[0]++;
                }
                super.visit(stmt, arg);
            }
            
            @Override
            public void visit(CatchClause stmt, Void arg) {
                complexity[0]++;
                super.visit(stmt, arg);
            }
        }, null);
        
        return complexity[0];
    }
    
    /**
     * 计算代码行数 (不包括空行和注释)
     */
    private int calculateLOC(MethodDeclaration method) {
        if (!method.getBody().isPresent()) {
            return 0;
        }
        
        int startLine = method.getBegin().get().line;
        int endLine = method.getEnd().get().line;
        
        return endLine - startLine + 1;
    }
    
    /**
     * 统计方法调用次数
     */
    private int countMethodCalls(MethodDeclaration method) {
        if (!method.getBody().isPresent()) {
            return 0;
        }
        
        final int[] count = {0};
        
        method.getBody().get().accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr methodCall, Void arg) {
                count[0]++;
                super.visit(methodCall, arg);
            }
        }, null);
        
        return count[0];
    }
    
    /**
     * 检查是否是API端点
     */
    private boolean isAPIEndpoint(MethodDeclaration method) {
        for (AnnotationExpr ann : method.getAnnotations()) {
            String name = ann.getNameAsString();
            if (name.equals("GetMapping") || name.equals("PostMapping") ||
                name.equals("PutMapping") || name.equals("DeleteMapping") ||
                name.equals("PatchMapping") || name.equals("RequestMapping")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 打印分析报告
     */
    public void printReport() {
        System.out.println("\n=== 方法分析报告 ===\n");
        
        if (allMetrics.isEmpty()) {
            System.out.println("未找到方法进行分析。");
            return;
        }
        
        System.out.println("共分析 " + allMetrics.size() + " 个方法\n");
        
        // 按复杂度排序
        allMetrics.sort(Comparator.comparingInt((MethodMetrics m) -> m.cyclomaticComplexity).reversed());
        
        System.out.println("复杂度最高的前10个方法:\n");
        System.out.printf("%-30s %-30s %-12s %-12s %-12s %-10s\n", 
            "文件", "方法名", "复杂度", "行数", "参数数", "调用数");
        System.out.println(repeat("-", 110));
        
        int limit = Math.min(10, allMetrics.size());
        for (int i = 0; i < limit; i++) {
            MethodMetrics m = allMetrics.get(i);
            System.out.printf("%-30s %-30s %-12d %-12d %-12d %-10d %s\n",
                truncate(m.fileName, 30),
                truncate(m.methodName, 30),
                m.cyclomaticComplexity,
                m.linesOfCode,
                m.parameterCount,
                m.methodCallCount,
                m.isAPIEndpoint ? "[API]" : ""
            );
        }
        
        // 统计信息
        System.out.println("\n统计信息:");
        
        long apiCount = allMetrics.stream().filter(m -> m.isAPIEndpoint).count();
        System.out.println("  API端点数量: " + apiCount);
        
        double avgComplexity = allMetrics.stream()
            .mapToInt(m -> m.cyclomaticComplexity)
            .average()
            .orElse(0);
        System.out.printf("  平均复杂度: %.2f\n", avgComplexity);
        
        double avgLOC = allMetrics.stream()
            .mapToInt(m -> m.linesOfCode)
            .average()
            .orElse(0);
        System.out.printf("  平均方法长度: %.2f 行\n", avgLOC);
        
        long highComplexity = allMetrics.stream()
            .filter(m -> m.cyclomaticComplexity > 10)
            .count();
        System.out.println("  高复杂度方法 (CC > 10): " + highComplexity);
        
        long longMethods = allMetrics.stream()
            .filter(m -> m.linesOfCode > 50)
            .count();
        System.out.println("  长方法 (> 50行): " + longMethods);
        
        long manyParams = allMetrics.stream()
            .filter(m -> m.parameterCount > 5)
            .count();
        System.out.println("  参数过多的方法 (> 5个): " + manyParams);
        
        long withoutJavadoc = allMetrics.stream()
            .filter(m -> m.isAPIEndpoint && !m.hasJavadoc)
            .count();
        System.out.println("  缺少Javadoc的API: " + withoutJavadoc);
        
        // 质量建议
        System.out.println("\n💡 质量建议:");
        if (highComplexity > 0) {
            System.out.println("  - 有 " + highComplexity + " 个方法复杂度过高，建议重构");
        }
        if (longMethods > 0) {
            System.out.println("  - 有 " + longMethods + " 个方法过长，建议拆分");
        }
        if (manyParams > 0) {
            System.out.println("  - 有 " + manyParams + " 个方法参数过多，建议使用对象封装");
        }
        if (withoutJavadoc > 0) {
            System.out.println("  - 有 " + withoutJavadoc + " 个API缺少文档注释");
        }
        
        System.out.println("\n=== 分析完成 ===");
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 方法指标数据类
     */
    static class MethodMetrics {
        String fileName;
        String methodName;
        int lineNumber;
        int cyclomaticComplexity;  // 圈复杂度
        int linesOfCode;            // 代码行数
        int parameterCount;         // 参数数量
        int methodCallCount;        // 方法调用次数
        boolean isAPIEndpoint;      // 是否是API端点
        boolean hasJavadoc;         // 是否有文档注释
    }
}
