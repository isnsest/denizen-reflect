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
import com.isnsest.denizen.reflect.util.JavaExpressionEngine;
import com.isnsest.denizen.reflect.util.LibraryLoader;

import java.lang.reflect.*;
import java.util.*;

import static com.isnsest.denizen.reflect.util.JavaExpressionEngine.resolveClass;

public class ProxyCommand extends AbstractCommand {

    // @Plugin denizen-reflect
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

        List<Class<?>> interfaceList = new ArrayList<>();
        for (String interfaceName : interfaces) {
            try {
                String path = entry.getScript().getContainer().getRelativeFileName().replace("\\", "/");
                path = path.substring(path.indexOf("scripts/") + "scripts/".length());
                JavaExpressionEngine.ImportContext imports = JavaExpressionEngine.importContexts.get(path);
                JavaExpressionEngine.EvalContext ctx = new JavaExpressionEngine.EvalContext(imports, entry);
                Class<?> imported = ctx.imports().resolveType(interfaceName);
                Class<?> clazz = null;
                if (imported != null) {
                    clazz = imported;
                } else {
                    try {
                        clazz = resolveClass(name);
                    } catch (ClassNotFoundException ignored) {}
                }
                if (clazz != null && clazz.isInterface()) {
                    interfaceList.add(clazz);
                } else {
                    Debug.echoError("Class is not an interface: " + interfaceName);
                }
            } catch (Exception e) {
                Debug.echoError("Interface not found: " + interfaceName);
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

            private static final Method INVOKE_DEFAULT;

            static {
                Method method;
                try {
                    method = InvocationHandler.class.getDeclaredMethod("invokeDefault", Object.class, Method.class, Object[].class);
                    method.setAccessible(true);
                } catch (NoSuchMethodException e) {
                    method = null;
                }
                INVOKE_DEFAULT = method;
            }

        @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String methodName = method.getName();
                String handlerKey = methodName.toLowerCase();
                ObjectTag scriptName = scriptHandlers.get(handlerKey);

                if (scriptName == null) {
                        if (method.isDefault() && INVOKE_DEFAULT != null) {
                            return INVOKE_DEFAULT.invoke(this, proxy, method, args);
                        }

                    if (methodName.equals("toString") && method.getParameterCount() == 0) {
                            return proxy.getClass().getName() + "@" + Integer.toHexString(proxy.hashCode());
                        }
                        if (methodName.equals("hashCode") && method.getParameterCount() == 0) {
                            return System.identityHashCode(proxy);
                        }
                        if (methodName.equals("equals") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class) {
                            return proxy == args[0];
                        }

                        return null;
                    }

                int i = 0;

                ListTag definitions;
                ListTag argsList = new ListTag();
                if (args != null) {
                    for (Object arg : args) {
                        argsList.addObject(new JavaReflectedObjectTag(arg));
                    }
                }

                if (scriptName.getJavaObject() instanceof String) {
                    ScriptContainer container = ScriptRegistry.getScriptContainer((String) scriptName.getJavaObject());
                    definitions = new ListTag(container.getString("definitions"));
                    ScriptEntryData data = creationEntry.entryData.clone();

                    InstantQueue queue = new InstantQueue("PROXY_");
                    queue.addEntries(container.getBaseEntries(data));
                    for (String arg : argsList) {
                        try {
                            queue.addDefinition(definitions.get(i), arg);
                        } catch (Exception e) {
                            queue.addDefinition(String.valueOf(i + 1), arg);
                        } i++;
                    }
                    queue.addDefinition("proxy", new JavaReflectedObjectTag(proxy));
                    queue.addDefinition("method", new ElementTag(methodName));
                    queue.start();
                } else if (scriptName.getJavaObject() instanceof SectionCommand.Section section) {
                    section.run(null, args);
                } else {
                    Debug.echoError("Proxy handler refers to missing script: " + scriptName);
                    return null;
                }
                return proxy;
            }
        }
}