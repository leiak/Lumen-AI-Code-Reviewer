# AI Code Review Council

多模型对弈式代码评审系统。三层架构：
- **Spring AI 1.1** - 模型与工具接入
- **LangGraph4j 1.6** - 编排定义（当前 MVP 用直接编排替代）
- **SQLite (JamJet 替代)** - 持久化/审计/快照

## 快速开始

```bash
# 编译
export JAVA_HOME=/path/to/java-21
mvn -B package

# 校验配置
java -jar target/review.jar validate --config=council.yaml

# 运行评审（需要 ANTHROPIC_API_KEY 环境变量）
export ANTHROPIC_API_KEY=sk-ant-...
java -jar target/review.jar run HEAD

# 查看成本
java -jar target/review.jar cost rev-abc12345

# 打印 LangGraph4j StateGraph 拓扑 (Mermaid)
java -jar target/review.jar graph --config=council.yaml

# 启动 Web API (端口 8080，配置改用 --server.port=N)
java -jar target/review.jar serve --server.port=8090

# 调用 API
curl -s http://localhost:8090/health
curl -s -X POST -H "Content-Type: text/plain" --data-binary @council.yaml http://localhost:8090/validate
curl -s -X POST -H "Content-Type: application/json" -d '{"yaml":"...","diff":"..."}' http://localhost:8090/sessions
curl -s http://localhost:8090/sessions/rev-abc12345/cost
curl -s "http://localhost:8090/sessions/rev-abc12345/graph?format=mermaid"
```

## 当前实现的功能

| 功能 | 状态 |
|---|---|
| 多 reviewer 并行评审（architect/security/perf） | ✅ |
| 加权投票 + Jaccard 去重 | ✅ |
| Per-reviewer + path-rule HumanGate 判定 | ✅ |
| LLM 生成 patch + 自动 apply | ✅ |
| Re-reviewer 检测回归 | ✅ |
| 预算（token/cost）追踪 + 超限 abort | ✅ |
| SQLite 持久化（sessions/snapshots/audit/llm_calls） | ✅ |
| LLM 调用成本/延迟审计 | ✅ |
| CLI：run / validate / cost / graph | ✅ |
| Web API：/health /validate /sessions /cost /graph | ✅ |
| LangGraph4j StateGraph 拓扑定义 + 并行 dispatch | ✅（CLI `graph` 显示 Mermaid/PlantUML） |
| Demo repo（含 SQL 注入 / N+1 / 资源泄漏 / 硬编码密钥） | ✅ `demo-repo/` |
| 前端 SPA（Vite + React + TS + Tailwind + Mermaid） | ✅ `frontend/` |

## 测试覆盖

```
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
- ReviewState / Budget / immutable state transitions
- CouncilConfig YAML 解析（含 human-gates / budget / path-rules）
- PromptTemplate 占位符渲染
- Aggregator Jaccard 去重 + severity 计数
- FindingParser markdown fence 剥离
- GateEvaluator per-reviewer + path-rule 优先级
- PatchApplier 文件替换
- SessionRepository / StateSnapshotRepository / AuditRepository / LlmCallRepository
- StateGraphIntegrationTest 真实构建 LangGraph4j StateGraph 并验证 5 个节点（Spring context）
```

## 配置示例

完整配置见 `council.yaml`，关键字段：

```yaml
council:
  name: team-java-backend
loop:
  max-rounds: 2
reviewers:
  - role: security
    model: claude-sonnet-5-20250929
    prompt-file: prompts/security.md
human-gates:
  default: never
  per-reviewer:
    security:
      - severity: critical
        action: must_approve
  path-rules:
    - pattern: "**/auth/**"
      gate: always
budget:
  max-tokens: 200000
  max-cost-usd: "1.00"
  on-exceeded: abort
```

## 项目结构

```
src/main/java/com/review/council/
├── CouncilApplication.java      # Spring Boot 入口（CLI 模式默认 / serve 模式 Web）
├── ai/                          # ChatClient 多 provider
├── aggregator/                  # Aggregator + Jaccard dedup
├── audit/                       # AuditRepository + LlmCallRepository
├── cli/                         # Picocli 命令（run/validate/cost/graph）
├── config/                      # CouncilConfig + 8 个子配置 + YAML 解析
├── fixer/                       # GenericFixerNode + PatchApplier
├── gate/                        # GateEvaluator + Node
├── graph/                       # CouncilOrchestrator + CouncilStateGraphRunner
├── nodes/                       # 节点接口 + NodeContext
├── persistence/                 # SessionRepository + StateSnapshotRepository + DataSource
├── reviewer/                    # GenericReviewerNode + FindingParser + ReReviewer
├── state/                       # ReviewState + Finding + Patch + CodeDiff + Budget
└── web/                         # ReviewController (REST)

src/main/resources/
├── application.yml              # Spring Boot 配置
├── db/migration/V1__init.sql    # SQLite schema
└── prompts/                     # 6 个内置 prompt
```

## 文档

- 设计文档：`docs/superpowers/specs/2026-09-14-ai-code-review-council-design.md`
- 实施计划：`docs/superpowers/plans/2026-09-14-ai-code-review-council-plan.md`
- Demo repo：`demo-repo/`（含 SQL 注入 / N+1 / 资源泄漏 / 硬编码密钥，可直接 `review run`）

## 快速演示（不需要 API key 的部分）

```bash
# 1. 验证配置文件
java -jar target/review.jar validate --config=council.yaml

# 2. 打印 LangGraph4j StateGraph 拓扑（真实图）
java -jar target/review.jar graph --config=council.yaml

# 3. 启动 Web API 并 curl 测试
java -jar target/review.jar serve --server.port=8090 &
curl -s http://localhost:8090/health
curl -s -X POST -H "Content-Type: text/plain" --data-binary @council.yaml http://localhost:8090/validate

# 4. 跑 demo（需要 ANTHROPIC_API_KEY）
export ANTHROPIC_API_KEY=sk-ant-...
java -jar target/review.jar run --config=council.yaml

# 5. 启动前端（需要 Node 18+）
cd frontend
npm install
npm run dev    # → http://localhost:5173（自动代理 /api → :8090）
```

## 前端

`frontend/` 是 Vite + React + TypeScript + Tailwind + Mermaid 的单页应用。

页面：
- **Dashboard** — 后端健康检查 + 快捷入口 + session 查询
- **Validate** — 粘贴 `council.yaml` 实时校验
- **Graph** — 实时渲染 LangGraph4j StateGraph 拓扑（Mermaid）
- **Session detail** — 单个 session 的 cost / tokens / 原始 JSON

Vite dev server 把 `/api/*` 代理到后端的 `http://localhost:8090`，所以浏览器里直接 `fetch('/api/...')` 即可。生产构建用 `npm run build` 输出到 `frontend/dist/`，可挂到任意静态服务器。

## 调整说明（vs spec）

| 项目 | spec | 实际 | 原因 |
|---|---|---|---|
| 构建工具 | Gradle | Maven | 当前环境无 Gradle |
| JamJet | 0.4.0 | SQLite 直连 | 阿里云镜像无对应 artifact |
| Spring AI | 2.0.0 | 1.1.0 | 2.0 国内镜像下载慢；API 一致 |
| Graph 编排 | LangGraph4j StateGraph + 拓扑 + 并行 dispatch | ✅ StateGraph 已上线，`graph` 命令可视化 | 并行 reviewer 在 dispatch 节点内用 ExecutorService |
| Jaccard 阈值 | 0.85 | 0.7 | 实际 LLM 输出词序变化多，0.85 太严格 |
