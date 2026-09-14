# AI Code Review Council · 设计文档

**日期**：2026-09-14
**状态**：待评审
**作者**：Brainstorming Session

---

## 0. 概述与目标

### 0.1 解决的问题
构建一个**多模型对弈式 AI 代码评审系统**。给定一份 Git diff，多个 LLM 角色（架构师、安全、性能、测试）独立评审 → 聚合 → 自动修复 → 重评审循环，直到问题关闭或达到轮次上限。

### 0.2 核心设计原则
- **三框架分工明确**：Spring AI 管模型、LangGraph4j 管编排、JamJet 管运行时
- **每层都可独立配置**：模型选谁、图怎么走、跑多久、断点在哪都能改
- **生产可用**：能跑、能停、能恢复、能重放、能审计、能限预算

### 0.3 形态
- **MVP**：Java 21 + Spring Boot 3 单 JAR（CLI 优先，Web API 同步开放）
- **触发**：`java -jar review.jar run <git-ref>`
- **配置入口**：`council.yaml`（团队级）+ `application.yml`（运维级）+ CLI 参数（任务级）

### 0.4 关键非目标（v1.0.0 不做）
- 模型微调/训练
- PostgreSQL 后端（v1.1）
- Docker 官方镜像（v1.1）
- OAuth2（v1.1）
- Maven/Gradle 插件（v1.2）
- Web UI（独立项目）
- 多项目并行评审（v1.1）

---

## 1. 整体架构与分层

### 1.1 系统分层图

```
┌──────────────────────────────────────────────────────────────┐
│  ① 用户接口层 (CLI + REST API)                                │
│  ┌────────────────────┐  ┌────────────────────────────────┐ │
│  │ CLI (Picocli)      │  │ Spring Boot Web (端口可配)     │ │
│  │ review <git-diff>  │  │ POST /api/reviews              │ │
│  │ review --resume ID │  │ GET  /api/reviews/{id}         │ │
│  │ review --dry-run   │  │ POST /api/reviews/{id}/resume  │ │
│  └────────────────────┘  └────────────────────────────────┘ │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│  ② JamJet 生产运行时（持久化/审计/恢复/预算/人机协作）         │
│  - SQLite 存所有 state 快照（默认）/ PostgreSQL 可选          │
│  - 每 LLM 调用记录：input hash / output / tokens / latency    │
│  - 检查点：每完成一个 node 自动存盘                            │
│  - 崩溃恢复：CLI 重启后 `review --resume <id>` 接上次节点    │
│  - 预算熔断：单评审 token 上限 / 时间上限 / 成本上限           │
│  - Human Gate：critical 级问题暂停，等用户拍板（CLI 交互）     │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│  ③ LangGraph4j 评审委员会图（核心编排）                        │
│                                                              │
│   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌─────────────┐   ┌─────────┐ │
│   │ Planner  │ → │ Reviewer │ →│ Aggregator│ → │GateEvaluator│ → │ Fixer   │ │
│   │ (动态子) │   │   ×N     │   │  (合并)   │   │(闸门判定)   │   │ (生成   │ │
│   │          │   │ 并行     │   │           │   │HumanGate    │   │  patch) │ │
│   └──────────┘   └──────────┘   └──────────┘   └─────────────┘   └────┬────┘ │
│        ↑                                                              │       │
│        │           ┌──────────────┐                                    │       │
│        └───────────│  Re-Reviewer │←───────────────────────────────────┘       │
│                    │  (验证修复)  │                                            │
│                    └──────┬───────┘                                            │
│                           ↓  (全部通过 / 达轮数上限)                            │
│                    ┌──────────────┐                                            │
│                    │     Done     │                                            │
│                    └──────────────┘                                            │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│  ④ Spring AI 模型与工具层                                       │
│  - ChatClient：Claude / GPT / DeepSeek / Ollama（多 provider）│
│  - EmbeddingClient：代码片段向量化（用于历史评审相似度检索）   │
│  - VectorStore：默认内存 / 可选 PGvector                     │
│  - MCP Tools：jgit（读 diff）/ shell（跑测试）/ file ops      │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 配置化矩阵

| 配置维度 | 在哪配 | 谁能改 | 例子 |
|---|---|---|---|
| **模型 provider** | `application.yml` | 运维 | `spring.ai.openai.api-key`, `spring.ai.ollama.base-url` |
| **评审角色清单** | `council.yaml` | 团队 lead | 见 §3.2 |
| **图拓扑** | `council.yaml` | 架构师 | 见 §3.2 |
| **每个 reviewer 的 prompt** | `prompts/*.md` | 开发者 | 模板里 `{{diff}}` `{{language}}` 占位 |
| **预算/超时** | `application.yml` | 运维 | `jamjet.budget.max-tokens=200000` |
| **持久化后端** | `application.yml` | 运维 | `jamjet.persistence=sqlite` / `postgres` |
| **自定义节点** | Java SPI (`META-INF/services`) | 高级用户 | 实现 `Reviewer` 接口打成 JAR |

---

## 2. LangGraph4j 图结构 + State 设计

### 2.1 状态对象 `ReviewState`

```java
public record ReviewState(
    // ── 输入（不可变） ──
    String sessionId,                // JamJet 生成，用于持久化关联
    CodeDiff diff,                   // 解析后的 diff（统一抽象，不绑定 git）
    Language language,               // 探测出的语言
    CouncilConfig config,            // 当前 council.yaml 快照（运行时不可变）
    
    // ── 累积产物 ──
    List<Finding> findings,          // 所有 reviewer 产出的问题
    List<Patch> proposedPatches,     // fixer 生成的候选修复
    List<Patch> appliedPatches,      // 已应用的修复（含 re-review 结果）
    int round,                       // 当前轮次（0 = 首次评审）
    
    // ── 预算（JamJet 维护） ──
    Budget budget,                   // tokensUsed, timeElapsedMs, costUsd
    
    // ── 流程控制 ──
    FlowSignal signal                // CONTINUE | WAIT_HUMAN | DONE | ABORT
) {}
```

**关键设计**：所有累积字段都不可变。每个节点返回**新的** `ReviewState` 实例，便于：
- LangGraph4j 在并行分支里安全拷贝
- JamJet 做检查点（状态即快照）
- 任意时刻可序列化到审计日志

### 2.2 完整图拓扑

```
                    ┌─────────────────────┐
                    │  START              │
                    └──────────┬──────────┘
                               ▼
                    ┌─────────────────────┐
                    │  LoadDiff           │  ← jgit 或 stdin 读 diff
                    │  In:  (empty)       │     解析成 CodeDiff
                    │  Out: ReviewState   │
                    └──────────┬──────────┘
                               ▼
                    ┌─────────────────────┐
                    │  Planner            │  ← 选 reviewer 子集（v1 默认全跑）
                    │  In:  diff, lang    │
                    │  Out: selectedRoles │
                    └──────────┬──────────┘
                               ▼
                ╔══════════════════════════╗
                ║  ReviewerFanOut (并行)    ║  ← LangGraph4j Send API
                ║  In:  state              ║     按 selectedRoles 分发
                ║  Out: List<Finding>      ║     每角色独立分支
                ╚════════════╤═════════════╝
                             ▼
                    ┌─────────────────────┐
                    │  Aggregator          │  ← 合并 + 加权 + 决策
                    │  In:  findings       │
                    │  Out: signal         │     关键决策点 ↓
                    └──────────┬──────────┘
                               │
              ┌────────────────┼─────────────────┐
              │ no findings    │ critical       │ major/minor only
              ▼                ▼                 ▼
       ┌────────────┐   ┌──────────────┐   ┌────────────┐
       │   DONE     │   │ GateEvaluator│   │ GateEval   │
       │ (clean PR) │   │  (闸门判定)  │   │ (按规则问) │
       └────────────┘   └──────┬───────┘   └──────┬─────┘
                              │                  │
                              └────────┬─────────┘
                                       ▼
                                ┌─────────────┐
                                │   Fixer     │  ← 生成 patches
                                │   In: find  │
                                │   Out: pat  │
                                └──────┬──────┘
                                       ▼
                                ┌─────────────┐
                                │ ApplyPatches│  ← jgit apply（沙箱）
                                │   In: pat   │
                                │   Out: applied│
                                └──────┬──────┘
                                       ▼
                                ┌─────────────┐
                                │ ReReviewer  │  ← 验证修复
                                │   In: state │
                                │   Out: new  │
                                │        find │
                                └──────┬──────┘
                                       │
                       ┌───────────────┼───────────────┐
                       │ round<max &   │ round≥max     │
                       │ 有新问题      │ 或无新问题    │
                       ▼               ▼               ▼
                ┌────────────┐  ┌────────────┐  ┌────────────┐
                │ → Aggregator│  │   DONE     │  │   DONE     │
                │   (round++) │  │ (有遗留)   │  │ (干净)     │
                └────────────┘  └────────────┘  └────────────┘
```

### 2.3 节点接口（插拔协议的基石）

```java
// 所有节点的统一契约
public interface CouncilNode<I, O> {
    O apply(I input, NodeContext ctx);
}

// 具体节点类型
public interface ReviewerNode extends CouncilNode<ReviewerInput, List<Finding>> {}
public interface FixerNode    extends CouncilNode<FixerInput,    List<Patch>> {}
public interface ReReviewerNode extends CouncilNode<ReReviewInput, List<Finding>> {}

// 节点上下文（由 LangGraph4j + JamJet 注入）
public record NodeContext(
    JamJetCheckpoint checkpoint,    // 持久化句柄
    Budget budget,                   // 预算查询/扣减
    Map<String, Tool> tools,         // 该节点可用的 MCP 工具
    EventBus events                  // 进度上报（CLI/Web 都订阅）
) {}
```

### 2.4 并行评审实现

LangGraph4j 的 `Send` API 让"按角色动态分叉"成为可能：

```java
// 在 Aggregator 之后，按 state.config.selectedRoles 动态派发
.map(state -> state.config().reviewers().stream()
    .map(role -> new Send("reviewer_" + role.name(),
                          new ReviewerInput(state, role)))
    .toList())
```

每个 reviewer 在独立线程跑，结果汇入 `Aggregator`。

### 2.5 边界条件处理

| 场景 | 行为 |
|---|---|
| 单个 reviewer 抛异常 | 标记 `failed`，其他 reviewer 继续；最终报告里标"该维度未评审" |
| Aggregator 后无 finding | 直接 Done，不进 Fixer |
| Critical 问题触发 Gate | 见 §2.6 |
| Fixer 生成 patch 但 `git apply` 失败 | 标记 `failed-patch`，进入 ReReview 时作为 finding 回报 |
| ReReview 发现新问题 | round+1，回 Aggregator |
| 达到 `max-rounds` 还有问题 | 强制 Done，问题清单原样输出，标 "未关闭" |
| 预算耗尽（token / time / cost）| JamJet 抛 BudgetExceeded，保存当前 state，`--resume` 时询问是否加预算 |

### 2.6 HumanGate 颗粒度（修订版）

#### 新增独立节点 `GateEvaluator`

```
ReviewerFanOut → Aggregator → GateEvaluator → Fixer
                                  │
                                  ├─ if "需要 gate 的 finding 存在" → 触发 JamJet.interrupt()
                                  └─ if "全部自动通过" → 直达 Fixer
```

#### GateEvaluator 判定规则

```yaml
human-gates:
  # 默认：所有 reviewer 都不需要人工
  default: never

  # 按 reviewer 维度覆盖
  per-reviewer:
    security:
      - severity: critical       # 该 reviewer 的 critical 全问
        action: must_approve      # 必须 approve 才继续
      - severity: major
        action: notify_only       # 通知，但不阻塞
    architect:
      - severity: critical
        action: must_approve
      - severity: major
        action: must_approve
    performance:
      - severity: critical
        action: must_approve
    test_coverage:
      - severity: critical
        action: must_approve
    style:
      - any: skip                 # 这个 reviewer 从来不 gate

  # 按文件路径规则（高级用户）
  path-rules:
    - pattern: "**/auth/**"
      gate: always                # 改鉴权代码必须人工
    - pattern: "**/migration/**"
      gate: always                # 数据库迁移必须人工
    - pattern: "**/*.test.*"
      gate: never                 # 测试代码自动过
    - pattern: "**/Generated*.java"
      gate: never                 # 生成代码自动过
```

#### 优先级与组合
多条规则命中时按这个顺序合并（先匹配的优先）：
1. `per-reviewer.<role>.path-rules`（最具体的匹配）
2. `per-reviewer.<role>`（按角色）
3. `path-rules`（按路径）
4. `default`（兜底）

#### CLI 交互形态

```
─────────────────────────────────────────────────────
🔒 HumanGate · finding #2 of 3

  Reviewer:  security
  Severity:  CRITICAL
  Location:  src/main/auth/OAuthHandler.java:127
  Message:   Hardcoded client secret detected in source

  Suggested Fix (preview):
    - private static final String SECRET = "abc123...";
    + private static final String SECRET = System.getenv("OAUTH_SECRET");

  [a]pprove  [r]eject  [s]kip(fix others)  [d]etails  [q]uit
─────────────────────────────────────────────────────
```

Web API 形态：`POST /api/reviews/{id}/gate/{findingId}/decision`，body 是 `{action: "approve"|"reject"|"skip"}`。

#### 决策记录（审计）

```json
{
  "gate_id": "uuid",
  "finding_id": "f-002",
  "decision": "reject",
  "decided_by": "user@local",
  "decided_at": "2026-09-14T10:23:45Z",
  "rule_matched": "per-reviewer.security.severity=critical"
}
```

---

## 3. 配置系统 + 扩展机制

### 3.1 配置来源（分层覆盖，从上到下优先级递增）

```
┌─────────────────────────────────────────────────────────┐
│ Layer 0 · 代码默认值（DefaultCouncilConfig）            │  兜底
├─────────────────────────────────────────────────────────┤
│ Layer 1 · application.yml（Spring Boot 标准）           │  运维级
│   spring.ai.*  jamjet.*  server.port  等                │
├─────────────────────────────────────────────────────────┤
│ Layer 2 · council.yaml（评审委员会声明）                 │  团队级
│   评审角色/模型/prompt 路径/拓扑/human-gates           │
├─────────────────────────────────────────────────────────┤
│ Layer 3 · CLI 参数 / Web API 请求体                      │  单次任务
│   --config=team-a.yaml  --max-rounds=5  --dry-run      │
├─────────────────────────────────────────────────────────┤
│ Layer 4 · 环境变量（最高优先级）                          │  CI/CD
│   REVIEW_COUNCIL_CONFIG=/path  OPENAI_API_KEY=...      │
└─────────────────────────────────────────────────────────┘
```

### 3.2 `council.yaml` 完整结构

```yaml
# 评审委员会元数据
council:
  name: "team-java-backend"
  version: "1.0"                    # schema 版本，便于未来 migration
  description: "Java 后端服务的标准评审委员会"

# 评审范围控制
scope:
  include-globs: ["src/**/*.java"]
  exclude-globs: ["**/generated/**", "**/*.test.java"]
  max-diff-lines: 5000              # 超过则提示用户拆分 PR
  languages: [java, kotlin]         # 用于 prompt 上下文

# 评审循环
loop:
  max-rounds: 3
  early-stop: "no-critical-and-no-major"
  # 可选: "no-critical" | "no-issues" | "never"

# 评审角色清单（核心配置）
reviewers:
  - role: architect
    enabled: true
    model: claude-opus-5
    prompt-file: prompts/architect.md
    tools: [git_history, dependency_graph]
    timeout-seconds: 180
    retry-on-error: 1
    
  - role: security
    enabled: true
    model: gpt-5
    prompt-file: prompts/security.md
    tools: [secret_scanner, cve_db]
    timeout-seconds: 240
    
  - role: performance
    enabled: true
    model: ollama:qwen2.5-coder-32b
    prompt-file: prompts/perf.md
    tools: [git_history]
    timeout-seconds: 120
    
  - role: test_coverage
    enabled: true
    model: claude-haiku-4-5
    prompt-file: prompts/test.md
    
  - role: style
    enabled: false                # 关掉，不参与

# 聚合策略
aggregator:
  strategy: weighted_vote          # weighted_vote | unanimous | any_critical
  weights:
    critical: 3
    major: 2
    minor: 1
  dedup:
    enabled: true                 # 合并相似 finding（jaccard > 0.85）
    threshold: 0.85

# 人工闸门
human-gates:
  default: never
  per-reviewer:
    security:
      - severity: critical
        action: must_approve
    architect:
      - severity: critical
        action: must_approve
      - severity: major
        action: must_approve
  path-rules:
    - pattern: "**/auth/**"
      gate: always
    - pattern: "**/migration/**"
      gate: always

# 修复器
fixer:
  model: claude-opus-5
  prompt-file: prompts/fixer.md
  auto-apply: minor                # never | minor | minor_and_major
  human-gate-on: critical          # 与 human-gates 一致
  max-patches-per-round: 20
  tools: [git_apply, test_runner]  # fixer 能跑测试验证

# 重评审
re-reviewer:
  enabled: true
  model: claude-sonnet-5
  prompt-file: prompts/re-review.md
  focus: [regression, test_pass, style_drift]

# 预算
budget:
  max-tokens: 500000
  max-time-seconds: 1800
  max-cost-usd: 5.00
  on-exceeded: pause_and_ask       # pause_and_ask | abort | silent_continue

# 输出
output:
  format: [markdown, json]         # CLI 默认 markdown，API 默认 json
  include-applied-diffs: true
  include-failed-attempts: true
  save-to: .review-history/        # 留底，每次评审留一份
```

### 3.3 Prompt 模板系统

每个 reviewer/fixer/re-reviewer 都对应一个 prompt 文件，用 `{{ }}` 占位符：

```markdown
<!-- prompts/security.md -->
You are a security reviewer for {{language}} code.

Repository context:
- Project: {{repo.name}}
- Protected paths: {{repo.protectedPaths}}

Review the following diff for:
- Injection vulnerabilities (SQL, command, XSS)
- Authentication/authorization flaws
- Secret leakage
- Insecure dependencies

Diff:
```
{{diff}}
```

Output format (JSON):
{
  "findings": [
    {"severity": "critical|major|minor", "line": <n>, "message": "...", "suggested_fix": "..."}
  ]
}
```

可用占位符：`{{diff}}`、`{{language}}`、`{{repo.*}}`、`{{config.*}}`、`{{previousFindings}}`、`{{round}}`

### 3.4 扩展机制 1：Java SPI（自定义节点）

用户想加新角色（如 `db_schema_reviewer`），不用改主代码：

```java
// 用户在自己的 JAR 里
public class DbSchemaReviewer implements ReviewerNode {
    @Override
    public List<Finding> apply(ReviewerInput input, NodeContext ctx) {
        // 用户自定义逻辑：连数据库、查 schema、做检查
        var schemaDiff = fetchSchemaDiff(input.diff());
        return checkAgainstPolicies(schemaDiff, ctx.config());
    }
}

// META-INF/services/com.review.council.ReviewerNode
com.example.myreviewers.DbSchemaReviewer
```

然后 `council.yaml` 加：

```yaml
reviewers:
  - role: db_schema
    impl: com.example.myreviewers.DbSchemaReviewer  # 覆盖默认实现
```

### 3.5 扩展机制 2：MCP 工具接入

Reviewer/Fixer 能用的工具都通过 MCP 协议暴露：

```yaml
tools:
  secret_scanner:
    type: mcp
    command: ["python", "-m", "my_scanner"]
    env: {SCANNER_RULES: "owasp-top10"}
  test_runner:
    type: builtin               # 内置：junit/maven/npm test
    command: ["mvn", "test", "-DfailIfNoTests=false"]
    timeout-seconds: 300
  git_apply:
    type: builtin
    sandbox: true               # 在隔离 worktree 里跑
```

内置工具清单（v1）：`git_diff`、`git_apply`、`file_read`、`file_write`、`shell_exec`、`test_runner`、`git_history`、`secret_scanner`

### 3.6 配置验证：`review --validate`

不改代码也能校验配置：

```bash
$ java -jar review.jar --validate --config=council.yaml
✓ Schema valid (version 1.0)
✓ All 4 reviewers resolvable
✓ All prompt files exist
✓ All tool definitions valid
✓ Model providers reachable (claude-opus-5, gpt-5, ollama:qwen2.5-coder-32b)
✗ reviewer.style references prompt-file "prompts/style.md" but file not found
```

### 3.7 热更新策略

- **MVP**：session 启动时加载 council.yaml 快照，session 内不变（可预测、好调试）
- **v1.1**：文件 watch（jdk WatchService）+ JamJet checkpoint 版本控制，能识别"配置变更后接续 review"

---

## 4. JamJet 集成 + 持久化 + 审计 + 预算 + 崩溃恢复

### 4.1 JamJet 在本系统中的角色

JamJet 不是"接进来就行"的库，而是**全系统的运行时外壳**。我们用它的四大核心能力：

| JamJet 能力 | 在本系统的应用 |
|---|---|
| **持久化** | 每个 state 变更都快照，能从任意节点恢复 |
| **审计** | 每次 LLM 调用、每次工具调用、每次决策都留痕 |
| **预算** | token / 时间 / 成本三轴监控，超限行为可配置 |
| **人机协作** | interrupt + resume 机制实现 HumanGate |

### 4.2 持久化结构（SQLite 默认，PostgreSQL 可选）

```sql
-- 评审会话
CREATE TABLE review_sessions (
    id              TEXT PRIMARY KEY,         -- UUID
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    config_hash     TEXT NOT NULL,
    config_snapshot TEXT NOT NULL,
    status          TEXT NOT NULL,            -- running|paused|completed|failed|aborted
    current_node    TEXT,
    diff_hash       TEXT NOT NULL,
    diff_content    TEXT NOT NULL,
    metadata        JSON
);

-- State 快照（每个节点完成时存一条）
CREATE TABLE state_snapshots (
    session_id      TEXT NOT NULL REFERENCES review_sessions(id),
    node_name       TEXT NOT NULL,
    round           INTEGER NOT NULL,
    state_json      TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    PRIMARY KEY (session_id, node_name, round)
);

-- 审计日志（不可变）
CREATE TABLE audit_events (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT NOT NULL,
    node_name       TEXT,
    event_type      TEXT NOT NULL,
    timestamp       TIMESTAMP NOT NULL,
    payload         JSON NOT NULL,
    parent_event_id INTEGER
);

-- LLM 调用专项
CREATE TABLE llm_calls (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT NOT NULL,
    node_name       TEXT NOT NULL,
    model           TEXT NOT NULL,
    prompt_tokens   INTEGER,
    completion_tokens INTEGER,
    cost_usd        REAL,
    latency_ms      INTEGER,
    success         BOOLEAN,
    error           TEXT,
    timestamp       TIMESTAMP NOT NULL
);

-- 工具调用
CREATE TABLE tool_calls (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT NOT NULL,
    tool_name       TEXT NOT NULL,
    arguments       JSON,
    result          JSON,
    success         BOOLEAN,
    error           TEXT,
    latency_ms      INTEGER,
    timestamp       TIMESTAMP NOT NULL
);

CREATE INDEX idx_sessions_status ON review_sessions(status);
CREATE INDEX idx_snapshots_session ON state_snapshots(session_id);
CREATE INDEX idx_audit_session ON audit_events(session_id, timestamp);
CREATE INDEX idx_llm_calls_session ON llm_calls(session_id);
```

### 4.3 审计事件示例

```json
// LLM 调用
{
  "type": "llm_call",
  "session_id": "rev-abc",
  "node": "reviewer_security",
  "model": "gpt-5",
  "tokens": {"prompt": 4521, "completion": 823},
  "cost_usd": 0.0423,
  "latency_ms": 6420,
  "input_hash": "sha256:...",
  "output_preview": "{\"findings\":[{...}]}"
}

// HumanGate 决策
{
  "type": "gate_decision",
  "session_id": "rev-abc",
  "finding_id": "f-002",
  "decision": "reject",
  "decided_by": "user@local",
  "rule_matched": "per-reviewer.security.severity=critical"
}

// 状态变更
{
  "type": "state_change",
  "session_id": "rev-abc",
  "from_node": "aggregator",
  "to_node": "gate_evaluator",
  "trigger": "found_critical_finding",
  "round": 0
}
```

### 4.4 预算三轴

```java
public record BudgetState(
    long tokensUsed,
    long maxTokens,
    long elapsedMs,
    long maxTimeMs,
    BigDecimal costUsd,
    BigDecimal maxCostUsd
) {
    public BudgetStatus check() {
        if (tokensUsed >= maxTokens) return EXHAUSTED_TOKENS;
        if (elapsedMs >= maxTimeMs)  return EXHAUSTED_TIME;
        if (costUsd.compareTo(maxCostUsd) >= 0) return EXHAUSTED_COST;
        if (tokensUsed > maxTokens * 0.8) return WARN_TOKENS;
        return OK;
    }
}
```

**预算检查时机**：
- 每个节点**入口**检查（防止无意义启动）
- 每个 LLM 调用**返回后**累计
- 每秒一次的 watchdog（防 hang）

**触发后行为**（由 `budget.on-exceeded` 配置）：
- `pause_and_ask`（默认）：JamJet interrupt，CLI 弹"加预算还是终止"
- `abort`：保存当前 state，session 标 `aborted`，下次 `resume` 需要改 budget
- `silent_continue`：记 WARN 事件，不阻塞（危险，仅用于调试）

### 4.5 崩溃恢复流程

```
                          ┌─────────────────┐
                          │  Session Start  │
                          └────────┬────────┘
                                   ▼
                          ┌─────────────────┐
                          │ 检查未完成 session │
                          └────────┬────────┘
                                   │
                       ┌───────────┼───────────┐
                       ▼           ▼           ▼
                  无未完成     有 paused    有 running
                  （新会话）   （human gate） （崩溃/强杀）
                       │           │           │
                       ▼           ▼           ▼
                   正常流程     → 询问用户    → 询问用户
                                approve/reject  resume from
                                继续            last checkpoint
```

**`review --resume <sessionId>` 行为**：
1. 加载 session 元数据 + config_snapshot
2. 校验 config 是否被改（hash 比对，不一致提示）
3. 加载**最近一次成功的 state_snapshot**
4. 从当前节点的下一个节点继续
5. JamJet 把后续事件都 append 到原审计日志

**关键保障**：
- 每个节点是**幂等**的（重新执行结果一致，便于重放）
- checkpoint 是**写前日志**（state 落库后才更新 `current_node`）
- 重新执行会**重算 token/cost**（不重复扣费，只记录）

### 4.6 Replay 能力

```bash
$ java -jar review.jar --replay rev-abc
Session rev-abc · 2026-09-14 · 4 reviewers · 2 rounds · $0.42 · 38s

Round 1
  Planner      · 4 reviewers selected
  Architect    · 1 critical, 2 major        (4.2s · 4521 tok · $0.12)
  Security     · 2 critical, 0 major        (6.4s · 3802 tok · $0.04)
  ...

Round 2
  Aggregator   · 3 unique findings (after dedup)
  Gate         · 2 critical found · user approved all
  Fixer        · 3 patches generated · all applied
  ReReview     · 0 new issues
DONE

# 成本分析（按 reviewer 维度聚合）
$ java -jar review.jar --analyze-cost rev-abc
              tokens    cost     avg_latency
architect     18,420    $0.18    4.2s
security      24,103    $0.09    6.4s
...
```

### 4.7 与 LangGraph4j 节点的双向绑定

```
LangGraph4j 节点执行前 ─┐
                        ├──→ JamJet.beginNode()
                        │       ├─ 检查预算
                        │       ├─ 写 state 快照（前）
                        │       └─ 开始计时
                        ↓
                    [节点逻辑跑]
                        ↓
                        ├──→ JamJet.endNode(result)
                        │       ├─ 累计 token/cost
                        │       ├─ 写 state 快照（后）
                        │       ├─ 写审计事件
                        │       └─ 检查是否触发 gate
                        ↓
LangGraph4j 拿到返回值流转
```

JamJet 的 `NodeContext` 注入到每个 LangGraph4j 节点方法里 —— 节点不用知道 JamJet 的存在，但要做的关键操作（check state / write audit / trigger gate）都通过它做。

---

## 5. CLI + Web API 接口 + 错误处理

### 5.1 CLI 命令（Picocli）

```bash
# 全局参数（可放任意子命令前）
--config=<path>           # council.yaml 路径，默认 ./council.yaml
--data-dir=<path>         # SQLite/历史 目录，默认 ./.review-data
--log-level=<level>       # trace|debug|info|warn|error
--non-interactive         # CI 模式：所有 gate/预算询问自动按策略执行
--model-override=<map>    # 临时覆盖模型

# 子命令
review run [OPTIONS] <GIT_REF>
  GIT_REF: 分支/commit/PR 号（如 HEAD, main, origin/main, #123）
  --base=<ref>             # diff base
  --max-rounds=<n>
  --output=<path>
  --format=<fmt>           # markdown|json|sarif
  --dry-run

review resume <SESSION_ID> [--budget-tokens=<n>] [--budget-cost=<usd>]
review show <SESSION_ID>
review replay <SESSION_ID>
review cost <SESSION_ID>
review diff <SESSION_ID> <ROUND>

review validate --config=<path>
review init
review prompts list|show <name>
review tools list
review models ping

review sessions list
review sessions gc --older-than=<days>
review export <SESSION_ID> --out=<f>
```

### 5.2 Web API

```yaml
# application.yml
server:
  port: 8080
review:
  web:
    enabled: true
    auth: api-key             # none | api-key | oauth2
    api-keys:                 # 简单模式
      - name: ci-bot
        key: ${REVIEW_API_KEY_CI}
```

#### REST 端点

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/v1/reviews` | 创建评审（异步） |
| `GET`  | `/api/v1/reviews/{id}` | 查询状态和结果 |
| `GET`  | `/api/v1/reviews/{id}/state` | 完整 ReviewState（调试） |
| `GET`  | `/api/v1/reviews/{id}/audit` | 流式审计日志（SSE） |
| `POST` | `/api/v1/reviews/{id}/gates/{findingId}/decision` | HumanGate 决策 |
| `POST` | `/api/v1/reviews/{id}/resume` | 恢复暂停/崩溃会话 |
| `POST` | `/api/v1/reviews/{id}/abort` | 主动终止 |
| `GET`  | `/api/v1/reviews?status=&limit=` | 列会话 |
| `GET`  | `/api/v1/configs` | 列出可用配置 |
| `POST` | `/api/v1/configs/validate` | 校验配置 |

#### 创建评审请求/响应

```http
POST /api/v1/reviews
{
  "config": "team-java-backend",
  "diff_source": {
    "type": "git",
    "repo": "/path/to/repo",
    "base": "main",
    "head": "feature/auth-fix"
  },
  "options": {
    "max_rounds": 3,
    "non_interactive": false,
    "callback_url": "https://your-app/webhooks/review-complete"
  }
}
```
```http
202 Accepted
Location: /api/v1/reviews/rev-abc123
{
  "session_id": "rev-abc123",
  "status": "running",
  "created_at": "2026-09-14T10:00:00Z",
  "stream_url": "/api/v1/reviews/rev-abc123/audit"
}
```

#### 查询状态响应

```json
{
  "session_id": "rev-abc123",
  "status": "paused",
  "current_node": "gate_evaluator",
  "round": 1,
  "pending_gates": [
    {
      "finding_id": "f-002",
      "reviewer": "security",
      "severity": "critical",
      "location": "src/auth/OAuthHandler.java:127",
      "message": "Hardcoded client secret",
      "rule_matched": "per-reviewer.security.severity=critical"
    }
  ],
  "summary": {
    "findings_total": 5,
    "findings_by_severity": {"critical": 2, "major": 2, "minor": 1},
    "rounds_done": 1,
    "tokens_used": 18420,
    "cost_usd": 0.18,
    "elapsed_seconds": 42
  }
}
```

#### 审计流（SSE）

```
event: llm_call
data: {"node":"reviewer_security","model":"gpt-5","tokens":3802,"cost":0.04}

event: gate_decision
data: {"finding":"f-002","action":"approve"}

event: state_change
data: {"from":"gate_evaluator","to":"fixer","round":1}
```

### 5.3 错误处理分层

#### Layer 1：CLI/API 入口错误（用户能直接改的）

| 错误 | 处理 |
|---|---|
| `council.yaml` 不存在/解析失败 | 友好报错，提示路径 + `review validate` 建议 |
| 模型 API key 缺失 | 提示去 `application.yml` 配 |
| Git 仓库读取失败 | 提示路径/权限问题 |
| API key 无效（Web） | 401 + `WWW-Authenticate` |

#### Layer 2：配置错误（用户需改 council.yaml）

| 错误 | 处理 |
|---|---|
| reviewer 引用的 prompt 文件不存在 | 启动时 fail-fast，提示具体路径 |
| 模型名不在白名单 | 启动校验拦截 |
| 拓扑引用不存在的节点 | `review validate` 拦截 |

#### Layer 3：运行时错误（系统能恢复的）

| 错误 | 处理 |
|---|---|
| 单个 reviewer 抛异常 | 标记 failed，记录审计，其他 reviewer 继续 |
| 单次 LLM 调用超时 | 重试 N 次（默认 1），仍失败则该 reviewer failed |
| Tool 调用失败 | 重试 + 降级（标记 finding 为 `unverified`） |
| Fixer 生成的 patch `git apply` 失败 | 进入 re-review 时作为 finding 回归 |

#### Layer 4：致命错误（需用户介入）

| 错误 | 处理 |
|---|---|
| 预算耗尽 + `pause_and_ask` | JamJet interrupt，CLI 弹问 |
| Aggregator 后状态损坏（如 json 解析失败） | 保存快照，session 标 failed |
| 数据库写入失败 | 立即 abort，避免脏 checkpoint |
| 所有 reviewer 都失败 | 不进 Fixer，session 标 `no_review_possible` |

#### 错误信息格式（统一）

```json
{
  "error": {
    "code": "BUDGET_EXHAUSTED",
    "message": "Token budget exceeded (used 520000 / limit 500000)",
    "recoverable": true,
    "next_action": "review resume rev-abc --budget-tokens=1000000",
    "documentation": "https://docs.review.local/errors/BUDGET_EXHAUSTED"
  }
}
```

### 5.4 安全要点

| 关注 | 处理 |
|---|---|
| **diff 内容可能含 secret** | 输入侧：默认 redact（正则匹配 secret pattern），原始内容不持久化 |
| **fixer 自动改代码的风险** | 沙箱 worktree + dry-run 默认开；`--apply` 才真改 |
| **API key 泄露** | 日志默认 mask；`--export` 时所有敏感字段重写 |
| **审计日志含敏感数据** | 单独 `audit-retention` 配置 + 加密存储选项（v1.1） |
| **Web API 认证** | v1 用 API key + 路径白名单；v1.1 接 OAuth2 |

---

## 6. 测试策略 + 部署形态 + MVP 落地清单

### 6.1 测试策略（金字塔）

```
                    ╱╲
                   ╱  ╲
                  ╱ E2E╲             1-3 个：跑完整流程的真实 diff
                 ╱──────╲
                ╱ 集成测试 ╲           10-15 个：单节点/双节点组合 + 真实 JamJet
               ╱────────────╲
              ╱   单元测试    ╲        40-60 个：纯函数、配置解析、序列化
             ╱────────────────╲
```

#### Layer 1：单元测试（纯逻辑，无 LLM）

| 模块 | 测试重点 |
|---|---|
| `CouncilConfig` 解析 | yaml → 对象，schema 校验，默认值填充 |
| `Aggregator` | 加权投票、去重（Jaccard）、critical 判断 |
| `GateEvaluator` | 规则匹配顺序、优先级合并 |
| `PromptTemplate` | 占位符渲染、转义 |
| `BudgetState` | 三轴检查、warn/exhausted 边界 |
| `IdempotencyKey` | 生成、序列化、比对 |
| `SecretRedactor` | 各 secret pattern 命中 |
| `DiffParser` | 各种 git diff 格式（unified、rename、binary） |

#### Layer 2：集成测试（节点 + 真实 JamJet，Mock LLM）

| 场景 | 验证 |
|---|---|
| 单 reviewer 失败不影响其他人 | 抛异常的 reviewer → 其他继续 → 报告里标"未评审" |
| Aggregator 去重生效 | 两个 reviewer 找相似问题 → 合并为 1 |
| HumanGate 触发 | critical finding → JamJet interrupt → 恢复 |
| Fixer patch 应用失败 | `git apply` 失败 → re-review 时作为 finding |
| ReReview 发现新问题 | round+1 回 Aggregator |
| 达到 max-rounds | 强制 Done，遗留 finding 标 "未关闭" |
| 预算耗尽 | 三种 on-exceeded 策略各跑一遍 |
| Crash recovery | session 运行到一半 kill，`resume` 从 last checkpoint |
| 并行 reviewer | 4 个 reviewer 同时跑，耗时 < max(latency) × 1.2 |

**Mock LLM 提供商**：

```java
@Test
void securityReviewerFindsHardcodedSecret() {
    mockLLM.register("hardcoded secret", List.of(
        new Finding("critical", 127, "Hardcoded client secret", "...")
    ));
    var findings = securityReviewer.apply(input, ctx);
    assertThat(findings).hasSize(1);
}
```

#### Layer 3：端到端测试（完整流程，真实 LLM 但用最便宜模型）

| 场景 | 做法 |
|---|---|
| 简单 PR（10 行 Java） | 用真实 Claude Haiku，全流程跑通 |
| 中等 PR（200 行） | 4 reviewer 并行 |
| 边界 case：超大 diff | 验证 max-diff-lines 拦截 |
| CLI/Web 互通 | Web 创建的 session，CLI 能 resume |

E2E 用例 CI 默认只跑"简单 PR"那条；本地 `make e2e-full` 跑全部。

#### 覆盖目标

| 层 | 目标 |
|---|---|
| 单元测试 | 行覆盖 ≥ 80%，分支覆盖 ≥ 70% |
| 集成测试 | 关键路径 100% 覆盖（所有 §2.5 列出的场景） |
| E2E | 至少 1 个 happy path + 1 个 recovery path |

### 6.2 部署形态

#### 形态 1：单 JAR（默认）

```bash
./gradlew bootJar
# 产物：build/libs/review-1.0.0.jar (~80MB)

java -jar review.jar run --config=council.yaml HEAD~1..HEAD
java -jar review.jar --server web --port 8080
```

**系统要求**：JRE 21+，最低 512MB 堆（建议 2GB），磁盘每次评审 5-50MB。

#### 形态 2：Docker（可选，v1.1 官方镜像）

```dockerfile
FROM eclipse-temurin:21-jre
COPY review.jar /app/review.jar
ENTRYPOINT ["java", "-jar", "/app/review.jar"]
```

### 6.3 MVP 落地清单（v1.0.0）

#### ✅ MUST（发布即用）

| 类别 | 功能 |
|---|---|
| **CLI** | `run` / `resume` / `validate` / `init` / `show` / `cost` |
| **Web API** | 创建/查询/决策/恢复/中止/审计流 6 个核心端点 |
| **评审角色** | 4 个内置：architect / security / performance / test_coverage |
| **聚合** | 加权投票 + Jaccard 去重 |
| **HumanGate** | critical 必问（按规则配置） |
| **Fixer** | 自动生成 patch，在 worktree 沙箱应用，--apply 才落地 |
| **ReReviewer** | 跑测试 + 看 diff + 标回归 |
| **预算** | token/time/cost 三轴，三种 on-exceeded 策略 |
| **持久化** | SQLite 默认；schema migration v1→v2 框架 |
| **审计** | llm_call/tool_call/gate_decision/state_change 四类事件 |
| **崩溃恢复** | `review resume <id>` 接 last checkpoint |
| **Replay** | `review replay <id>` 完整回放 |
| **Secret redact** | 默认开，覆盖常见 pattern |
| **配置** | 4 层覆盖（代码/yaml/CLI/env）|
| **SPI 扩展** | `ReviewerNode` 接口 + META-INF/services |
| **MCP 工具** | git_diff / git_apply / file_read / file_write / shell_exec |
| **错误处理** | 4 层错误分类，结构化 error 输出 |
| **测试** | 单元 + 集成 + 1 个 E2E |

#### 🕐 DEFERRED（v1.1+）

- PostgreSQL 持久化后端
- 配置文件热更新（WatchService）
- Docker 镜像官方构建
- OAuth2 认证
- Maven/Gradle 插件
- Prompt 模板可视化编辑器
- SARIF 输出格式
- Planner Agent 动态生成 reviewer 子集
- 多项目并行评审
- Web UI

#### ❌ OUT OF SCOPE

- 模型微调 / 训练
- 任何对 LLM 提供商本身的封装（即不替代 Spring AI 已有的）
- 实时协作（多人同时评审一个 session）
- 移动端

### 6.4 验收标准（Definition of Done）

1. **功能验收**
   - [ ] `make demo` 一条命令跑通完整 demo（在 sample repo 上）
   - [ ] 4 个内置 reviewer 都能产出有效 finding（人工抽检 5 个 PR）
   - [ ] HumanGate CLI 交互可用；Web API 决策可用
   - [ ] Fixer 生成的 patch 至少 80% 能 `git apply` 成功
   - [ ] ReReviewer 能识别至少 1 类回归（测试失败）
   - [ ] Crash recovery 实测：kill -9 进程后 resume 能继续

2. **质量验收**
   - [ ] 单元测试覆盖率 ≥ 80%
   - [ ] 集成测试覆盖所有边界 case
   - [ ] 1 个 E2E happy path + 1 个 recovery path 通过

3. **可运维验收**
   - [ ] README 一遍能跑通
   - [ ] `--validate` 能抓出常见配置错误
   - [ ] `review cost <id>` 输出真实可信（与 provider 后台对账）
   - [ ] 默认配置下，单次评审（200 行 diff）总成本 ≤ $0.5

4. **文档验收**
   - [ ] 设计文档（本文件）
   - [ ] 用户手册（`docs/user-guide.md`）
   - [ ] 配置参考（`docs/council-yaml-reference.md`）
   - [ ] SPI 开发指南（`docs/custom-reviewer.md`）
   - [ ] 故障排查（`docs/troubleshooting.md`）

### 6.5 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| LLM API 不稳定 | 评审中断 | 每个 reviewer 节点内自动重试 + 整体 budget 控制 |
| LLM 输出不合规 JSON | Aggregator 失败 | 严格 prompt + 输出解析 fallback（部分成功） |
| Fixer 改坏代码 | 用户信任崩塌 | 默认沙箱 + diff 预览 + `--apply` 才落地 + undo 支持 |
| 成本失控 | 财务冲击 | 三轴预算 + 默认上限 + monthly report（v1.1） |
| 大 diff 跑得慢 | 体验差 | max-diff-lines + 自动拆分建议（v1.1） |
| 多语言支持差 | 局限 Java 团队 | v1 内置 java/kotlin prompt，架构留多语言钩子 |

---

## 附录 A：技术栈与版本

| 组件 | 版本 | 说明 |
|---|---|---|
| Java | 21 LTS | Spring Boot 3 + LangGraph4j 要求 |
| Spring Boot | 3.3.x | Web / Config / Actuator |
| Spring AI | 1.0.x | ChatClient / EmbeddingClient / VectorStore |
| LangGraph4j | 最新稳定版 | StateGraph + Send API |
| JamJet | 最新稳定版 | 持久化 + 审计 + interrupt |
| Picocli | 4.7.x | CLI 框架 |
| SQLite JDBC | 3.45.x | 默认持久化 |
| Jackson | 2.17.x | JSON 序列化 |
| JUnit 5 | 5.10.x | 测试框架 |
| Testcontainers | 1.20.x | 集成测试 |

## 附录 B：项目结构

```
review-council/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── council.yaml                  # 默认 council 配置
├── application.yml               # 默认 Spring 配置
├── prompts/                      # 默认 prompts
│   ├── architect.md
│   ├── security.md
│   ├── perf.md
│   ├── test.md
│   ├── fixer.md
│   └── re-review.md
├── sample-repo/                  # demo 用的样本仓库
│   └── ...
├── docs/
│   ├── user-guide.md
│   ├── council-yaml-reference.md
│   ├── custom-reviewer.md
│   └── troubleshooting.md
└── src/
    ├── main/
    │   ├── java/com/review/council/
    │   │   ├── CouncilApplication.java
    │   │   ├── cli/              # Picocli commands
    │   │   ├── web/              # REST controllers
    │   │   ├── graph/            # LangGraph4j graph definition
    │   │   ├── nodes/            # Node implementations
    │   │   ├── state/            # ReviewState + types
    │   │   ├── config/           # CouncilConfig + YAML parser
    │   │   ├── aggregator/       # Aggregator + dedup
    │   │   ├── gate/             # GateEvaluator
    │   │   ├── fixer/            # Fixer + patch apply
    │   │   ├── reviewer/         # Built-in reviewer implementations
    │   │   ├── tools/            # MCP tool wrappers
    │   │   ├── persistence/      # JamJet integration + SQLite
    │   │   ├── audit/            # Audit log writers
    │   │   ├── budget/           # Budget tracking
    │   │   ├── secret/           # Secret redactor
    │   │   └── spi/              # SPI interfaces
    │   └── resources/
    │       ├── application.yml
    │       ├── council-default.yaml
    │       ├── prompts/          # 内置 prompts 打包
    │       └── db/migration/     # Flyway migrations
    └── test/
        ├── java/com/review/council/
        │   ├── unit/             # 单元测试
        │   ├── integration/      # 集成测试
        │   └── e2e/              # 端到端测试
        └── resources/
            └── fixtures/         # 测试用 diff/config
```

---

**文档版本**：v1.0
**最后更新**：2026-09-14
