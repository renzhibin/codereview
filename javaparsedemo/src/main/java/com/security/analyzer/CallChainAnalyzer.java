package com.security.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;


/**
 * 方法调用链分析器
 * 可以追踪方法的上下游调用关系，包括间接调用
 */
public class CallChainAnalyzer {
    
    // 存储所有方法定义: key = 类名.方法名, value = MethodInfo
    private final Map<String, MethodInfo> methodRegistry = new HashMap<>();
    
    // 存储调用关系: key = 调用者, value = 被调用的方法列表
    private final Map<String, List<String>> callGraph = new HashMap<>();
    
    // 存储反向调用关系: key = 被调用方法, value = 调用者列表
    private final Map<String, List<String>> reverseCallGraph = new HashMap<>();
    
    private final Path projectPath;
    
    public CallChainAnalyzer(String projectPath) {
        this.projectPath = Paths.get(projectPath);
    }
    
    public static void main(String[] args) {
        System.out.println("=== 方法调用链分析器 ===\n");
        
        String targetProject = "../springboot-vuln-demo/src/main/java";
        if (args.length > 0) {
            targetProject = args[0];
        }
        
        CallChainAnalyzer analyzer = new CallChainAnalyzer(targetProject);
        
        // 第1步：扫描所有方法
        analyzer.scanProject();
        
        // 第2步：分析调用关系
        analyzer.buildCallGraph();
        
        // 第3步：查询调用链
        System.out.println("\n=== 示例查询 ===\n");
        
        // 查找谁调用了 findById
        analyzer.findCallersOf("findById", 3);
        
        System.out.println("\n==================================================\n");
        
        // 查找 getUserById 调用了什么
        analyzer.findCalleesOf("getUserById", 3);
        
        System.out.println("\n==================================================\n");
        
        // 完整的调用链
        analyzer.findFullChain("getUserById", "findById");
    }
    
    /**
     * 扫描项目，收集所有方法定义
     */
    public void scanProject() {
        System.out.println("正在扫描项目: " + projectPath.toAbsolutePath());
        
        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(this::parseFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        System.out.println("扫描完成！找到 " + methodRegistry.size() + " 个方法\n");
    }
    
    /**
     * 解析单个文件
     */
    private void parseFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            
            // 收集所有方法定义
            cu.accept(new VoidVisitorAdapter<Void>() {
                private String currentClass = "";
                
                @Override
                public void visit(ClassOrInterfaceDeclaration cls, Void arg) {
                    currentClass = cls.getNameAsString();
                    super.visit(cls, arg);
                }
                
                @Override
                public void visit(MethodDeclaration method, Void arg) {
                    super.visit(method, arg);
                    
                    String methodKey = currentClass + "." + method.getNameAsString();
                    methodRegistry.put(methodKey, new MethodInfo(
                        currentClass,
                        method.getNameAsString(),
                        filePath.toString(),
                        method.getBegin().get().line
                    ));
                    
                    // 同时记录不带类名的方法（用于快速查找）
                    methodRegistry.putIfAbsent(method.getNameAsString(), 
                        methodRegistry.get(methodKey));
                }
            }, null);
            
        } catch (IOException e) {
            System.err.println("解析文件失败: " + filePath);
        }
    }
    
    /**
     * 构建调用图
     */
    public void buildCallGraph() {
        System.out.println("正在构建调用图...");
        
        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(this::analyzeMethodCalls);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        System.out.println("调用图构建完成！找到 " + callGraph.size() + " 个调用关系\n");
    }
    
    /**
     * 分析方法调用关系
     */
    private void analyzeMethodCalls(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            
            cu.accept(new VoidVisitorAdapter<Void>() {
                private String currentMethod = "";
                
                @Override
                public void visit(MethodDeclaration method, Void arg) {
                    currentMethod = method.getNameAsString();
                    super.visit(method, arg);
                }
                
                @Override
                public void visit(MethodCallExpr methodCall, Void arg) {
                    super.visit(methodCall, arg);
                    
                    String calledMethod = methodCall.getNameAsString();
                    
                    // 记录调用关系
                    callGraph.computeIfAbsent(currentMethod, k -> new ArrayList<>())
                             .add(calledMethod);
                    
                    // 记录反向调用关系
                    reverseCallGraph.computeIfAbsent(calledMethod, k -> new ArrayList<>())
                                    .add(currentMethod);
                }
            }, null);
            
        } catch (IOException e) {
            System.err.println("分析文件失败: " + filePath);
        }
    }
    
    /**
     * 查找谁调用了指定方法（上游）
     * @param methodName 方法名
     * @param depth 追踪深度（几层间接调用）
     */
    public void findCallersOf(String methodName, int depth) {
        System.out.println("📥 查找谁调用了 '" + methodName + "' (深度=" + depth + "):\n");
        
        Set<String> visited = new HashSet<>();
        findCallersRecursive(methodName, depth, 0, "", visited);
    }
    
    private void findCallersRecursive(String methodName, int maxDepth, int currentDepth, 
                                      String indent, Set<String> visited) {
        if (currentDepth > maxDepth || visited.contains(methodName)) {
            return;
        }
        
        visited.add(methodName);
        
        List<String> callers = reverseCallGraph.get(methodName);
        if (callers == null || callers.isEmpty()) {
            if (currentDepth == 0) {
                System.out.println(indent + "❌ 没有找到调用者");
            }
            return;
        }
        
        for (String caller : callers) {
            MethodInfo info = methodRegistry.get(caller);
            if (info != null) {
                System.out.println(indent + "└─ " + caller + " (在 " + info.fileName + ":" + info.line + ")");
            } else {
                System.out.println(indent + "└─ " + caller);
            }
            
            // 递归查找上游
            findCallersRecursive(caller, maxDepth, currentDepth + 1, indent + "   ", visited);
        }
    }
    
    /**
     * 查找指定方法调用了什么（下游）
     * @param methodName 方法名
     * @param depth 追踪深度
     */
    public void findCalleesOf(String methodName, int depth) {
        System.out.println("📤 查找 '" + methodName + "' 调用了什么 (深度=" + depth + "):\n");
        
        Set<String> visited = new HashSet<>();
        findCalleesRecursive(methodName, depth, 0, "", visited);
    }
    
    private void findCalleesRecursive(String methodName, int maxDepth, int currentDepth,
                                      String indent, Set<String> visited) {
        if (currentDepth > maxDepth || visited.contains(methodName)) {
            return;
        }
        
        visited.add(methodName);
        
        List<String> callees = callGraph.get(methodName);
        if (callees == null || callees.isEmpty()) {
            if (currentDepth == 0) {
                System.out.println(indent + "❌ 没有找到被调用的方法");
            }
            return;
        }
        
        for (String callee : callees) {
            MethodInfo info = methodRegistry.get(callee);
            if (info != null) {
                System.out.println(indent + "└─ " + callee + " (在 " + info.fileName + ":" + info.line + ")");
            } else {
                System.out.println(indent + "└─ " + callee);
            }
            
            // 递归查找下游
            findCalleesRecursive(callee, maxDepth, currentDepth + 1, indent + "   ", visited);
        }
    }
    
    /**
     * 查找从方法A到方法B的完整调用链
     */
    public void findFullChain(String from, String to) {
        System.out.println("🔗 查找从 '" + from + "' 到 '" + to + "' 的完整调用链:\n");
        
        List<List<String>> allPaths = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        currentPath.add(from);
        findPathsDFS(from, to, currentPath, visited, allPaths, 10);
        
        if (allPaths.isEmpty()) {
            System.out.println("❌ 没有找到调用链");
        } else {
            System.out.println("✅ 找到 " + allPaths.size() + " 条调用链:\n");
            for (int i = 0; i < allPaths.size(); i++) {
                System.out.println("路径 " + (i + 1) + ":");
                printPath(allPaths.get(i));
                System.out.println();
            }
        }
    }
    
    private void findPathsDFS(String current, String target, List<String> currentPath,
                              Set<String> visited, List<List<String>> allPaths, int maxDepth) {
        if (currentPath.size() > maxDepth) {
            return;
        }
        
        if (current.equals(target)) {
            allPaths.add(new ArrayList<>(currentPath));
            return;
        }
        
        visited.add(current);
        
        List<String> callees = callGraph.get(current);
        if (callees != null) {
            for (String callee : callees) {
                if (!visited.contains(callee)) {
                    currentPath.add(callee);
                    findPathsDFS(callee, target, currentPath, visited, allPaths, maxDepth);
                    currentPath.remove(currentPath.size() - 1);
                }
            }
        }
        
        visited.remove(current);
    }
    
    private void printPath(List<String> path) {
        for (int i = 0; i < path.size(); i++) {
            String method = path.get(i);
            MethodInfo info = methodRegistry.get(method);
            
            if (info != null) {
                System.out.print("  " + method + " (" + info.fileName.substring(
                    info.fileName.lastIndexOf('/') + 1) + ":" + info.line + ")");
            } else {
                System.out.print("  " + method);
            }
            
            if (i < path.size() - 1) {
                System.out.println(" →");
            }
        }
        System.out.println();
    }
    
    /**
     * 方法信息类
     */
    static class MethodInfo {
        String className;
        String methodName;
        String fileName;
        int line;
        
        MethodInfo(String className, String methodName, String fileName, int line) {
            this.className = className;
            this.methodName = methodName;
            this.fileName = fileName;
            this.line = line;
        }
    }
}

