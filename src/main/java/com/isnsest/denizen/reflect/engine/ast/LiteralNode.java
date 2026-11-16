package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.EvalContext;

public final class LiteralNode extends Node {
    private final Object value;

    public LiteralNode(Object value) {
        this.value = value;
    }

    @Override
    public Object eval(EvalContext ctx) {
        return value;
    }
}
