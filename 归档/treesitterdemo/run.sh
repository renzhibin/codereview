#!/bin/bash
# 运行 Tree-sitter 调用链分析器

echo "======================================"
echo "🚀 启动调用链分析器"
echo "======================================"
echo ""

# 检查依赖
if ! python3 -c "import tree_sitter" 2>/dev/null; then
    echo "❌ 缺少依赖，正在安装..."
    echo ""
    ./install.sh
    echo ""
fi

# 运行分析器
python3 callchain_analyzer.py "$@"

