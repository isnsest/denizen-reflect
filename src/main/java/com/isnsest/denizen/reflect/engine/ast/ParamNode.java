package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.EvalContext;

public final class ParamNode extends Node {
    private final int slot;

    public ParamNode(int slot) {
        this.slot = slot;
    }

    @Override
    public Object eval(EvalContext ctx) {
        return ctx.param(slot);
    }
}
