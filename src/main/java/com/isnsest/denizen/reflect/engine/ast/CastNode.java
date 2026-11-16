package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.EvalContext;
import com.isnsest.denizen.reflect.engine.reflect.ReflectionUtil;

public final class CastNode extends Node {
    private final String typeName;
    private final Node target;
    private volatile Class<?> cachedType;

    public CastNode(String typeName, Node target) {
        this.typeName = typeName;
        this.target = target;
    }

    @Override
    public Object eval(EvalContext ctx) throws Throwable {
        Object value = target.eval(ctx);
        if (value == null) return null;

        Class<?> type = cachedType;
        if (type == null) {
            type = switch (typeName) {
                case "int" -> int.class;
                case "double" -> double.class;
                case "float" -> float.class;
                case "long" -> long.class;
                case "short" -> short.class;
                case "byte" -> byte.class;
                case "boolean" -> boolean.class;
                case "char" -> char.class;
                default -> ctx.imports().findClass(typeName);
            };
            if (type == null) throw new RuntimeException("Unknown class for cast: " + typeName);
            cachedType = type;
        }

        return ReflectionUtil.adaptArgument(type, value);
    }
}
