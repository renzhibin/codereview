#!/usr/bin/env python3
"""
Tree-sitter 示例 - 解析 Java 代码
需要先安装: pip install tree-sitter tree-sitter-java
"""

try:
    from tree_sitter import Language, Parser
    import tree_sitter_java as tsjava
except ImportError:
    print("❌ 请先安装依赖:")
    print("   pip3 install --user tree-sitter tree-sitter-java")
    exit(1)

# 示例 Java 代码
java_code = """
public class UserController {
    
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        }
        return ResponseEntity.notFound().build();
    }
    
    public void updateUserRole(Long userId, String role) {
        User user = userRepository.findById(userId).get();
        user.setRole(role);
        userRepository.save(user);
    }
}
"""

def main():
    print("=== Tree-sitter Java 解析示例 ===\n")
    
    # 初始化解析器
    JAVA_LANGUAGE = Language(tsjava.language())
    parser = Parser(JAVA_LANGUAGE)
    
    # 解析代码
    tree = parser.parse(bytes(java_code, "utf8"))
    root_node = tree.root_node
    
    print("1️⃣  完整语法树结构:")
    print("─" * 50)
    print(root_node.sexp())
    print()
    
    print("\n2️⃣  查找所有方法定义:")
    print("─" * 50)
    find_methods(root_node)
    
    print("\n3️⃣  查找所有方法调用:")
    print("─" * 50)
    find_method_calls(root_node)
    
    print("\n4️⃣  查找所有注解:")
    print("─" * 50)
    find_annotations(root_node)

def find_methods(node):
    """递归查找所有方法定义"""
    if node.type == 'method_declaration':
        # 找方法名
        for child in node.children:
            if child.type == 'identifier':
                method_name = java_code[child.start_byte:child.end_byte]
                line = child.start_point[0] + 1
                print(f"  ✅ 方法: {method_name} (行 {line})")
                break
    
    for child in node.children:
        find_methods(child)

def find_method_calls(node):
    """递归查找所有方法调用"""
    if node.type == 'method_invocation':
        # 提取方法名
        for child in node.children:
            if child.type == 'identifier':
                method_name = java_code[child.start_byte:child.end_byte]
                line = child.start_point[0] + 1
                print(f"  📞 调用: {method_name} (行 {line})")
                break
    
    for child in node.children:
        find_method_calls(child)

def find_annotations(node):
    """递归查找所有注解"""
    if node.type == 'marker_annotation':
        annotation = java_code[node.start_byte:node.end_byte]
        line = node.start_point[0] + 1
        print(f"  🏷️  注解: {annotation} (行 {line})")
    
    for child in node.children:
        find_annotations(child)

if __name__ == "__main__":
    main()


