package com.review.council.reviewer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.review.council.state.Finding;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FindingParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Finding> parse(String llmJson) throws Exception {
        if (llmJson == null || llmJson.isBlank()) return List.of();
        var cleaned = llmJson.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```", "").trim();
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) return List.of();
        var tree = mapper.readTree(cleaned);
        var arr = tree.get("findings");
        if (arr == null || !arr.isArray()) return List.of();
        List<Finding> result = new ArrayList<>();
        for (var node : arr) {
            String sev = node.has("severity") ? node.get("severity").asText() : "minor";
            int line = node.has("line") ? node.get("line").asInt() : 0;
            String msg = node.has("message") ? node.get("message").asText() : "";
            String fix = node.has("suggested_fix") ? node.get("suggested_fix").asText() : "";
            result.add(Finding.of("unknown", sev, line, msg, fix));
        }
        return result;
    }
}
