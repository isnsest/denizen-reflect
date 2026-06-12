package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.scripts.ScriptRegistry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.scripts.containers.core.TaskScriptContainer;
import com.denizenscript.denizencore.scripts.queues.ContextSource;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.scripts.queues.core.InstantQueue;
import com.denizenscript.denizencore.tags.*;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.events.CustomTagEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagCommand extends AbstractCommand {

    public static class DynamicTagRegistry {
        public static Map<String, ObjectTagProcessor<ObjectTag>> processors = new HashMap<>();
    }

    public TagCommand() {
        setName("tag");
        setSyntax("tag [create/delete] [<tag_name>] (static) (in:<object_name>) (executor:{event}/<script>/<section>/<tag>)");
        setRequiredArguments(2, 4);
        setParseArgs(false);
        autoCompile();
    }

    // <--[command]
    // @Name Tag
    // @Syntax tag [create/delete] [<tag_name>] (static) (in:<object_name>) (executor:{event}/<script>/<section>/<tag>)
    // @Required 2
    // @Maximum 5
    // @Short Tag manager.
    // @Group denizen-reflect
    //
    // @Description
    // Creates or deletes a custom tag.
    //
    // @Usage
    // Use to create tag <hello>.
    // - tag create hello
    //
    // @Usage
    // Use to create tag <player.hello>.
    // - tag create hello in:player
    //
    // -->

    @SuppressWarnings("unused")
    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") @ArgLinear String action,
                                   @ArgName("tag_name") @ArgLinear String tag_name,
                                   @ArgName("static") boolean _static,
                                   @ArgName("in") @ArgPrefixed @ArgDefaultText("null") String in,
                                   @ArgName("executor") @ArgPrefixed @ArgDefaultText("event") @ArgRaw @ArgNoDebug String executorRaw) {

        executorRaw = executorRaw.substring(executorRaw.indexOf(":") + 1);
        Object executorObj = TagManager.tagObject(executorRaw, scriptEntry.context).getJavaObject();
        Object executor = executorRaw;

        if (executorObj instanceof SectionCommand.Section section) {
            executor = section;
        } else if (ScriptRegistry.containsScript(executorRaw, TaskScriptContainer.class)) {
            executor = ScriptRegistry.getScriptContainer(executorRaw);
        }

        final Object finalExecutor = executor;

        switch (action) {
            case "create":
                if (!in.equals("null")) {
                    try {
                        ObjectTagProcessor<? extends ObjectTag> processor = DynamicTagRegistry.processors.get(in);
                        if (processor == null) {
                            processor = TagManager.baseTags.get(in).processor;
                        }
                        if (processor == null) {
                            Debug.echoError("Base Tag '" + in + "' not found.");
                            return;
                        }

                        if (processor.registeredObjectTags.containsKey(tag_name)) {
                            Debug.echoError("Tag '" + tag_name + "' already created in " + in);
                        } else {
                            processor.registerTag(ObjectTag.class, tag_name, (attribute, object) -> {
                                return execute(finalExecutor, attribute, object, tag_name, in);
                            });

                            ObjectTagProcessor.TagData<?, ?> tagData = processor.registeredObjectTags.get(tag_name);
                            if (tagData != null) tagData.isStatic = _static;
                        }
                    } catch (Exception e) {
                        Debug.echoError("Error while creating tag in '" + in + "': " + e.getMessage());
                    }
                } else {
                    if (TagManager.baseTags.containsKey(tag_name)) {
                        Debug.echoError("Base Tag '" + tag_name + "' already created.");
                    } else {
                        ObjectTagProcessor<ObjectTag> newProcessor = new ObjectTagProcessor<>();
                        newProcessor.type = ObjectTag.class;
                        DynamicTagRegistry.processors.put(tag_name, newProcessor);

                        TagManager.internalRegisterTagHandler(ObjectTag.class, tag_name, (attribute) -> {
                            return execute(finalExecutor, attribute, null, tag_name, null);
                        }, _static);

                        TagManager.TagBaseData data = TagManager.baseTags.get(tag_name);
                        if (data != null) data.processor = newProcessor;
                    }
                }
                break;

            case "delete":
                if (!in.equals("null")) {
                    ObjectTagProcessor<ObjectTag> processor = DynamicTagRegistry.processors.get(in);
                    if (processor == null) {
                        Debug.echoError("Base Tag '" + in + "' not found.");
                    } else if (!processor.registeredObjectTags.containsKey(tag_name)) {
                        Debug.echoError("Tag '" + tag_name + "' not found in " + in);
                    } else {
                        processor.registeredObjectTags.remove(tag_name);
                    }
                } else {
                    if (!TagManager.baseTags.containsKey(tag_name)) {
                        Debug.echoError("Base Tag '" + tag_name + "' not found.");
                    } else {
                        TagManager.baseTags.remove(tag_name);
                        DynamicTagRegistry.processors.remove(tag_name);
                    }
                }
                break;

            default:
                Debug.echoError("Invalid action " + action + ". Expected 'create' or 'delete'.");
                break;
        }
    }

    private static ObjectTag execute(Object executor, Attribute attribute, @Nullable ObjectTag object, String tag_name, String type) {
        ContextSource.SimpleMap contextSource = new ContextSource.SimpleMap();
        contextSource.contexts = new HashMap<>();
        contextSource.contexts.put("id", new ElementTag(tag_name));
        contextSource.contexts.put("object", object);
        contextSource.contexts.put("object_type", new ElementTag(type));
        contextSource.contexts.put("raw_param", new ElementTag(attribute.getRawParam()));
        contextSource.contexts.put("param", attribute.getParamObject());

        if (executor.equals("event")) {
            return CustomTagEvent.runCustomTag(attribute.context.getScriptEntryData(), attribute, object, tag_name, type).determination;
        } else if (executor instanceof SectionCommand.Section section) {
            section.entryData = attribute.context.getScriptEntryData();
            ScriptQueue queue = section.run(contextSource);
            return queue.determinations.isEmpty() ? null : queue.determinations.getObject(queue.determinations.size() - 1);
        } else if (executor instanceof TaskScriptContainer container) {
            PlayerTag player = ((BukkitScriptEntryData) attribute.context.getScriptEntryData()).getPlayer();
            ScriptEntryData scriptEntryData = new BukkitScriptEntryData(player, null);
            List<ScriptEntry> entries = container.getEntries(scriptEntryData, "script");
            InstantQueue queue = new InstantQueue(container.getName());
            queue.addEntries(entries);
            queue.setContextSource(contextSource);
            queue.start();
            return queue.determinations.isEmpty() ? null : queue.determinations.getObject(queue.determinations.size() - 1);
        } else {
            return TagManager.tagObject(executor.toString(), attribute.context);
        }
    }
}