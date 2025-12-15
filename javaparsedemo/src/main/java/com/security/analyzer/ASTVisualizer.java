package com.security.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * AST 可视化工具
 * 展示 Java 源代码的抽象语法树结构
 */
public class ASTVisualizer {
    
    private int indentLevel = 0;
    private static final String INDENT = "  ";
    
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
        System.out.println("=== JavaParser AST 可视化工具 ===\n");
        
        String targetProject = "../springboot-vuln-demo/src/main/java";
        if (args.length > 0) {
            targetProject = args[0];
        }
        
        ASTVisualizer visualizer = new ASTVisualizer();
        visualizer.visualizeProject(targetProject);
    }
    
    /**
     * 可视化整个项目
     */
    public void visualizeProject(String projectPath) {
        Path path = Paths.get(projectPath);
        System.out.println("正在分析项目: " + path.toAbsolutePath() + "\n");
        
        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(this::visualizeFile);
        } catch (IOException e) {
            System.err.println("错误: 无法读取项目目录 - " + e.getMessage());
        }
    }
    
    /**
     * 可视化单个文件
     */
    public void visualizeFile(Path filePath) {
        try {
            System.out.println("\n" + repeat("=", 80));
            System.out.println("📄 文件: " + filePath.getFileName());
            System.out.println(repeat("=", 80));
            
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            
            // 显示包信息
            cu.getPackageDeclaration().ifPresent(pkg -> {
                System.out.println("\n📦 包: " + pkg.getNameAsString());
            });
            
            // 显示导入
            if (!cu.getImports().isEmpty()) {
                System.out.println("\n📥 导入:");
                for (ImportDeclaration imp : cu.getImports()) {
                    System.out.println("  - " + imp.getNameAsString() + 
                                     (imp.isStatic() ? " (static)" : "") +
                                     (imp.isAsterisk() ? ".*" : ""));
                }
            }
            
            // 遍历AST
            cu.accept(new ClassVisitor(), null);
            
        } catch (IOException e) {
            System.err.println("错误: 无法解析文件 - " + e.getMessage());
        }
    }
    
    /**
     * 打印缩进
     */
    private void printIndent() {
        System.out.print(repeat(INDENT, indentLevel));
    }
    
    /**
     * 类/接口访问器
     */
    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
            System.out.println("\n" + (cid.isInterface() ? "🏛️  接口: " : "🏛️  类: ") + 
                             cid.getNameAsString());
            
            // 显示修饰符
            if (!cid.getModifiers().isEmpty()) {
                System.out.print("  修饰符: ");
                System.out.println(cid.getModifiers());
            }
            
            // 显示继承
            if (!cid.getExtendedTypes().isEmpty()) {
                System.out.print("  继承: ");
                cid.getExtendedTypes().forEach(t -> System.out.print(t.getNameAsString() + " "));
                System.out.println();
            }
            
            // 显示实现
            if (!cid.getImplementedTypes().isEmpty()) {
                System.out.print("  实现: ");
                cid.getImplementedTypes().forEach(t -> System.out.print(t.getNameAsString() + " "));
                System.out.println();
            }
            
            // 显示注解
            if (!cid.getAnnotations().isEmpty()) {
                System.out.println("  注解:");
                for (AnnotationExpr ann : cid.getAnnotations()) {
                    System.out.println("    @" + ann.getNameAsString());
                }
            }
            
            indentLevel++;
            super.visit(cid, arg);
            indentLevel--;
        }
        
        @Override
        public void visit(EnumDeclaration ed, Void arg) {
            System.out.println("\n📋 枚举: " + ed.getNameAsString());
            
            if (!ed.getEntries().isEmpty()) {
                System.out.println("  值:");
                for (EnumConstantDeclaration entry : ed.getEntries()) {
                    System.out.println("    - " + entry.getNameAsString());
                }
            }
            
            super.visit(ed, arg);
        }
        
        @Override
        public void visit(FieldDeclaration fd, Void arg) {
            printIndent();
            System.out.print("📋 字段: ");
            
            fd.getVariables().forEach(var -> {
                System.out.print(fd.getElementType() + " " + var.getNameAsString());
                var.getInitializer().ifPresent(init -> 
                    System.out.print(" = " + init));
                System.out.println();
            });
            
            // 显示字段注解
            if (!fd.getAnnotations().isEmpty()) {
                printIndent();
                System.out.print("  注解: ");
                fd.getAnnotations().forEach(ann -> 
                    System.out.print("@" + ann.getNameAsString() + " "));
                System.out.println();
            }
            
            super.visit(fd, arg);
        }
        
        @Override
        public void visit(ConstructorDeclaration cd, Void arg) {
            printIndent();
            System.out.println("🔧 构造方法: " + cd.getNameAsString() + 
                             "(" + getParameters(cd) + ")");
            
            // 显示注解
            if (!cd.getAnnotations().isEmpty()) {
                printIndent();
                System.out.print("  注解: ");
                cd.getAnnotations().forEach(ann -> 
                    System.out.print("@" + ann.getNameAsString() + " "));
                System.out.println();
            }
            
            super.visit(cd, arg);
        }
        
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            printIndent();
            System.out.println("🔧 方法: " + md.getNameAsString() + 
                             "(" + getParameters(md) + "): " + md.getTypeAsString());
            
            // 显示注解
            if (!md.getAnnotations().isEmpty()) {
                printIndent();
                System.out.print("  注解: ");
                md.getAnnotations().forEach(ann -> 
                    System.out.print("@" + ann.getNameAsString() + " "));
                System.out.println();
            }
            
            // 显示修饰符
            if (!md.getModifiers().isEmpty()) {
                printIndent();
                System.out.println("  修饰符: " + md.getModifiers());
            }
            
            // 显示方法体信息
            md.getBody().ifPresent(body -> {
                int stmtCount = body.getStatements().size();
                if (stmtCount > 0) {
                    printIndent();
                    System.out.println("  语句数: " + stmtCount);
                }
            });
            
            super.visit(md, arg);
        }
        
        /**
         * 获取参数列表字符串
         */
        private String getParameters(CallableDeclaration<?> callable) {
            if (callable.getParameters().isEmpty()) {
                return "";
            }
            
            StringBuilder params = new StringBuilder();
            for (int i = 0; i < callable.getParameters().size(); i++) {
                Parameter param = callable.getParameters().get(i);
                
                // 添加注解
                if (!param.getAnnotations().isEmpty()) {
                    param.getAnnotations().forEach(ann -> 
                        params.append("@").append(ann.getNameAsString()).append(" "));
                }
                
                params.append(param.getTypeAsString())
                      .append(" ")
                      .append(param.getNameAsString());
                
                if (i < callable.getParameters().size() - 1) {
                    params.append(", ");
                }
            }
            
            return params.toString();
        }
    }
}
