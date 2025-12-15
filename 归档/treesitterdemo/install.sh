#!/bin/bash
# Tree-sitter 调用链分析器安装脚本

echo "======================================"
echo "🌳 Tree-sitter 调用链分析器安装"
echo "======================================"
echo ""

# 检查 Python3
if ! command -v python3 &> /dev/null; then
    echo "❌ 未找到 Python3，请先安装 Python 3"
    exit 1
fi

echo "✅ 找到 Python: $(python3 --version)"
echo ""

# 安装依赖
echo "📦 正在安装依赖..."
echo ""

pip3 install --user --break-system-packages tree-sitter tree-sitter-java

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✅ 安装成功！"
    echo "======================================"
    echo ""
    echo "现在可以运行："
    echo "  python3 callchain_analyzer.py"
    echo ""
else
    echo ""
    echo "❌ 安装失败，请检查错误信息"
    exit 1
fi

