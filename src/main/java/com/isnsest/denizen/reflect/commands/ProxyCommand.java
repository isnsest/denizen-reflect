package com.isnsest.denizen.reflect.commands;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.*;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import com.denizenscript.denizencore.scripts.ScriptRegistry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.ArgDefaultText;
import com.denizenscript.denizencore.scripts.commands.generator.ArgLinear;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.scripts.containers.ScriptContainer;
import com.denizenscript.denizencore.scripts.queues.core.InstantQueue;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import com.denizenscript.denizencore.utilities.text.StringHolder;
import com.isnsest.denizen.reflect.DenizenReflect;
import com.isnsest.denizen.reflect.engine.ImportContext;
import com.isnsest.denizen.reflect.engine.JavaExpressionEngine;
import com.isnsest.denizen.reflect.util.LibraryLoader;
import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.*;

public class ProxyCommand extends AbstractCommand {

    public ProxyCommand() {
        setName("proxy");
        setSyntax("proxy [<interfaces>|...] (using:<map>) (as:<name>)");
        setRequiredArguments(3, 3);
        autoCompile();
    }

    // <--[command]
    // @Name Proxy
    // @Syntax proxy [<interfaces>|...] (using:<map>) (as:<name>)
    // @Required 1
    // @Maximum 3
    // @Short Creates a proxy for given interfaces.
    // @Group denizen-reflect
    //
    // @Description
    // Creates a new proxy object using the specified interfaces.
    //
    // @Usage
    // Use to create proxy of Runnable.
    // - proxy Runnable using:[run=task]
    // -->

    @SuppressWarnings("unused")
    public static void autoExecute(
            ScriptEntry entry,
            @ArgName("interfaces") @ArgLinear ListTag interfaces,
            @ArgName("using") @ArgPrefixed MapTag using,
            @ArgName("as") @ArgPrefixed @ArgDefaultText("proxy") String name) {

        ImportContext imports = JavaExpressionEngine.getImports(entry);
        List<Class<?>> interfaceList = new ArrayList<>();
        for (String interfaceName : interfaces) {
            Class<?> clazz = imports.findClass(interfaceName);
            if (clazz == null) {
                Debug.echoError("Interface not found: " + interfaceName);
            } else if (!clazz.isInterface()) {
                Debug.echoError("Class is not an interface: " + interfaceName);
            } else {
                interfaceList.add(clazz);
            }
        }

        if (interfaceList.isEmpty()) {
            Debug.echoError("No valid interfaces provided for proxy.");
            return;
        }

        Map<String, ObjectTag> scriptHandlers = new HashMap<>();
        for (Map.Entry<StringHolder, ObjectTag> mapEntry : using.map.entrySet()) {
            scriptHandlers.put(mapEntry.getKey().low, mapEntry.getValue());
        }

        Object proxyInstance = Proxy.newProxyInstance(
                LibraryLoader.getClassLoader(),
                interfaceList.toArray(new Class[0]),
                new DenizenInvocationHandler(scriptHandlers, entry)
        );

        entry.getResidingQueue().addDefinition(name, new JavaReflectedObjectTag(proxyInstance));
    }

    private record DenizenInvocationHandler(Map<String, ObjectTag> scriptHandlers,
                                            ScriptEntry creationEntry) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            ObjectTag handler = scriptHandlers.get(method.getName().toLowerCase());
            if (handler == null) {
                return invokeUnhandled(proxy, method, args);
            }

            Object target = handler.getJavaObject();
            ScriptContainer container = target instanceof String scriptName ? ScriptRegistry.getScriptContainer(scriptName) : null;
            if (container != null) {
                runScript(container, proxy, method.getName(), args);
            } else if (target instanceof SectionCommand.Section section) {
                Bukkit.getScheduler().runTask(DenizenReflect.getInstance(), () -> section.run(null, args));
            } else {
                Debug.echoError("Proxy handler refers to missing script: " + handler);
                return null;
            }
            return proxy;
        }

        private static Object invokeUnhandled(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            String name = method.getName();
            int count = method.getParameterCount();
            if (name.equals("toString") && count == 0) {
                return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
            }
            if (name.equals("hashCode") && count == 0) {
                return System.identityHashCode(proxy);
            }
            if (name.equals("equals") && count == 1 && method.getParameterTypes()[0] == Object.class) {
                return proxy == args[0];
            }
            return null;
        }

        private void runScript(ScriptContainer container, Object proxy, String methodName, Object[] args) {
            ListTag definitions = new ListTag(container.getString("definitions"));
            ScriptEntryData data = creationEntry.entryData.clone();
            Bukkit.getScheduler().runTask(DenizenReflect.getInstance(), () -> {
                InstantQueue queue = new InstantQueue("PROXY_");
                queue.addEntries(container.getBaseEntries(data));
                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] == null) continue;
                        String definition = i < definitions.size() ? definitions.get(i) : String.valueOf(i + 1);
                        queue.addDefinition(definition, new JavaReflectedObjectTag(args[i]));
                    }
                }
                queue.addDefinition("proxy", new JavaReflectedObjectTag(proxy));
                queue.addDefinition("method", new ElementTag(methodName));
                queue.start();
            });
        }
    }
}