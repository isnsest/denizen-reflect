package com.isnsest.denizen.reflect.engine;

import com.denizenscript.denizencore.scripts.ScriptEntry;

import java.util.HashMap;
import java.util.Map;

public final class EvalContext {

    public static final Object[] NO_PARAMS = new Object[0];

    private final ImportContext imports;
    private final ScriptEntry scriptEntry;
    private final Object[] params;
    private Map<String, Object> locals;

    EvalContext(ImportContext imports, ScriptEntry scriptEntry, Object[] params) {
        this(imports, scriptEntry, params, null);
    }

    private EvalContext(ImportContext imports, ScriptEntry scriptEntry, Object[] params, Map<String, Object> locals) {
        this.imports = imports == null ? ImportContext.EMPTY : imports;
        this.scriptEntry = scriptEntry;
        this.params = params;
        this.locals = locals;
    }

    public ImportContext imports() {
        return imports;
    }

    public ScriptEntry scriptEntry() {
        return scriptEntry;
    }

    public Object param(int slot) {
        return params[slot];
    }

    public Object getLocal(String name) {
        Map<String, Object> l = locals;
        return l == null ? null : l.get(name);
    }

    public void setLocal(String name, Object value) {
        if (locals == null) locals = new HashMap<>();
        locals.put(name, value);
    }

    public EvalContext forLambda(Map<String, Object> extraLocals) {
        Map<String, Object> copy = locals == null ? new HashMap<>() : new HashMap<>(locals);
        if (extraLocals != null) copy.putAll(extraLocals);
        return new EvalContext(imports, scriptEntry, params, copy);
    }
}
