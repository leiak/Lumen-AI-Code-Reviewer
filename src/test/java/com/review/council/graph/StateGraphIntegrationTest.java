package com.review.council.graph;

import com.review.council.config.CouncilConfig;
import com.review.council.config.CouncilConfigYamlLoader;
import org.bsc.langgraph4j.GraphRepresentation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = com.review.council.CouncilApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StateGraphIntegrationTest {

    @Autowired CouncilStateGraphRunner runner;
    @Autowired CouncilConfigYamlLoader loader;

    @Test
    void buildsStateGraphAndPrintsTopology() throws Exception {
        CouncilConfig config;
        try (var in = new java.io.FileInputStream("council.yaml")) {
            config = loader.load(in);
        }
        var graph = runner.defineGraph(config);
        assertNotNull(graph);

        GraphRepresentation plantuml = graph.getGraph(GraphRepresentation.Type.PLANTUML, "council");
        GraphRepresentation mermaid = graph.getGraph(GraphRepresentation.Type.MERMAID, "council");

        System.out.println("\n=== PlantUML ===\n" + plantuml.content());
        System.out.println("\n=== Mermaid ===\n" + mermaid.content());

        // Topology contains the expected nodes
        String p = plantuml.content();
        assertTrue(p.contains("dispatch"), "Expected dispatch node");
        assertTrue(p.contains("aggregator"), "Expected aggregator node");
        assertTrue(p.contains("gate"), "Expected gate node");
        assertTrue(p.contains("fixer"), "Expected fixer node");
        assertTrue(p.contains("re_reviewer"), "Expected re_reviewer node");
    }
}
