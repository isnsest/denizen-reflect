package com.isnsest.denizen.reflect.util;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.events.core.PreScriptReloadScriptEvent;
import com.denizenscript.denizencore.events.core.ScriptGeneratesErrorScriptEvent;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.util.codegen.ScriptClassRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ImportManager {

    private static final Path root = Paths.get("plugins/Denizen/scripts");

    private static PreScriptReloadScriptEvent pre;
    private static ScriptGeneratesErrorScriptEvent err;

    public static ScriptEvent runImport() {

        JavaExpressionEngine.clearAllImports();

        ScriptClassRegistry.initialize();

        try (var walk = Files.walk(root)) {
            walk.filter(f -> f.toString().toLowerCase().endsWith(".dsc"))
                    .forEach(ImportManager::parseFile);
        }
        catch (IOException ignored) {}

        try {
            return pre != null ? pre.fire() : null;
        }
        catch (Throwable ignored) {}
        return null;
    }

    public static void registerEventHooks() {
        pre = PreScriptReloadScriptEvent.instance;
        PreScriptReloadScriptEvent.instance = new PreScriptReloadScriptEvent() {

            @Override
            public boolean couldMatch(ScriptPath path) {
                return path.context != null;
            }

            @Override
            public ScriptEvent fire() {
                return runImport();
            }
        };

        err = ScriptGeneratesErrorScriptEvent.instance;
        ScriptGeneratesErrorScriptEvent.instance = new ScriptGeneratesErrorScriptEvent() {
            @Override
            public ScriptEvent fire() {
                if (this.message.contains("IMPORT")) {
                    this.cancelled = true;
                    this.cancellationChanged();
                    return null;
                }
                ScriptEvent result = null;
                if (err != null) {
                    try {
                        err.message = this.message;
                        err.queue = this.queue;
                        err.script = this.script;
                        err.line = this.line;
                        result = err.fire();
                    } catch (Throwable t) {
                        Debug.echoError("Denizen-Reflect: Error calling original ScriptGeneratesErrorScriptEvent: " + t.getMessage());
                        Debug.echoError(t);
                    }
                }
                return result;
            }
        };
    }

    private static void parseFile(Path file) {
        String rel = ImportManager.root.relativize(file).toString().replace(FileSystems.getDefault().getSeparator(), "/");

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Map<String, String> map = parseImport(lines);

            for (var e : map.entrySet()) {
                try {
                    JavaExpressionEngine.importClass(rel, e.getKey(), e.getValue());
                }
                catch (ClassNotFoundException ex) {
                    Debug.echoError("Class not found: " + e.getKey() + " (file: " + rel + ")");
                }
                catch (Throwable ignored) {}
            }

        } catch (Exception ignored) {}
    }

    private static Map<String, String> parseImport(List<String> lines) {

        Map<String, String> out = new HashMap<>();

        int base = -1;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equalsIgnoreCase("import:")) {
                base = i;
                break;
            }
        }
        if (base == -1) return out;

        int indent = indent(lines.get(base)) + 1;

        for (int i = base + 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw.trim().isEmpty()) continue;
            if (indent(raw) < indent) break;

            String line = raw.trim();
            if (line.startsWith("#")) continue;

            String cls, alias = null;

            if (line.contains(" as ")) {
                String[] p = line.split(" as ", 2);
                cls = p[0].trim();
                alias = p[1].trim();
            }
            else if (line.contains(":")) {
                String[] p = line.split(":", 2);
                cls = p[0].trim();
                alias = p[1].trim();
                if (alias.isEmpty()) alias = null;
            }
            else cls = line;

            if (cls.isEmpty()) continue;
            if (alias == null) alias = lastSeg(cls);

            out.put(cls, alias);
        }

        return out;
    }

    private static int indent(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        return i;
    }

    private static String lastSeg(String s) {
        int i = Math.max(s.lastIndexOf('.'), s.lastIndexOf('$'));
        return i == -1 ? s : s.substring(i + 1);
    }
}
