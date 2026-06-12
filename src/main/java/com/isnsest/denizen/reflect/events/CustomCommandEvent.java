package com.isnsest.denizen.reflect.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.ScriptEntryData;

public class CustomCommandEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // custom command
    //
    // @Plugin denizen-reflect
    //
    // @Switch id:<id> to only process the event if a specific action is performed.
    //
    // @Group denizen-reflect
    //
    // @Triggers when a custom command is executed.
    //
    // @Context
    // <context.id> returns the ElementTag of the id command.
    // <context.(argument)> returns the ObjectTag of the specified argument.
    //
    // @Determine
    // ElementTag to send a command execution error.
    // -->

    public static CustomCommandEvent instance;

    public String id;
    public ScriptEntryData entryData = null;
    public ScriptEntry scriptEntry = null;
    public ObjectTag determination = null;



    public CustomCommandEvent() {
        instance = this;
        registerCouldMatcher("custom command");
        registerSwitches("id");
        this.<CustomCommandEvent, ObjectTag>registerDetermination(null, ObjectTag.class, (evt, context, output) -> {
            determination = output;
        });
    }

    @Override
    public boolean matches(ScriptPath path) {
        return runGenericSwitchCheck(path, "id", id);
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return entryData;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ObjectTag getContext(String name) {
        if (scriptEntry.hasObject(name)) {
            return scriptEntry.getObjectTag(name);
        } else if (name.equals("id")) {
            return new ElementTag(id);
        } else {
            return super.getContext(name);
        }
    }

    public static ObjectTag runCustomCommand(ScriptEntry scriptEntry, String id) {

        instance.id = id;
        instance.entryData = scriptEntry.entryData;
        instance.scriptEntry = scriptEntry;

        instance.fire();
        return instance.determination;
    }
}
