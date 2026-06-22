package com.isnsest.denizen.reflect.util.codegen;

import org.bukkit.Bukkit;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ClasspathResolver {
    private String cachedClasspath = null;

    public String resolve() {
        if (cachedClasspath != null) {
            return cachedClasspath;
        }

        Set<String> cp = new HashSet<>();

        String sysCp = System.getProperty("java.class.path");
        if (sysCp != null) {
            cp.addAll(Arrays.asList(sysCp.split(File.pathSeparator)));
        }

        try {
            for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                File jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                cp.add(jarFile.getAbsolutePath());
            }
        } catch (Exception ignored) {}

        String[] coreClasses = {
                "net.kyori.adventure.text.Component",
                "net.kyori.adventure.audience.Audience",
                "net.kyori.adventure.key.Namespaced",
                "net.kyori.adventure.text.event.HoverEventSource",
                "com.destroystokyo.paper.event.player.PlayerJumpEvent",
                "net.md_5.bungee.api.chat.BaseComponent"
        };
        for (String cls : coreClasses) {
            try {
                Class<?> c = Class.forName(cls);
                File jarFile = new File(c.getProtectionDomain().getCodeSource().getLocation().toURI());
                cp.add(jarFile.getAbsolutePath());
            } catch (Throwable ignored) {}
        }

        String[] commonLibFolders = {"bin", "cache", "plugins"};
        for (String folderName : commonLibFolders) {
            scanJarsSafely(new File(folderName), cp, 2);
        }

        scanJarsSafely(new File("libraries"), cp, 10);

        cachedClasspath = String.join(File.pathSeparator, cp);
        return cachedClasspath;
    }

    private void scanJarsSafely(File dir, Set<String> cp, int maxDepth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || maxDepth < 0) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                scanJarsSafely(f, cp, maxDepth - 1);
            } else if (f.getName().endsWith(".jar")) {
                cp.add(f.getAbsolutePath());
            }
        }
    }
}