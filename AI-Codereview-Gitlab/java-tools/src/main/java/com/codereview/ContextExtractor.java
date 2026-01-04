package com.codereview;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 代码上下文提取工具
 * 提取Java代码的完整上下文信息，包括源文件、相关类、方法调用链等
 */
public class ContextExtractor {
    
    private static final int MAX_RELATED_FILES = 10; // 最多提取10个相关文件
    private static final int MAX_FILE_SIZE = 500 * 1024; // 最大文件大小500KB
    private static final int DEFAULT_UP_DEPTH = 2;   // 向上追踪调用链的默认层数
    private static final int DEFAULT_DOWN_DEPTH = 2; // 向下追踪调用链的默认层数

    // 符号解析 & 调用图（简单缓存）
    private static CombinedTypeSolver TYPE_SOLVER;
    private static JavaSymbolSolver SYMBOL_SOLVER;
    /** from 方法 -> to 方法集合，key 形如 com.example.Foo#bar */
    private static final Map<String, Set<String>> CALL_GRAPH_DOWN = new HashMap<String, Set<String>>();
    /** to 方法 -> from 方法集合 */
    private static final Map<String, Set<String>> CALL_GRAPH_UP = new HashMap<String, Set<String>>();
    /** 方法 -> 源文件相对路径（例如 src/main/java/com/example/Foo.java） */
    private static final Map<String, String> METHOD_TO_FILE = new HashMap<String, String>();
    
    public static void main(String[] args) {
        try {
            // 解析命令行参数
            String repoPath = getArg(args, "--repo-path");
            String changedFilesStr = getArg(args, "--changed-files");
            String changedMethodsJson = getArg(args, "--changed-methods");
            
            if (repoPath == null || changedFilesStr == null) {
                printUsage();
                System.exit(1);
            }
            
            String[] changedFiles = changedFilesStr.split(",");

            // 解析方法级改动信息（可选）
            Map<String, List<String>> changedMethodsMap = null;
            if (changedMethodsJson != null && !changedMethodsJson.isEmpty()) {
                Gson gson = new Gson();
                // JSON结构: { "src/main/xx/Foo.java": ["methodA", "methodB"], ... }
                @SuppressWarnings("unchecked")
                Map<String, List<String>> tempMap = (Map<String, List<String>>) gson.fromJson(
                        changedMethodsJson,
                        Map.class
                );
                changedMethodsMap = tempMap;
            }

            // 分析上下文
            ContextResult result = analyzeContext(repoPath, changedFiles, changedMethodsMap);
            
            // 输出JSON格式
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static String getArg(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return args[i + 1];
            }
        }
        return null;
    }
    
    private static void printUsage() {
        System.err.println("用法: java ContextExtractor --repo-path <path> --changed-files <file1,file2,...>");
        System.err.println("示例: java ContextExtractor --repo-path /path/to/repo --changed-files src/main/java/User.java,src/main/java/UserController.java");
    }
    
    private static ContextResult analyzeContext(
            String repoPath,
            String[] changedFiles,
            Map<String, List<String>> changedMethodsMap
    ) {
        ContextResult result = new ContextResult();

        // 初始化符号解析和全局调用图（只构建一次，后续复用）
        initSymbolSolver(repoPath);
        buildGlobalCallGraph(repoPath);

        int upDepth = getIntEnv("CONTEXT_CALL_DEPTH_UP", DEFAULT_UP_DEPTH);
        int downDepth = getIntEnv("CONTEXT_CALL_DEPTH_DOWN", DEFAULT_DOWN_DEPTH);
        
        for (String relativeFilePath : changedFiles) {
            relativeFilePath = relativeFilePath.trim();
            
            // 跳过非Java文件
            if (!relativeFilePath.endsWith(".java")) {
                continue;
            }
            
            File file = new File(repoPath, relativeFilePath);
            if (!file.exists() || !file.isFile()) {
                System.err.println("警告: 文件不存在或不是文件: " + file.getAbsolutePath());
                continue;
            }
            
            try {
                // 1. 解析修改的文件
                CompilationUnit cu = StaticJavaParser.parse(file);
                
                FileContext fileCtx = new FileContext();
                fileCtx.path = relativeFilePath;
                fileCtx.fullContent = readFile(file);
                fileCtx.methods = new ArrayList<MethodInfo>();
                fileCtx.annotations = new ArrayList<String>();
                
                // 2. 提取类信息
                List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                for (ClassOrInterfaceDeclaration cls : classes) {
                    fileCtx.className = cls.getNameAsString();
                    // 提取类注解
                    for (AnnotationExpr ann : cls.getAnnotations()) {
                        fileCtx.annotations.add(ann.getNameAsString());
                    }
                }
                
                // 3. 提取方法信息和注解（文件内全量）
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodDeclaration method, Void arg) {
                        super.visit(method, arg);
                        MethodInfo mi = new MethodInfo();
                        mi.name = method.getNameAsString();
                        mi.annotations = new ArrayList<String>();
                        for (AnnotationExpr ann : method.getAnnotations()) {
                            mi.annotations.add(ann.getNameAsString());
                        }
                        mi.signature = method.getDeclarationAsString(false, false, false);
                        fileCtx.methods.add(mi);
                    }
                }, null);
                
                result.changedFiles.add(fileCtx);
                
                // 4. 以“被改方法”为中心，按调用链前后各 N 层提取相关文件
                Set<String> targetMethods = null;
                if (changedMethodsMap != null && changedMethodsMap.get(relativeFilePath) != null) {
                    targetMethods = new HashSet<String>(changedMethodsMap.get(relativeFilePath));
                }
                
                // 计算当前文件中“被改方法”的 methodKey 集合
                Set<String> startMethodKeys = new HashSet<String>();
                String packageName = cu.getPackageDeclaration()
                        .map(p -> p.getNameAsString())
                        .orElse("");
                for (ClassOrInterfaceDeclaration cls : classes) {
                    String className = cls.getNameAsString();
                    String classFqn = packageName.isEmpty() ? className : packageName + "." + className;

                    for (MethodDeclaration method : cls.getMethods()) {
                        String methodName = method.getNameAsString();
                        if (targetMethods == null || targetMethods.isEmpty() || targetMethods.contains(methodName)) {
                            String methodKey = buildMethodKey(classFqn, methodName);
                            startMethodKeys.add(methodKey);
                        }
                    }
                }

                // 基于调用图向上/向下各追踪 N 层，收集相关方法
                Set<String> relatedMethodKeys = collectRelatedMethods(startMethodKeys, upDepth, downDepth);
                
                // 构建调用链字符串（用于输出给LLM）
                List<String> chains = buildCallChains(startMethodKeys, relatedMethodKeys, upDepth, downDepth);
                result.callChains.addAll(chains);

                // 将相关方法所属文件加入 relatedFiles（限制数量）
                for (String methodKey : relatedMethodKeys) {
                    if (result.relatedFiles.size() >= MAX_RELATED_FILES) {
                        break;
                    }

                    String relatedFilePath = METHOD_TO_FILE.get(methodKey);
                    if (relatedFilePath == null) {
                        continue;
                    }

                    // 避免把当前 changed file 自己再作为 relatedFile 加入
                    if (relatedFilePath.equals(relativeFilePath)) {
                        continue;
                    }

                    // 去重：如果该文件已经作为 relatedFile 加入，则跳过
                    boolean exists = false;
                    for (RelatedFile rf : result.relatedFiles) {
                        if (rf.path.equals(relatedFilePath)) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) {
                        continue;
                    }

                    File relatedFile = new File(repoPath, relatedFilePath);
                    if (relatedFile.exists() && relatedFile.length() < MAX_FILE_SIZE) {
                        RelatedFile rf = new RelatedFile();
                        rf.path = relatedFilePath;
                        rf.fullContent = readFile(relatedFile);
                        rf.reason = "call-chain related to " + relativeFilePath;
                        result.relatedFiles.add(rf);
                    }
                }
                
            } catch (IOException e) {
                System.err.println("警告: 解析文件失败: " + file.getAbsolutePath() + ", 原因: " + e.getMessage());
            }
        }
        
        // 去重调用链
        Set<String> uniqueChains = new LinkedHashSet<String>(result.callChains);
        result.callChains = new ArrayList<String>(uniqueChains);
        if (result.callChains.size() > 20) {
            result.callChains = result.callChains.subList(0, 20);
        }
        
        return result;
    }

    // ===== 符号解析 & 全局调用图构建 =====

    private static void initSymbolSolver(String repoPath) {
        if (TYPE_SOLVER != null && SYMBOL_SOLVER != null) {
            return;
        }
        try {
            CombinedTypeSolver combined = new CombinedTypeSolver();
            // JDK / 标准库
            combined.add(new ReflectionTypeSolver(false));
            // 项目源码
            File srcMainJava = new File(repoPath, "src/main/java");
            if (srcMainJava.exists() && srcMainJava.isDirectory()) {
                combined.add(new JavaParserTypeSolver(srcMainJava));
            }
            TYPE_SOLVER = combined;
            SYMBOL_SOLVER = new JavaSymbolSolver(TYPE_SOLVER);
            StaticJavaParser.getConfiguration().setSymbolResolver(SYMBOL_SOLVER);
        } catch (Exception e) {
            // 符号解析失败时，后续调用链相关能力降级，不影响基本功能
            TYPE_SOLVER = null;
            SYMBOL_SOLVER = null;
        }
    }

    /**
     * 构建全局调用图：扫描 src/main/java 下的所有 Java 文件，
     * 为每个方法建立 from->to、to->from 的调用关系，以及方法到文件的映射。
     */
    private static void buildGlobalCallGraph(String repoPath) {
        // 已经构建过则直接返回（简单缓存）
        if (!CALL_GRAPH_DOWN.isEmpty() || !CALL_GRAPH_UP.isEmpty()) {
            return;
        }
        try {
            Path srcRoot = Paths.get(repoPath, "src/main/java");
            if (!Files.exists(srcRoot) || !Files.isDirectory(srcRoot)) {
                return;
            }

            Files.walk(srcRoot)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        File f = p.toFile();
                        String rel = srcRoot.relativize(p).toString().replace("\\", "/");
                        String relativePath = "src/main/java/" + rel;
                        try {
                            CompilationUnit cu = StaticJavaParser.parse(f);
                            String packageName = cu.getPackageDeclaration()
                                    .map(pd -> pd.getNameAsString())
                                    .orElse("");

                            List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                            for (ClassOrInterfaceDeclaration cls : classes) {
                                String className = cls.getNameAsString();
                                String classFqn = packageName.isEmpty() ? className : packageName + "." + className;

                                for (MethodDeclaration method : cls.getMethods()) {
                                    String methodName = method.getNameAsString();
                                    String methodKey = buildMethodKey(classFqn, methodName);

                                    METHOD_TO_FILE.put(methodKey, relativePath);

                                    // 在方法体内收集调用的其他方法（向下边）
                                    method.accept(new VoidVisitorAdapter<Void>() {
                                        @Override
                                        public void visit(MethodCallExpr call, Void arg) {
                                            super.visit(call, arg);
                                            if (TYPE_SOLVER == null || SYMBOL_SOLVER == null) {
                                                return;
                                            }
                                            try {
                                                ResolvedMethodDeclaration resolved = JavaParserFacade.get(TYPE_SOLVER)
                                                        .solve(call)
                                                        .getCorrespondingDeclaration();
                                                String calledClassFqn = resolved.declaringType().getQualifiedName();
                                                String calledMethodName = resolved.getName();
                                                String calledKey = buildMethodKey(calledClassFqn, calledMethodName);

                                                CALL_GRAPH_DOWN
                                                        .computeIfAbsent(methodKey, k -> new LinkedHashSet<String>())
                                                        .add(calledKey);
                                                CALL_GRAPH_UP
                                                        .computeIfAbsent(calledKey, k -> new LinkedHashSet<String>())
                                                        .add(methodKey);
                                            } catch (Exception e) {
                                                // 单个调用解析失败不影响整体
                                            }
                                        }
                                    }, null);
                                }
                            }
                        } catch (Exception e) {
                            // 单个文件解析失败不影响整体
                        }
                    });
        } catch (IOException e) {
            // 构建调用图失败时，调用链相关能力降级
        }
    }

    /**
     * 从起始方法集合出发，沿调用图向上/向下分别追踪指定层数，收集相关方法。
     */
    private static Set<String> collectRelatedMethods(Set<String> startMethods, int upDepth, int downDepth) {
        Set<String> related = new LinkedHashSet<String>();
        if (startMethods == null || startMethods.isEmpty()) {
            return related;
        }

        // 向下 BFS
        if (downDepth > 0) {
            Deque<MethodDepth> queue = new ArrayDeque<MethodDepth>();
            Set<String> visited = new HashSet<String>();
            for (String m : startMethods) {
                queue.add(new MethodDepth(m, 0));
                visited.add(m);
            }
            while (!queue.isEmpty()) {
                MethodDepth md = queue.poll();
                if (md.depth >= downDepth) {
                    continue;
                }
                Set<String> nexts = CALL_GRAPH_DOWN.get(md.methodKey);
                if (nexts == null) {
                    continue;
                }
                for (String to : nexts) {
                    if (visited.contains(to)) {
                        continue;
                    }
                    visited.add(to);
                    related.add(to);
                    queue.add(new MethodDepth(to, md.depth + 1));
                }
            }
        }

        // 向上 BFS
        if (upDepth > 0) {
            Deque<MethodDepth> queue = new ArrayDeque<MethodDepth>();
            Set<String> visited = new HashSet<String>();
            for (String m : startMethods) {
                queue.add(new MethodDepth(m, 0));
                visited.add(m);
            }
            while (!queue.isEmpty()) {
                MethodDepth md = queue.poll();
                if (md.depth >= upDepth) {
                    continue;
                }
                Set<String> uppers = CALL_GRAPH_UP.get(md.methodKey);
                if (uppers == null) {
                    continue;
                }
                for (String up : uppers) {
                    if (visited.contains(up)) {
                        continue;
                    }
                    visited.add(up);
                    related.add(up);
                    queue.add(new MethodDepth(up, md.depth + 1));
                }
            }
        }

        // 不把起始方法本身放进去（起点的方法所在文件已经在 changedFiles 里）
        related.removeAll(startMethods);
        return related;
    }

    private static String buildMethodKey(String classFqn, String methodName) {
        return classFqn + "#" + methodName;
    }

    private static int getIntEnv(String name, int defaultValue) {
        try {
            String v = System.getenv(name);
            if (v == null || v.isEmpty()) {
                return defaultValue;
            }
            return Integer.parseInt(v);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private static String readFile(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new String(bytes);
        } catch (IOException e) {
            return "// 读取文件失败: " + e.getMessage();
        }
    }
    
    /**
     * 构建调用链字符串列表
     * 从起始方法出发，构建到相关方法的调用链路径
     */
    private static List<String> buildCallChains(Set<String> startMethods, Set<String> relatedMethods, int upDepth, int downDepth) {
        List<String> chains = new ArrayList<String>();
        if (startMethods == null || startMethods.isEmpty()) {
            return chains;
        }
        
        // 构建向下调用链（startMethod -> relatedMethod）
        if (downDepth > 0) {
            for (String startMethod : startMethods) {
                Deque<MethodDepth> queue = new ArrayDeque<MethodDepth>();
                Set<String> visited = new HashSet<String>();
                queue.add(new MethodDepth(startMethod, 0));
                visited.add(startMethod);
                
                while (!queue.isEmpty()) {
                    MethodDepth md = queue.poll();
                    if (md.depth >= downDepth) {
                        continue;
                    }
                    
                    Set<String> nexts = CALL_GRAPH_DOWN.get(md.methodKey);
                    if (nexts == null) {
                        continue;
                    }
                    
                    for (String to : nexts) {
                        if (visited.contains(to)) {
                            continue;
                        }
                        if (relatedMethods.contains(to)) {
                            // 找到相关方法，构建调用链字符串
                            String chain = formatMethodKey(startMethod) + " -> " + formatMethodKey(to);
                            chains.add(chain);
                        }
                        visited.add(to);
                        queue.add(new MethodDepth(to, md.depth + 1));
                    }
                }
            }
        }
        
        // 构建向上调用链（relatedMethod -> startMethod）
        if (upDepth > 0) {
            for (String startMethod : startMethods) {
                Deque<MethodDepth> queue = new ArrayDeque<MethodDepth>();
                Set<String> visited = new HashSet<String>();
                queue.add(new MethodDepth(startMethod, 0));
                visited.add(startMethod);
                
                while (!queue.isEmpty()) {
                    MethodDepth md = queue.poll();
                    if (md.depth >= upDepth) {
                        continue;
                    }
                    
                    Set<String> uppers = CALL_GRAPH_UP.get(md.methodKey);
                    if (uppers == null) {
                        continue;
                    }
                    
                    for (String up : uppers) {
                        if (visited.contains(up)) {
                            continue;
                        }
                        if (relatedMethods.contains(up)) {
                            // 找到相关方法，构建调用链字符串
                            String chain = formatMethodKey(up) + " -> " + formatMethodKey(startMethod);
                            chains.add(chain);
                        }
                        visited.add(up);
                        queue.add(new MethodDepth(up, md.depth + 1));
                    }
                }
            }
        }
        
        return chains;
    }
    
    /**
     * 格式化方法key为可读字符串
     * 例如: com.example.Foo#bar -> Foo.bar()
     */
    private static String formatMethodKey(String methodKey) {
        if (methodKey == null || !methodKey.contains("#")) {
            return methodKey;
        }
        String[] parts = methodKey.split("#");
        if (parts.length != 2) {
            return methodKey;
        }
        String classFqn = parts[0];
        String methodName = parts[1];
        
        // 提取类名（最后一个点后的部分）
        String className = classFqn;
        int lastDot = classFqn.lastIndexOf('.');
        if (lastDot >= 0) {
            className = classFqn.substring(lastDot + 1);
        }
        
        return className + "." + methodName + "()";
    }
    
    // 数据类
    static class ContextResult {
        List<FileContext> changedFiles = new ArrayList<FileContext>();
        List<RelatedFile> relatedFiles = new ArrayList<RelatedFile>();
        List<String> callChains = new ArrayList<String>();
    }
    
    static class FileContext {
        String path;
        String fullContent;
        String className;
        List<String> annotations;
        List<MethodInfo> methods;
    }
    
    static class MethodInfo {
        String name;
        List<String> annotations;
        String signature;
    }
    
    static class RelatedFile {
        String path;
        String fullContent;
        String reason;
    }

    /**
     * 简单的 (methodKey, depth) 结构，用于 BFS
     */
    static class MethodDepth {
        String methodKey;
        int depth;

        MethodDepth(String methodKey, int depth) {
            this.methodKey = methodKey;
            this.depth = depth;
        }
    }
}

