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
    public String graph(@RequestParam(defaultValue = "mermaid") String format) throws Exception {
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
}
