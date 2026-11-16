package com.isnsest.denizen.reflect.engine.ast;

import com.denizenscript.denizen.utilities.Utilities;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.scripts.queues.ContextSource;
import com.isnsest.denizen.reflect.engine.EvalContext;
import com.isnsest.denizen.reflect.engine.JavaExpressionEngine;

public final class VariableNode extends Node {

    private final String name;
    private final String lowerName;

    private volatile Class<?> cachedClass;
    private volatile boolean classLookupAttempted;
    private volatile boolean plainName;

    public VariableNode(String name) {
        this.name = JavaExpressionEngine.unescape(name);
        this.lowerName = this.name.toLowerCase();
    }

    @Override
    public Object eval(EvalContext ctx) {
        Object local = ctx.getLocal(name);
        if (local != null) return local;

        Class<?> cls = cachedClass;
        if (cls != null) return cls;

        ScriptEntry entry = ctx.scriptEntry();

        if (name.equals("player")) {
            try {
                return Utilities.getEntryPlayer(entry).getJavaObject();
            } catch (Exception ignored) {
            }
        }
        if (name.equals("scriptEntry")) return entry;

        if (entry != null) {
            Object value = fromQueueOrContext(entry);
            if (value != null) return value;
        }

        if (!classLookupAttempted && name.indexOf('@') == -1) {
            cls = ctx.imports().findClass(name);
            if (cls != null) {
                cachedClass = cls;
                return cls;
            }
            classLookupAttempted = true;
        }

        if (plainName) return name;
        return fetchObject(entry);
    }

    private Object fromQueueOrContext(ScriptEntry entry) {
        ScriptQueue queue = entry.getResidingQueue();
        if (queue != null) {
            ObjectTag def = queue.getDefinitionObject(name);
            if (def == null && !name.equals(lowerName)) def = queue.getDefinitionObject(lowerName);
            if (def != null) return def.getJavaObject();
        }

        ContextSource source = entry.getContext() == null ? null : entry.getContext().contextSource;
        if (source != null) {
            ObjectTag contextObj = source.getContext(name);
            if (contextObj == null && !name.equals(lowerName)) contextObj = source.getContext(lowerName);
            if (contextObj != null) return contextObj.getJavaObject();
        }
        return null;
    }

    private Object fetchObject(ScriptEntry entry) {
        try {
            ObjectTag result = ObjectFetcher.pickObjectFor(name, entry == null ? null : entry.context);
            if (result != null) {
                Object internal = result.getJavaObject();
                if (result instanceof ElementTag && name.equals(internal)) {
                    plainName = true;
                    return name;
                }
                if (internal != null) return internal;
            }
        } catch (Exception ignored) {
        }
        return name;
    }
}
