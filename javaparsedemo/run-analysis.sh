#!/bin/bash

# JavaParser 分析工具运行脚本

echo "==================================="
echo "  JavaParser 安全分析工具套件"
echo "==================================="
echo ""

# 检查Maven是否安装
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到Maven，请先安装Maven"
    exit 1
fi

# 编译项目
echo "📦 编译项目..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"
echo ""

# 提供选项菜单
echo "请选择要运行的分析器:"
echo "1) 安全漏洞分析器 (SecurityAnalyzer)"
echo "2) AST可视化工具 (ASTVisualizer)"
echo "3) 方法复杂度分析器 (MethodAnalyzer)"
echo "4) 运行所有分析器"
echo ""

read -p "请输入选项 (1-4): " choice

case $choice in
    1)
        echo ""
        echo "🔍 运行安全漏洞分析器..."
        echo ""
        mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" -q
        ;;
    2)
        echo ""
        echo "🌳 运行AST可视化工具..."
        echo ""
        mvn exec:java -Dexec.mainClass="com.security.analyzer.ASTVisualizer" -q
        ;;
    3)
        echo ""
        echo "📊 运行方法复杂度分析器..."
        echo ""
        mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer" -q
        ;;
    4)
        echo ""
        echo "🔍 运行安全漏洞分析器..."
        echo ""
        mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" -q
        
        echo ""
        echo "按回车键继续..."
        read
        
        echo ""
        echo "🌳 运行AST可视化工具..."
        echo ""
        mvn exec:java -Dexec.mainClass="com.security.analyzer.ASTVisualizer" -q
        
        echo ""
        echo "按回车键继续..."
        read
        
        echo ""
        echo "📊 运行方法复杂度分析器..."
        echo ""
        mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer" -q
        ;;
    *)
        echo "无效选项"
        exit 1
        ;;
esac

echo ""
echo "==================================="
echo "  分析完成"
echo "==================================="

