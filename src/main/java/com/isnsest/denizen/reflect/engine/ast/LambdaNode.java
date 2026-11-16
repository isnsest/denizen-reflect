package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.EvalContext;
import com.isnsest.denizen.reflect.engine.Lambda;

import java.util.List;

public final class LambdaNode extends Node {
    private final List<String> paramNames;
    private final Node body;

    public LambdaNode(List<String> paramNames, Node body) {
        this.paramNames = paramNames;
        this.body = body;
    }

    @Override
    public Object eval(EvalContext ctx) {
        return new Lambda(paramNames, body, ctx);
    }
}
