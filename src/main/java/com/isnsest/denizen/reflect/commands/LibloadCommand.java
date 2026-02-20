package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.tags.TagManager;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.util.LibraryLoader;

public class LibloadCommand extends AbstractCommand {

    // @Plugin denizen-reflect
    public LibloadCommand() {
        setName("libload");
        setSyntax("libload [<url>]");
        setRequiredArguments(1, 1);
        autoCompile();
        registerTags();
    }

    // <--[command]
    // @Name Libload
    // @Syntax libload [<url>]
    // @Required 1
    // @Maximum 1
    // @Short Loads an external JAR library at runtime.
    // @Group denizen-reflect
    //
    // @Description
    // Asynchronously downloads and injects a Java library (JAR file) from the specified URL into the plugin's classpath.
    // This allows scripts to utilize third-party dependencies or external APIs without requiring a server restart
    // or manual file placement.
    //
    // @Tags
    // <util.libraries>
    //
    // @Usage
    // Use to load a specific JSON library from a Maven repository.
    // - libload https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar
    //
    // -->

    @SuppressWarnings("unused")
    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("target") @ArgLinear @ArgRaw String target) {
        try {
            LibraryLoader.loadSingle(target);
        } catch (Exception e) {
            Debug.echoError("Failed to import library from URL: " + target);
            Debug.echoError(e);
        }
    }

    public static void registerTags() {
        // <--[tag]
        // @attribute <util.libraries>
        // @returns ListTag
        // @description
        // Returns a list of all currently accessible JAR libraries.
        // This includes both preloaded dependencies and libraries injected via 'libload'.
        // -->
        TagManager.baseTags.get("util").processor.registerTag(ListTag.class, "libraries", (attribute, object) -> {
            return new ListTag(LibraryLoader.getLoadedLibraries());
        });
    }

}