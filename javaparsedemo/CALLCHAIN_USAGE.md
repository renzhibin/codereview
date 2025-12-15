# 方法调用链分析器 - 使用说明

## 🎯 功能介绍

**方法调用链分析器** 是一个强大的工具，可以分析Java项目中方法之间的调用关系，支持：

- ⬆️  **上游分析** - 找出谁调用了某个方法（被哪些方法调用）
- ⬇️  **下游分析** - 找出某个方法调用了哪些方法  
- 🔄 **递归穿透** - 自动递归查找，包括间接调用关系
- 📊 **调用深度** - 计算调用链的最大深度
- 🔍 **交互式查询** - 支持实时查询任何方法

## 🚀 快速开始

### 方式1: 使用启动脚本（推荐）

```bash
cd javaparsedemo
./run-callchain.sh
```

### 方式2: Maven命令

```bash
# Demo版 - 自动分析几个关键方法
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainDemo"

# 交互式版 - 可以查询任意方法
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer"
```

## 📖 使用教程

### 1. Demo版（自动演示）

运行后会自动分析几个关键方法：

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainDemo"
```

**输出示例：**

```
=== 方法调用链分析器 - Demo ===

正在分析项目: ../springboot-vuln-demo/src/main/java

找到 8 个Java文件
步骤1: 收集方法定义...
✅ 收集到 42 个方法
步骤2: 分析调用关系...
✅ 构建调用图完成

=== 统计信息 ===
总方法数: 42
有调用关系的方法: 15
被调用的方法: 20

================================================================================
🔍 分析方法: OrderController.getOrderById
📍 位置: OrderController.java:22
================================================================================

⬆️  上游调用链（谁调用了它）: 0 个方法
--------------------------------------------------------------------------------
  (无上游调用者 - 可能是入口方法或未被调用)

⬇️  下游调用链（它调用了谁）: 3 个方法
--------------------------------------------------------------------------------
  1. OrderRepository.findById
     └─ OrderRepository.java:8
  2. Optional.orElseThrow
     └─ (外部方法)
  3. RuntimeException.<init>
     └─ (外部方法)

📊 调用深度统计:
  最大上游深度: 0
  最大下游深度: 1
```

### 2. 交互式版（自定义查询）

运行交互式版本可以查询任意方法：

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer"
```

**交互菜单：**

```
=== 方法调用链查询 ===
1. 查找方法的上游调用者（谁调用了它）
2. 查找方法的下游被调用方法（它调用了谁）
3. 查找完整调用链（上游+下游）
4. 列出所有可用方法
5. 搜索方法
0. 退出

请选择 (0-5): _
```

#### 选项1: 查找上游调用者

```
请选择: 1
请输入方法名（格式: ClassName.methodName 或 methodName）: getUserById

================================================================================
方法: UserController.getUserById
位置: UserController.java:23
================================================================================

找到 0 个上游调用者（包括间接调用）:

  (没有找到上游调用者)
```

#### 选项2: 查找下游被调用方法

```
请选择: 2
请输入方法名: getOrderById

================================================================================
方法: OrderController.getOrderById
位置: OrderController.java:22
================================================================================

找到 2 个下游被调用方法（包括间接调用）:

  → orderRepository.findById
     文件: OrderRepository.java:8
  → Optional.orElseThrow
     (外部或未解析)
```

#### 选项3: 查找完整调用链

```
请选择: 3
请输入方法名: updateOrder

================================================================================
方法: OrderController.updateOrder
位置: OrderController.java:48
================================================================================

【上游调用链】找到 0 个上游调用者:

  (无上游调用者)

【下游调用链】找到 5 个下游被调用方法:

  → OrderRepository.findById
     文件: OrderRepository.java:8
  → OrderRepository.save
     文件: OrderRepository.java:12
  → Order.setAmount
     文件: Order.java:45
  → Order.setDescription
     文件: Order.java:50
  → Optional.orElseThrow
     (外部方法)
```

#### 选项4: 列出所有方法

```
请选择: 4

所有可用方法:
--------------------------------------------------------------------------------
DataInitializer.init                               DataInitializer.java:15
Order.getId                                        Order.java:25
Order.getUser                                      Order.java:30
Order.getAmount                                    Order.java:35
OrderController.getAllOrders                       OrderController.java:18
OrderController.getOrderById                       OrderController.java:22
OrderController.createOrder                        OrderController.java:35
OrderController.updateOrder                        OrderController.java:48
OrderController.deleteOrder                        OrderController.java:77
OrderRepository.findById                           OrderRepository.java:8
OrderRepository.save                               OrderRepository.java:12
UserController.getAllUsers                         UserController.java:19
UserController.getUserById                         UserController.java:23
...
```

#### 选项5: 搜索方法

```
请选择: 5
请输入搜索关键词: order

搜索结果:
--------------------------------------------------------------------------------
OrderController.getAllOrders                       OrderController.java:18
OrderController.getOrderById                       OrderController.java:22
OrderController.createOrder                        OrderController.java:35
OrderController.updateOrder                        OrderController.java:48
OrderController.deleteOrder                        OrderController.java:77
UserController.getUserOrders                       UserController.java:79

找到 6 个匹配的方法
```

## 💡 使用场景

### 场景1: 追踪数据流

了解一个数据从哪里来，到哪里去：

```bash
# 分析 save 方法
请输入方法名: save

# 查看谁调用了 save（数据从哪里来）
# 查看 save 调用了什么（数据到哪里去）
```

### 场景2: 影响分析

修改一个方法前，了解会影响哪些地方：

```bash
# 分析方法的上游
请输入方法名: updateOrder

# 输出会显示所有调用 updateOrder 的地方
# 修改 updateOrder 会影响这些调用者
```

### 场景3: 安全审计

追踪敏感操作的调用链：

```bash
# 分析 deleteOrder 方法
请输入方法名: deleteOrder

# 上游：谁可以触发删除操作
# 下游：删除操作会调用哪些方法
```

### 场景4: 代码理解

快速理解复杂的调用关系：

```bash
# 分析入口方法
请输入方法名: getUserById

# 查看完整调用链
# 理解整个业务流程
```

## 🔧 技术细节

### 调用关系构建过程

1. **第一遍扫描** - 收集所有方法定义
   ```
   收集到 42 个方法
   └─ 类名.方法名 -> MethodInfo(文件名, 行号, 包名)
   ```

2. **第二遍扫描** - 分析方法调用
   ```
   构建调用图:
   ├─ callGraph: A调用B, B调用C
   └─ reverseCallGraph: C被B调用, B被A调用
   ```

3. **递归查询** - 穿透间接调用
   ```
   查询 A 的下游:
   ├─ 直接: A -> B, A -> C
   ├─ 间接: B -> D, C -> E
   └─ 结果: [B, C, D, E]
   ```

### 方法解析策略

```java
// 1. 带作用域的调用
orderRepository.findById(id)  -> OrderRepository.findById

// 2. 本类方法调用
processOrder(order)           -> CurrentClass.processOrder

// 3. 无法确定类的调用
save(data)                    -> ?.save
```

### 递归深度限制

为防止无限递归，设置了最大深度限制：

```java
private Set<String> findUpstream(String methodKey, Set<String> visited, int depth) {
    if (depth > 10 || visited.contains(methodKey)) {  // 最大深度10
        return visited;
    }
    // ...
}
```

## ⚠️ 注意事项

1. **外部方法**
   - JDK标准库方法会标记为"外部方法"
   - 第三方库方法无法追踪
   - 只分析项目内的Java源代码

2. **方法解析限制**
   - 简单的名称匹配，不做完整的类型推断
   - 接口方法调用可能无法准确解析到实现类
   - 反射调用无法识别

3. **性能考虑**
   - 大型项目可能需要较长时间分析
   - 建议先运行Demo版了解项目结构

4. **间接调用**
   - 递归深度限制为10层
   - 可能存在未发现的间接调用

## 📊 输出说明

### 方法信息

```
🔍 分析方法: OrderController.getOrderById
📍 位置: OrderController.java:22
```
- 方法的完整限定名
- 所在文件和行号

### 上游调用链

```
⬆️  上游调用链（谁调用了它）: 2 个方法
  1. UserController.getUserOrders
     └─ UserController.java:80
  2. DataInitializer.init  
     └─ DataInitializer.java:25
```
- 显示所有调用该方法的方法（包括间接）
- 每个调用者的位置

### 下游调用链

```
⬇️  下游调用链（它调用了谁）: 3 个方法
  1. OrderRepository.findById
     └─ OrderRepository.java:8
  2. Optional.orElseThrow
     └─ (外部方法)
  3. RuntimeException.<init>
     └─ (外部方法)
```
- 显示该方法调用的所有方法（包括间接）
- 区分项目内方法和外部方法

### 调用深度统计

```
📊 调用深度统计:
  最大上游深度: 2
  最大下游深度: 3
```
- 上游：从入口点到当前方法的最长路径
- 下游：从当前方法到叶子方法的最长路径

## 🎓 高级用法

### 分析自定义项目

```bash
# 分析指定目录
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer" \
  -Dexec.args="/path/to/your/project/src"
```

### 组合使用

```bash
# 1. 先用Demo版快速浏览
./run-callchain.sh

# 2. 再用交互式版深入分析关键方法
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer"
```

### 配合其他分析器

```bash
# 1. 安全分析 - 找到有问题的方法
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"

# 2. 调用链分析 - 追踪问题方法的影响范围
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer"

# 3. 复杂度分析 - 评估修复难度
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer"
```

## 🔍 实战案例

### 案例1: 追踪IDOR漏洞影响

```
步骤1: 运行安全分析器，发现getOrderById有IDOR漏洞
步骤2: 运行调用链分析器，查询getOrderById
步骤3: 查看上游，发现哪些地方会调用它
步骤4: 评估修复后的影响范围
```

### 案例2: 理解业务流程

```
步骤1: 从Controller的某个入口方法开始
步骤2: 查看完整调用链
步骤3: 绘制流程图
步骤4: 文档化业务逻辑
```

### 案例3: 代码重构准备

```
步骤1: 确定要重构的方法
步骤2: 查看上游调用者（需要更新的地方）
步骤3: 查看下游被调用方法（可能受影响的方法）
步骤4: 评估重构风险
```

## 🚀 下一步

- 📚 查看 [START_HERE.md](START_HERE.md) 了解完整项目
- 🔒 查看 [DEMO_EXAMPLE.md](DEMO_EXAMPLE.md) 学习安全分析
- 📖 查看 [USAGE.md](USAGE.md) 了解其他工具

---

**提示**：方法调用链分析器是理解代码结构、追踪数据流、评估修改影响的强大工具！

