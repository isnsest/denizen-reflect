package com.isnsest.denizen.reflect.util.codegen;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.util.HashMap;
import java.util.Map;

class ByteCodeProvider extends ForwardingJavaFileManager<JavaFileManager> {
    private final Map<String, ByteCodeData> registry = new HashMap<>();

    public ByteCodeProvider(JavaFileManager fileManager) {
        super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
        ByteCodeData data = new ByteCodeData(className);
        registry.put(className, data);
        return data;
    }

    public Map<String, ByteCodeData> getByteCodes() {
        return registry;
    }
}