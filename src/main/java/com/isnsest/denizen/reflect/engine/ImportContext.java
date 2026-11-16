package com.isnsest.denizen.reflect.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ImportContext {

    static final ImportContext EMPTY = new ImportContext(Collections.emptyMap(), Collections.emptySet(), false);

    private final Map<String, Class<?>> imports;
    private final Set<String> starImports;
    private final boolean global;
    private final ExpressionCache cache = new ExpressionCache();

    ImportContext(boolean global) {
        this(new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet(), global);
    }

    private ImportContext(Map<String, Class<?>> imports, Set<String> starImports, boolean global) {
        this.imports = imports;
        this.starImports = starImports;
        this.global = global;
    }

    void addImport(String alias, Class<?> cls) {
        imports.put(alias, cls);
    }

    void addStarImport(String packageName) {
        starImports.add(packageName);
    }

    ExpressionCache.Compiled compile(String expression, boolean strict) {
        return cache.get(expression, strict);
    }

    void clearCache() {
        cache.clear();
    }

    public Class<?> resolveType(String name) {
        Class<?> cls = imports.get(name);
        if (cls != null) return cls;

        for (String pkg : starImports) {
            Class<?> c = ClassResolver.tryResolve(pkg + "." + name);
            if (c != null) return c;
        }

        if (!global) {
            ImportContext g = JavaExpressionEngine.globalImports();
            if (g != null) return g.resolveType(name);
        }
        return null;
    }

    public Class<?> findClass(String name) {
        if (name == null || name.isEmpty()) return null;

        Class<?> c = resolveType(name);
        if (c != null) return c;
        c = ClassResolver.tryResolve(name);
        if (c != null) return c;

        String[] parts = name.split("[.$]", -1);
        if (parts.length == 1) {
            return isTypeLike(name) ? ClassResolver.tryResolve("java.lang." + name) : null;
        }

        Class<?> head = resolveType(parts[0]);
        if (head == null && isTypeLike(parts[0])) head = ClassResolver.tryResolve("java.lang." + parts[0]);
        if (head != null) {
            c = walkNested(head, parts, 1);
            if (c != null) return c;
        }

        String[] dotted = name.split("\\.", -1);
        for (int i = dotted.length - 1; i >= 1; i--) {
            Class<?> outer = ClassResolver.tryResolve(String.join(".", Arrays.copyOfRange(dotted, 0, i)));
            if (outer != null) {
                String rest = String.join(".", Arrays.copyOfRange(dotted, i, dotted.length));
                return walkNested(outer, rest.split("[.$]", -1), 0);
            }
        }
        return null;
    }

    private static Class<?> walkNested(Class<?> c, String[] parts, int from) {
        for (int i = from; i < parts.length && c != null; i++) {
            c = ClassResolver.nested(c, parts[i]);
        }
        return c;
    }

    private static boolean isTypeLike(String s) {
        return !s.isEmpty() && Character.isUpperCase(s.charAt(0));
    }
}
