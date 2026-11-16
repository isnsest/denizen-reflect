package com.isnsest.denizen.reflect.engine;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.JavaReflectedObjectTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.containers.ScriptContainer;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.tags.core.EscapeTagUtil;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.engine.reflect.ReflectionUtil;
import meigo.denizen.DenizenTagFinder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JavaExpressionEngine {

    private static final String GLOBAL = "<global>";
    private static final String SCRIPTS_DIR = "scripts/";

    private static final Map<String, ImportContext> IMPORT_CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<ScriptContainer, String> PATH_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> SIMPLE_CLASS_CACHE = new ConcurrentHashMap<>();

    private JavaExpressionEngine() {
    }

    public static void importClass(String path, String className, String alias) throws ClassNotFoundException {
        String key = path == null || path.isEmpty() ? GLOBAL : path;
        ImportContext ctx = IMPORT_CONTEXTS.computeIfAbsent(key, p -> new ImportContext(GLOBAL.equals(p)));

        if (className.endsWith(".*")) {
            ctx.addStarImport(className.substring(0, className.length() - 2));
            return;
        }

        Class<?> cls = resolveClass(className);
        ctx.addImport(alias != null && !alias.isEmpty() ? alias : cls.getSimpleName(), cls);
    }

    public static void clearAllImports() {
        IMPORT_CONTEXTS.clear();
        PATH_CACHE.clear();
        ImportContext.EMPTY.clearCache();
        ClassResolver.clearCache();
        ReflectionUtil.clearCache();
    }

    public static ImportContext getImports(String path) {
        if (path != null && !path.isEmpty()) {
            ImportContext own = IMPORT_CONTEXTS.get(path);
            if (own != null) return own;
        }
        ImportContext global = globalImports();
        return global != null ? global : ImportContext.EMPTY;
    }

    public static ImportContext getImports(ScriptEntry entry) {
        return getImports(scriptPath(entry));
    }

    static ImportContext globalImports() {
        return IMPORT_CONTEXTS.get(GLOBAL);
    }

    public static String scriptPath(ScriptEntry entry) {
        if (entry == null || entry.getScript() == null) return "";
        ScriptContainer container = entry.getScript().getContainer();
        if (container == null) return "";
        String path = PATH_CACHE.get(container);
        if (path == null) {
            String raw = container.getRelativeFileName();
            path = raw == null ? "" : normalizePath(raw);
            PATH_CACHE.put(container, path);
        }
        return path;
    }

    private static String normalizePath(String raw) {
        String path = raw.replace('\\', '/');
        int idx = path.indexOf(SCRIPTS_DIR);
        return idx != -1 ? path.substring(idx + SCRIPTS_DIR.length()) : path;
    }

    public static Class<?> resolveClass(String name) throws ClassNotFoundException {
        return ClassResolver.resolve(name);
    }

    public static Object execute(String expression, ScriptEntry scriptEntry) {
        return evaluate(expression, scriptEntry, true);
    }

    public static void run(String expression, ScriptEntry scriptEntry) {
        evaluate(expression, scriptEntry, false);
    }

    private static Object evaluate(String expression, ScriptEntry scriptEntry, boolean wrap) {
        if (expression == null) return null;
        expression = escapeTags(expression);

        try {
            Object result = evaluateRaw(expression, scriptEntry, getImports(scriptEntry));
            if (!wrap || result == null) return result;
            return wrapObject(result, scriptEntry == null ? null : scriptEntry.context);
        } catch (Throwable t) {
            if (t instanceof NullPointerException || t.getMessage() == null) return null;
            Debug.echoError("Error evaluating Java expression: " + unescape(expression));
            Debug.echoError(t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    private static String escapeTags(String expression) {
        if (expression.indexOf('<') == -1 || expression.indexOf('>') == -1) return expression;
        for (String tag : DenizenTagFinder.findTags(expression)) {
            expression = expression.replace(tag, EscapeTagUtil.escape(tag)
                    .replace(",", "ƈ")
                    .replace("-", "Ţ"));
        }
        return expression;
    }

    private static Object evaluateRaw(String expression, ScriptEntry scriptEntry, ImportContext imports) throws Throwable {
        expression = expression.trim();
        int last = expression.length() - 1;

        if (last >= 1 && expression.charAt(0) == '%' && expression.charAt(last) == '%'
                && closingPercent(expression, 1) == last) {
            if (last == 1) return "%";
            return evaluateRaw(expression.substring(1, last), scriptEntry, imports);
        }

        if (expression.indexOf('%') == -1) {
            return eval(imports.compile(expression, false), scriptEntry, imports);
        }
        if (closingPercent(expression, 0) != -1) {
            return evalTemplate(expression, scriptEntry, imports);
        }

        ExpressionCache.Compiled compiled;
        try {
            compiled = imports.compile(expression, true);
        } catch (RuntimeException notAnExpression) {
            return evalTemplate(expression, scriptEntry, imports);
        }
        return eval(compiled, scriptEntry, imports);
    }

    private static Object eval(ExpressionCache.Compiled compiled, ScriptEntry scriptEntry, ImportContext imports) throws Throwable {
        return compiled.root().eval(new EvalContext(imports, scriptEntry, compiled.params()));
    }

    private static int closingPercent(String s, int from) {
        char quote = 0;
        for (int i = from, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '%') {
                return i;
            }
        }
        return -1;
    }

    private static String evalTemplate(String template, ScriptEntry scriptEntry, ImportContext imports) throws Throwable {
        StringBuilder out = new StringBuilder();
        int idx = 0;
        int len = template.length();

        while (idx < len) {
            int start = template.indexOf('%', idx);
            if (start < 0) {
                out.append(template, idx, len);
                break;
            }

            if (start + 1 < len && template.charAt(start + 1) == '%') {
                out.append(template, idx, start).append('%');
                idx = start + 2;
                continue;
            }

            int end = closingPercent(template, start + 1);
            if (end < 0) end = template.indexOf('%', start + 1);
            if (end < 0) {
                out.append(template, idx, len);
                break;
            }

            out.append(template, idx, start);

            String inner = template.substring(start + 1, end).trim();
            if (!inner.isEmpty()) {
                if (inner.startsWith("<") && inner.endsWith(">")) {
                    inner = inner.substring(1, inner.length() - 1);
                }
                Object val = eval(imports.compile(inner, false), scriptEntry, imports);
                if (val instanceof ObjectTag tag) val = tag.getJavaObject();
                out.append(val);
            }

            idx = end + 1;
        }
        return out.toString();
    }

    public static String unescape(String expression) {
        if (expression.indexOf('ƈ') >= 0 || expression.indexOf('Ţ') >= 0) {
            expression = expression.replace('ƈ', ',').replace('Ţ', '-');
        }
        return EscapeTagUtil.unEscape(expression);
    }

    public static ObjectTag wrapObject(Object result, TagContext context) {
        if (isSimple(result)) {
            return CoreUtilities.objectToTagForm(result, context);
        }
        return new JavaReflectedObjectTag(result);
    }

    private static boolean isSimple(Object obj) {
        if (obj == null) return false;
        if (obj instanceof ObjectTag) return true;
        Class<?> clazz = obj.getClass();
        Boolean cached = SIMPLE_CLASS_CACHE.get(clazz);
        if (cached == null) {
            cached = isSimpleClass(clazz);
            SIMPLE_CLASS_CACHE.put(clazz, cached);
        }
        return cached;
    }

    private static boolean isSimpleClass(Class<?> clazz) {
        return List.class.isAssignableFrom(clazz)
                || Map.class.isAssignableFrom(clazz)
                || clazz == Collections.EMPTY_SET.getClass()
                || clazz.getName().startsWith("java.util.Collections$Unmodifiable")
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class;
    }
}
