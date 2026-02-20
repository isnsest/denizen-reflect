package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.scripts.commands.queue.IfCommand;
import com.denizenscript.denizencore.scripts.queues.ContextSource;
import com.denizenscript.denizencore.scripts.queues.ScriptQueue;
import com.denizenscript.denizencore.utilities.CoreConfiguration;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.ScriptUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.BracedCommand;
import com.denizenscript.denizencore.utilities.text.StringHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AsyncWhileCommand extends BracedCommand {

    // @Plugin denizen-reflect
    public AsyncWhileCommand() {
        setName("async-while");
        setSyntax("async-while [stop/next/[<value>] (!)(<operator> <value>) (&&/|| ...)] [<commands>]");
        setRequiredArguments(1, -1);
        setParseArgs(false);
    }

    // <--[command]
    // @Name Async-While
    // @Syntax async-while [stop/next/[<value>] (!)(<operator> <value>) (&&/|| ...)] [<commands>]
    // @Required 1
    // @Maximum -1
    // @Short Runs a series of braced commands asynchronously until the tag returns false.
    // @Group queue
    // @Guide https://guide.denizenscript.com/guides/basics/loops.html
    //
    // @Description
    // Runs the braced commands asynchronously until the if comparisons return false.
    // Uses the same comparison syntax as the <@link command if> command.
    // Unlike the normal while command, this does not block the current queue.
    //
    // You can end the loop early using 'stop', or skip to the next iteration with 'next'.

    //
    // @Tags
    // <[loop_index]> to get the number of loops so far.
    //
    // @Usage
    // Use to loop until a player sneaks, or the player goes offline. (Note: generally use 'waituntil' for this instead)
    // - async-while !<player.is_sneaking> && <player.is_online>:
    //     - narrate "Waiting for you to sneak..."
    //     - wait 1s
    //
    // -->

    private static class WhileData {
        public int index;
        public List<String> value;
        public long LastChecked;
        public int instaTicks;
        public ObjectTag originalIndexValue;

        public void reapplyAtEnd(ScriptQueue queue) {
            queue.addDefinition("loop_index", originalIndexValue);
        }
    }

    @Override
    public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
        List<String> comparisons = new ArrayList<>();
        if (scriptEntry.getOriginalArguments().size() == 1) {
            String arg = scriptEntry.getOriginalArguments().get(0);
            if (CoreUtilities.equalsIgnoreCase(arg, "stop")) {
                scriptEntry.addObject("stop", new ElementTag(true));
            }
            else if (CoreUtilities.equalsIgnoreCase(arg, "next")) {
                scriptEntry.addObject("next", new ElementTag(true));
            }
            else if (arg.equals("\0CALLBACK")) {
                scriptEntry.addObject("callback", new ElementTag(true));
            }
        }
        for (String arg : scriptEntry.getOriginalArguments()) {
            if (arg.equals("{")) {
                break;
            }
            comparisons.add(arg);
        }
        if (comparisons.isEmpty() && !scriptEntry.hasObject("stop") && !scriptEntry.hasObject("next") && !scriptEntry.hasObject("callback")) {
            throw new InvalidArgumentsException("Must specify a comparison value or 'stop' or 'next'!");
        }
        scriptEntry.addObject("comparisons", comparisons);

    }

    public void async_while(ScriptEntry scriptEntry, List<ScriptEntry> directEntries) {

        String queueId = "Async-While";
        ScriptEntryData entryData = scriptEntry.entryData.clone();
        ContextSource contextSource = scriptEntry.context.contextSource;
        MapTag defMap = scriptEntry.queue.definitions.duplicate();

        Consumer<ScriptQueue> configure = (queue) -> {
            if (defMap != null) {
                for (Map.Entry<StringHolder, ObjectTag> val : defMap.entrySet()) {
                    queue.addDefinition(val.getKey().str, val.getValue());
                }
            }
        };

        ScriptUtilities.createAndStartQueueArbitrary(queueId, directEntries, entryData, contextSource, configure);
    }

    @Override
    public void execute(ScriptEntry scriptEntry) {
        ElementTag stop = scriptEntry.getElement("stop");
        ElementTag next = scriptEntry.getElement("next");
        ElementTag callback = scriptEntry.getElement("callback");
        ScriptQueue queue = scriptEntry.queue;
        if (stop != null && stop.asBoolean()) {
            if (scriptEntry.dbCallShouldDebug()) {
                Debug.report(scriptEntry, getName(), stop);
            }
            boolean hasnext = false;
            for (int i = 0; i < queue.getQueueSize(); i++) {
                ScriptEntry entry = queue.getEntry(i);
                List<String> args = entry.getOriginalArguments();
                if (entry.getCommandName().equals("ASYNC-WHILE") && args.size() == 1 && args.get(0).equals("\0CALLBACK")) {
                    hasnext = true;
                    break;
                }
            }
            if (hasnext) {
                while (queue.getQueueSize() > 0) {
                    ScriptEntry entry = queue.getEntry(0);
                    List<String> args = entry.getOriginalArguments();
                    if (entry.getCommandName().equals("ASYNC-WHILE") && args.size() == 1 && args.get(0).equals("\0CALLBACK")) {
                        ((WhileData) entry.getOwner().getData()).reapplyAtEnd(queue);
                        queue.removeFirst();
                        break;
                    }
                    queue.removeFirst();
                }
            }
            else {
                Debug.echoError(scriptEntry, "Cannot stop while: not in one!");
            }
            return;
        }
        else if (next != null && next.asBoolean()) {
            if (scriptEntry.dbCallShouldDebug()) {
                Debug.report(scriptEntry, getName(), next);
            }
            boolean hasnext = false;
            for (int i = 0; i < queue.getQueueSize(); i++) {
                ScriptEntry entry = queue.getEntry(i);
                List<String> args = entry.getOriginalArguments();
                if (entry.getCommandName().equals("ASYNC-WHILE") && args.size() == 1 && args.get(0).equals("\0CALLBACK")) {
                    hasnext = true;
                    break;
                }
            }
            if (hasnext) {
                while (queue.getQueueSize() > 0) {
                    ScriptEntry entry = queue.getEntry(0);
                    List<String> args = entry.getOriginalArguments();
                    if (entry.getCommandName().equals("ASYNC-WHILE") && args.size() == 1 && args.get(0).equals("\0CALLBACK")) {
                        break;
                    }
                    queue.removeFirst();
                }
            }
            else {
                Debug.echoError(scriptEntry, "Cannot 'while next': not in one!");
            }
            return;
        }
        else if (callback != null && callback.asBoolean()) {
            if (scriptEntry.getOwner() != null && (scriptEntry.getOwner().getCommandName().equals("ASYNC-WHILE") ||
                    scriptEntry.getOwner().getBracedSet() == null || scriptEntry.getOwner().getBracedSet().isEmpty() ||
                    scriptEntry.getBracedSet().get(0).value.get(scriptEntry.getBracedSet().get(0).value.size() - 1) != scriptEntry)) {
                WhileData data = (WhileData) scriptEntry.getOwner().getData();
                data.index++;
                if (CoreUtilities.monotonicMillis() - data.LastChecked < 50) {
                    data.instaTicks++;
                    if (data.instaTicks > CoreConfiguration.whileMaxLoops && CoreConfiguration.whileMaxLoops != 0) {
                        return;
                    }
                }
                else {
                    data.instaTicks = 0;
                }
                data.LastChecked = CoreUtilities.monotonicMillis();
                boolean run = new IfCommand.ArgComparer().compare(new ArrayList(data.value), scriptEntry);
                if (run) {
                    if (scriptEntry.dbCallShouldDebug()) {
                        Debug.echoDebug(scriptEntry, Debug.DebugElement.Header, "While loop " + data.index);
                    }
                    queue.addDefinition("loop_index", String.valueOf(data.index));
                    List<ScriptEntry> bracedCommands = BracedCommand.getBracedCommandsDirect(scriptEntry.getOwner(), scriptEntry);
                    ScriptEntry callbackEntry = scriptEntry.clone();
                    callbackEntry.copyFrom(scriptEntry);
                    callbackEntry.setOwner(scriptEntry.getOwner());
                    bracedCommands.add(callbackEntry);
                    for (int i = 0; i < bracedCommands.size(); i++) {
                        bracedCommands.get(i).setInstant(true);
                    }
                    async_while(scriptEntry, bracedCommands);
                }
                else {
                    data.reapplyAtEnd(queue);
                    if (scriptEntry.dbCallShouldDebug()) {
                        Debug.echoDebug(scriptEntry, Debug.DebugElement.Header, "While loop complete");
                    }
                }
            }
            else {
                Debug.echoError(scriptEntry, "While CALLBACK invalid: not a real callback!");
            }
        }
        else {
            List<String> comparisons = (List<String>) scriptEntry.getObject("comparisons");
            boolean run = new IfCommand.ArgComparer().compare(comparisons, scriptEntry);
            if (scriptEntry.dbCallShouldDebug()) {
                Debug.report(scriptEntry, getName(), db("run_first_loop", run));
            }
            if (!run) {
                return;
            }
            WhileData datum = new WhileData();
            datum.index = 1;
            datum.value = comparisons;
            datum.LastChecked = CoreUtilities.monotonicMillis();
            datum.instaTicks = 1;
            scriptEntry.setData(datum);
            ScriptEntry callbackEntry = new ScriptEntry("ASYNC-WHILE", new String[] {"\0CALLBACK"},
                    (scriptEntry.getScript() != null ? scriptEntry.getScript().getContainer() : null));
            callbackEntry.copyFrom(scriptEntry);
            callbackEntry.setOwner(scriptEntry);
            List<ScriptEntry> bracedCommandsList = getBracedCommandsDirect(scriptEntry, scriptEntry);
            if (bracedCommandsList == null || bracedCommandsList.isEmpty()) {
                Debug.echoError(scriptEntry, "Empty subsection - did you forget a ':'?");
                return;
            }
            datum.originalIndexValue = queue.getDefinitionObject("loop_index");
            queue.addDefinition("loop_index", "1");
            bracedCommandsList.add(callbackEntry);
            for (int i = 0; i < bracedCommandsList.size(); i++) {
                bracedCommandsList.get(i).setInstant(true);
            }
            scriptEntry.setInstant(true);
            async_while(scriptEntry, bracedCommandsList);
        }
    }
}