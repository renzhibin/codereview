package com.security.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * 方法调用链分析器 - 非交互式演示版本
 * 自动分析几个关键方法的调用链
 */
public class MethodCallChainDemo {
    
    private final Map<String, MethodInfo> methodRegistry = new HashMap<>();
    private final Map<String, Set<String>> callGraph = new HashMap<>();
    private final Map<String, Set<String>> reverseCallGraph = new HashMap<>();
    private final Path projectPath;
    
    public MethodCallChainDemo(String projectPath) {
        this.projectPath = Paths.get(projectPath);
    }
    
    public static void main(String[] args) {
        System.out.println("=== 方法调用链分析器 - Demo ===\n");
        
        String targetProject = "../springboot-vuln-demo/src/main/java";
        if (args.length > 0) {
            targetProject = args[0];
        }
        
        MethodCallChainDemo analyzer = new MethodCallChainDemo(targetProject);
        
        try {
            // 分析项目
            analyzer.analyze();
            
            // 自动演示几个关键方法
            analyzer.demonstrateCallChains();
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void analyze() {
        System.out.println("正在分析项目: " + projectPath.toAbsolutePath() + "\n");
        
        try (Stream<Path> paths = Files.walk(projectPath)) {
            List<Path> javaFiles = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFiles::add);
            
            System.out.println("找到 " + javaFiles.size() + " 个Java文件");
            
            // 收集方法定义
            System.out.println("步骤1: 收集方法定义...");
            for (Path file : javaFiles) {
                collectMethodDefinitions(file);
            }
            System.out.println("✅ 收集到 " + methodRegistry.size() + " 个方法");
            
            // 收集方法调用关系
            System.out.println("步骤2: 分析调用关系...");
            for (Path file : javaFiles) {
                collectMethodCalls(file);
            }
            System.out.println("✅ 构建调用图完成\n");
            
            printStatistics();
            
        } catch (IOException e) {
            System.err.println("错误: 无法读取项目目录 - " + e.getMessage());
        }
    }
    
    private void collectMethodDefinitions(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
            
            cu.accept(new VoidVisitorAdapter<Void>() {
                private String currentClass = "";
                
                @Override
                public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
                    currentClass = cid.getNameAsString();
                    super.visit(cid, arg);
                }
                
                @Override
                public void visit(MethodDeclaration method, Void arg) {
                    super.visit(method, arg);
                    
                    String methodKey = currentClass + "." + method.getNameAsString();
                    MethodInfo info = new MethodInfo(
                        currentClass,
                        method.getNameAsString(),
                        filePath.getFileName().toString(),
                        method.getBegin().isPresent() ? method.getBegin().get().line : 0,
                        packageName
                    );
                    
                    methodRegistry.put(methodKey, info);
                }
            }, null);
            
        } catch (IOException e) {
            // 忽略
        }
    }
    
    private void collectMethodCalls(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            
            cu.accept(new VoidVisitorAdapter<Void>() {
                private String currentClass = "";
                private String currentMethod = "";
                
                @Override
                public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
                    currentClass = cid.getNameAsString();
                    super.visit(cid, arg);
                }
                
                @Override
                public void visit(MethodDeclaration method, Void arg) {
                    currentMethod = method.getNameAsString();
                    super.visit(method, arg);
                }
                
                @Override
                public void visit(MethodCallExpr call, Void arg) {
                    super.visit(call, arg);
                    
                    if (!currentClass.isEmpty() && !currentMethod.isEmpty()) {
                        String caller = currentClass + "." + currentMethod;
                        String callee = resolveMethodCall(call);
                        
                        callGraph.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
                        reverseCallGraph.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
                    }
                }
            }, null);
            
        } catch (IOException e) {
            // 忽略
        }
    }
    
    private String resolveMethodCall(MethodCallExpr call) {
        String methodName = call.getNameAsString();
        
        if (call.getScope().isPresent()) {
            String scope = call.getScope().get().toString();
            
            // 尝试解析类名
            for (String registeredMethod : methodRegistry.keySet()) {
                if (registeredMethod.endsWith("." + methodName)) {
                    String className = registeredMethod.substring(0, registeredMethod.lastIndexOf('.'));
                    if (scope.toLowerCase().contains(className.toLowerCase()) ||
                        scope.equals(className)) {
                        return registeredMethod;
                    }
                }
            }
            
            return scope + "." + methodName;
        }
        
        // 尝试在当前类中查找
        return "?." + methodName;
    }
    
    private void printStatistics() {
        System.out.println("=== 统计信息 ===");
        System.out.println("总方法数: " + methodRegistry.size());
        System.out.println("有调用关系的方法: " + callGraph.size());
        System.out.println("被调用的方法: " + reverseCallGraph.size());
        System.out.println();
    }
    
    /**
     * 演示几个关键方法的调用链
     */
    private void demonstrateCallChains() {
        System.out.println(repeat("=", 80));
        System.out.println("                    方法调用链分析演示");
        System.out.println(repeat("=", 80));
        
        // 演示方法列表
        String[] demoMethods = {
            "getOrderById",
            "getUserById",
            "updateOrder",
            "deleteOrder",
            "save",
            "findById"
        };
        
        for (String methodName : demoMethods) {
            Set<String> matches = findMatchingMethods(methodName);
            
            if (!matches.isEmpty()) {
                for (String methodKey : matches) {
                    analyzeMethod(methodKey);
                    break; // 只分析第一个匹配
                }
            }
        }
        
        // 使用说明
        System.out.println("\n" + repeat("=", 80));
        System.out.println("                    使用说明");
        System.out.println(repeat("=", 80));
        System.out.println("\n交互式使用：");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"com.security.analyzer.MethodCallChainAnalyzer\"\n");
        System.out.println("命令行查询（非交互）：");
        System.out.println("  java -cp target/classes com.security.analyzer.MethodCallChainDemo <项目路径>\n");
    }
    
    /**
     * 分析单个方法
     */
    private void analyzeMethod(String methodKey) {
        System.out.println("\n" + repeat("=", 80));
        System.out.println("🔍 分析方法: " + methodKey);
        
        MethodInfo info = methodRegistry.get(methodKey);
        if (info != null) {
            System.out.println("📍 位置: " + info.fileName + ":" + info.lineNumber);
        }
        
        System.out.println(repeat("=", 80));
        
        // 上游分析
        Set<String> upstream = findUpstream(methodKey, new HashSet<>(), 0);
        System.out.println("\n⬆️  上游调用链（谁调用了它）: " + upstream.size() + " 个方法");
        System.out.println(repeat("-", 80));
        if (upstream.isEmpty()) {
            System.out.println("  (无上游调用者 - 可能是入口方法或未被调用)");
        } else {
            printCallChain(upstream, "  ");
        }
        
        // 下游分析
        Set<String> downstream = findDownstream(methodKey, new HashSet<>(), 0);
        System.out.println("\n⬇️  下游调用链（它调用了谁）: " + downstream.size() + " 个方法");
        System.out.println(repeat("-", 80));
        if (downstream.isEmpty()) {
            System.out.println("  (无下游调用 - 可能是叶子方法)");
        } else {
            printCallChain(downstream, "  ");
        }
        
        // 调用深度
        int maxUpDepth = calculateMaxDepth(methodKey, reverseCallGraph, new HashSet<>(), 0);
        int maxDownDepth = calculateMaxDepth(methodKey, callGraph, new HashSet<>(), 0);
        System.out.println("\n📊 调用深度统计:");
        System.out.println("  最大上游深度: " + maxUpDepth);
        System.out.println("  最大下游深度: " + maxDownDepth);
    }
    
    /**
     * 计算最大调用深度
     */
    private int calculateMaxDepth(String methodKey, Map<String, Set<String>> graph, 
                                  Set<String> visited, int currentDepth) {
        if (visited.contains(methodKey) || currentDepth > 10) {
            return currentDepth;
        }
        
        visited.add(methodKey);
        Set<String> neighbors = graph.get(methodKey);
        
        if (neighbors == null || neighbors.isEmpty()) {
            return currentDepth;
        }
        
        int maxDepth = currentDepth;
        for (String neighbor : neighbors) {
            int depth = calculateMaxDepth(neighbor, graph, new HashSet<>(visited), currentDepth + 1);
            maxDepth = Math.max(maxDepth, depth);
        }
        
        return maxDepth;
    }
    
    private Set<String> findUpstream(String methodKey, Set<String> visited, int depth) {
        if (depth > 10 || visited.contains(methodKey)) {
            return visited;
        }
        
        Set<String> callers = reverseCallGraph.get(methodKey);
        if (callers == null || callers.isEmpty()) {
            return visited;
        }
        
        for (String caller : callers) {
            if (!visited.contains(caller) && !caller.equals(methodKey)) {
                visited.add(caller);
                findUpstream(caller, visited, depth + 1);
            }
        }
        
        return visited;
    }
    
    private Set<String> findDownstream(String methodKey, Set<String> visited, int depth) {
        if (depth > 10 || visited.contains(methodKey)) {
            return visited;
        }
        
        Set<String> callees = callGraph.get(methodKey);
        if (callees == null || callees.isEmpty()) {
            return visited;
        }
        
        for (String callee : callees) {
            if (!visited.contains(callee) && !callee.equals(methodKey)) {
                visited.add(callee);
                findDownstream(callee, visited, depth + 1);
            }
        }
        
        return visited;
    }
    
    private void printCallChain(Set<String> methods, String prefix) {
        List<String> sortedMethods = new ArrayList<>(methods);
        Collections.sort(sortedMethods);
        
        int count = 0;
        for (String method : sortedMethods) {
            count++;
            MethodInfo info = methodRegistry.get(method);
            if (info != null) {
                System.out.println(prefix + count + ". " + method);
                System.out.println(prefix + "   └─ " + info.fileName + ":" + info.lineNumber);
            } else {
                System.out.println(prefix + count + ". " + method + " (外部方法)");
            }
        }
    }
    
    private Set<String> findMatchingMethods(String input) {
        Set<String> matches = new HashSet<>();
        
        if (methodRegistry.containsKey(input)) {
            matches.add(input);
            return matches;
        }
        
        for (String key : methodRegistry.keySet()) {
            if (key.endsWith("." + input) || key.equals(input)) {
                matches.add(key);
            }
        }
        
        return matches;
    }
    
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    static class MethodInfo {
        String className;
        String methodName;
        String fileName;
        int lineNumber;
        String packageName;
        
        MethodInfo(String className, String methodName, String fileName, 
                  int lineNumber, String packageName) {
            this.className = className;
            this.methodName = methodName;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
            this.packageName = packageName;
        }
    }
}

