#!/usr/bin/env python
"""
代码评审基准测试 - 运行脚本

使用方法:
  python3 tests/run_benchmark.py           # 快速测试（5个用例，并发）
  python3 tests/run_benchmark.py --all     # 完整测试（80个用例，并发）
  python3 tests/run_benchmark.py --all --serial  # 完整测试（串行）
"""
import sys
from pathlib import Path

# 添加项目路径
sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

# 直接运行测试框架
from test_review_benchmark import main

if __name__ == "__main__":
    # 如果没有参数，默认使用 --quick
    if len(sys.argv) == 1:
        sys.argv.append("--quick")
    
    sys.exit(main())

