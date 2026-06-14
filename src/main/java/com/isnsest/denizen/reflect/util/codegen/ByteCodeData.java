package com.isnsest.denizen.reflect.util.codegen;

import javax.tools.*;
import java.io.*;
import java.net.URI;

class ByteCodeData extends SimpleJavaFileObject {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public ByteCodeData(String name) {
        super(URI.create("byte:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
    }

    @Override
    public OutputStream openOutputStream() {
        return outputStream;
    }

    public byte[] getByteCode() {
        return outputStream.toByteArray();
    }
}