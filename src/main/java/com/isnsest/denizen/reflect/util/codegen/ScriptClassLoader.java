package com.isnsest.denizen.reflect.util.codegen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScriptClassLoader extends ClassLoader {

    private final Map<String, byte[]> pending = new ConcurrentHashMap<>();

    public ScriptClassLoader(ClassLoader parent) {
        super(parent);
    }

    public void addPending(String name, byte[] bytes) {
        pending.put(name, bytes);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = pending.remove(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}