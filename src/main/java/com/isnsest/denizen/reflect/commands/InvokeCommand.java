package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizencore.objects.ObjectFetcher;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.tags.TagManager;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.util.JavaExpressionEngine;

public class InvokeCommand extends AbstractCommand {

    // @Plugin denizen-reflect
    public InvokeCommand() {
        setName("invoke");
        setSyntax("invoke [<java_expression>]");
        setRequiredArguments(1, 1);
        autoCompile();
        registerTags();
    }

    // <--[language]
    // @name Invoke System
    // @group denizen-reflect
    // @plugin denizen-reflect
    // @description
    // The Invoke System allows executing Java expressions in Denizen scripts.
    // Supports calling methods, accessing fields, using constructors, and reading
    // definitions and contexts.
    //
    // Examples:
    // <code>
    // - invoke player.setHealth(0)
    // - define hp 5
    // - invoke player.setHealth(hp)
    // - narrate <invoke[player.getName()]>
    // - narrate <invoke[new String("denizen-reflect")]>
    // </code>
    //
    // Tags:
    // - <invoke[<java_expression>]> returns the result of the expression.
    //
    // -->

    // <--[command]
    // @Name Invoke
    // @Syntax invoke [<java_expression>]
    // @Required 1
    // @Maximum 1
    // @Short Calls Java code.
    // @Group denizen-reflect
    //
    // @Description
    // Executes a Java string: methods, fields, private methods and fields, constructors.
    //
    // @Tags
    // <invoke[<java_expression>]>
    //
    // @Usage
    // Use to change player health.
    // - invoke player.setHealth(0)
    //
    // @Usage
    // Use to get definition.
    // - define health 0
    // - invoke player.setHealth(health)
    //
    // @Usage
    // Use to get context — example: <context.damager>.
    // - invoke damager.setHealth(0)
    //
    // -->

    @SuppressWarnings("unused")
    public static void autoExecute(ScriptEntry scriptEntry, @ArgName("expression") @ArgLinear String expression) {
        try {
            JavaExpressionEngine.execute(expression, scriptEntry);
        } catch (Exception e) {
            Debug.echoError(e.getLocalizedMessage());
        }
    }

    public static void registerTags() {
        // <--[tag]
        // @attribute <invoke[<java_expression>]>
        // @returns ObjectTag
        // @description
        // Returns the result of the expression.
        // -->
        TagManager.registerTagHandler(ObjectTag.class, "invoke", (attribute) -> {
            String result = JavaExpressionEngine.execute(attribute.getParam(), attribute.getScriptEntry()).toString();
            return ObjectFetcher.pickObjectFor(result, attribute.context);
        });
    }
}