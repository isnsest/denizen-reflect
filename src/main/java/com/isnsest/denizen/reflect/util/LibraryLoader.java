package com.isnsest.denizen.reflect.util;

import com.denizenscript.denizencore.utilities.debugging.Debug;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class LibraryLoader {

    private static final DynamicClassLoader classLoader = new DynamicClassLoader(
            new URL[0],
            LibraryLoader.class.getClassLoader()
    );

    private static final Set<String> loadedLibraries = ConcurrentHashMap.newKeySet();

    public static void loadSingle(String urlString) {
        String rawName = urlString.substring(urlString.lastIndexOf('/') + 1).split("[?#]")[0];
        String libName = (rawName.endsWith(".jar") ? rawName : rawName + ".jar").replace(".jar", "");

        if (loadedLibraries.contains(libName)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                URL url = URI.create(urlString).toURL();
                Path tempJar = Files.createTempFile("reflect_", "_" + libName + ".jar");
                tempJar.toFile().deleteOnExit();

                try (InputStream in = url.openStream()) {
                    Files.copy(in, tempJar, StandardCopyOption.REPLACE_EXISTING);
                }

                if (Files.size(tempJar) < 1024) {
                    throw new IOException("Downloaded file is too small (invalid jar).");
                }

                registerLibrary(tempJar.toUri().toURL(), libName);
                Debug.log("denizen-reflect", "Successfully imported: " + libName);

            } catch (Exception e) {
                Debug.echoError("Failed to import library: " + libName + " - " + e.getMessage());
            }
        });
    }

    public static void loadLibraries(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
            return;
        }

        try (Stream<Path> stream = Files.walk(folder)) {
            stream.filter(p -> p.toString().endsWith(".jar")).forEach(p -> {
                try {
                    registerLibrary(p.toUri().toURL(), p.getFileName().toString().replace(".jar", ""));
                } catch (Exception ignored) {}
            });
        }
    }

    private static void registerLibrary(URL url, String name) {
        if (loadedLibraries.add(name)) {
            classLoader.addURL(url);
        }
    }

    public static ClassLoader getClassLoader() {
        return classLoader;
    }

    public static Set<String> getLoadedLibraries() {
        return Collections.unmodifiableSet(loadedLibraries);
    }

    private static class DynamicClassLoader extends URLClassLoader {
        public DynamicClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
        }
    }
}