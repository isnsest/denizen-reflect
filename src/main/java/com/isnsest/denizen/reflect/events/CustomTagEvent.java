package com.isnsest.denizen.reflect.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.tags.Attribute;

public class CustomTagEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // custom tag
    //
    // @Plugin denizen-reflect
    //
    // @Switch id:<id> object_type:<object_type> to only process the event if a specific action is performed.
    //
    // @Group denizen-reflect
    //
    // @Triggers when a custom tag is filled in.
    //
    // @Context
    // <context.id> returns the ElementTag of the id command.
    // <context.object> returns the ObjectTag of the handler.
    // <context.object_type> returns the ElementTag of handler type.
    // <context.raw_param> returns the ElementTag of the raw_param.
    // <context.param> returns the ObjectTag of the param.
    //
    // @Determine
    // ObjectTag to fill the tag.
    // -->

    public static CustomTagEvent instance;

    public String id;
    public String type;
    public ScriptEntryData entryData = null;
    public ObjectTag object;
    public Attribute attribute;

    public ObjectTag determination;

    public CustomTagEvent() {
        instance = this;
        registerCouldMatcher("custom tag");
        registerSwitches("id", "object_type");
        this.<CustomTagEvent, ObjectTag>registerDetermination(null, ObjectTag.class, (evt, context, output) -> evt.determination = output);
    }

    @Override
    public boolean matches(ScriptPath path) {
        if (!runGenericSwitchCheck(path, "id", id)) {
            return false;
        }
        return runGenericSwitchCheck(path, "object_type", type);
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return entryData;
    }

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "id" -> new ElementTag(id);
            case "object" -> object;
            case "object_type" -> new ElementTag(type);
            case "raw_param" -> new ElementTag(attribute.getRawParam());
            case "param" -> attribute.getParamObject();
            default -> super.getContext(name);
        };
    }

    public static CustomTagEvent runCustomTag(ScriptEntryData data, Attribute attribute, ObjectTag object, String id, String type) {

        instance.id = id;
        instance.entryData = data;
        instance.attribute = attribute;
        instance.object = object;
        instance.type = type;

        return (CustomTagEvent) instance.fire();
    }
}
