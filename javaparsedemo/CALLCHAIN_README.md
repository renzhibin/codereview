# 🔗 方法调用链分析器

## 简介

**方法调用链分析器** 是JavaParser工具套件的最新成员，专门用于分析Java项目中方法之间的调用关系。

## ✨ 核心功能

### 1. 上游分析（谁调用了它）⬆️
找出所有调用某个方法的地方，**包括间接调用**。

**示例**：
```
分析方法: save
上游调用链:
  → createOrder (直接调用)  
  → updateOrder (直接调用)
  → OrderController.handleRequest (间接：通过createOrder)
```

### 2. 下游分析（它调用了谁）⬇️  
找出某个方法调用的所有方法，**递归穿透到最深层**。

**示例**：
```
分析方法: createOrder
下游调用链:
  → validate (直接调用)
  → save (直接调用)
  → log (直接调用)
  → userRepository.findById (间接：通过validate)
  → orderRepository.save (间接：通过save)
```

### 3. 完整调用链 🔄
同时显示上游和下游，全面了解方法的调用关系。

### 4. 调用深度统计 📊
计算从入口点到叶子节点的最长调用路径。

## 🚀 快速开始

### 一键运行Demo

```bash
cd javaparsedemo
./run-callchain.sh
```

### 交互式查询

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer"
```

## 📖 使用示例

### 示例1: 追踪安全漏洞的影响范围

假设SecurityAnalyzer发现`getOrderById`方法有IDOR漏洞：

```bash
# 1. 查询谁会调用这个有漏洞的方法
请输入方法名: getOrderById

输出:
⬆️ 上游调用链: 3 个方法
  → OrderController.getUserOrders  (暴露给用户)
  → AdminController.exportOrders   (管理员功能)  
  → BatchProcessor.processOrders   (定时任务)

# 结论: 需要在这3个地方都加上授权检查
```

### 示例2: 理解复杂业务流程

```bash
# 查询完整调用链
请输入方法名: processPayment

输出:
⬆️  上游（谁会触发支付）:
  → OrderController.checkout
  → SubscriptionService.renew  
  → RefundService.processRefund

⬇️  下游（支付流程调用了什么）:
  → validatePayment
  → callPaymentGateway
  → updateOrderStatus
  → sendNotification
  → createInvoice
```

### 示例3: 评估重构影响

```bash
# 准备重构 updateOrder 方法
请输入方法名: updateOrder

上游调用者: 
  → OrderController.updateOrder (API入口)
  → AdminController.batchUpdate (批量更新)
  → ScheduledTask.syncOrders (定时同步)

# 结论: 重构会影响3个调用点，需要全部测试
```

## 🎯 实际输出示例

```
================================================================================
🔍 分析方法: OrderController.getOrderById
📍 位置: OrderController.java:22
================================================================================

⬆️  上游调用链（谁调用了它）: 0 个方法
--------------------------------------------------------------------------------
  (无上游调用者 - 可能是入口方法或未被调用)

⬇️  下游调用链（它调用了谁）: 2 个方法
--------------------------------------------------------------------------------
  1. OrderRepository.findById
     └─ OrderRepository.java:8
  2. Optional.orElseThrow
     └─ (外部方法)

📊 调用深度统计:
  最大上游深度: 0
  最大下游深度: 1
```

## 🔧 技术实现

### 两遍扫描策略

```java
// 第一遍：收集所有方法定义
for (File file : javaFiles) {
    收集: 类名.方法名 -> MethodInfo(文件, 行号)
}

// 第二遍：分析调用关系
for (File file : javaFiles) {
    构建: 调用图(A->B) 和 反向调用图(B<-A)
}
```

### 递归穿透算法

```java
findUpstream(method, visited, depth=0):
    if depth > 10 or method in visited:
        return
    
    for caller in reverseCallGraph[method]:
        visited.add(caller)
        findUpstream(caller, visited, depth+1)  // 递归
    
    return visited
```

## 💡 使用场景

| 场景 | 用途 |
|------|------|
| **安全审计** | 追踪漏洞方法的所有调用路径 |
| **影响分析** | 评估修改某个方法会影响哪些地方 |
| **代码理解** | 快速理解复杂的业务流程 |
| **重构准备** | 确定需要更新的所有调用点 |
| **数据流追踪** | 了解数据从哪里来到哪里去 |
| **文档生成** | 自动生成调用关系图 |

## 📁 文件结构

```
javaparsedemo/src/main/java/com/security/analyzer/
├── MethodCallChainAnalyzer.java    # 交互式版本（支持查询）
├── MethodCallChainDemo.java        # 演示版本（自动分析）
└── run-callchain.sh                # 启动脚本
```

## 📚 相关文档

- **CALLCHAIN_USAGE.md** - 详细使用指南（本文档的完整版）
- **START_HERE.md** - 项目快速入门
- **README.md** - 项目总览

## ⚙️ 运行选项

### Demo版（自动演示）

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainDemo"
```

**特点**：
- ✅ 自动分析几个关键方法
- ✅ 快速了解工具功能
- ✅ 适合第一次使用

### 交互式版（自定义查询）

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer"
```

**特点**：
- ✅ 查询任意方法
- ✅ 支持搜索和过滤
- ✅ 实时交互
- ✅ 适合深度分析

### 分析自定义项目

```bash
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer" \
  -Dexec.args="/path/to/your/project/src"
```

## 🎓 学习路径

1. **初级** - 运行`./run-callchain.sh`查看Demo
2. **中级** - 使用交互式版查询几个方法
3. **高级** - 结合SecurityAnalyzer追踪漏洞影响

## ⚠️ 限制说明

1. **只分析项目内代码** - JDK和第三方库方法标记为"外部方法"
2. **简单名称匹配** - 不做完整类型推断，可能有误差
3. **递归深度限制** - 最大10层，防止无限递归
4. **接口调用** - 无法准确解析到具体实现类
5. **反射和动态调用** - 无法识别

## 🌟 优势特点

- ✅ **递归穿透** - 自动找出所有间接调用
- ✅ **双向分析** - 同时支持上游和下游查询
- ✅ **交互式** - 支持实时查询任意方法
- ✅ **可视化** - 清晰的树形结构展示
- ✅ **性能** - 快速分析中小型项目

## 🚀 与其他工具配合

```bash
# 工作流示例

# 1. 安全扫描
mvn exec:java -Dexec.mainClass="com.security.analyzer.SecurityAnalyzer"
# 发现: getOrderById 有IDOR漏洞

# 2. 调用链分析  
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodCallChainAnalyzer"
# 输入: getOrderById
# 结果: 找到3个上游调用者

# 3. 复杂度分析
mvn exec:java -Dexec.mainClass="com.security.analyzer.MethodAnalyzer"
# 评估: 修复的工作量
```

---

**提示**：方法调用链分析是理解大型代码库的利器，结合安全分析器使用效果更佳！

详细使用指南请查看 **CALLCHAIN_USAGE.md**

