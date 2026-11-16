package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.EvalContext;

import java.util.List;

public final class BlockNode extends Node {
    private final Node[] statements;

    public BlockNode(List<Node> statements) {
        this.statements = statements.toArray(new Node[0]);
    }

    @Override
    public Object eval(EvalContext ctx) throws Throwable {
        Object result = null;
        for (Node node : statements) {
            result = node.eval(ctx);
        }
        return result;
    }
}
