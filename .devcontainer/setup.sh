#!/usr/bin/env bash
set -euo pipefail

echo "[setup] 预取后端依赖，保证首次启动不必再等下载"
cd backend
mvn -B -q dependency:go-offline

echo "[setup] 安装前端依赖"
cd ../frontend
npm install

echo "[setup] 完成。容器启动后会自动执行 docker compose up -d --build"
