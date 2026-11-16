package com.isnsest.denizen.reflect.engine.ast;

import com.denizenscript.denizencore.objects.ObjectFetcher;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.isnsest.denizen.reflect.engine.ClassResolver;
import com.isnsest.denizen.reflect.engine.EvalContext;
import com.isnsest.denizen.reflect.engine.reflect.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class BracketInitNode extends Node {

    private static final Pattern SPLIT_KV = Pattern.compile("[=:]");

    private final Node target;
    private final String inside;

    public BracketInitNode(Node target, String inside) {
        this.target = target;
        this.inside = inside == null ? "" : inside.trim();
    }

    @Override
    public Object eval(EvalContext ctx) throws Throwable {
        Object base = target.eval(ctx);
        if (base instanceof Class<?> cls) {
            return instantiate(cls);
        }
        if (base instanceof String name) {
            Class<?> cls = ClassResolver.tryResolve(name);
            if (cls != null) return instantiate(cls);
            return fetchDenizenObject(name, ctx);
        }
        throw new RuntimeException("Bracket literal requires class name or string on the left, got: " + base);
    }

    private Object fetchDenizenObject(String name, EvalContext ctx) {
        String raw = name + "[" + inside + "]";
        try {
            ObjectTag tag = ObjectFetcher.pickObjectFor(raw, ctx.scriptEntry().context);
            return tag.getJavaObject();
        } catch (Exception e) {
            return raw;
        }
    }

    private Object instantiate(Class<?> cls) throws Throwable {
        if (inside.isEmpty()) {
            return ReflectionUtil.construct(cls, ReflectionUtil.EMPTY_ARRAY);
        }
        if (inside.contains("=") || inside.contains(":")) {
            return instantiateWithFields(cls);
        }
        List<String> parts = splitTopLevel(inside);
        Object[] args = new Object[parts.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = unquote(parts.get(i));
        }
        return ReflectionUtil.construct(cls, args);
    }

    private Object instantiateWithFields(Class<?> cls) throws Throwable {
        Object obj = ReflectionUtil.construct(cls, ReflectionUtil.EMPTY_ARRAY);
        for (String part : splitTopLevel(inside)) {
            String[] kv = SPLIT_KV.split(part, 2);
            if (kv.length < 2) continue;
            String fieldName = kv[0].trim();

            Field field = ReflectionUtil.findFieldDeep(cls, fieldName);
            if (field == null) throw new RuntimeException("Field '" + fieldName + "' not found in " + cls.getName());

            Object value = parseLiteral(field.getType(), unquote(kv[1].trim()));
            field.setAccessible(true);
            field.set(obj, ReflectionUtil.adaptArgument(field.getType(), value));
        }
        return obj;
    }

    private static String unquote(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            if ((first == '"' || first == '\'') && text.charAt(text.length() - 1) == first) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseLiteral(Class<?> type, String text) {
        try {
            if (type == String.class) return text;
            if (type == int.class || type == Integer.class) return Integer.parseInt(text);
            if (type == boolean.class || type == Boolean.class) return text.equalsIgnoreCase("true") || text.equals("1");
            if (Number.class.isAssignableFrom(type)) return text.contains(".") ? Double.parseDouble(text) : Integer.parseInt(text);
            if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, text);
        } catch (Exception ignored) {
        }
        return text;
    }

    private static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int start = 0;
        int depth = 0;
        char quote = 0;

        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote && s.charAt(i - 1) != '\\') quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (c == ',' || c == ';')) {
                out.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        out.add(s.substring(start).trim());
        return out;
    }
}
