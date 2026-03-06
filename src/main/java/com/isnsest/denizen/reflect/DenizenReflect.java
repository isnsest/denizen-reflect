package com.isnsest.denizen.reflect;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.events.core.PreScriptReloadScriptEvent;
import com.denizenscript.denizencore.events.core.ScriptGeneratesErrorScriptEvent;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.core.UtilTagBase;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.commands.*;
import com.isnsest.denizen.reflect.util.ImportManager;
import com.isnsest.denizen.reflect.util.LibraryLoader;
import com.isnsest.denizen.reflect.util.Metrics;
import com.isnsest.denizen.reflect.events.CustomCommandEvent;
import com.isnsest.denizen.reflect.events.CustomTagEvent;
import com.isnsest.denizen.reflect.events.PlaceholderEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.isnsest.denizen.reflect.commands.Command.commands;

public class DenizenReflect extends JavaPlugin {

    public static DenizenReflect instance;
    Metrics metrics;

    public static DenizenReflect getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        register();
        try {
            Path libsFolder = getDataFolder().toPath().resolve("libs");
            LibraryLoader.loadLibraries(libsFolder);
        } catch (IOException e) {
            Debug.echoError("Failed to load external libraries for DenizenReflect:");
            Debug.echoError(e);
        }
        new Thread(() -> {

            while (PreScriptReloadScriptEvent.instance == null
                    || ScriptGeneratesErrorScriptEvent.instance == null
                    || ObjectFetcher.objectsByPrefix.isEmpty()) {

                try { Thread.sleep(10); }
                catch (InterruptedException ignored) { return; }
            }

            ImportManager.registerEventHooks();

        }, "Denizen-Reflect-Init").start();
    }

    private void register() {
        DenizenCore.commandRegistry.registerCommand(AsyncWhileCommand.class);
        DenizenCore.commandRegistry.registerCommand(InvokeCommand.class);
        DenizenCore.commandRegistry.registerCommand(TagCommand.class);
        DenizenCore.commandRegistry.registerCommand(Command.class);
        DenizenCore.commandRegistry.registerCommand(EventCommand.class);
        DenizenCore.commandRegistry.registerCommand(SectionCommand.class);
        DenizenCore.commandRegistry.registerCommand(ProxyCommand.class);

        //

        ScriptEvent.registerScriptEvent(CustomTagEvent.class);
        ScriptEvent.registerScriptEvent(CustomCommandEvent.class);
    }

    private void postRegister() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            DenizenCore.commandRegistry.registerCommand(PlaceholderCommand.class);
            ScriptEvent.registerScriptEvent(PlaceholderEvent.class);
        }
        DenizenCore.commandRegistry.registerCommand(LibloadCommand.class);

        UtilTagBase.instance.tagProcessor.registerTag(ObjectTag.class, "custom_commands", (attribute, object) -> {
            return new ListTag(commands);
        });
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        Debug.log("denizen-reflect", "Loading..");

        metrics = new Metrics(this, 28366);
        metrics.addCustomChart(
                new Metrics.AdvancedPie("libraries", () -> {
                    Map<String, Integer> data = new HashMap<>();
                    for (String libraryName : LibraryLoader.getLoadedLibraries()) {
                        data.put(libraryName, 1);
                    }
                    return data;
                })
        );
        metrics.addCustomChart(
                new Metrics.SimplePie("Denizen", () -> Bukkit.getPluginManager().getPlugin("Denizen").getDescription().getVersion())
        );
        metrics.addCustomChart(
                new Metrics.SimplePie("dDiscordBot", () -> Bukkit.getPluginManager().getPlugin("dDiscordBot").getDescription().getVersion())
        );

        postRegister();
        Debug.log("denizen-reflect", "Loaded successfully!");
    }

    @Override
    public void onDisable() {
    }
}