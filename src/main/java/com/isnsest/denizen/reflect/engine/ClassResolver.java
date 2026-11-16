package com.isnsest.denizen.reflect.engine;

import com.isnsest.denizen.reflect.util.LibraryLoader;
import com.isnsest.denizen.reflect.util.codegen.ScriptClassRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClassResolver {

    private static final Map<String, Class<?>> CACHE = new ConcurrentHashMap<>();
    private static final Class<?> NOT_FOUND = Void.class;

    private ClassResolver() {
    }

    public static Class<?> resolve(String name) throws ClassNotFoundException {
        Class<?> cached = CACHE.get(name);
        if (cached != null) {
            if (cached == NOT_FOUND) throw new ClassNotFoundException(name);
            return cached;
        }

        try {
            ClassLoader scriptLoader = ScriptClassRegistry.getActiveScriptLoader();
            if (scriptLoader != null) {
                try {
                    Class<?> cls = Class.forName(name, true, scriptLoader);
                    CACHE.put(name, cls);
                    return cls;
                } catch (ClassNotFoundException ignored) {
                }
            }

            Class<?> cls = Class.forName(name, true, LibraryLoader.getClassLoader());
            CACHE.put(name, cls);
            return cls;
        } catch (ClassNotFoundException e) {
            CACHE.put(name, NOT_FOUND);
            throw e;
        }
    }

    public static Class<?> tryResolve(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return resolve(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    public static Class<?> nested(Class<?> outer, String simpleName) {
        if (outer == null || simpleName == null || simpleName.isEmpty()) return null;
        for (Class<?> c : outer.getClasses()) {
            if (c.getSimpleName().equals(simpleName)) return c;
        }
        for (Class<?> c : outer.getDeclaredClasses()) {
            if (c.getSimpleName().equals(simpleName)) return c;
        }
        return null;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
