package com.security.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 代码上下文提取工具
 * 提取Java代码的完整上下文信息，包括源文件、相关类、方法调用链等
 */
public class ContextExtractor {
    
    private static final int MAX_RELATED_FILES = 10; // 最多提取10个相关文件
    private static final int MAX_FILE_SIZE = 500 * 1024; // 最大文件大小500KB
    
    public static void main(String[] args) {
        try {
            // 解析命令行参数
            String repoPath = getArg(args, "--repo-path");
            String changedFilesStr = getArg(args, "--changed-files");
            
            if (repoPath == null || changedFilesStr == null) {
                printUsage();
                System.exit(1);
            }
            
            String[] changedFiles = changedFilesStr.split(",");
            
            // 分析上下文
            ContextResult result = analyzeContext(repoPath, changedFiles);
            
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
    
    private static ContextResult analyzeContext(String repoPath, String[] changedFiles) {
        ContextResult result = new ContextResult();
        Set<String> processedImports = new HashSet<>();
        
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
                fileCtx.methods = new ArrayList<>();
                fileCtx.annotations = new ArrayList<>();
                
                // 2. 提取类信息
                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                    fileCtx.className = cls.getNameAsString();
                    // 提取类注解
                    cls.getAnnotations().forEach(ann -> {
                        fileCtx.annotations.add(ann.getNameAsString());
                    });
                });
                
                // 3. 提取方法信息和注解
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodDeclaration method, Void arg) {
                        super.visit(method, arg);
                        MethodInfo mi = new MethodInfo();
                        mi.name = method.getNameAsString();
                        mi.annotations = method.getAnnotations().stream()
                                .map(AnnotationExpr::getNameAsString)
                                .collect(Collectors.toList());
                        mi.signature = method.getDeclarationAsString(false, false, false);
                        fileCtx.methods.add(mi);
                    }
                }, null);
                
                result.changedFiles.add(fileCtx);
                
                // 4. 分析import的类（限制数量）
                if (result.relatedFiles.size() < MAX_RELATED_FILES) {
                    List<ImportDeclaration> imports = cu.getImports();
                    for (ImportDeclaration imp : imports) {
                        if (result.relatedFiles.size() >= MAX_RELATED_FILES) {
                            break;
                        }
                        
                        String importName = imp.getNameAsString();
                        
                        // 跳过已处理的import和标准库
                        if (processedImports.contains(importName) || 
                            importName.startsWith("java.") || 
                            importName.startsWith("javax.") ||
                            importName.startsWith("org.springframework.web.bind.annotation.")) {
                            continue;
                        }
                        
                        processedImports.add(importName);
                        
                        // 查找import对应的文件
                        String importFilePath = findImportFile(repoPath, importName);
                        if (importFilePath != null) {
                            File importFile = new File(repoPath, importFilePath);
                            if (importFile.exists() && importFile.length() < MAX_FILE_SIZE) {
                                RelatedFile rf = new RelatedFile();
                                rf.path = importFilePath;
                                rf.fullContent = readFile(importFile);
                                rf.reason = "imported by " + relativeFilePath;
                                result.relatedFiles.add(rf);
                            }
                        }
                    }
                }
                
                // 5. 分析方法调用链（简化版）
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodCallExpr call, Void arg) {
                        super.visit(call, arg);
                        try {
                            String callChain = buildCallChain(call);
                            if (callChain != null && !callChain.isEmpty()) {
                                result.callChains.add(callChain);
                            }
                        } catch (Exception e) {
                            // 忽略调用链构建错误
                        }
                    }
                }, null);
                
            } catch (IOException e) {
                System.err.println("警告: 解析文件失败: " + file.getAbsolutePath() + ", 原因: " + e.getMessage());
            }
        }
        
        // 去重调用链
        result.callChains = result.callChains.stream()
                .distinct()
                .limit(20) // 最多20条
                .collect(Collectors.toList());
        
        return result;
    }
    
    private static String readFile(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            return "// 读取文件失败: " + e.getMessage();
        }
    }
    
    private static String findImportFile(String repoPath, String importName) {
        // 将包名转换为文件路径
        // 例如: com.example.User -> src/main/java/com/example/User.java
        String[] parts = importName.split("\\.");
        String className = parts[parts.length - 1];
        String packagePath = String.join("/", Arrays.copyOf(parts, parts.length - 1));
        
        // 尝试常见的源码路径
        String[] possiblePaths = {
            "src/main/java/" + packagePath + "/" + className + ".java",
            "src/java/" + packagePath + "/" + className + ".java",
            "src/" + packagePath + "/" + className + ".java",
            packagePath + "/" + className + ".java"
        };
        
        for (String path : possiblePaths) {
            File file = new File(repoPath, path);
            if (file.exists() && file.isFile()) {
                return path;
            }
        }
        
        return null;
    }
    
    private static String buildCallChain(MethodCallExpr call) {
        try {
            StringBuilder chain = new StringBuilder();
            
            // 获取方法名
            String methodName = call.getNameAsString();
            
            // 获取调用者（如果有）
            if (call.getScope().isPresent()) {
                String scope = call.getScope().get().toString();
                // 简化scope（去掉复杂表达式）
                if (scope.length() < 50) {
                    chain.append(scope).append(".");
                }
            }
            
            chain.append(methodName).append("()");
            
            return chain.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    // 数据类
    static class ContextResult {
        List<FileContext> changedFiles = new ArrayList<>();
        List<RelatedFile> relatedFiles = new ArrayList<>();
        List<String> callChains = new ArrayList<>();
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
}

