import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom")
    `java-library`
}

applyPlatformAndCoreConfiguration()
applyShadowConfiguration()

loom {
    accessWidenerPath.set(project.file("src/main/resources/worldedit.accesswidener"))
}

val minecraftVersion = "1.21.1"
val loaderVersion = "0.17.2"
val myAttribute = Attribute.of("myOwnAttribute", String::class.java)

configurations.named("archives") {
    attributes {
        attribute(myAttribute, "myOwnValue")
    }
}


configurations.all {
    resolutionStrategy {
        //   force("com.google.guava:guava:21.0")
    }
}

val fabricApiConfiguration: Configuration = configurations.create("fabricApi")

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven { url = uri("https://maven.nucleoid.xyz/") }
    maven { url = uri("https://jitpack.io") }
    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    flatDir {
        dirs("libs")
    }
}

dependencies {
    "api"(project(":worldedit-core"))
    //"implementation"("org.apache.logging.log4j:log4j-slf4j-impl:2.8.1")

    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    "mappings"(loom.officialMojangMappings())
    "modImplementation"("net.fabricmc:fabric-loader:$loaderVersion")

    api("org.xerial:sqlite-jdbc:3.42.0.1")
    "implementation"(libs.fastutil)
    "compileOnly"("net.kyori:adventure-api")
    "implementation"("org.yaml:snakeyaml:2.2")
    api(libs.lz4Java) { isTransitive = false }
    api("net.jpountz.lz4:lz4:1.0.0")
    api(libs.sparsebitset)
    api(libs.parallelgzip) { isTransitive = false }
    implementation(libs.zstd) { isTransitive = false }
    //implementation("dev.notmyfault.serverlib:ServerLib")
    "modImplementation"("xyz.nucleoid:stimuli:0.4.12+1.21")
    "modImplementation"("maven.modrinth:KOHu7RCS:T5A0M0sB")
    modCompileOnly("name:PlotSquared-7.3.9-SNAPSHOT")
    // [1] declare fabric-api dependency...
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:0.116.5+1.21.1")

    // No need for this at runtime
    "modCompileOnly"("me.lucko:fabric-permissions-api:0.1-SNAPSHOT")

    // Hook these up manually, because Fabric doesn't seem to quite do it properly.
    "compileOnly"("net.fabricmc:sponge-mixin:latest")
    "annotationProcessor"("net.fabricmc:sponge-mixin:latest")
    "annotationProcessor"("net.fabricmc:fabric-loom:1.0-SNAPSHOT")

}

configure<BasePluginExtension> {
    archivesName.set("${project.name}-mc$minecraftVersion")
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    // this will ensure that this task is redone when the versions change.
    inputs.property("version", project.ext["internalVersion"])

    from(sourceSets["main"].resources.srcDirs) {
        include("fabric.mod.json")
        expand("version" to project.ext["internalVersion"])
    }

    // copy everything else except the mod json
    from(sourceSets["main"].resources.srcDirs) {
        exclude("fabric.mod.json")
    }
}

addJarManifest(includeClasspath = false, kind = WorldEditKind.Mod)

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("dist-dev")
    dependencies {
        relocate("org.slf4j", "com.sk89q.worldedit.slf4j")
        relocate("org.apache.logging.slf4j", "com.sk89q.worldedit.log4jbridge")
        relocate("org.antlr.v4", "com.sk89q.worldedit.antlr4")
        include(dependency("org.mozilla:rhino-runtime"))
        relocate("org.anarres", "com.fastasyncworldedit.core.internal.io") {
            include(dependency("org.anarres:parallelgzip:1.0.5"))
        }
        include(dependency("org.xerial:sqlite-jdbc:3.42.0.1"))
        include(dependency("org.yaml:snakeyaml"))
        include(dependency(libs.sparsebitset.get()))
        include(dependency("com.github.luben:zstd-jni"))
        include(dependency("dev.notmyfault.serverlib:ServerLib:2.3.1"))
        //include(dependency("io.papermc:paperlib"))
        relocate("net.kyori", "com.fastasyncworldedit.core.adventure") {
            include(dependency("net.kyori:adventure-nbt:4.9.3"))
        }
        relocate("org.lz4", "com.fastasyncworldedit.core.lz4") {
            include(dependency("org.lz4:lz4-java:1.8.0"))
        }

        include(dependency("org.slf4j:slf4j-api"))
        include(dependency("org.apache.logging.log4j:log4j-slf4j-impl"))
        include(dependency("org.antlr:antlr4-runtime"))

    }
    exclude("META-INF/versions/9/module-info.class")
    minimize {
        exclude(dependency("org.mozilla:rhino-runtime"))
    }
}

tasks.register<Jar>("deobfJar") {
    from(sourceSets["main"].output)
    archiveClassifier.set("dev")
}

artifacts {
    add("archives", tasks.named("deobfJar"))
}

tasks.named<RemapJarTask>("remapJar").configure {
    val shadow = tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadow)
    mustRunAfter(shadow)
    input.set(shadow.flatMap { it.archiveFile })

    addNestedDependencies.set(true)
    // Optional: set destination dir if you want a specific folder
    // destinationDirectory.set(layout.buildDirectory.dir("libs"))

    // Produce a clean, release-like filename (not the -dist-dev one)
    archiveFileName.set("${rootProject.name}-Fabric-${project.version}.${archiveExtension.getOrElse("jar")}")
    doFirst {
        println("remapJar (shaded) input = ${input.get().asFile.absolutePath}")
    }
}


tasks.named("assemble").configure {
    dependsOn("remapJar")
}


