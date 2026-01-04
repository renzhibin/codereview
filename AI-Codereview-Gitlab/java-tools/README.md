# Java Context Extractor

使用JavaParser提取Java代码上下文信息的工具。

## 编译

```bash
cd java-tools
mvn clean compile
mvn dependency:copy-dependencies
```

## 使用

```bash
java -cp target/classes:target/dependency/* com.codereview.ContextExtractor \
  --repo-path /path/to/repo \
  --changed-files src/main/java/User.java,src/main/java/UserController.java
```

## 输出

JSON格式的上下文信息，包括：
- 修改文件的完整内容
- 相关依赖类
- 方法调用链

