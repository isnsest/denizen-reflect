package com.isnsest.denizen.reflect.engine.ast;

import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.engine.EvalContext;
import com.isnsest.denizen.reflect.engine.reflect.ReflectionUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public final class MethodCallNode extends Node {

    private static final int PIC_SIZE = 3;

    private record Site(Class<?> owner, Method method, Class<?>[] paramTypes, boolean varargs, boolean isStatic) {

        Site(Class<?> owner, Method method) {
            this(owner, method, method.getParameterTypes(), method.isVarArgs(), Modifier.isStatic(method.getModifiers()));
        }

        Object call(Object target, Object[] values) throws Throwable {
            Object[] adapted = paramTypes.length == 0 ? ReflectionUtil.EMPTY_ARRAY
                    : ReflectionUtil.adaptArguments(paramTypes, values, varargs);
            return method.invoke(isStatic ? null : target, adapted);
        }
    }

    private final Node target;
    private final String methodName;
    private final Node[] args;

    private volatile Site[] sites = new Site[PIC_SIZE];

    public MethodCallNode(Node target, String methodName, List<Node> args) {
        this.target = target;
        this.methodName = methodName;
        this.args = args.toArray(new Node[0]);
    }

    @Override
    public Object eval(EvalContext ctx) throws Throwable {
        Object obj = target.eval(ctx);
        if (obj == null) return null;

        Class<?> owner = obj instanceof Class<?> c ? c : obj.getClass();
        Object[] values = evalAll(args, ctx);

        for (Site site : sites) {
            if (site != null && site.owner == owner) return site.call(obj, values);
        }

        Site site = link(owner, values);
        return site == null ? null : site.call(obj, values);
    }

    private synchronized Site link(Class<?> owner, Object[] values) {
        Site[] current = sites;
        for (Site site : current) {
            if (site != null && site.owner == owner) return site;
        }

        Method method = ReflectionUtil.findMethodDeep(owner, methodName, values);
        if (method == null) {
            Debug.echoError("Method '" + methodName + "' not found in " + owner.getSimpleName());
            return null;
        }
        method.setAccessible(true);

        Site site = new Site(owner, method);
        Site[] copy = current.clone();
        int slot = PIC_SIZE - 1;
        for (int i = 0; i < PIC_SIZE; i++) {
            if (copy[i] == null) {
                slot = i;
                break;
            }
        }
        copy[slot] = site;
        sites = copy;
        return site;
    }
}
