package com.isnsest.denizen.reflect.util.codegen;

import com.denizenscript.denizen.Denizen;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import org.bukkit.Bukkit;

import javax.tools.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ScriptClassRegistry {

    private static final ScriptClassRegistry INSTANCE = new ScriptClassRegistry();

    public static void initialize() {
        INSTANCE.runInitialization();
    }

    private ScriptClassLoader persistentLoader = null;

    private final List<Class<?>> currentClasses = new ArrayList<>();
    private final Map<String, String> classToPathMap = new ConcurrentHashMap<>();
    private final Set<String> persistentPaths = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean compiling = new AtomicBoolean(false);
    private final ClasspathResolver classpathResolver = new ClasspathResolver();
    private StandardJavaFileManager cachedFileManager = null;

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("class\\s+([a-zA-Z0-9_$]+)");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([a-zA-Z0-9_.]+);");
    private static final Pattern PERSISTENT_PATTERN = Pattern.compile("(?i)@persistent");

    private static ScriptClassLoader activeVolatileLoader;

    private ScriptClassRegistry() {
    }

    public static ClassLoader getActiveScriptLoader() {
        return activeVolatileLoader;
    }

    public void runInitialization() {
        if (!compiling.compareAndSet(false, true)) {
            Debug.log("denizen-reflect", "Compilation is already in progress.");
            return;
        }

        try {
            Path scriptsRoot = Denizen.instance.getDataFolder().toPath().resolve("scripts");
            if (!Files.exists(scriptsRoot)) {
                compiling.set(false);
                return;
            }

            if (persistentLoader == null) {
                persistentLoader = new ScriptClassLoader(getClass().getClassLoader());
            }

            final List<JavaFileObject> compilationUnits = new ArrayList<>();
            final Set<String> classesToShutdown = new HashSet<>();
            final Set<String> foundSimpleNames = new HashSet<>();

            final Set<String> currentPersistentPaths = new HashSet<>();
            final Set<String> classesForPersistentLoader = new HashSet<>();
            final Set<String> classesToSkipLoading = new HashSet<>();

            boolean recreatePersistent = false;
            List<Path> allFiles;

            try (Stream<Path> walk = Files.walk(scriptsRoot)) {
                allFiles = walk.filter(p -> p.toString().toLowerCase().endsWith(".java")).toList();
            }

            List<ScriptFile> scriptFiles = new ArrayList<>();
            for (Path filePath : allFiles) {
                try {
                    ScriptFile sf = new ScriptFile(filePath, scriptsRoot);
                    if (sf.isJava()) {
                        scriptFiles.add(sf);
                    }
                } catch (Exception e) {
                    Debug.echoError("Error parsing file " + filePath.getFileName() + ": " + e.getMessage());
                }
            }

            for (ScriptFile sf : scriptFiles) {
                foundSimpleNames.add(sf.getSimpleName());

                if (sf.isPersistent()) {
                    currentPersistentPaths.add(sf.getRelativePath());
                    if (!persistentPaths.contains(sf.getRelativePath())) {
                        recreatePersistent = true;
                    }
                } else {
                    if (persistentPaths.contains(sf.getRelativePath())) {
                        recreatePersistent = true;
                    }
                }
            }

            for (String oldPersistentPath : persistentPaths) {
                if (!currentPersistentPaths.contains(oldPersistentPath)) {
                    recreatePersistent = true;
                    break;
                }
            }

            if (recreatePersistent) {
                synchronized (currentClasses) {
                    for (Class<?> clazz : currentClasses) {
                        String relPath = classToPathMap.get(clazz.getName());
                        if (relPath != null && persistentPaths.contains(relPath)) {
                            classesToShutdown.add(clazz.getSimpleName());
                        }
                    }
                }
                persistentLoader = new ScriptClassLoader(getClass().getClassLoader());
            }

            ScriptClassLoader volatileLoader = new ScriptClassLoader(persistentLoader);
            activeVolatileLoader = volatileLoader;

            if (!recreatePersistent) {
                for (ScriptFile sf : scriptFiles) {
                    if (sf.isPersistent()) {
                        classesToSkipLoading.add(sf.getFullClassName());
                    }
                }
            }

            for (ScriptFile sf : scriptFiles) {
                classToPathMap.put(sf.getFullClassName(), sf.getRelativePath());

                boolean shouldReload = !sf.isPersistent() || recreatePersistent;
                if (shouldReload) {
                    classesToShutdown.add(sf.getSimpleName());
                }

                if (sf.isPersistent()) {
                    classesForPersistentLoader.add(sf.getFullClassName());
                }

                String finalSource = sf.getContent().replaceAll("(?im)\\s*@persistent\\s*\\r?\\n?", "");

                compilationUnits.add(new SimpleJavaFileObject(URI.create("string:///" + sf.getFullClassName().replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignore) {
                        return finalSource;
                    }
                });
            }

            synchronized (currentClasses) {
                Iterator<Class<?>> it = currentClasses.iterator();
                while (it.hasNext()) {
                    Class<?> clazz = it.next();
                    String simpleName = clazz.getSimpleName();

                    if (classesToShutdown.contains(simpleName) || !foundSimpleNames.contains(simpleName)) {
                        invokeShutdown(clazz);
                        classToPathMap.remove(clazz.getName());
                        it.remove();
                    }
                }
            }

            if (!compilationUnits.isEmpty()) {
                final ScriptClassLoader volLoader = volatileLoader;
                final ScriptClassLoader persLoader = persistentLoader;
                CompletableFuture.runAsync(() -> runBatchCompilation(
                        volLoader, persLoader, classesForPersistentLoader, classesToSkipLoading, compilationUnits, currentPersistentPaths
                ));
            } else {
                compiling.set(false);
            }

        } catch (Exception e) {
            Debug.echoError(e);
            compiling.set(false);
        }
    }

    private void runBatchCompilation(
            ScriptClassLoader volLoader,
            ScriptClassLoader persLoader,
            Set<String> classesForPersistentLoader,
            Set<String> classesToSkipLoading,
            List<JavaFileObject> units,
            Set<String> currentPersistentPaths
    ) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            Debug.echoError("JavaCompiler is null! Make sure the server is running on JDK, not JRE.");
            compiling.set(false);
            return;
        }

        try {
            if (cachedFileManager == null) {
                cachedFileManager = compiler.getStandardFileManager(null, null, null);
            }
            ByteCodeProvider manager = new ByteCodeProvider(cachedFileManager);

            String classpath = classpathResolver.resolve();
            List<String> options = Arrays.asList("-classpath", classpath, "-nowarn", "-g:none", "-proc:none");

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavaCompiler.CompilationTask task = compiler.getTask(null, manager, diagnostics, options, null, units);

            CompletableFuture.supplyAsync(task::call).whenComplete((success, throwable) -> {
                if (throwable != null) {
                    Debug.echoError(throwable);
                    compiling.set(false);
                    return;
                }

                if (Boolean.TRUE.equals(success)) {
                    Map<String, ByteCodeData> results = manager.getByteCodes();
                    Bukkit.getScheduler().runTask(Denizen.instance, () -> {
                        try {
                            results.forEach((name, data) -> {
                                try {
                                    String baseName = name;
                                    int dollarIndex = name.indexOf('$');
                                    if (dollarIndex != -1) {
                                        baseName = name.substring(0, dollarIndex);
                                    }

                                    if (classesToSkipLoading.contains(baseName)) {
                                        return;
                                    }

                                    byte[] bytes = data.getByteCode();
                                    ScriptClassLoader loaderToUse = classesForPersistentLoader.contains(baseName) ? persLoader : volLoader;
                                    Class<?> clazz = loaderToUse.defineFromByteCode(name, bytes);

                                    if (dollarIndex == -1) {
                                        registerClass(clazz);
                                        invokeInit(clazz);
                                    }
                                } catch (Exception e) {
                                    Debug.echoError("Error defining " + name + ": " + e.getMessage());
                                }
                            });

                            persistentPaths.clear();
                            persistentPaths.addAll(currentPersistentPaths);
                            Debug.log("denizen-reflect", "Successfully compiled " + units.size() + " Java scripts.");
                        } finally {
                            compiling.set(false);
                        }
                    });
                } else {
                    Bukkit.getScheduler().runTask(Denizen.instance, () -> {
                        Debug.echoError("Batch compilation failed! Errors:");
                        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                                String fileName = "Unknown";
                                if (diagnostic.getSource() != null) {
                                    String path = diagnostic.getSource().toUri().getPath();
                                    if (path != null) {
                                        int lastSlash = path.lastIndexOf('/');
                                        fileName = lastSlash != -1 ? path.substring(lastSlash + 1) : path;
                                    } else {
                                        fileName = diagnostic.getSource().getName();
                                    }
                                }
                                Debug.echoError("File: " + fileName + " | Line: " + diagnostic.getLineNumber() + " | " + diagnostic.getMessage(Locale.ENGLISH));
                            }
                        }
                        compiling.set(false);
                    });
                }
            });
        } catch (Exception e) {
            Debug.echoError(e);
            compiling.set(false);
        }
    }

    private void registerClass(Class<?> clazz) {
        synchronized (currentClasses) {
            Iterator<Class<?>> iterator = currentClasses.iterator();
            while (iterator.hasNext()) {
                Class<?> existingClass = iterator.next();
                if (existingClass.getSimpleName().equals(clazz.getSimpleName())) {
                    invokeShutdown(existingClass);
                    classToPathMap.remove(existingClass.getName());
                    iterator.remove();
                }
            }
            currentClasses.add(clazz);
        }
    }

    private void invokeInit(Class<?> clazz) {
        try {
            clazz.getDeclaredMethod("init").invoke(null);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            Debug.log("denizen-reflect", "Error running init on class " + clazz.getSimpleName() + ": " + e.getMessage());
        }
    }

    private void invokeShutdown(Class<?> clazz) {
        try {
            clazz.getDeclaredMethod("shutdown").invoke(null);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            Debug.log("denizen-reflect", "Error running shutdown on class " + clazz.getSimpleName() + ": " + e.getMessage());
        }
    }

    private static class ScriptFile {
        private final String content;
        private final String cleanContent;
        private final String relativePath;
        private final String simpleClassName;
        private final String fullClassName;
        private final boolean persistent;

        public ScriptFile(Path path, Path scriptsRoot) throws IOException {
            this.content = Files.readString(path).trim();
            this.relativePath = scriptsRoot.relativize(path).toString()
                    .replace("\\", "/").replaceAll("(?i)\\.java$", "");

            this.cleanContent = stripLeadingComments(this.content);
            this.persistent = PERSISTENT_PATTERN.matcher(this.content).find();

            if (this.cleanContent.startsWith("{")) {
                this.simpleClassName = path.getFileName().toString().replaceAll("(?i)\\.java$", "");
                this.fullClassName = "scripts." + this.simpleClassName;
            } else {
                this.simpleClassName = extractByPattern(CLASS_NAME_PATTERN, this.cleanContent);
                if (this.simpleClassName == null) {
                    throw new IllegalArgumentException("No class name found");
                }
                String pkg = extractByPattern(PACKAGE_PATTERN, this.cleanContent);
                this.fullClassName = (pkg != null ? pkg : "scripts") + "." + this.simpleClassName;
            }
        }

        public boolean isJava() {
            if (content.isEmpty()) return false;
            String checkContent = cleanContent.replaceAll("(?im)\\s*@persistent\\s*\\r?\\n?", "").trim();
            if (checkContent.startsWith("{")) return true;
            if (checkContent.startsWith("package ") || checkContent.startsWith("import ")) {
                return !checkContent.startsWith("import:") && !checkContent.startsWith("import :");
            }
            return checkContent.startsWith("class ") ||
                    checkContent.startsWith("public class ") ||
                    checkContent.startsWith("final class ") ||
                    checkContent.startsWith("public final class ");
        }

        public String getContent() {
            return content;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getSimpleName() {
            return simpleClassName;
        }

        public String getFullClassName() {
            return fullClassName;
        }

        public boolean isPersistent() {
            return persistent;
        }

        private String stripLeadingComments(String rawContent) {
            String clean = rawContent.trim();
            if (clean.startsWith("\uFEFF")) {
                clean = clean.substring(1).trim();
            }
            while (true) {
                if (clean.startsWith("//")) {
                    int nextNewLine = clean.indexOf('\n');
                    if (nextNewLine == -1) return "";
                    clean = clean.substring(nextNewLine).trim();
                } else if (clean.startsWith("/*")) {
                    int endComment = clean.indexOf("*/");
                    if (endComment == -1) return "";
                    clean = clean.substring(endComment + 2).trim();
                } else {
                    break;
                }
            }
            return clean;
        }

        private String extractByPattern(Pattern pattern, String source) {
            Matcher matcher = pattern.matcher(source);
            return matcher.find() ? matcher.group(1).trim() : null;
        }
    }
}