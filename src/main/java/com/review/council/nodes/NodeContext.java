package com.review.council.nodes;

import com.review.council.audit.AuditRepository;
import com.review.council.audit.LlmCallRepository;
import com.review.council.persistence.StateSnapshotRepository;
import com.review.council.state.Budget;
import java.util.Map;

public record NodeContext(
    String sessionId,
    AuditRepository audit,
    LlmCallRepository llmCalls,
    StateSnapshotRepository snapshots,
    Budget budget,
    Map<String, Object> tools
) {}
