package com.review.council.web;

import com.review.council.audit.LlmCallRepository;
import com.review.council.config.CouncilConfig;
import com.review.council.config.CouncilConfigYamlLoader;
import com.review.council.graph.CouncilOrchestrator;
import com.review.council.graph.CouncilStateGraphRunner;
import com.review.council.persistence.SessionRepository;
import com.review.council.reviewer.FindingParser;
import com.review.council.state.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * REST API (web mode via `review serve`).
 *
 * POST /sessions          - start a new review
 * GET  /sessions/{id}     - get session status + findings
 * GET  /sessions/{id}/cost - total cost so far
 * GET  /sessions/{id}/graph - Mermaid graph snapshot
 * POST /validate          - validate a council.yaml body
 * GET  /health            - liveness
 */
@RestController
public class ReviewController {

    @Autowired CouncilOrchestrator orchestrator;
    @Autowired CouncilStateGraphRunner graphRunner;
    @Autowired CouncilConfigYamlLoader loader;
    @Autowired SessionRepository sessions;
    @Autowired LlmCallRepository llmCalls;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "ts", System.currentTimeMillis());
    }

    @PostMapping("/sessions")
    public Map<String, Object> start(@RequestBody StartRequest req) throws Exception {
        CouncilConfig config;
        try (var in = new ByteArrayInputStream(req.yaml().getBytes())) {
            config = loader.load(in);
        } catch (Exception e) {
            return Map.of("error", "invalid config: " + e.getMessage());
        }
        String sessionId = "rev-" + UUID.randomUUID().toString().substring(0, 8);
        sessions.insert(sessionId, "hash", "yaml", "running", "diff-hash", req.diff());

        var initial = new ReviewState(sessionId,
            new CodeDiff(req.gitRef() != null ? req.gitRef() : "HEAD", "HEAD", List.of()),
            Language.JAVA, config,
            List.of(), List.of(), List.of(), 0,
            Budget.empty(config.budget().maxTokens(),
                config.budget().maxTimeSeconds() * 1000,
                config.budget().maxCostUsd()),
            FlowSignal.CONTINUE);

        var result = orchestrator.run(initial);
        return Map.of(
            "sessionId", sessionId,
            "rounds", result.round(),
            "findings", result.findings(),
            "appliedPatches", result.appliedPatches().size(),
            "cost", orchestrator.totalCost(sessionId)
        );
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        var s = sessions.load(id);
        if (s.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(s.get());
    }

    @GetMapping("/sessions/{id}/cost")
    public Map<String, Object> cost(@PathVariable String id) {
        return Map.of(
            "sessionId", id,
            "costUsd", BigDecimal.valueOf(llmCalls.totalCostFor(id)),
            "tokens", llmCalls.totalTokensFor(id)
        );
    }

    @GetMapping(value = "/sessions/{id}/graph", produces = "text/plain")
    public String graphMermaid(@RequestParam(defaultValue = "mermaid") String format) throws Exception {
        CouncilConfig config;
        try (var in = new java.io.FileInputStream("./council.yaml")) {
            config = loader.load(in);
        }
        var g = graphRunner.defineGraph(config);
        var type = format.equalsIgnoreCase("plantuml")
            ? org.bsc.langgraph4j.GraphRepresentation.Type.PLANTUML
            : org.bsc.langgraph4j.GraphRepresentation.Type.MERMAID;
        return g.getGraph(type, config.name()).content();
    }

    /**
     * Structured topology for the new UI (v1.1).
     * Returns JSON with typed nodes / edges / legend so the frontend can render
     * a clean graph without relying on LangGraph4j's verbatim mermaid template
     * (which produces duplicate commented edges and no styling on __START__/__END__).
     */
    @GetMapping(value = "/sessions/{id}/graph", produces = "application/json")
    public Map<String, Object> graphJson(@PathVariable String id) throws Exception {
        CouncilConfig config;
        try (var in = new java.io.FileInputStream("./council.yaml")) {
            config = loader.load(in);
        }
        return com.review.council.graph.CouncilGraphTopology.build(config);
    }

    @PostMapping(value = "/validate", consumes = "text/plain")
    public Map<String, Object> validateRaw(@RequestBody String yaml) {
        return doValidate(yaml);
    }

    @PostMapping(value = "/validate", consumes = "application/json")
    public Map<String, Object> validateJson(@RequestBody Map<String, String> body) {
        return doValidate(body.getOrDefault("yaml", ""));
    }

    private Map<String, Object> doValidate(String yaml) {
        try (var in = new ByteArrayInputStream(yaml.getBytes())) {
            var cfg = loader.load(in);
            return Map.of(
                "valid", true,
                "name", cfg.name(),
                "reviewers", cfg.reviewers().size(),
                "maxRounds", cfg.maxRounds()
            );
        } catch (Exception e) {
            return Map.of("valid", false, "error", e.getMessage());
        }
    }

    public record StartRequest(String yaml, String diff, String gitRef) {}

    /**
     * Returns a fake completed review so the UI can show what a real result looks like
     * without needing ANTHROPIC_API_KEY. Hardcoded sample based on the demo-repo's
     * UserService.java bugs.
     */
    @GetMapping("/demo/start")
    public Map<String, Object> demoStart() {
        var f1 = Map.<String, Object>of(
            "id", "f-001", "severity", "critical", "reviewer", "security",
            "file", "src/main/java/com/demo/app/UserService.java", "line", 15,
            "message", "SQL 注入：findByName 用字符串拼接构造 SQL。" +
                "改用 PreparedStatement + 占位符绑定参数。",
            "category", "安全");
        var f2 = Map.<String, Object>of(
            "id", "f-002", "severity", "major", "reviewer", "perf",
            "file", "src/main/java/com/demo/app/UserService.java", "line", 22,
            "message", "N+1 查询：getOrdersForUsers 每个 user 触发一次 SELECT。" +
                "改用 `WHERE user_id IN (?, ?, ...)` 批量查或 JOIN。",
            "category", "性能");
        var f3 = Map.<String, Object>of(
            "id", "f-003", "severity", "major", "reviewer", "security",
            "file", "src/main/java/com/demo/app/UserService.java", "line", 50,
            "message", "硬编码密码：DB_PASSWORD 常量写了 'admin123'。" +
                "改用环境变量或 secret store 注入。",
            "category", "安全");
        var f4 = Map.<String, Object>of(
            "id", "f-004", "severity", "minor", "reviewer", "architect",
            "file", "src/main/java/com/demo/app/UserService.java", "line", 38,
            "message", "资源泄漏：findAll 未关闭 Connection/Statement/ResultSet。" +
                "改用 try-with-resources。",
            "category", "可靠性");
        var p1 = Map.<String, Object>of(
            "id", "p-001", "file", "src/main/java/com/demo/app/UserService.java",
            "status", "applied", "description", "findByName 改用 PreparedStatement（参数化查询）");
        var p2 = Map.<String, Object>of(
            "id", "p-002", "file", "src/main/java/com/demo/app/UserService.java",
            "status", "applied", "description", "findAll 改用 try-with-resources 释放资源");
        return Map.of(
            "sessionId", "rev-demo01",
            "rounds", 2,
            "findings", List.of(f1, f2, f3, f4),
            "appliedPatches", List.of(p1, p2),
            "cost", 0.0423,
            "demo", true,
            "note", "这是静态演示数据。真实评审需要配置 *_API_KEY 并从 /start 启动。"
        );
    }
}
