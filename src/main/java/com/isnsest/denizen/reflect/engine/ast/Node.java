package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.EvalContext;
import com.isnsest.denizen.reflect.engine.reflect.ReflectionUtil;

public abstract class Node {

    public abstract Object eval(EvalContext ctx) throws Throwable;

    static Object[] evalAll(Node[] nodes, EvalContext ctx) throws Throwable {
        if (nodes.length == 0) return ReflectionUtil.EMPTY_ARRAY;
        Object[] values = new Object[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            values[i] = nodes[i].eval(ctx);
        }
        return values;
    }
}
