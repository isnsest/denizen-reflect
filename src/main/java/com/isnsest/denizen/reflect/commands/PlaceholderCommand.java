package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.scripts.ScriptRegistry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.scripts.containers.ScriptContainer;
import com.denizenscript.denizencore.scripts.containers.core.TaskScriptContainer;
import com.denizenscript.denizencore.scripts.queues.ContextSource;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.scripts.queues.core.InstantQueue;
import me.clip.placeholderapi.events.ExpansionsLoadedEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import com.isnsest.denizen.reflect.DenizenReflect;
import com.isnsest.denizen.reflect.events.PlaceholderEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class PlaceholderCommand extends AbstractCommand implements Listener {

    public static HashMap<String, DExpansion> expansions = new HashMap<>();

    // @Plugin denizen-reflect
    public PlaceholderCommand() {
        setName("placeholder");
        setSyntax("placeholder [create/delete] [<placeholder>] [author:<author>] [version:<version>] (executor:{event}/<script>/<section>)");
        setRequiredArguments(2, 5);
        isProcedural = false;
        autoCompile();
        Bukkit.getPluginManager().registerEvents(this, DenizenReflect.getInstance());
    }

    // <--[command]
    // @Name Placeholder
    // @Syntax placeholder [create/delete] [<placeholder>] [author:<author>] [version:<version>] (executor:{event}/<script>/<section>)
    // @Required 2
    // @Maximum 5
    // @Short Placeholder manager.
    // @Group denizen-reflect
    //
    // @Description
    // Allows you to create placeholders.
    //
    // @Usage
    // Use to create placeholder %test%.
    // - placeholder create test author:Nybik_YT version:1.0
    // -->

    @SuppressWarnings("unused")
    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") @ArgLinear String action,
                                   @ArgName("placeholder") @ArgLinear String placeholder,
                                   @ArgName("author") @ArgPrefixed @ArgDefaultNull String author,
                                   @ArgName("version") @ArgPrefixed @ArgDefaultNull String version,
                                   @ArgName("executor") @ArgPrefixed @ArgDefaultText("event") ObjectTag executor) {
        switch (action) {
            case "create" -> {
                if (author == null || version == null) {
                    throw new InvalidArgumentsRuntimeException("Author and version cannot be null.");
                }
                DExpansion expansion = new DExpansion();
                expansion.author = author;
                expansion.identifier = placeholder;
                expansion.version = version;

                ScriptContainer scriptContainer = ScriptRegistry.getScriptContainer(executor.toString());
                expansion.executor = Objects.requireNonNullElse(scriptContainer, executor.getJavaObject());

                expansion.register();
                expansions.put(placeholder, expansion);
            }
            case "delete" -> {
                if (expansions.containsKey(placeholder)) {
                    expansions.get(placeholder).unregister();
                    expansions.remove(placeholder);
                } else {
                    throw new InvalidArgumentsRuntimeException("Placeholder '" + placeholder + "' does not exist.");
                }
            }
            default -> throw new InvalidArgumentsRuntimeException("Invalid action " + action + ". Expected 'create/delete'");
        }
    }

    @EventHandler
    public void onPapiReload(ExpansionsLoadedEvent event) {
        for (DExpansion expansion : expansions.values()) { expansion.register(); }
    }

    public static class DExpansion extends PlaceholderExpansion {

        String author;
        String identifier;
        String version;
        Object executor;

        @Override
        @NotNull
        public String getAuthor() {
            return author; //
        }

        @Override
        @NotNull
        public String getIdentifier() {
            return identifier;
        }

        @Override
        @NotNull
        public String getVersion() {
            return version;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            if (executor.toString().equals("event")) {
                return PlaceholderEvent.runPlaceholder(identifier, params, player);
            } else if (executor instanceof SectionCommand.Section section) {
                ContextSource.SimpleMap contextSource = new ContextSource.SimpleMap();
                contextSource.contexts = new HashMap<>();
                contextSource.contexts.put("id", new ElementTag(identifier));
                contextSource.contexts.put("params", new ElementTag(params));

                ScriptQueue queue = section.run(contextSource);
                return queue.determinations.getLast();
            } else if (executor instanceof TaskScriptContainer container) {
                ScriptEntryData scriptEntryData = new BukkitScriptEntryData(new PlayerTag(player), null);
                List<ScriptEntry> entries = container.getEntries(scriptEntryData, "script");
                InstantQueue queue = new InstantQueue(container.getName());
                queue.addEntries(entries);
                queue.setContextSource(name -> switch (name) {
                    case "id" -> new ElementTag(identifier);
                    case "params" -> new ElementTag(params);
                    default -> null;
                });
                queue.start();
                return queue.determinations.getLast();
            }
            return null;
        }
    }


}