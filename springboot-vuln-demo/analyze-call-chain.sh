#!/bin/bash
# 调用链分析脚本
# 用法: ./analyze-call-chain.sh

echo "================================"
echo "🔍 SpringBoot 方法调用链分析"
echo "================================"
echo ""

# 1. 找到所有 Controller 方法
echo "📌 1. 所有 Controller 端点方法："
echo "-----------------------------------"
semgrep -e '@GetMapping(...) 
public $RET $METHOD(...) { ... }' --lang=java src/ 2>/dev/null | grep -A2 "┆"

semgrep -e '@PostMapping(...) 
public $RET $METHOD(...) { ... }' --lang=java src/ 2>/dev/null | grep -A2 "┆"

semgrep -e '@PutMapping(...) 
public $RET $METHOD(...) { ... }' --lang=java src/ 2>/dev/null | grep -A2 "┆"

semgrep -e '@DeleteMapping(...) 
public $RET $METHOD(...) { ... }' --lang=java src/ 2>/dev/null | grep -A2 "┆"

echo ""
echo "📌 2. Repository 调用（数据访问层）："
echo "-----------------------------------"
semgrep -e '$REPO.findById($X)' --lang=java src/ 2>/dev/null | grep "┆"

echo ""
semgrep -e '$REPO.save($X)' --lang=java src/ 2>/dev/null | grep "┆"

echo ""
semgrep -e '$REPO.delete($X)' --lang=java src/ 2>/dev/null | grep "┆"

echo ""
semgrep -e '$REPO.findAll()' --lang=java src/ 2>/dev/null | grep "┆"

echo ""
echo "📌 3. 敏感方法调用："
echo "-----------------------------------"
semgrep -e '$USER.setRole(...)' --lang=java src/ 2>/dev/null | grep "┆"

echo ""
echo "📌 4. ResponseEntity 返回："
echo "-----------------------------------"
semgrep -e 'ResponseEntity.ok(...)' --lang=java src/ 2>/dev/null | grep "┆" | head -10

echo ""
echo "================================"
echo "✅ 分析完成"
echo "================================"

