package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.objects.ObjectFetcher;
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
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagManager;
import com.denizenscript.denizencore.utilities.ScriptUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.events.CustomTagEvent;

import java.util.HashMap;
import java.util.List;

public class TagCommand extends AbstractCommand {

    // @Plugin denizen-reflect
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

        switch (action) {
            case "create":
                Object finalExecutor = executor;
                if (!in.equals("null")) {
                    try {
                        ObjectTagProcessor<? extends ObjectTag> processor = TagManager.baseTags.get(in).processor;
                        if (processor.registeredObjectTags.containsKey(tag_name)) {
                            Debug.echoError("Tag '" + tag_name + "' already created in " + in);
                        } else {
                            processor.registerTagInternal(ObjectTag.class, tag_name, (attribute, object) -> {
                                return execute(finalExecutor, attribute, object, tag_name);
                            }, _static, new String[0]);
                        }
                    } catch (Exception e) {
                        Debug.echoError("Base Tag '" + in + "' not found. (Exception)");
                    }
                } else {
                    if (TagManager.baseTags.containsKey(tag_name)) {
                        Debug.echoError("Base Tag '" + tag_name + "' already created.");
                    } else {
                        TagManager.internalRegisterTagHandler(ObjectTag.class, tag_name, (attribute) -> {
                            return execute(finalExecutor, attribute, new ElementTag("null"), tag_name);
                        }, _static);
                    }
                }
                break;
            case "delete":
                if (!in.equals("null")) {
                    try {
                        ObjectTagProcessor<? extends ObjectTag> processor = TagManager.baseTags.get(in).processor;
                        if (!processor.registeredObjectTags.containsKey(tag_name)) {
                            Debug.echoError("Tag '" + tag_name + "' not found in " + in);
                        } else {
                            processor.registeredObjectTags.remove(tag_name);
                        }
                    } catch (Exception e) {
                        Debug.echoError("Base Tag '" + in + "' not found. (Exception[2])");
                    }
                } else {
                    if (!TagManager.baseTags.containsKey(tag_name)) {
                        Debug.echoError("Base Tag '" + tag_name + "' not found.");
                    } else {
                        TagManager.baseTags.remove(tag_name);
                    }
                }
                break;
            default:
                Debug.echoError("Invalid action " + action + ". Expected 'create' or 'delete'.");
                break;

        }
    }

    private static ObjectTag execute(Object executor,
                                     Attribute attribute,
                                     ObjectTag object,
                                     String tag_name) {
        ObjectTag obj = ObjectFetcher.pickObjectFor(object.identify(), attribute.context);

        ContextSource.SimpleMap contextSource = new ContextSource.SimpleMap();
        contextSource.contexts = new HashMap<>();
        contextSource.contexts.put("id", new ElementTag(tag_name));
        contextSource.contexts.put("object", obj);
        contextSource.contexts.put("object_type", new ElementTag(obj.getPrefix().toLowerCase()));
        contextSource.contexts.put("raw_param", new ElementTag(attribute.getRawParam()));
        contextSource.contexts.put("param", attribute.getParamObject());

        if (executor.equals("event")) {
            return CustomTagEvent.runCustomTag(attribute.context.getScriptEntryData(), attribute, obj, tag_name).determination;
        } else if (executor instanceof SectionCommand.Section section) {
            section.entryData = attribute.context.getScriptEntryData();
            ScriptQueue queue = section.run(contextSource);
            if (queue.determinations.isEmpty()) {
                return null;
            }
            return queue.determinations.getObject(queue.determinations.size() - 1);
        } else if (executor instanceof TaskScriptContainer container) {
            PlayerTag player = ((BukkitScriptEntryData) attribute.context.getScriptEntryData()).getPlayer();
            ScriptEntryData scriptEntryData = new BukkitScriptEntryData(player, null);
            List<ScriptEntry> entries = container.getEntries(scriptEntryData, "script");
            InstantQueue queue = new InstantQueue(container.getName());
            queue.addEntries(entries);
            queue.setContextSource(contextSource);
            queue.start();
            if (queue.determinations.isEmpty()) {
                return null;
            }
            return queue.determinations.getObject(queue.determinations.size() - 1);
        } else {
            return TagManager.tagObject(executor.toString(), attribute.context);
        }
    }

}