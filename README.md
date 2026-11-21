# 智慧自习室座位预约与占座检测系统

《高级软件工程》项目实践 · 题目二 · 第 2 小组
长春工业大学 · 计算机技术 · 指导教师 赵辉

系统解决的不是"有没有座位"，而是让座位的占用状态**可记录、可验证、可回收**：
选座预约 → 扫码签到 → 使用中暂离 → 超时自动释放 → 违规记录与候补通知。

## 一键启动（GitHub Codespaces）

1. 打开仓库页面，点击 **Code → Codespaces → Create codespace**；
2. 等待容器初始化（会自动预取 Maven 与 npm 依赖）；
3. 容器启动时自动执行 `docker compose up -d --build`，随后在 **端口 5173** 打开前端页面即可选座演示。

本地已有 Docker 时，等价命令只有一条：

```bash
docker compose up -d --build
```

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| frontend | 5173 | 座位图与预约、签到演示页 |
| backend | 8080 | Spring Boot 接口 |
| mysql | 3306 | 业务数据，首次启动执行 `deploy/init.sql` |
| redis | 6379 | 预占与签到码防重放 |

## 目录结构

```
seat-reservation/
├── .devcontainer/        Codespaces 环境定义与依赖预热脚本
├── .github/workflows/    CI：后端打包 + 前端构建
├── backend/              Spring Boot 3.2 + JPA + MySQL + Redis
│   └── src/main/java/cn/ccut/seat/
│       ├── domain/       实体：room / seat / reservation / checkin_record / violation
│       ├── repo/         数据访问，含行锁与条件更新
│       ├── service/      预约、签到、超时释放、候补
│       ├── security/     签到码签名与验签（HMAC-SHA256）
│       └── web/          REST 接口与统一异常处理
├── frontend/             Vue 3 + Vite 座位图
├── deploy/init.sql       建表、唯一索引与示例数据
├── docs/                项目实践报告与图表
└── docker-compose.yml   一键编排
```

## 核心实现要点

- **并发控制三层**：Redis 预占（5 分钟）解决体验、数据库行锁解决正确性、`(seat_id, start_time)` 唯一索引兜底。
- **签到防重放**：签到码是 `base64(预约号:座位号:截止时间:随机数).HMAC` 的结构化令牌，验签 + 时间窗 + 随机数一次性校验，截图转发无效。
- **占座判定**：每分钟扫描"未签到超时""暂离超时"，用带状态条件的 `UPDATE` 保证幂等，影响行数为 1 才扣分与释放。
- **状态单一写入口**：座位状态只能由座位服务写，预约状态只能由预约服务写，避免两套逻辑各自改状态。

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/rooms` | 自习室列表 |
| GET | `/api/rooms/{id}/seats?start=&end=` | 座位图与时段占用情况 |
| POST | `/api/reservations` | 预约（行锁 + 冲突校验 + 信用门槛） |
| GET | `/api/reservations/{id}` | 预约单详情（含签到码） |
| POST | `/api/reservations/{id}/away` | 暂离 |
| POST | `/api/reservations/{id}/back` | 暂离返回 |
| POST | `/api/reservations/{id}/release` | 管理员强制释放 |
| POST | `/api/checkin` | 扫码签到 |
| POST | `/api/waitlist` | 加入候补 |

## 团队分工

| 成员 | 学号 | 主要职责 |
| --- | --- | --- |
| 李冉 | 2202604081 | 组长；需求调研、总体架构、报告统稿与版本库管理 |
| 冯诗祺 | 2202604059 | 干系人与产品分析、用例图与用例描述、非功能需求 |
| 刘雨可 | 2202604127 | 类图、时序图、状态机、数据库与接口设计、判定规则 |
| 佟思澄 | 2202604018 | 核心接口与定时任务实现、测试用例、部署脚本 |

## 文档

- `docs/智慧自习室座位预约与占座检测系统-项目实践报告.pdf`（提交版）
- `docs/智慧自习室座位预约与占座检测系统-项目实践报告.docx`（可编辑源文件）
- `docs/figures/` 报告中的 7 张图源文件

## 本期简化说明

演示版为课程周期内的最小可运行闭环：候补通知以日志代替公众号模板消息，身份认证以学号参数代替统一身份认证，测试数据为 `deploy/init.sql` 预置的两个自习室各 20 个座位。
