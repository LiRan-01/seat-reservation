#!/usr/bin/env bash
# 依赖预热脚本：任何一步失败都不阻断容器创建，只打印告警。
# 这样即使网络抖动，codespace 也能正常进入编辑器，再手动重试即可。
set +e

echo "[setup] 预取后端 Maven 依赖"
if command -v mvn >/dev/null 2>&1; then
  (cd backend && mvn -B -q dependency:go-offline) || echo "[setup][warn] Maven 依赖预取失败，可稍后手动执行"
else
  echo "[setup][warn] 未找到 mvn，跳过后端预热"
fi

echo "[setup] 安装前端依赖"
if command -v npm >/dev/null 2>&1; then
  (cd frontend && npm install) || echo "[setup][warn] npm install 失败，可稍后手动执行"
else
  echo "[setup][warn] 未找到 npm，跳过前端预热"
fi

echo "[setup] 完成。容器启动后会尝试执行 docker compose up -d --build；若未自动启动，在终端手动执行一次即可"
