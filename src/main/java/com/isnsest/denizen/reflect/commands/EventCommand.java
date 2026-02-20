package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultText;
import com.denizenscript.denizencore.scripts.commands.generator.ArgLinear;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.utilities.debugging.Debug;

public class EventCommand extends AbstractCommand {

    // @Plugin denizen-reflect
    public EventCommand() {
        setName("event");
        setSyntax("event [rename] [<event_name>] [to:<new_event_name>]");
        setRequiredArguments(3, 3);
        autoCompile();
    }

    // <--[command]
    // @Name Event
    // @Syntax event [rename] [<event_name>] [to:<new_event_name>]
    // @Required 3
    // @Maximum 3
    // @Short Event manager.
    // @Group denizen-reflect
    //
    // @Description
    // Renames Denizen events at runtime.
    //
    // @Usage
    // Use to rename 'player jumps' event.
    // - event rename 'player jumps' 'to:player jumps2'
    // -->

    @SuppressWarnings("unused")
    public static void autoExecute(ScriptEntry scriptEntry,
                            @ArgName("action") @ArgLinear String action,
                            @ArgName("event_name") @ArgLinear String event_name,
                            @ArgName("to") @ArgPrefixed @ArgDefaultText("null") String to) {
        if (action.equals("rename")) {
            event_name = event_name.toLowerCase().replaceAll(" ", "");
            if (!ScriptEvent.eventLookup.containsKey(event_name)) {
                Debug.echoError("No such event: " + event_name);
                return;
            }
            ScriptEvent event = ScriptEvent.eventLookup.get(event_name);
            event.eventData.couldMatchers.clear();
            event.registerCouldMatcher(to);
        } else {
            Debug.echoError("Invalid action " + action + ". Expected 'rename'.");
        }
    }
}