# Tree-sitter 方法调用链分析器

基于 Tree-sitter 的 Java 方法调用链分析工具，支持：
- ✅ 查找方法的所有调用者（上游）
- ✅ 查找方法调用了什么（下游）
- ✅ 查找完整的调用链（A→B→C→D）
- ✅ 支持间接调用追踪（递归分析）
- ✅ 跨文件分析

## 🚀 快速开始

### 1. 安装依赖

```bash
# 安装 Tree-sitter 和 Java 语法支持
pip3 install --user tree-sitter tree-sitter-java
```

### 2. 运行分析

```bash
# 分析 springboot-vuln-demo 项目
python3 callchain_analyzer.py

# 或指定其他项目路径
python3 callchain_analyzer.py /path/to/your/java/project
```

## 📊 功能演示

### 1️⃣ 查找方法的调用者（上游）

找出谁调用了 `findById` 方法：

```python
analyzer.find_callers("findById", max_depth=3)
```

输出：
```
📥 查找谁调用了 'findById' (深度=3):

└─ getUserById (在 UserController.java:23)
└─ updateUserRole (在 UserController.java:42)
└─ getOrderById (在 OrderController.java:22)
└─ updateOrder (在 OrderController.java:48)
└─ deleteOrder (在 OrderController.java:77)
```

### 2️⃣ 查找方法调用了什么（下游）

找出 `getUserById` 调用了哪些方法：

```python
analyzer.find_callees("getUserById", max_depth=3)
```

输出：
```
📤 查找 'getUserById' 调用了什么 (深度=3):

└─ findById
└─ isPresent
└─ get
└─ getId
└─ getUsername
└─ getEmail
└─ getRole
└─ ok
└─ notFound
└─ build
```

### 3️⃣ 查找完整调用链

找出从 `getUserById` 到 `findById` 的完整路径：

```python
analyzer.find_call_chain("getUserById", "findById")
```

输出：
```
🔗 查找从 'getUserById' 到 'findById' 的调用链:

✅ 找到 1 条调用链:

路径 1:
  getUserById (UserController.java:23) →
  findById
```

### 4️⃣ 完整分析一个方法

```python
analyzer.analyze_method("updateUserRole")
```

输出完整的上下游调用关系。

## 🎯 使用场景

### 场景1：安全审计 - 追踪数据流

```python
# 追踪用户输入如何流向危险函数
analyzer.find_call_chain("getUserById", "setRole")
```

### 场景2：代码重构 - 影响分析

```python
# 查看修改某个方法会影响哪些调用者
analyzer.find_callers("findById", max_depth=5)
```

### 场景3：漏洞分析 - 完整攻击路径

```python
# 从 Controller 到 Repository 的完整路径
analyzer.find_call_chain("updateOrder", "save")
```

## 📋 API 文档

### CallChainAnalyzer 类

```python
analyzer = CallChainAnalyzer(project_path)
```

#### 方法

| 方法 | 参数 | 说明 |
|------|------|------|
| `scan_project()` | 无 | 扫描项目，构建调用图 |
| `find_callers(method, depth)` | method: 方法名<br>depth: 追踪深度 | 查找谁调用了该方法 |
| `find_callees(method, depth)` | method: 方法名<br>depth: 追踪深度 | 查找该方法调用了什么 |
| `find_call_chain(from, to, depth)` | from: 起始方法<br>to: 目标方法<br>depth: 最大深度 | 查找完整调用链 |
| `analyze_method(method)` | method: 方法名 | 完整分析方法 |

## 🔧 自定义使用

### 示例：查找所有控制器方法的调用链

```python
from callchain_analyzer import CallChainAnalyzer

analyzer = CallChainAnalyzer("../springboot-vuln-demo/src/main/java")
analyzer.scan_project()

# 查找所有控制器方法
controller_methods = [
    "getUserById",
    "updateUserRole", 
    "getOrderById",
    "updateOrder",
    "deleteOrder"
]

for method in controller_methods:
    print(f"\n{'='*60}")
    print(f"分析方法: {method}")
    print('='*60)
    analyzer.find_callees(method, max_depth=5)
```

### 示例：导出调用图为 JSON

```python
import json

# 导出调用关系
call_graph_data = {
    "methods": {
        name: {
            "file": info.file_path,
            "line": info.line,
            "class": info.class_name
        }
        for name, info in analyzer.methods.items()
    },
    "calls": dict(analyzer.call_graph)
}

with open("call_graph.json", "w") as f:
    json.dump(call_graph_data, f, indent=2)
```

## 🆚 与其他工具对比

| 特性 | Tree-sitter | JavaParser | Semgrep |
|------|-------------|------------|---------|
| 速度 | ⚡⚡⚡ 超快 | ⚡⚡ 快 | ⚡⚡ 快 |
| 调用链追踪 | ✅ 支持 | ✅ 支持 | ❌ 不支持 |
| 学习曲线 | 🟡 中等 | 🟡 中等 | 🟢 简单 |
| 多语言支持 | ✅ 40+ 语言 | ❌ 仅 Java | ✅ 30+ 语言 |
| 自定义扩展 | ✅ 容易 | ✅ 容易 | ⚠️ 较难 |

## 🐛 已知限制

1. **方法重载**：暂不区分同名但参数不同的方法
2. **Lambda 表达式**：暂不完全支持 lambda 中的调用
3. **反射调用**：无法追踪反射调用
4. **跨模块**：需要所有源码在同一目录

## 📚 扩展阅读

- [Tree-sitter 官方文档](https://tree-sitter.github.io/)
- [tree-sitter-java GitHub](https://github.com/tree-sitter/tree-sitter-java)
- [Python 绑定文档](https://github.com/tree-sitter/py-tree-sitter)

## 🤝 贡献

欢迎提 Issue 和 PR！

## 📄 许可证

MIT License

