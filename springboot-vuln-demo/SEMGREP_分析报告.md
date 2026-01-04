# SpringBoot 越权漏洞 Demo - Semgrep 安全分析报告

## 📊 扫描概要

- **扫描时间**: 2024年12月
- **扫描工具**: Semgrep 1.119.0
- **自定义规则**: 8 条越权检测规则
- **扫描文件**: 8 个 Java 文件
- **检测到的问题**: 17 个安全漏洞（全部为阻断级别）

## 🔴 严重漏洞统计

| 漏洞类型 | 数量 | 严重程度 |
|---------|------|----------|
| 水平越权（IDOR） | 6 | ERROR |
| 垂直越权（权限提升） | 3 | ERROR |
| 不安全的身份验证 | 6 | WARNING |
| 缺少所有权检查 | 2 | WARNING |

## 🚨 详细漏洞分析

### 1. 水平越权漏洞（Horizontal IDOR）

#### 1.1 订单查看越权 (OrderController.java:22-45)
- **规则**: `spring-missing-authorization-check-get`
- **严重程度**: ERROR
- **CWE**: CWE-639 (Authorization Bypass Through User-Controlled Key)
- **OWASP**: A01:2021 - Broken Access Control

**问题代码**:
```java
@GetMapping("/{orderId}")
public ResponseEntity<?> getOrderById(
        @PathVariable Long orderId,
        @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
    Optional<Order> orderOpt = orderRepository.findById(orderId);
    // 缺少授权检查：没有验证订单是否属于当前用户
    ...
}
```

**影响**: 攻击者可以通过修改 `orderId` 参数查看任意用户的订单信息。

**修复建议**:
```java
Order order = orderOpt.get();
if (order.getUserId() != currentUserId && !isAdmin(currentUser)) {
    return ResponseEntity.status(403).build();
}
```

#### 1.2 订单修改越权 (OrderController.java:48-74)
- **规则**: `spring-missing-authorization-check-put`
- **严重程度**: ERROR
- **CWE**: CWE-639

**问题代码**:
```java
@PutMapping("/{orderId}")
public ResponseEntity<?> updateOrder(
        @PathVariable Long orderId,
        @RequestBody Map<String, Object> updates,
        @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
    // 缺少所有权验证
    ...
}
```

**影响**: 攻击者可以修改其他用户的订单状态和金额。

#### 1.3 订单删除越权 (OrderController.java:77-95)
- **规则**: `spring-missing-authorization-check-delete`
- **严重程度**: ERROR
- **CWE**: CWE-639

**问题代码**:
```java
@DeleteMapping("/{orderId}")
public ResponseEntity<?> deleteOrder(
        @PathVariable Long orderId,
        @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
    // 没有验证订单所有权
    orderRepository.delete(order);
    ...
}
```

**影响**: 攻击者可以删除任意用户的订单。

#### 1.4 用户信息查看越权 (UserController.java:23-38)
- **规则**: `spring-missing-authorization-check-get`
- **严重程度**: ERROR

**问题代码**:
```java
@GetMapping("/{userId}")
public ResponseEntity<?> getUserById(@PathVariable Long userId) {
    // 没有检查当前用户是否有权限查看这个用户的信息
    Optional<User> user = userRepository.findById(userId);
    ...
}
```

**影响**: 任何用户都可以查看其他用户的个人信息（用户名、邮箱、角色等）。

#### 1.5 用户订单查看越权 (UserController.java:79-85)
- **规则**: `spring-missing-authorization-check-get`
- **严重程度**: ERROR

#### 1.6 用户角色修改越权 (UserController.java:42-66)
- **规则**: `spring-missing-authorization-check-put`
- **严重程度**: ERROR

---

### 2. 垂直越权漏洞（Vertical Privilege Escalation）

#### 2.1 角色修改权限绕过 (UserController.java:62)
- **规则**: `spring-role-modification-missing-check`
- **严重程度**: ERROR
- **CWE**: CWE-269 (Improper Privilege Management)
- **OWASP**: A01:2021 - Broken Access Control

**问题代码**:
```java
user.setRole(newRole);  // 任何人都可以修改任何用户的角色！
```

**影响**: 这是最严重的漏洞之一。普通用户可以将自己或其他用户提升为管理员，从而获得系统的完全控制权。

**攻击示例**:
```bash
# 普通用户 alice (ID=2) 将自己提升为管理员
curl -X PUT -H "X-User-Id: 2" \
  "http://localhost:8080/api/users/2/role?newRole=ADMIN"
```

**修复建议**:
```java
// 方法1: 使用 Spring Security 注解
@PreAuthorize("hasRole('ADMIN')")
@PutMapping("/{userId}/role")
public ResponseEntity<?> updateUserRole(...) { ... }

// 方法2: 手动检查
Optional<User> currentUserOpt = userRepository.findById(currentUserId);
if (currentUserOpt.isEmpty() || !currentUserOpt.get().getRole().equals("ADMIN")) {
    return ResponseEntity.status(403).body("Only admins can modify roles");
}
```

#### 2.2 管理员统计信息访问漏洞 (OrderController.java:98-114)
- **规则**: `spring-admin-endpoint-missing-role-check`
- **严重程度**: ERROR
- **CWE**: CWE-284 (Improper Access Control)

**问题代码**:
```java
@GetMapping("/admin/stats")
public ResponseEntity<?> getOrderStats(
        @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
    // 没有检查当前用户是否是管理员
    List<Order> allOrders = orderRepository.findAll();
    ...
}
```

**影响**: 普通用户可以访问管理员专用的统计信息，包括所有订单总数和总收入。

#### 2.3 管理员用户列表访问漏洞 (UserController.java:69-76)
- **规则**: `spring-admin-endpoint-missing-role-check`
- **严重程度**: ERROR

**问题代码**:
```java
@GetMapping("/admin/all")
public ResponseEntity<?> getAllUsers(
        @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
    // 没有检查当前用户是否是管理员
    List<User> users = userRepository.findAll();
    ...
}
```

**影响**: 普通用户可以获取所有用户的完整信息列表。

---

### 3. 不安全的身份验证机制

#### 3.1 使用自定义 Header 进行身份验证
- **规则**: `spring-weak-authentication-header`
- **严重程度**: WARNING
- **CWE**: CWE-287 (Improper Authentication)
- **OWASP**: A07:2021 - Identification and Authentication Failures
- **检测到的位置**: 6 处（所有控制器方法）

**问题代码**:
```java
@RequestHeader(value = "X-User-Id", required = false) Long currentUserId
```

**影响**: 
- 客户端可以任意伪造 `X-User-Id` Header
- 攻击者可以冒充任何用户，包括管理员
- 没有实际的身份验证机制

**攻击示例**:
```bash
# 攻击者假装自己是管理员（ID=1）
curl -H "X-User-Id: 1" http://localhost:8080/api/users/admin/all

# 攻击者假装自己是 alice（ID=2）
curl -H "X-User-Id: 2" http://localhost:8080/api/users/3
```

**修复建议**:
1. **使用 Spring Security**: 实现基于 Session 或 JWT 的身份验证
2. **使用 Spring Security Context**: 从安全上下文中获取当前用户
3. **不要信任客户端传递的用户 ID**

示例:
```java
// 使用 Spring Security
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@GetMapping("/{userId}")
public ResponseEntity<?> getUserById(@PathVariable Long userId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    User currentUser = (User) auth.getPrincipal();
    Long currentUserId = currentUser.getId();
    
    if (!currentUserId.equals(userId) && !currentUser.isAdmin()) {
        return ResponseEntity.status(403).build();
    }
    ...
}
```

---

### 4. 缺少所有权验证

#### 4.1 Repository 查询缺少所有权检查
- **规则**: `spring-repository-findbyid-without-ownership-check`
- **严重程度**: WARNING
- **检测到的位置**: 2 处

**问题**: 使用 `repository.findById()` 查询数据后，直接返回给用户，没有验证资源所有权。

---

## 📋 完整漏洞清单

| # | 文件 | 行号 | 漏洞类型 | 严重程度 | CWE |
|---|------|------|----------|----------|-----|
| 1 | OrderController.java | 22-45 | 水平越权 (GET) | ERROR | CWE-639 |
| 2 | OrderController.java | 25 | 不安全的身份验证 | WARNING | CWE-287 |
| 3 | OrderController.java | 27 | 缺少所有权检查 | WARNING | CWE-639 |
| 4 | OrderController.java | 48-74 | 水平越权 (PUT) | ERROR | CWE-639 |
| 5 | OrderController.java | 52 | 不安全的身份验证 | WARNING | CWE-287 |
| 6 | OrderController.java | 77-95 | 水平越权 (DELETE) | ERROR | CWE-639 |
| 7 | OrderController.java | 80 | 不安全的身份验证 | WARNING | CWE-287 |
| 8 | OrderController.java | 98-114 | 垂直越权（管理员端点） | ERROR | CWE-284 |
| 9 | OrderController.java | 99 | 不安全的身份验证 | WARNING | CWE-287 |
| 10 | UserController.java | 23-38 | 水平越权 (GET) | ERROR | CWE-639 |
| 11 | UserController.java | 27 | 缺少所有权检查 | WARNING | CWE-639 |
| 12 | UserController.java | 42-66 | 水平越权 (PUT) | ERROR | CWE-639 |
| 13 | UserController.java | 46 | 不安全的身份验证 | WARNING | CWE-287 |
| 14 | UserController.java | 62 | 角色修改权限绕过 | ERROR | CWE-269 |
| 15 | UserController.java | 69-76 | 垂直越权（管理员端点） | ERROR | CWE-284 |
| 16 | UserController.java | 70 | 不安全的身份验证 | WARNING | CWE-287 |
| 17 | UserController.java | 79-85 | 水平越权 (GET) | ERROR | CWE-639 |

---

## 🛠️ 通用修复建议

### 1. 实现 Spring Security

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### 2. 配置安全策略

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/*/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic();
        return http.build();
    }
}
```

### 3. 使用方法级安全注解

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/all")
public ResponseEntity<?> getAllUsers() { ... }

@PreAuthorize("#userId == authentication.principal.id or hasRole('ADMIN')")
@GetMapping("/{userId}")
public ResponseEntity<?> getUserById(@PathVariable Long userId) { ... }
```

### 4. 创建权限检查工具类

```java
@Component
public class AuthorizationService {
    
    public boolean canAccessOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        return order.getUserId().equals(userId) || user.getRole().equals("ADMIN");
    }
    
    public boolean isAdmin(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return user.getRole().equals("ADMIN");
    }
}
```

### 5. 使用 DTO 和输入验证

```java
public class UpdateOrderRequest {
    @NotBlank
    private String status;
    
    @Min(0)
    private Double amount;
    
    // getters and setters
}

@PutMapping("/{orderId}")
public ResponseEntity<?> updateOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody UpdateOrderRequest request) {
    ...
}
```

---

## 📈 风险评估

### 业务影响

| 风险 | 可能性 | 影响程度 | 综合风险 |
|------|--------|----------|----------|
| 数据泄露（查看他人信息） | 高 | 高 | **严重** |
| 数据篡改（修改他人订单） | 高 | 高 | **严重** |
| 数据删除（删除他人订单） | 高 | 严重 | **严重** |
| 权限提升（提升为管理员） | 高 | 严重 | **严重** |
| 访问管理员功能 | 高 | 高 | **严重** |

### 攻击场景

1. **场景1：普通用户提升为管理员**
   ```bash
   curl -X PUT -H "X-User-Id: 2" \
     "http://localhost:8080/api/users/2/role?newRole=ADMIN"
   ```
   → 普通用户获得完全控制权

2. **场景2：查看竞争对手订单**
   ```bash
   curl -H "X-User-Id: 2" http://localhost:8080/api/orders/1
   curl -H "X-User-Id: 2" http://localhost:8080/api/orders/2
   # 遍历所有订单ID
   ```
   → 商业机密泄露

3. **场景3：恶意删除他人订单**
   ```bash
   for i in {1..1000}; do
     curl -X DELETE -H "X-User-Id: 999" \
       http://localhost:8080/api/orders/$i
   done
   ```
   → 业务瘫痪

---

## 🔍 Semgrep 自定义规则说明

本次扫描使用的 8 条自定义规则：

1. **spring-missing-authorization-check-get**: 检测 GET 端点缺少授权检查
2. **spring-missing-authorization-check-put**: 检测 PUT 端点缺少授权检查
3. **spring-missing-authorization-check-delete**: 检测 DELETE 端点缺少授权检查
4. **spring-admin-endpoint-missing-role-check**: 检测管理员端点缺少角色检查
5. **spring-role-modification-missing-check**: 检测角色修改缺少权限检查
6. **spring-repository-findbyid-without-ownership-check**: 检测资源查询缺少所有权验证
7. **spring-weak-authentication-header**: 检测使用不安全的自定义 Header 认证
8. **spring-missing-request-body-validation**: 检测请求体缺少验证

规则文件: `semgrep-idor-rules.yaml`

---

## 📚 参考资料

- [OWASP Top 10 2021 - A01: Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)
- [CWE-639: Authorization Bypass Through User-Controlled Key](https://cwe.mitre.org/data/definitions/639.html)
- [CWE-269: Improper Privilege Management](https://cwe.mitre.org/data/definitions/269.html)
- [CWE-284: Improper Access Control](https://cwe.mitre.org/data/definitions/284.html)
- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [Semgrep Rule Writing Guide](https://semgrep.dev/docs/writing-rules/overview/)

---

## ✅ 后续行动计划

### 短期（1-2周）
- [ ] 集成 Spring Security
- [ ] 修复所有 ERROR 级别的漏洞
- [ ] 实现基于角色的访问控制（RBAC）
- [ ] 移除 X-User-Id Header，使用安全的身份验证

### 中期（1个月）
- [ ] 实现 JWT 或 OAuth2 认证
- [ ] 添加审计日志
- [ ] 创建自动化安全测试
- [ ] 将 Semgrep 集成到 CI/CD 流程

### 长期（持续）
- [ ] 定期进行安全审计
- [ ] 安全培训
- [ ] 监控和告警
- [ ] 漏洞赏金计划

---

**报告生成时间**: 2024年12月  
**扫描工具版本**: Semgrep 1.119.0  
**自定义规则版本**: v1.0

