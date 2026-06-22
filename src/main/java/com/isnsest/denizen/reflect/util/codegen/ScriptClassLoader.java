package com.isnsest.denizen.reflect.util.codegen;

public class ScriptClassLoader extends ClassLoader {
    public ScriptClassLoader(ClassLoader parent) {
        super(parent);
    }

    public Class<?> defineFromByteCode(String name, byte[] bytes) {
        return defineClass(name, bytes, 0, bytes.length);
    }
}