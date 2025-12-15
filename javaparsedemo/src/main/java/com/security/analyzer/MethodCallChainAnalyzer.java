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
 * 方法调用链分析器
 * 分析方法的上游调用者和下游被调用方法，支持递归穿透
 */
public class MethodCallChainAnalyzer {
    
    // 方法信息：类名.方法名 -> MethodInfo
    private final Map<String, MethodInfo> methodRegistry = new HashMap<>();
    
    // 调用关系：调用者 -> 被调用者列表
    private final Map<String, Set<String>> callGraph = new HashMap<>();
    
    // 反向调用关系：被调用者 -> 调用者列表
    private final Map<String, Set<String>> reverseCallGraph = new HashMap<>();
    
    private final Path projectPath;
    
    public MethodCallChainAnalyzer(String projectPath) {
        this.projectPath = Paths.get(projectPath);
    }
    
    public static void main(String[] args) {
        System.out.println("=== 方法调用链分析器 ===\n");
        
        String targetProject = "../springboot-vuln-demo/src/main/java";
        if (args.length > 0) {
            targetProject = args[0];
        }
        
        MethodCallChainAnalyzer analyzer = new MethodCallChainAnalyzer(targetProject);
        
        try {
            // 分析项目
            analyzer.analyze();
            
            // 交互式查询
            analyzer.interactiveQuery();
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 分析整个项目
     */
    public void analyze() {
        System.out.println("正在分析项目: " + projectPath.toAbsolutePath() + "\n");
        
        try (Stream<Path> paths = Files.walk(projectPath)) {
            List<Path> javaFiles = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFiles::add);
            
            System.out.println("找到 " + javaFiles.size() + " 个Java文件\n");
            
            // 第一遍：收集所有方法定义
            System.out.println("步骤1: 收集方法定义...");
            for (Path file : javaFiles) {
                collectMethodDefinitions(file);
            }
            System.out.println("收集到 " + methodRegistry.size() + " 个方法\n");
            
            // 第二遍：收集方法调用关系
            System.out.println("步骤2: 分析调用关系...");
            for (Path file : javaFiles) {
                collectMethodCalls(file);
            }
            System.out.println("构建调用图完成\n");
            
            // 统计信息
            printStatistics();
            
        } catch (IOException e) {
            System.err.println("错误: 无法读取项目目录 - " + e.getMessage());
        }
    }
    
    /**
     * 收集方法定义
     */
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
            // 忽略解析错误
        }
    }
    
    /**
     * 收集方法调用关系
     */
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
                        
                        // 记录调用关系
                        callGraph.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
                        reverseCallGraph.computeIfAbsent(callee, k -> new HashSet<>()).add(caller);
                    }
                }
            }, null);
            
        } catch (IOException e) {
            // 忽略解析错误
        }
    }
    
    /**
     * 解析方法调用
     */
    private String resolveMethodCall(MethodCallExpr call) {
        String methodName = call.getNameAsString();
        
        // 尝试获取调用的类名
        if (call.getScope().isPresent()) {
            String scope = call.getScope().get().toString();
            
            // 简单处理，可以扩展为更复杂的类型解析
            if (scope.contains("Repository") || scope.contains("Service") || 
                scope.contains("Controller")) {
                return scope + "." + methodName;
            }
        }
        
        // 如果无法确定类名，只返回方法名
        return "?." + methodName;
    }
    
    /**
     * 打印统计信息
     */
    private void printStatistics() {
        System.out.println("=== 统计信息 ===");
        System.out.println("总方法数: " + methodRegistry.size());
        System.out.println("有调用关系的方法: " + callGraph.size());
        System.out.println("被调用的方法: " + reverseCallGraph.size());
        
        // 找出调用最多的方法
        String mostCalling = callGraph.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().size()))
            .map(e -> e.getKey() + " (调用" + e.getValue().size() + "个方法)")
            .orElse("无");
        System.out.println("调用方法最多的: " + mostCalling);
        
        // 找出被调用最多的方法
        String mostCalled = reverseCallGraph.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().size()))
            .map(e -> e.getKey() + " (被" + e.getValue().size() + "个方法调用)")
            .orElse("无");
        System.out.println("被调用最多的: " + mostCalled);
        System.out.println();
    }
    
    /**
     * 交互式查询
     */
    private void interactiveQuery() {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.println("\n=== 方法调用链查询 ===");
            System.out.println("1. 查找方法的上游调用者（谁调用了它）");
            System.out.println("2. 查找方法的下游被调用方法（它调用了谁）");
            System.out.println("3. 查找完整调用链（上游+下游）");
            System.out.println("4. 列出所有可用方法");
            System.out.println("5. 搜索方法");
            System.out.println("0. 退出");
            System.out.print("\n请选择 (0-5): ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    queryUpstream(scanner);
                    break;
                case "2":
                    queryDownstream(scanner);
                    break;
                case "3":
                    queryFullChain(scanner);
                    break;
                case "4":
                    listAllMethods();
                    break;
                case "5":
                    searchMethods(scanner);
                    break;
                case "0":
                    System.out.println("退出程序");
                    return;
                default:
                    System.out.println("无效选项");
            }
        }
    }
    
    /**
     * 查询上游调用者
     */
    private void queryUpstream(Scanner scanner) {
        System.out.print("\n请输入方法名（格式: ClassName.methodName 或 methodName）: ");
        String input = scanner.nextLine().trim();
        
        Set<String> matchingMethods = findMatchingMethods(input);
        
        if (matchingMethods.isEmpty()) {
            System.out.println("未找到匹配的方法");
            return;
        }
        
        for (String methodKey : matchingMethods) {
            System.out.println("\n" + repeat("=", 80));
            System.out.println("方法: " + methodKey);
            
            MethodInfo info = methodRegistry.get(methodKey);
            if (info != null) {
                System.out.println("位置: " + info.fileName + ":" + info.lineNumber);
            }
            
            System.out.println(repeat("=", 80));
            
            Set<String> upstream = findUpstream(methodKey, new HashSet<>(), 0);
            
            if (upstream.isEmpty()) {
                System.out.println("没有找到上游调用者");
            } else {
                System.out.println("\n找到 " + upstream.size() + " 个上游调用者（包括间接调用）:\n");
                printCallChain(upstream, "上游");
            }
        }
    }
    
    /**
     * 查询下游被调用方法
     */
    private void queryDownstream(Scanner scanner) {
        System.out.print("\n请输入方法名（格式: ClassName.methodName 或 methodName）: ");
        String input = scanner.nextLine().trim();
        
        Set<String> matchingMethods = findMatchingMethods(input);
        
        if (matchingMethods.isEmpty()) {
            System.out.println("未找到匹配的方法");
            return;
        }
        
        for (String methodKey : matchingMethods) {
            System.out.println("\n" + repeat("=", 80));
            System.out.println("方法: " + methodKey);
            
            MethodInfo info = methodRegistry.get(methodKey);
            if (info != null) {
                System.out.println("位置: " + info.fileName + ":" + info.lineNumber);
            }
            
            System.out.println(repeat("=", 80));
            
            Set<String> downstream = findDownstream(methodKey, new HashSet<>(), 0);
            
            if (downstream.isEmpty()) {
                System.out.println("没有找到下游被调用方法");
            } else {
                System.out.println("\n找到 " + downstream.size() + " 个下游被调用方法（包括间接调用）:\n");
                printCallChain(downstream, "下游");
            }
        }
    }
    
    /**
     * 查询完整调用链
     */
    private void queryFullChain(Scanner scanner) {
        System.out.print("\n请输入方法名（格式: ClassName.methodName 或 methodName）: ");
        String input = scanner.nextLine().trim();
        
        Set<String> matchingMethods = findMatchingMethods(input);
        
        if (matchingMethods.isEmpty()) {
            System.out.println("未找到匹配的方法");
            return;
        }
        
        for (String methodKey : matchingMethods) {
            System.out.println("\n" + repeat("=", 80));
            System.out.println("方法: " + methodKey);
            
            MethodInfo info = methodRegistry.get(methodKey);
            if (info != null) {
                System.out.println("位置: " + info.fileName + ":" + info.lineNumber);
            }
            
            System.out.println(repeat("=", 80));
            
            // 上游
            Set<String> upstream = findUpstream(methodKey, new HashSet<>(), 0);
            System.out.println("\n【上游调用链】找到 " + upstream.size() + " 个上游调用者:\n");
            if (upstream.isEmpty()) {
                System.out.println("  (无上游调用者)");
            } else {
                printCallChain(upstream, "上游");
            }
            
            // 下游
            Set<String> downstream = findDownstream(methodKey, new HashSet<>(), 0);
            System.out.println("\n【下游调用链】找到 " + downstream.size() + " 个下游被调用方法:\n");
            if (downstream.isEmpty()) {
                System.out.println("  (无下游调用)");
            } else {
                printCallChain(downstream, "下游");
            }
        }
    }
    
    /**
     * 递归查找上游调用者
     */
    private Set<String> findUpstream(String methodKey, Set<String> visited, int depth) {
        if (depth > 10 || visited.contains(methodKey)) {
            return visited;
        }
        
        Set<String> callers = reverseCallGraph.get(methodKey);
        if (callers == null || callers.isEmpty()) {
            return visited;
        }
        
        for (String caller : callers) {
            if (!visited.contains(caller)) {
                visited.add(caller);
                // 递归查找
                findUpstream(caller, visited, depth + 1);
            }
        }
        
        return visited;
    }
    
    /**
     * 递归查找下游被调用方法
     */
    private Set<String> findDownstream(String methodKey, Set<String> visited, int depth) {
        if (depth > 10 || visited.contains(methodKey)) {
            return visited;
        }
        
        Set<String> callees = callGraph.get(methodKey);
        if (callees == null || callees.isEmpty()) {
            return visited;
        }
        
        for (String callee : callees) {
            if (!visited.contains(callee)) {
                visited.add(callee);
                // 递归查找
                findDownstream(callee, visited, depth + 1);
            }
        }
        
        return visited;
    }
    
    /**
     * 打印调用链
     */
    private void printCallChain(Set<String> methods, String direction) {
        List<String> sortedMethods = new ArrayList<>(methods);
        Collections.sort(sortedMethods);
        
        for (String method : sortedMethods) {
            MethodInfo info = methodRegistry.get(method);
            if (info != null) {
                System.out.println("  → " + method);
                System.out.println("     文件: " + info.fileName + ":" + info.lineNumber);
            } else {
                System.out.println("  → " + method + " (外部或未解析)");
            }
        }
    }
    
    /**
     * 查找匹配的方法
     */
    private Set<String> findMatchingMethods(String input) {
        Set<String> matches = new HashSet<>();
        
        // 精确匹配
        if (methodRegistry.containsKey(input)) {
            matches.add(input);
            return matches;
        }
        
        // 模糊匹配：只提供方法名
        for (String key : methodRegistry.keySet()) {
            if (key.endsWith("." + input) || key.equals(input)) {
                matches.add(key);
            }
        }
        
        return matches;
    }
    
    /**
     * 列出所有方法
     */
    private void listAllMethods() {
        System.out.println("\n所有可用方法:");
        System.out.println(repeat("-", 80));
        
        List<String> sortedMethods = new ArrayList<>(methodRegistry.keySet());
        Collections.sort(sortedMethods);
        
        for (String method : sortedMethods) {
            MethodInfo info = methodRegistry.get(method);
            System.out.printf("%-50s %s:%d\n", 
                method, info.fileName, info.lineNumber);
        }
    }
    
    /**
     * 搜索方法
     */
    private void searchMethods(Scanner scanner) {
        System.out.print("\n请输入搜索关键词: ");
        String keyword = scanner.nextLine().trim().toLowerCase();
        
        System.out.println("\n搜索结果:");
        System.out.println(repeat("-", 80));
        
        int count = 0;
        for (String method : methodRegistry.keySet()) {
            if (method.toLowerCase().contains(keyword)) {
                MethodInfo info = methodRegistry.get(method);
                System.out.printf("%-50s %s:%d\n", 
                    method, info.fileName, info.lineNumber);
                count++;
            }
        }
        
        System.out.println("\n找到 " + count + " 个匹配的方法");
    }
    
    /**
     * 重复字符串
     */
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    /**
     * 方法信息
     */
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

