package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsException;
import com.denizenscript.denizencore.exceptions.InvalidArgumentsRuntimeException;
import com.denizenscript.denizencore.objects.Argument;
import com.denizenscript.denizencore.objects.ArgumentHelper;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultNull;
import com.denizenscript.denizencore.scripts.commands.generator.ArgLinear;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.tags.TagManager;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.isnsest.denizen.reflect.events.CustomCommandEvent;

import java.util.*;

@SuppressWarnings("unused")
public class Command extends AbstractCommand {

    public Command() {
        setName("command");
        setSyntax("command [create/delete/rename] [<command_name>] (with:<value>)");
        setRequiredArguments(2, 3);
        isProcedural = false;
        autoCompile();
    }

    // <--[command]
    // @Name Command
    // @Syntax command [create/delete/rename] [<command_name>] (with:<value>)
    // @Required 2
    // @Maximum 3
    // @Short Command manager.
    // @Group denizen-reflect
    // @Description
    // Creates, deletes, or renames Denizen commands at runtime.
    //
    // Argument Map Structure:
    //    - type: linear (default) | prefixed | boolean
    //    - name: (required) Variable name.
    //    - tooltip: (optional) Syntax display.
    //    - default: (optional) Default value.
    //
    // @Usage
    // Use to create custom command: '- role [<role>] ({player}/<entity>|...) (announce)'.
    // - definemap args:
    //     1:
    //         name: role
    //     2:
    //         name: players
    //         tooltip: "{player}/<&lt>entity<&gt>|..."
    //         default: self
    //     3:
    //         type: boolean
    //         name: announce
    // - command create role with:<[args]>
    //
    // @Usage
    // Use to create a custom command: '- emote [<player>] [type:<type>]'.
    // - definemap args:
    //     1:
    //         name: player
    //     2:
    //         type: prefixed
    //         name: type
    // - command create emote with:<[args]>
    //
    // @Usage
    // Use to create a custom command: '- playertick (<#.#>/freeze)'.
    // - definemap args:
    //     1:
    //        name: tick
    //        tooltip: <&lt>#.#<&gt>/freeze
    //        default: 20
    // - command create playertick with:<[args]>
    //
    // @Usage
    // Use to rename the command: '- narrate', to '- narrate2'.
    // - command rename narrate with:narrate2
    // -->

    public static void autoExecute(ScriptEntry scriptEntry,
                                   @ArgName("action") @ArgLinear String action,
                                   @ArgName("command_name") @ArgLinear String commandName,
                                   @ArgName("with") @ArgPrefixed @ArgDefaultNull ObjectTag with) {
        switch (action) {
            case "create" -> {
                DenizenCore.commandRegistry.instances.remove(commandName);
                create(commandName, with, scriptEntry);
            }
            case "delete" -> {
                if (DenizenCore.commandRegistry.instances.remove(commandName) == null) {
                    Debug.echoError("No such command: " + commandName);
                }
            }
            case "rename" -> {
                AbstractCommand instance = DenizenCore.commandRegistry.instances.get(commandName);
                if (instance == null) {
                    Debug.echoError("No such command: " + commandName);
                    return;
                }
                if (with == null) {
                    Debug.echoError("Rename requires 'with:<new_name>' argument.");
                    return;
                }
                String newName = with.toString();
                instance.setName(newName);
                instance.setSyntax(instance.syntax.replace(commandName, newName));

                DenizenCore.commandRegistry.instances.remove(commandName);
                DenizenCore.commandRegistry.instances.put(newName, instance);
            }
            default -> Debug.echoError("Invalid action '" + action + "'. Expected: create, delete, rename.");
        }
    }

    private static void create(String commandName, ObjectTag withObj, ScriptEntry contextEntry) {
        if (withObj == null) {
            DenizenCore.commandRegistry.instances.put(commandName, new DynamicCommand(commandName, Collections.emptyList()));
            return;
        }

        MapTag map = withObj.asType(MapTag.class, contextEntry.context);
        if (map == null) {
            Debug.echoError("Invalid argument 'with': expected a MapTag definition.");
            return;
        }

        List<ArgConfig> configList = new ArrayList<>(map.size());

        for (ObjectTag val : map.values()) {
            MapTag argData = val.asType(MapTag.class, contextEntry.context);
            if (argData == null) continue;

            ElementTag nameEl = argData.getElement("name");
            if (nameEl == null) {
                Debug.echoError("Command creation: Argument missing 'name'.");
                continue;
            }

            String name = nameEl.asString();
            String typeStr = argData.containsKey("type") ? argData.getElement("type").asString() : "linear";
            ArgType type = ArgType.fromString(typeStr);

            if (type == ArgType.BOOLEAN) {
                configList.add(new ArgConfig(type, name, null, new ElementTag(false)));
                continue;
            }

            String tooltip = argData.containsKey("tooltip") ? argData.getElement("tooltip").asString() : null;
            ObjectTag defaultValue = null;
            if (argData.containsKey("default")) {
                defaultValue = argData.getObject("default");
            }

            configList.add(new ArgConfig(type, name, tooltip, defaultValue));
        }

        DenizenCore.commandRegistry.instances.put(commandName, new DynamicCommand(commandName, configList));
    }

    private enum ArgType {
        LINEAR, PREFIXED, BOOLEAN;

        public static ArgType fromString(String value) {
            int len = value.length();
            if (len == 8) {
                if (CoreUtilities.toLowerCase(value).equals("prefixed")) return PREFIXED;
            } else if (len == 7 || len == 4) {
                String lower = CoreUtilities.toLowerCase(value);
                if (lower.equals("boolean") || lower.equals("bool")) return BOOLEAN;
            }
            return LINEAR;
        }
    }

    private record ArgConfig(ArgType type, String name, String tooltip, ObjectTag defaultValue) {
        public boolean isOptional() { return defaultValue != null; }

        public String toSyntaxString() {
            if (type == ArgType.BOOLEAN) {
                return "(" + name + ")";
            }

            String body = (tooltip != null) ? tooltip : ("<" + name + ">");
            String content = (type == ArgType.PREFIXED) ? (name + ":" + body) : body;

            return isOptional() ? "(" + content + ")" : "[" + content + "]";
        }
    }

    private static class DynamicCommand extends AbstractCommand {
        private final String commandName;
        private final List<ArgConfig> argConfigs;

        public DynamicCommand(String commandName, List<ArgConfig> argConfigs) {
            this.commandName = commandName;
            this.argConfigs = argConfigs;
            this.setName(commandName);
            this.setParseArgs(false);
            this.isProcedural = false;
            generateSyntax();
        }

        private void generateSyntax() {
            if (argConfigs.isEmpty()) {
                setSyntax(commandName);
                setRequiredArguments(0, 0);
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(commandName).append(" ");
            int reqArgs = 0;
            int maxArgs = 0;
            for (ArgConfig config : argConfigs) {
                sb.append(config.toSyntaxString()).append(" ");
                if (!config.isOptional()) reqArgs++;
                maxArgs++;
            }
            setSyntax(sb.toString().trim());
            setRequiredArguments(reqArgs, maxArgs);
        }

        @Override
        public void parseArgs(ScriptEntry scriptEntry) throws InvalidArgumentsException {
            List<Argument> rawArgs = ArgumentHelper.interpret(scriptEntry, scriptEntry.getOriginalArguments());
            int argsSize = rawArgs.size();

            if (argConfigs.isEmpty()) {
                if (argsSize > 0) {
                    rawArgs.getFirst().reportUnhandled();
                }
                return;
            }

            boolean[] consumed = new boolean[argsSize];
            int consumedCount = 0;
            int linearIndex = 0;

            for (ArgConfig config : argConfigs) {
                Argument foundArg = null;
                int foundIndex = -1;

                if (config.type == ArgType.BOOLEAN) {
                    for (int i = 0; i < argsSize; i++) {
                        if (!consumed[i]) {
                            Argument arg = rawArgs.get(i);
                            if (arg.matches(config.name)) {
                                foundArg = arg;
                                foundIndex = i;
                                break;
                            }
                        }
                    }

                    if (foundArg != null) {
                        consumed[foundIndex] = true;
                        consumedCount++;
                        scriptEntry.addObject(config.name, new ElementTag(true));
                    } else {
                        scriptEntry.addObject(config.name, new ElementTag(false));
                    }
                    continue;
                }

                if (config.type == ArgType.PREFIXED) {
                    for (int i = 0; i < argsSize; i++) {
                        if (!consumed[i]) {
                            Argument arg = rawArgs.get(i);
                            if (arg.matchesPrefix(config.name)) {
                                foundArg = arg;
                                foundIndex = i;
                                break;
                            }
                        }
                    }
                } else {
                    while (linearIndex < argsSize) {
                        if (!consumed[linearIndex]) {
                            Argument arg = rawArgs.get(linearIndex);
                            if (!arg.hasPrefix()) {
                                foundArg = arg;
                                foundIndex = linearIndex;
                                linearIndex++;
                                break;
                            }
                        }
                        linearIndex++;
                    }
                }

                if (foundArg != null) {
                    consumed[foundIndex] = true;
                    consumedCount++;
                    scriptEntry.addObject(config.name, TagManager.tagObject(foundArg.getValue(), scriptEntry.getContext()));
                } else {
                    if (config.isOptional()) {
                        scriptEntry.addObject(config.name, config.defaultValue);
                    } else {
                        String display = (config.tooltip != null) ? config.tooltip : ("<" + config.name + ">");
                        String errorPart = config.type == ArgType.PREFIXED
                                ? "'" + config.name + ":' for " + display
                                : display;
                        throw new InvalidArgumentsException("Missing required argument " + errorPart);
                    }
                }
            }

            if (consumedCount != argsSize) {
                for (int i = 0; i < argsSize; i++) {
                    if (!consumed[i]) {
                        rawArgs.get(i).reportUnhandled();
                    }
                }
            }
        }

        @Override
        public void execute(ScriptEntry scriptEntry) {
            String event = CustomCommandEvent.runCustomCommand(scriptEntry, commandName);
            if (event != null) {
                throw new InvalidArgumentsRuntimeException(event);
            }
        }
    }
}