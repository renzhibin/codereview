#!/bin/bash

# JavaParser 快速开始脚本

echo "╔════════════════════════════════════════════════════════╗"
echo "║     JavaParser 安全分析工具 - 快速开始              ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: 未找到Maven，请先安装Maven"
    exit 1
fi

echo "✅ 检测到Maven: $(mvn -version | head -1)"
echo ""

# 编译项目
echo "📦 步骤1/3: 编译项目..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"
echo ""

# 运行安全分析
echo "🔍 步骤2/3: 运行安全漏洞分析..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer" -q

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 询问是否运行其他分析器
echo "📊 步骤3/3: 更多分析选项"
echo ""
echo "是否运行其他分析器？"
echo "  1) 方法复杂度分析器"
echo "  2) AST可视化工具"
echo "  3) 全部运行"
echo "  4) 跳过"
echo ""

read -p "请选择 (1-4): " choice

case $choice in
    1)
        echo ""
        echo "📊 运行方法复杂度分析器..."
        echo ""
        mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer" -q
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
        
        echo ""
        echo "🌳 运行AST可视化工具..."
        echo ""
        mvn exec:java -Dexec.mainClass="com.security.analyzer.ASTVisualizer" -q
        ;;
    4)
        echo "跳过其他分析"
        ;;
    *)
        echo "无效选择"
        ;;
esac

echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║                   分析完成！                          ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""
echo "📖 查看更多示例: cat DEMO_EXAMPLE.md"
echo "📚 使用指南: cat USAGE.md"
echo "📄 项目说明: cat README.md"
echo ""

