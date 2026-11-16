package com.isnsest.denizen.reflect.engine.reflect;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.isnsest.denizen.reflect.engine.Lambda;
import com.isnsest.denizen.reflect.util.LibraryLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionUtil {

    public static final Object[] EMPTY_ARRAY = new Object[0];

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType SPREAD_CTOR = MethodType.methodType(Object.class, Object[].class);
    private static final int MAX_REFLECTION_CACHE_SIZE = 10000;

    private static final Map<MemberKey, MethodHandle> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<MemberKey, MethodHandle> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<MemberKey, CtorInfo> CTOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, List<Method>>> METHODS_INDEX = new ConcurrentHashMap<>();

    private ReflectionUtil() {
    }

    public record CtorInfo(MethodHandle handle, Class<?>[] paramTypes, boolean varargs) {
    }

    private static final class MemberKey {
        private final Class<?> clazz;
        private final String name;
        private final Class<?>[] paramTypes;
        private final int hashCode;

        MemberKey(Class<?> clazz, String name, Class<?>[] paramTypes) {
            this.clazz = clazz;
            this.name = name;
            this.paramTypes = paramTypes;
            this.hashCode = Objects.hash(clazz, name) * 31 + Arrays.hashCode(paramTypes);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemberKey that)) return false;
            return hashCode == that.hashCode && clazz == that.clazz &&
                    name.equals(that.name) && Arrays.equals(paramTypes, that.paramTypes);
        }

        @Override
        public int hashCode() { return hashCode; }
    }

    public static CtorInfo resolveCtor(Class<?> type, Object[] values) throws Exception {
        MemberKey key = new MemberKey(type, "<ctor>", getTypes(values));
        CtorInfo info = CTOR_CACHE.get(key);
        if (info != null) return info;

        Constructor<?> ctor = findConstructorDeep(type, values);
        if (ctor == null) throw new NoSuchMethodException("No constructor for " + type.getName());
        ctor.setAccessible(true);

        Class<?>[] paramTypes = ctor.getParameterTypes();
        MethodHandle handle = LOOKUP.unreflectConstructor(ctor)
                .asSpreader(Object[].class, paramTypes.length)
                .asType(SPREAD_CTOR);
        info = new CtorInfo(handle, paramTypes, ctor.isVarArgs());

        if (!ctor.isVarArgs() && isApplicable(paramTypes, values, true) && hasOnlyPlainValues(values)) {
            if (CTOR_CACHE.size() > MAX_REFLECTION_CACHE_SIZE) CTOR_CACHE.clear();
            CTOR_CACHE.put(key, info);
        }
        return info;
    }

    private static boolean hasOnlyPlainValues(Object[] values) {
        for (Object v : values) {
            if (v == null || v instanceof ObjectTag || v instanceof Lambda) return false;
        }
        return true;
    }

    public static void clearCache() {
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
        METHODS_INDEX.clear();
        CTOR_CACHE.clear();
    }

    private static void checkCacheSize() {
        if (METHOD_CACHE.size() > MAX_REFLECTION_CACHE_SIZE) {
            clearCache();
        }
    }

    public static Object construct(Class<?> type, Object[] args) throws Throwable {
        Class<?>[] argTypes = getTypes(args);
        MemberKey key = new MemberKey(type, "<init>", argTypes);
        MethodHandle handle = METHOD_CACHE.get(key);

        if (handle == null) {
            checkCacheSize();
            Constructor<?> ctor = findConstructorDeep(type, args);
            if (ctor == null) throw new NoSuchMethodException("No constructor for " + type.getName());

            ctor.setAccessible(true);
            handle = LOOKUP.unreflectConstructor(ctor);
            METHOD_CACHE.put(key, handle);
        }

        Class<?>[] paramTypes = handle.type().parameterArray();
        int count = paramTypes.length;
        boolean varargs = count > 0 && paramTypes[count - 1].isArray() && args.length >= count - 1;
        return handle.invokeWithArguments(adaptArguments(paramTypes, args, varargs));
    }

    private static Class<?>[] getTypes(Object[] args) {
        if (args == null || args.length == 0) return new Class[0];
        Class<?>[] types = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? null : args[i].getClass();
        }
        return types;
    }

    public static Method findMethodDeep(Class<?> type, String name, Object[] args) {
        if (type == null) return null;
        Map<String, List<Method>> methodsByName = METHODS_INDEX.computeIfAbsent(type, clz -> {
            Map<String, List<Method>> map = new HashMap<>();

            for (Method m : clz.getMethods()) {
                map.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
            }

            for (Class<?> c = clz; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    List<Method> list = map.computeIfAbsent(m.getName(), k -> new ArrayList<>());
                    if (!list.contains(m)) {
                        list.add(m);
                    }
                }
            }
            return map;
        });

        List<Method> candidates = methodsByName.get(name);
        if (candidates == null) return null;

        return pick(candidates, args);
    }

    private static <T extends Executable> T pick(Iterable<T> candidates, Object[] args) {
        for (int phase = 0; phase < 4; phase++) {
            boolean strict = phase < 2;
            boolean varargs = phase % 2 == 1;
            T best = null;
            for (T m : candidates) {
                if (m.isVarArgs() != varargs) continue;
                Class<?>[] pt = m.getParameterTypes();
                boolean ok = varargs ? isVarArgApplicable(pt, args, strict) : isApplicable(pt, args, strict);
                if (ok && (best == null || moreSpecific(pt, best.getParameterTypes()))) best = m;
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean moreSpecific(Class<?>[] a, Class<?>[] b) {
        if (a.length != b.length || Arrays.equals(a, b)) return false;
        for (int i = 0; i < a.length; i++) {
            if (!primitiveToWrapper(b[i]).isAssignableFrom(primitiveToWrapper(a[i]))) return false;
        }
        return true;
    }

    public static Field findFieldDeep(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    static Constructor<?> findConstructorDeep(Class<?> type, Object[] args) {
        return pick(Arrays.asList(type.getDeclaredConstructors()), args);
    }

    public static Object[] adaptArguments(Class<?>[] paramTypes, Object[] args, boolean isVarargs) {
        int pLen = paramTypes.length;
        if (pLen == 0) return EMPTY_ARRAY;

        if (!isVarargs) {
            if (args.length == pLen && alreadyAdapted(paramTypes, args)) return args;
            Object[] out = new Object[pLen];
            for (int i = 0; i < pLen; i++) {
                out[i] = adaptArgument(paramTypes[i], args[i]);
            }
            return out;
        }

        int fixedCount = pLen - 1;
        Object[] out = new Object[pLen];
        for (int i = 0; i < fixedCount; i++) {
            out[i] = adaptArgument(paramTypes[i], args[i]);
        }

        Class<?> arrayType = paramTypes[fixedCount];
        Class<?> componentType = arrayType.getComponentType();

        if (args.length == pLen && arrayType.isInstance(args[fixedCount])) {
            out[fixedCount] = args[fixedCount];
        } else {
            int varArgsCount = Math.max(0, args.length - fixedCount);
            Object varArgsArray = Array.newInstance(componentType, varArgsCount);
            for (int i = 0; i < varArgsCount; i++) {
                Object val = adaptArgument(componentType, args[fixedCount + i]);
                Array.set(varArgsArray, i, val);
            }
            out[fixedCount] = varArgsArray;
        }
        return out;
    }

    private static boolean alreadyAdapted(Class<?>[] paramTypes, Object[] args) {
        for (int i = 0; i < paramTypes.length; i++) {
            Object arg = args[i];
            Class<?> type = paramTypes[i];
            if (arg == null) {
                if (type.isPrimitive()) return false;
                continue;
            }
            if (arg instanceof ObjectTag || arg instanceof Lambda) return false;
            Class<?> argClass = arg.getClass();
            if (type.isPrimitive() ? argClass != primitiveToWrapper(type) : !type.isAssignableFrom(argClass)) return false;
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object adaptArgument(Class<?> paramType, Object arg) {
        switch (arg) {
            case null -> {
                return null;
            }
            case Lambda lambda when paramType.isInterface() -> {
                return createLambdaProxy(paramType, lambda);
            }
            case ObjectTag objectTag -> {
                Object javaObj = objectTag.getJavaObject();
                if (javaObj == null) return null;
                if (paramType.isInstance(javaObj)) return javaObj;
                return adaptArgument(paramType, javaObj);
            }
            default -> {}
        }

        if (paramType.isInstance(arg)) return arg;

        if (arg instanceof Number n) {
            if (paramType == float.class || paramType == Float.class) return n.floatValue();
            if (paramType == int.class || paramType == Integer.class) return n.intValue();
            if (paramType == double.class || paramType == Double.class) return n.doubleValue();
            if (paramType == long.class || paramType == Long.class) return n.longValue();
            if (paramType == short.class || paramType == Short.class) return n.shortValue();
            if (paramType == byte.class || paramType == Byte.class) return n.byteValue();
        }

        if (arg instanceof String text) {
            if (text.isEmpty()) return paramType == String.class ? "" : null;

            if (paramType == boolean.class || paramType == Boolean.class)
                return text.equalsIgnoreCase("true") || text.equals("1");

            if (paramType == char.class || paramType == Character.class)
                return text.charAt(0);

            if (paramType.isEnum()) {
                try { return Enum.valueOf((Class<Enum>) paramType, text.trim()); } catch (Exception ignored) {}
            }

            if (Number.class.isAssignableFrom(primitiveToWrapper(paramType)))
                return convertToNumber(primitiveToWrapper(paramType), text.trim());
        }

        return arg;
    }

    static boolean isApplicable(Class<?>[] paramTypes, Object[] args, boolean strict) {
        if (paramTypes.length != args.length) return false;
        for (int i = 0; i < paramTypes.length; i++) {
            if (!isArgApplicable(paramTypes[i], args[i], strict)) return false;
        }
        return true;
    }

    static boolean isVarArgApplicable(Class<?>[] paramTypes, Object[] args, boolean strict) {
        if (args.length < paramTypes.length - 1) return false;
        for (int i = 0; i < paramTypes.length - 1; i++) {
            if (!isArgApplicable(paramTypes[i], args[i], strict)) return false;
        }
        Class<?> varArgComponentType = paramTypes[paramTypes.length - 1].getComponentType();
        for (int i = paramTypes.length - 1; i < args.length; i++) {
            if (!isArgApplicable(varArgComponentType, args[i], strict)) return false;
        }
        return true;
    }

    static boolean isArgApplicable(Class<?> p, Object arg, boolean strict) {
        switch (arg) {
            case null -> {
                return !p.isPrimitive();
            }
            case Lambda ignored -> {
                return p.isInterface();
            }
            case ObjectTag objectTag -> {
                return isArgApplicable(p, objectTag.getJavaObject(), strict);
            }
            default -> {}
        }
        if (p.isInstance(arg)) return true;

        if (p.isPrimitive()) {
            Class<?> wrapper = primitiveToWrapper(p);
            if (wrapper.isInstance(arg)) return true;
            if (arg instanceof Number && !strict && Number.class.isAssignableFrom(wrapper)) return true;
        }

        if (arg instanceof String) {
            if (strict) return false;
            if (p == boolean.class || p == Boolean.class) return true;
            if (p == char.class || p == Character.class) return true;
            if (p.isEnum()) return true;
            if (isNumberType(p)) return isParsableNumber((String)arg);
        }
        if (arg instanceof Number && !strict) return isNumberType(p);
        return false;
    }

    private static boolean isParsableNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        int len = s.length();
        int start = (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
        if (start == len) return false;

        boolean hasDot = false;
        boolean hasDigit = false;
        for (int i = start; i < len; i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (hasDot) return false;
                hasDot = true;
            } else if (c == 'e' || c == 'E') {
                return isParsableNumber(s.substring(i + 1));
            } else if (c < '0' || c > '9') {
                if (i == len - 1 && (c == 'f' || c == 'F' || c == 'd' || c == 'D' || c == 'l' || c == 'L')) {
                    return hasDigit;
                }
                return false;
            } else {
                hasDigit = true;
            }
        }
        return hasDigit;
    }

    private static boolean isNumberType(Class<?> p) {
        Class<?> w = p.isPrimitive() ? primitiveToWrapper(p) : p;
        return Number.class.isAssignableFrom(w);
    }

    static Class<?> primitiveToWrapper(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == double.class) return Double.class;
        if (primitive == float.class) return Float.class;
        if (primitive == short.class) return Short.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == char.class) return Character.class;
        return primitive;
    }

    private static Number convertToNumber(Class<?> type, String text) {
        try {
            if (type == Integer.class) return Integer.parseInt(text);
            if (type == Long.class) return Long.parseLong(text);
            if (type == Double.class) return Double.parseDouble(text);
            if (type == Float.class) return Float.parseFloat(text);
            if (type == Short.class) return Short.parseShort(text);
            if (type == Byte.class) return Byte.parseByte(text);
        } catch (Exception ignored) {}
        return null;
    }

    private static Object createLambdaProxy(Class<?> interfaceType, Lambda lambda) {
        return Proxy.newProxyInstance(
                LibraryLoader.getClassLoader(),
                new Class<?>[]{interfaceType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "toString" -> {
                                return "LambdaProxy";
                            }
                            case "hashCode" -> {
                                return System.identityHashCode(proxy);
                            }
                            case "equals" -> {
                                return proxy == args[0];
                            }
                        }
                    }
                    List<String> names = lambda.paramNames();
                    Map<String, Object> locals = new HashMap<>();
                    if (args != null) {
                        for (int i = 0; i < args.length && i < names.size(); i++) {
                            locals.put(names.get(i), args[i]);
                        }
                    }
                    Object result = lambda.body().eval(lambda.closureContext().forLambda(locals));
                    return adaptArgument(method.getReturnType(), result);
                }
        );
    }

    public static Object getField(Object targetOrClass, String name) throws Throwable {
        boolean isStatic = targetOrClass instanceof Class<?>;
        Class<?> owner = isStatic ? (Class<?>) targetOrClass : targetOrClass.getClass();
        MemberKey key = new MemberKey(owner, name, null);
        MethodHandle handle = FIELD_CACHE.get(key);
        if (handle == null) {
            checkCacheSize();
            Field field = findFieldDeep(owner, name);
            if (field == null) throw new NoSuchFieldException("Field " + name + " not found");
            field.setAccessible(true);
            handle = LOOKUP.unreflectGetter(field);
            FIELD_CACHE.put(key, handle);
        }
        return isStatic ? handle.invoke() : handle.invoke(targetOrClass);
    }
}
