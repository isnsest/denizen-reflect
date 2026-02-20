import java.text.SimpleDateFormat
import java.util.Date
import org.gradle.api.tasks.bundling.Jar

plugins {
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://repo.extendedclip.com/releases")
    }

    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }

    maven {
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://maven.citizensnpcs.co/repo")
    }

    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "codemc"
    }
}

dependencies {
    implementation(files("libs/DenizenTagFinder.jar"))
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("io.papermc.paper:paper-api:${project.properties["craftbukkit.version"]}")
    compileOnly("com.denizenscript:denizen:${project.properties["denizen.version"]}")
}

val buildNumber: String = System.getenv("BUILD_NUMBER") ?: project.property("BUILD_NUMBER") as String
val buildDate: String = SimpleDateFormat("ddMMyyyy").format(Date())
val pluginVersion = "2.2.6"

group = "isnsest"
version = pluginVersion
description = "DenizenReflect"

tasks.withType<Jar> {
    archiveFileName.set("${rootProject.name}-${pluginVersion}.jar")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(mapOf("version" to pluginVersion))
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.shadowJar {
    archiveClassifier.set("all")
}
tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}
