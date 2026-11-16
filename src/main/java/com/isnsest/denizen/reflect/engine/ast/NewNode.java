package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.EvalContext;
import com.isnsest.denizen.reflect.engine.reflect.ReflectionUtil;

import java.util.List;

public final class NewNode extends Node {

    private final String typeName;
    private final Node[] args;

    private volatile Class<?> type;
    private volatile ReflectionUtil.CtorInfo ctor;

    public NewNode(String typeName, List<Node> args) {
        this.typeName = typeName;
        this.args = args.toArray(new Node[0]);
    }

    @Override
    public Object eval(EvalContext ctx) throws Throwable {
        Object[] values = evalAll(args, ctx);
        ReflectionUtil.CtorInfo info = ctor;
        if (info == null) info = link(ctx, values);
        return info.handle().invokeExact(ReflectionUtil.adaptArguments(info.paramTypes(), values, info.varargs()));
    }

    private synchronized ReflectionUtil.CtorInfo link(EvalContext ctx, Object[] values) throws Exception {
        ReflectionUtil.CtorInfo info = ctor;
        if (info != null) return info;

        Class<?> t = type;
        if (t == null) {
            t = ctx.imports().findClass(typeName);
            if (t == null) throw new ClassNotFoundException(typeName);
            type = t;
        }
        info = ReflectionUtil.resolveCtor(t, values);
        ctor = info;
        return info;
    }
}
