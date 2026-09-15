package com.review.council.cli;

import com.review.council.config.CouncilConfig;
import com.review.council.config.CouncilConfigYamlLoader;
import com.review.council.graph.CouncilOrchestrator;
import com.review.council.graph.CouncilStateGraphRunner;
import com.review.council.persistence.SessionRepository;
import com.review.council.scanner.*;
import com.review.council.state.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;

@Component
@Command(
    name = "review",
    mixinStandardHelpOptions = true,
    version = "review 1.0.0",
    description = "Lumen AI Code Reviewer",
    subcommands = { ReviewCli.RunCmd.class, ReviewCli.ValidateCmd.class,
                    ReviewCli.CostCmd.class, ReviewCli.GraphCmd.class,
                    ReviewCli.ScanCmd.class }
)
public class ReviewCli implements Runnable {
    @Override public void run() {
        new CommandLine(this).usage(System.out);
    }

    @Component
    @Command(name = "run", description = "Run a code review session on the current git diff")
    public static class RunCmd implements Callable<Integer> {
        @Autowired CouncilOrchestrator orchestrator;
        @Autowired CouncilConfigYamlLoader loader;
        @Autowired SessionRepository sessions;

        @Parameters(index = "0", arity = "0..1", description = "Git ref (HEAD, main, etc.). Defaults to working tree.")
        String gitRef;

        @Option(names = "--config", description = "council.yaml path", defaultValue = "./council.yaml")
        String configPath;

        @Option(names = "--max-rounds", description = "override max rounds")
        Integer maxRounds;

        @Option(names = "--non-interactive", description = "CI mode")
        boolean nonInteractive;

        @Override
        public Integer call() throws Exception {
            CouncilConfig config;
            try (var in = new FileInputStream(configPath)) {
                config = loader.load(in);
            } catch (Exception e) {
                System.err.println("✗ Failed to load config: " + e.getMessage());
                System.err.println("  Hint: run `review validate --config=" + configPath + "` first");
                return 1;
            }
            if (maxRounds != null) {
                config = new CouncilConfig(config.name(), config.version(), maxRounds,
                    config.earlyStop(), config.reviewers(), config.aggregator(),
                    config.humanGates(), config.fixer(), config.reReviewer(),
                    config.budget(), config.output());
            }

            String sessionId = "rev-" + UUID.randomUUID().toString().substring(0, 8);
            sessions.insert(sessionId, "hash", "yaml", "running", "diff-hash", "diff-content");

            var initial = new ReviewState(sessionId,
                new CodeDiff(gitRef, "HEAD", List.of()),
                Language.JAVA, config,
                List.of(), List.of(), List.of(), 0,
                Budget.empty(config.budget().maxTokens(), config.budget().maxTimeSeconds() * 1000,
                    config.budget().maxCostUsd()),
                FlowSignal.CONTINUE);

            System.out.println("▶ Starting review session " + sessionId);
            System.out.println("  Council: " + config.name() + " (" + config.reviewers().size() + " reviewers)");
            System.out.println("  Max rounds: " + config.maxRounds());

            var result = orchestrator.run(initial);

            System.out.println("\n═══════════════════════════════════════");
            System.out.println("Session " + sessionId + " completed");
            System.out.println("  Rounds: " + result.round());
            System.out.println("  Findings: " + result.findings().size());
            var counts = result.findings().stream()
                .collect(java.util.stream.Collectors.groupingBy(Finding::severity,
                    java.util.stream.Collectors.counting()));
            System.out.println("    critical=" + counts.getOrDefault("critical", 0L)
                + " major=" + counts.getOrDefault("major", 0L)
                + " minor=" + counts.getOrDefault("minor", 0L));
            System.out.println("  Cost: $" + orchestrator.totalCost(sessionId));
            System.out.println("═══════════════════════════════════════");
            return 0;
        }
    }

    @Component
    @Command(name = "validate", description = "Validate a council.yaml without running")
    public static class ValidateCmd implements Callable<Integer> {
        @Autowired CouncilConfigYamlLoader loader;

        @Option(names = "--config", defaultValue = "./council.yaml")
        String configPath;

        @Override
        public Integer call() throws Exception {
            File f = new File(configPath);
            if (!f.exists()) {
                System.err.println("✗ Config not found: " + configPath);
                return 1;
            }
            try (var in = new FileInputStream(configPath)) {
                var cfg = loader.load(in);
                System.out.println("✓ Schema valid");
                System.out.println("✓ Council name: " + cfg.name());
                System.out.println("✓ " + cfg.reviewers().size() + " reviewers configured:");
                for (var r : cfg.reviewers()) {
                    var p = new File("src/main/resources/" + r.promptFile());
                    String mark = p.exists() ? "✓" : "✗";
                    System.out.println("  " + mark + " " + r.role() + " (model=" + r.model()
                        + ", prompt=" + r.promptFile() + ")");
                }
                return 0;
            } catch (Exception e) {
                System.err.println("✗ Failed to parse: " + e.getMessage());
                return 1;
            }
        }
    }

    @Component
    @Command(name = "cost", description = "Show total cost for a session")
    public static class CostCmd implements Callable<Integer> {
        @Autowired CouncilOrchestrator orchestrator;

        @Parameters(index = "0") String sessionId;

        @Override
        public Integer call() {
            System.out.printf("Session %s total cost: $%.4f%n", sessionId, orchestrator.totalCost(sessionId));
            return 0;
        }
    }

    @Component
    @Command(name = "graph", description = "Print the LangGraph4j StateGraph topology (Mermaid)")
    public static class GraphCmd implements Callable<Integer> {
        @Autowired CouncilStateGraphRunner runner;
        @Autowired CouncilConfigYamlLoader loader;

        @Option(names = "--config", defaultValue = "./council.yaml")
        String configPath;

        @Option(names = "--format", defaultValue = "mermaid",
            description = "Output format: ${COMPLETION-CANDIDATES}")
        String format;

        @Override
        public Integer call() throws Exception {
            com.review.council.config.CouncilConfig config;
            try (var in = new FileInputStream(configPath)) {
                config = loader.load(in);
            }
            var graph = runner.defineGraph(config);
            var type = switch (format.toLowerCase()) {
                case "plantuml" -> org.bsc.langgraph4j.GraphRepresentation.Type.PLANTUML;
                default -> org.bsc.langgraph4j.GraphRepresentation.Type.MERMAID;
            };
            System.out.println(graph.getGraph(type, config.name()).content());
            return 0;
        }
    }

    @Component
    @Command(name = "scan",
             description = "Scan every source file in a git repo and review them all")
    public static class ScanCmd implements Callable<Integer> {
        @Autowired CouncilConfigYamlLoader loader;
        @Autowired FileChunker chunker;
        @Autowired ScanOrchestrator scanOrch;
        @Autowired SessionRepository sessions;

        @Option(names = "--config", defaultValue = "./council.yaml")
        String configPath;

        @Option(names = "--path", defaultValue = ".",
                description = "Git repo root to scan")
        String path;

        @Option(names = "--max-tokens", defaultValue = "50000",
                description = "Max tokens per chunk")
        int maxTokens;

        @Option(names = "--concurrency", defaultValue = "3",
                description = "Number of (chunk x reviewer) calls in flight")
        int concurrency;

        @Override
        public Integer call() throws Exception {
            CouncilConfig config;
            try (var in = new FileInputStream(configPath)) {
                config = loader.load(in);
            } catch (Exception e) {
                System.err.println("✗ Failed to load config: " + e.getMessage());
                return 1;
            }

            System.out.println("▶ Scanning " + new File(path).getAbsolutePath());
            var opts = new ScanOptions(path, maxTokens, concurrency,
                ScanOptions.defaults().includePatterns(),
                ScanOptions.defaults().excludePatterns(),
                ScanOptions.defaults().excludePathPatterns(),
                ScanOptions.defaults().maxFileSizeBytes());
            var scanner = new PathScanner(opts);

            var entries = scanner.scan();
            var chunks = chunker.chunk(entries);
            System.out.println("  ✓ PathScanner: " + entries.size() + " files");
            System.out.println("  ✓ FileChunker: " + chunks.size() + " chunks (<=" + maxTokens + " tok/chunk)");
            System.out.println("  ✓ Reviewers: " + config.reviewers().size() +
                " (" + config.reviewers().stream().map(com.review.council.config.ReviewerConfig::role)
                    .reduce((x, y) -> x + ", " + y).orElse("") + ")");

            String sessionId = "rev-scan-" + UUID.randomUUID().toString().substring(0, 8);
            sessions.insert(sessionId, "scan-hash", configPath, "running", "n/a", "scan:" + path);

            var result = scanOrch.run(sessionId, config, chunks, path, concurrency);
            sessions.updateStatus(sessionId, "completed", "done");

            var counts = result.findings().stream().collect(
                java.util.stream.Collectors.groupingBy(Finding::severity,
                    java.util.stream.Collectors.counting()));

            System.out.println("\n═══════════════════════════════════════");
            System.out.println("Scan " + sessionId + " completed in " + result.durationMs() / 1000 + "s");
            System.out.println("  Files: " + result.totalFiles() + " / chunks: " + result.totalChunks());
            System.out.println("  Total findings: " + result.findings().size()
                + " (critical=" + counts.getOrDefault("critical", 0L)
                + " major=" + counts.getOrDefault("major", 0L)
                + " minor=" + counts.getOrDefault("minor", 0L) + ")");
            System.out.println("  Cost: $" + String.format("%.4f", result.cost()));
            System.out.println("  Per-reviewer:");
            result.perReviewer().forEach((role, sum) -> {
                System.out.printf("    - %s: %d findings, in=%d out=%d, $%.4f%n",
                    role, sum.findingsCount(), sum.tokensIn(), sum.tokensOut(), sum.costUsd());
            });
            System.out.println("═══════════════════════════════════════");
            return 0;
        }
    }
}

class FileInputStream extends java.io.FileInputStream {
    public FileInputStream(String path) throws java.io.FileNotFoundException {
        super(path);
    }
}
