package com.review.council.nodes;
public interface CouncilNode<I, O> {
    String name();
    O apply(I input, NodeContext ctx);
}
