#!/usr/bin/env python3
"""
Tree-sitter Java 调用链分析器
真正使用 Tree-sitter API 进行语法树分析
"""

import sys
from pathlib import Path
from collections import defaultdict
from typing import Dict, List, Set, Optional

try:
    from tree_sitter import Language, Parser, Node
    import tree_sitter_java as tsjava
except ImportError:
    print("❌ 请先安装: pip3 install --user --break-system-packages tree-sitter tree-sitter-java")
    sys.exit(1)


class TreeSitterAnalyzer:
    """使用 Tree-sitter 的 Java 分析器"""
    
    def __init__(self, project_path: str):
        self.project_path = Path(project_path)
        
        # 初始化 Tree-sitter Java 解析器
        self.java_language = Language(tsjava.language())
        self.parser = Parser(self.java_language)
        
        # 数据存储
        self.methods: Dict[str, 'MethodInfo'] = {}
        self.call_graph: Dict[str, Set[str]] = defaultdict(set)
        self.reverse_call_graph: Dict[str, Set[str]] = defaultdict(set)
    
    def analyze(self):
        """分析项目"""
        print(f"🔍 使用 Tree-sitter 扫描: {self.project_path}\n")
        
        java_files = list(self.project_path.rglob("*.java"))
        
        for file_path in java_files:
            self._analyze_file(file_path)
        
        print(f"✅ {len(self.methods)} 个方法")
        print(f"✅ {sum(len(v) for v in self.call_graph.values())} 个调用关系\n")
    
    def _analyze_file(self, file_path: Path):
        """使用 Tree-sitter 分析文件"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                code = f.read()
            
            # 移除注释（Tree-sitter 会把注释也解析进去）
            import re
            code_no_comments = re.sub(r'//.*?$', '', code, flags=re.MULTILINE)
            code_no_comments = re.sub(r'/\*.*?\*/', '', code_no_comments, flags=re.DOTALL)
            
            # 使用 Tree-sitter 解析
            tree = self.parser.parse(bytes(code_no_comments, "utf8"))
            
            # 遍历语法树（使用原始代码获取行号）
            self._traverse(tree.root_node, code_no_comments, file_path, None)
            
        except Exception as e:
            print(f"⚠️  {file_path.name}: {e}")
    
    def _traverse(self, node: Node, code: str, file_path: Path, current_method: Optional[str]):
        """递归遍历 Tree-sitter 语法树"""
        
        # 1. 识别方法定义
        if node.type == 'method_declaration':
            method_name = self._extract_method_name(node, code)
            if method_name and len(method_name) >= 3:
                line = node.start_point[0] + 1
                self.methods[method_name] = MethodInfo(
                    name=method_name,
                    file_path=str(file_path),
                    line=line
                )
                current_method = method_name
        
        # 2. 识别方法调用
        if node.type == 'method_invocation' and current_method:
            called_method = self._extract_call_name(node, code)
            if called_method and called_method != current_method:
                self.call_graph[current_method].add(called_method)
                self.reverse_call_graph[called_method].add(current_method)
        
        # 3. 递归处理子节点
        for child in node.children:
            self._traverse(child, code, file_path, current_method)
    
    def _extract_method_name(self, node: Node, code: str) -> Optional[str]:
        """从 method_declaration 节点提取方法名"""
        # method_declaration 结构：
        # - modifiers (public/private)
        # - type (返回类型)
        # - identifier (方法名) <-- 我们要这个
        # - formal_parameters (参数列表)
        # - block (方法体)
        
        for child in node.children:
            if child.type == 'identifier':
                method_name = code[child.start_byte:child.end_byte]
                return method_name
            # 遇到参数列表就停止，避免提取到参数名
            if child.type == 'formal_parameters':
                break
        
        return None
    
    def _extract_call_name(self, node: Node, code: str) -> Optional[str]:
        """从 method_invocation 节点提取被调用的方法名"""
        # method_invocation 结构：
        # - identifier (对象名或方法名)
        # - . (点号，可选)
        # - identifier (方法名)
        # - argument_list (参数)
        
        identifiers = []
        for child in node.children:
            if child.type == 'identifier':
                name = code[child.start_byte:child.end_byte]
                identifiers.append(name)
        
        # 如果有多个 identifier，最后一个通常是方法名
        # 例如：userRepository.findById() -> findById
        if identifiers:
            return identifiers[-1]
        
        return None
    
    def find_callers(self, method: str, depth: int = 3):
        """查找谁调用了指定方法"""
        print(f"📥 谁调用了 '{method}' (深度={depth}):\n")
        
        if not self.reverse_call_graph.get(method):
            print(f"   ❌ 没有调用者\n")
            return
        
        self._show_callers(method, depth, 0, "", set())
        print()
    
    def _show_callers(self, method: str, max_depth: int, depth: int,
                     indent: str, visited: Set[str]):
        """递归显示调用者"""
        if depth > max_depth or method in visited:
            return
        
        visited.add(method)
        
        for caller in sorted(self.reverse_call_graph.get(method, set())):
            info = self.methods.get(caller)
            if info:
                print(f"{indent}└─ {caller} ({Path(info.file_path).name}:{info.line})")
            else:
                print(f"{indent}└─ {caller} (外部)")
            
            self._show_callers(caller, max_depth, depth + 1, indent + "   ", visited)
    
    def find_callees(self, method: str, depth: int = 3):
        """查找指定方法调用了什么"""
        print(f"📤 '{method}' 调用了什么 (深度={depth}):\n")
        
        if not self.call_graph.get(method):
            print(f"   ❌ 没有调用其他方法\n")
            return
        
        self._show_callees(method, depth, 0, "", set())
        print()
    
    def _show_callees(self, method: str, max_depth: int, depth: int,
                     indent: str, visited: Set[str]):
        """递归显示被调用的方法"""
        if depth > max_depth or method in visited:
            return
        
        visited.add(method)
        
        for callee in sorted(self.call_graph.get(method, set())):
            info = self.methods.get(callee)
            if info:
                print(f"{indent}└─ {callee} ({Path(info.file_path).name}:{info.line})")
            else:
                print(f"{indent}└─ {callee} (JDK/外部)")
            
            self._show_callees(callee, max_depth, depth + 1, indent + "   ", visited)
    
    def find_path(self, start: str, end: str, max_depth: int = 10):
        """查找从 start 到 end 的调用链"""
        print(f"🔗 '{start}' → '{end}' 的调用链:\n")
        
        paths = []
        self._dfs_path([start], end, set(), paths, max_depth)
        
        if not paths:
            print(f"   ❌ 没有找到调用链\n")
        else:
            print(f"   ✅ 找到 {len(paths)} 条调用链:\n")
            for i, path in enumerate(paths[:3], 1):
                print(f"   路径 {i}: {' → '.join(path)}\n")
    
    def _dfs_path(self, path: List[str], target: str, visited: Set[str],
                 all_paths: List[List[str]], max_depth: int):
        """DFS 搜索调用路径"""
        if len(path) > max_depth:
            return
        
        current = path[-1]
        
        if current == target:
            all_paths.append(path.copy())
            return
        
        if current in visited:
            return
        
        visited.add(current)
        
        for callee in self.call_graph.get(current, set()):
            path.append(callee)
            self._dfs_path(path, target, visited, all_paths, max_depth)
            path.pop()
        
        visited.remove(current)


class MethodInfo:
    """方法信息"""
    def __init__(self, name: str, file_path: str, line: int):
        self.name = name
        self.file_path = file_path
        self.line = line


def main():
    print("=" * 70)
    print("🌳 Tree-sitter Java 调用链分析器")
    print("=" * 70)
    print()
    
    target = "../springboot-vuln-demo/src/main/java"
    if len(sys.argv) > 1:
        target = sys.argv[1]
    
    # 创建分析器
    analyzer = TreeSitterAnalyzer(target)
    
    # 分析项目
    analyzer.analyze()
    
    print("=" * 70)
    print("🔍 示例查询")
    print("=" * 70)
    print()
    
    # 查询示例
    analyzer.find_callers("findById", depth=2)
    analyzer.find_callees("getUserById", depth=2)
    analyzer.find_path("getUserById", "findById")
    
    print("=" * 70)
    print("✅ 完成")
    print("=" * 70)


if __name__ == "__main__":
    main()
