package com.isnsest.denizen.reflect.engine.ast;

import com.isnsest.denizen.reflect.engine.ClassResolver;
import com.isnsest.denizen.reflect.engine.EvalContext;
import com.isnsest.denizen.reflect.engine.reflect.ReflectionUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class FieldAccessNode extends Node {

    private static final int PIC_SIZE = 3;
    private static final MethodType STATIC_GETTER = MethodType.methodType(Object.class);
    private static final MethodType INSTANCE_GETTER = MethodType.methodType(Object.class, Object.class);

    private record Getter(Class<?> owner, MethodHandle handle, boolean isStatic) {
        Object get(Object base) throws Throwable {
            return isStatic ? handle.invokeExact() : handle.invokeExact(base);
        }
    }

    private record PackageStep(String base, Object result) {
    }

    private record NestedStep(Class<?> outer, Class<?> nested) {
    }

    private final Node target;
    private final String fieldName;

    private volatile Getter[] getters = new Getter[PIC_SIZE];
    private volatile PackageStep packageStep;
    private volatile NestedStep nestedStep;

    public FieldAccessNode(Node target, String fieldName) {
        this.target = target;
        this.fieldName = fieldName;
    }

    @Override
    public Object eval(EvalContext ctx) throws Throwable {
        Object base = target.eval(ctx);

        if (base instanceof String path) {
            return packageMember(path);
        }
        if (base == null) {
            throw new RuntimeException("Cannot access field '" + fieldName + "' on null target");
        }

        NestedStep nested = nestedStep;
        if (nested != null && nested.outer == base) return nested.nested;

        Class<?> owner = base instanceof Class<?> c ? c : base.getClass();
        for (Getter getter : getters) {
            if (getter != null && getter.owner == owner) return getter.get(base);
        }

        Getter getter = link(owner);
        if (getter != null) return getter.get(base);

        if (base instanceof Class<?> outer) {
            Class<?> nestedClass = ClassResolver.nested(outer, fieldName);
            if (nestedClass != null) {
                nestedStep = new NestedStep(outer, nestedClass);
                return nestedClass;
            }
            if (fieldName.startsWith("[") && fieldName.endsWith("]")) {
                return new BracketInitNode(new LiteralNode(outer), fieldName.substring(1, fieldName.length() - 1)).eval(ctx);
            }
        }

        return ReflectionUtil.getField(base, fieldName);
    }

    private Object packageMember(String path) {
        PackageStep step = packageStep;
        if (step != null && step.base.equals(path)) return step.result;
        String full = path + "." + fieldName;
        Object result = ClassResolver.tryResolve(full);
        if (result == null) result = full;
        packageStep = new PackageStep(path, result);
        return result;
    }

    private synchronized Getter link(Class<?> owner) throws IllegalAccessException {
        Getter[] current = getters;
        for (Getter getter : current) {
            if (getter != null && getter.owner == owner) return getter;
        }

        Field field = ReflectionUtil.findFieldDeep(owner, fieldName);
        if (field == null) return null;
        field.setAccessible(true);

        boolean isStatic = Modifier.isStatic(field.getModifiers());
        MethodHandle handle = MethodHandles.lookup().unreflectGetter(field)
                .asType(isStatic ? STATIC_GETTER : INSTANCE_GETTER);
        Getter getter = new Getter(owner, handle, isStatic);

        Getter[] copy = current.clone();
        int slot = PIC_SIZE - 1;
        for (int i = 0; i < PIC_SIZE; i++) {
            if (copy[i] == null) {
                slot = i;
                break;
            }
        }
        copy[slot] = getter;
        getters = copy;
        return getter;
    }
}
