#!/bin/bash

echo "=== 方法调用链分析器 ==="
echo ""

cd "$(dirname "$0")"

# 确保已编译
if [ ! -d "target/classes" ]; then
    echo "编译项目..."
    mvn compile -q
fi

# 运行方法调用链分析器 - Demo版
echo "运行Demo版（自动分析几个关键方法）..."
echo ""
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainDemo" -q

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "💡 想要交互式查询？运行："
echo "   mvn exec:java -Dexec.mainClass=\"com.security.analyzer.MethodCallChainAnalyzer\""
echo ""

