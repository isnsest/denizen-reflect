package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.EvalContext;

public final class AssignmentNode extends Node {
    private final String name;
    private final Node valueExpression;

    public AssignmentNode(String name, Node valueExpression) {
        this.name = name;
        this.valueExpression = valueExpression;
    }

    @Override
    public Object eval(EvalContext ctx) throws Throwable {
        Object value = valueExpression.eval(ctx);
        ctx.setLocal(name, value);
        return value;
    }
}
