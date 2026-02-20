package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizencore.objects.core.JavaReflectedObjectTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.scripts.queues.ContextSource;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.scripts.commands.BracedCommand;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.utilities.ScriptUtilities;

import static com.isnsest.denizen.reflect.util.JavaExpressionEngine.wrapObject;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SectionCommand extends BracedCommand {

    // @Plugin denizen-reflect
    public SectionCommand() {
        setName("section");
        setSyntax("section (<definitions>) (as:<name>)");
        setRequiredArguments(0, 2);
        isProcedural = false;
        autoCompile();
    }

    // <--[command]
    // @Name Section
    // @Syntax section [as:<name>]
    // @Required 1
    // @Maximum 2
    // @Short Section of commands.
    // @Group denizen-reflect
    //
    // @Description
    // A group of commands inside.
    //
    // @Usage
    // - section text:
    //     - narrate "<[text]>, <[2]>"
    // - invoke section.run("Hello", "123")
    // -->

    public static class Section {
        public List<ScriptEntry> directEntries;
        public ContextSource contextSource;
        public TagContext context;
        public String queueId;
        public ScriptEntryData entryData;
        public MapTag defMap;
        public ListTag definitions;

        @SuppressWarnings("unused")
        public ScriptQueue run(ContextSource contextSource, Object... def) {
            Consumer<ScriptQueue> configure = (queue) -> {
                queue.definitions = defMap;
                if (def != null) {
                    int i = 0;
                    for (Object object : def) {
                        String key = definitions.get(i);
                        queue.addDefinition(key != null ? key : String.valueOf(i + 1), wrapObject(object, context));
                        i++;
                    }
                }
            };

            if (contextSource == null) {
                contextSource = this.contextSource;
            }

            return ScriptUtilities.createAndStartQueueArbitrary(queueId, directEntries, entryData, contextSource, configure);
        }

    }

    @SuppressWarnings("unused")
    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("definitions") @ArgLinear @ArgDefaultNull ListTag definitions,
                                   @ArgName("as") @ArgPrefixed @ArgDefaultText("section") String define) {
        UUID id = UUID.randomUUID();

        Section section = new Section();
        section.directEntries = getBracedCommandsDirect(scriptEntry, scriptEntry);
        section.queueId = "SECTION_" + scriptEntry.getScript().getContainer().getName();
        section.entryData = scriptEntry.entryData;
        section.contextSource = scriptEntry.context.contextSource;
        section.defMap = scriptEntry.queue.definitions.duplicate();
        section.definitions = definitions;
        section.context = scriptEntry.context;

        scriptEntry.getResidingQueue().addDefinition(define, new JavaReflectedObjectTag(section));
    }
}