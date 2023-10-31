import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.configuration.providers.minecraft.SingleJarMinecraftProvider.server
import net.fabricmc.loom.task.RemapJarTask

/*
buildscript {
    repositories {
        mavenCentral()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
    }
    dependencies {
        classpath("net.fabricmc:fabric-loom:1.1-SNAPSHOT")
    }
}

*/

plugins {
    id("fabric-loom")
    `java-library`
}
applyPlatformAndCoreConfiguration()
applyShadowConfiguration()

configure<LoomGradleExtensionAPI> {
    accessWidenerPath.set(project.file("src/main/resources/worldedit.accesswidener"))
}


val minecraftVersion = "1.19.2"
val yarnMappings = "1.19.2+build.28"
val loaderVersion = "0.14.19"

configurations.all {
    resolutionStrategy {
        //   force("com.google.guava:guava:21.0")
    }
}
loom {
    runs {
        runs.create("testServer") {
            server()
            name("WorldEdit Server")
        }
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
}

dependencies {
    "api"(project(":worldedit-core"))
    //"implementation"("org.apache.logging.log4j:log4j-slf4j-impl:2.8.1")

    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    "mappings"(loom.officialMojangMappings())
    "modImplementation"("net.fabricmc:fabric-loader:$loaderVersion")

    api("org.xerial:sqlite-jdbc:3.7.2")
    implementation(libs.fastutil)
    compileOnly("net.kyori:adventure-api")
    implementation("org.yaml:snakeyaml")
    api(libs.lz4Java) { isTransitive = false }
    api("net.jpountz.lz4:lz4:1.0.0")
    api(libs.sparsebitset)
    api(libs.parallelgzip) { isTransitive = false }
    implementation(libs.zstd) { isTransitive = false }
    implementation("dev.notmyfault.serverlib:ServerLib")
    "modImplementation"("xyz.nucleoid:stimuli:0.4.1+1.19.1")
    "modImplementation"("maven.modrinth:starlight:1.1.1+1.19")

    // [1] declare fabric-api dependency...
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:0.76.0+1.19.2")
    /*
    // [2] Load the API dependencies from the fabric mod json...
    @Suppress("UNCHECKED_CAST")
    val fabricModJson = file("src/main/resources/fabric.mod.json").bufferedReader().use {
        groovy.json.JsonSlurper().parse(it) as Map<String, Map<String, *>>
    }
    val wantedDependencies = (fabricModJson["depends"] ?: error("no depends in fabric.mod.json")).keys
        .filter { it == "fabric-api-base" || it.contains(Regex("v\\d$")) }
        .map { "net.fabricmc.fabric-api:$it" }
    logger.lifecycle("Looking for these dependencies:")
    for (wantedDependency in wantedDependencies) {
        logger.lifecycle(wantedDependency)
    }
    // [3] and now we resolve it to pick out what we want :D
    val fabricApiDependencies = fabricApiConfiguration.incoming.resolutionResult.allDependencies
        .onEach {
            if (it is UnresolvedDependencyResult) {
                throw kotlin.IllegalStateException("Failed to resolve Fabric API", it.failure)
            }
        }
        .filterIsInstance<ResolvedDependencyResult>()
        // pick out transitive dependencies
        .flatMap {
            it.selected.dependencies
        }
        // grab the requested versions
        .map { it.requested }
        .filterIsInstance<ModuleComponentSelector>()
        // map to standard notation
        .associateByTo(
            mutableMapOf(),
            keySelector = { "${it.group}:${it.module}" },
            valueTransform = { "${it.group}:${it.module}:${it.version}" }
        )
    fabricApiDependencies.keys.retainAll(wantedDependencies)
    // sanity check
    for (wantedDep in wantedDependencies) {
        check(wantedDep in fabricApiDependencies) { "Fabric API library $wantedDep is missing!" }
    }

    fabricApiDependencies.values.forEach {
        "include"(it)
        "modImplementation"(it)
    }*/

    // No need for this at runtime
    "modCompileOnly"("me.lucko:fabric-permissions-api:0.1-SNAPSHOT")

    // Hook these up manually, because Fabric doesn't seem to quite do it properly.
    "compileOnly"("net.fabricmc:sponge-mixin:latest")
    "annotationProcessor"("net.fabricmc:sponge-mixin:latest")
    "annotationProcessor"("net.fabricmc:fabric-loom:1.0-SNAPSHOT")

}

configure<BasePluginConvention> {
    archivesBaseName = "$archivesBaseName-mc$minecraftVersion"
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
        include(dependency("org.xerial:sqlite-jdbc:3.7.2"))
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

tasks.register<RemapJarTask>("remapShadowJar") {
    val shadowJar = tasks.getByName<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    input.set(shadowJar.archiveFile)
    archiveFileName.set(shadowJar.archiveFileName.get().replace(Regex("-dev\\.jar$"), ".jar"))
    archiveFileName.set("${rootProject.name}-Fabric-${project.version}.${archiveExtension.getOrElse("jar")}")
    addNestedDependencies.set(true)
    //remapAccessWidener.set(true)
}

tasks.named("assemble").configure {
    dependsOn("remapShadowJar")
}

