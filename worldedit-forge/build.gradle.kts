import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

project.description = "Forge"

buildscript {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
    dependencies {
        classpath("net.minecraftforge.gradle:ForgeGradle:5.1.+") {
            isChanging = true
        }

    }
}

apply {
    plugin("net.minecraftforge.gradle")
}


plugins {
    `java-library`
}

applyPlatformAndCoreConfiguration()
applyShadowConfiguration()

val minecraftVersion = "1.18.2"
val nextMajorMinecraftVersion: String = minecraftVersion.split('.').let { (useless, major) ->
    "$useless.${major.toInt() + 1}"
}
val mappingsMinecraftVersion = "1.18.2"
val forgeVersion = "40.1.0"

configurations.all {
    resolutionStrategy {
        //force("com.google.guava:guava:21.0")
    }
}

repositories {
    maven {
        name = "PaperMC"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "OSS Sonatype Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "EngineHub"
        url = uri("https://maven.enginehub.org/repo/")
    }
}

dependencies {
    api(projects.worldeditCore)
    api(projects.worldeditLibs.forge)
    implementation(libs.fastutil)
    implementation("io.papermc.paper:paper-api") {
        exclude("junit", "junit")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    compileOnly("net.kyori:adventure-api")
    implementation("org.yaml:snakeyaml")
    api(libs.lz4Java) {isTransitive = false}
    api("net.jpountz.lz4:lz4:1.0.0")
    api(libs.parallelgzip) { isTransitive = false }
    implementation(libs.zstd) { isTransitive = false }
    implementation("dev.notmyfault.serverlib:ServerLib")

    "minecraft"("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")
}


val apiClasspath = configurations.create("apiClasspath") {
    isCanBeResolved = true
    extendsFrom(configurations.api.get())
}

configure<net.minecraftforge.gradle.userdev.UserDevExtension> {
    mappings(mapOf(
            "channel" to "official",
            "version" to mappingsMinecraftVersion
    ))

    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        val runConfig = Action<net.minecraftforge.gradle.common.util.RunConfig> {
            properties(mapOf(
                    "forge.logging.markers" to "SCAN,REGISTRIES,REGISTRYDUMP",
                    "forge.logging.console.level" to "debug"
            ))
            workingDirectory = project.file("run").canonicalPath
            source(sourceSets["main"])
            lazyToken("minecraft_classpath") {
                apiClasspath.resolve().joinToString(File.pathSeparator) { it.absolutePath }
            }
        }
        create("client", runConfig)
        create("server", runConfig)
    }

}

configure<BasePluginConvention> {
    archivesBaseName = "$archivesBaseName-mc$minecraftVersion"
}

tasks.named<Copy>("processResources") {
    // this will ensure that this task is redone when the versions change.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val properties = mapOf(
            "version" to project.ext["internalVersion"],
            "forgeVersion" to forgeVersion,
            "minecraftVersion" to minecraftVersion,
            "nextMajorMinecraftVersion" to nextMajorMinecraftVersion
    )
    properties.forEach { (key, value) ->
        inputs.property(key, value)
    }

    // replace stuff in mcmod.info, nothing else
    from(sourceSets["main"].resources.srcDirs) {
         include("META-INF/mods.toml")

         // replace version and mcversion
         expand(properties)
     }

    // copy everything else except the mcmod.info

    from(sourceSets["main"].resources.srcDirs) {
        exclude("META-INF/mods.toml")
    }

    // copy from -core resources as well
  // from(project(":worldedit-core").tasks.named("processResources"))
}

addJarManifest(WorldEditKind.Mod, includeClasspath = false)

tasks.named<ShadowJar>("shadowJar") {

    archiveFileName.set("${rootProject.name}-Forge-${project.version}.${archiveExtension.getOrElse("jar")}")

    dependencies {
        relocate("org.antlr.v4", "com.sk89q.worldedit.antlr4")
        include(dependency(":worldedit-core"))
        include(dependency(":worldedit-libs:forge"))
        include(dependency("io.papermc.paper:paper-api"))
        include(dependency("org.antlr:antlr4-runtime"))
        include(dependency("org.mozilla:rhino-runtime"))
        relocate("org.anarres", "com.fastasyncworldedit.core.internal.io") {
            include(dependency("org.anarres:parallelgzip:1.0.5"))
        }
        include(dependency("org.yaml:snakeyaml"))
        include(dependency("com.github.luben:zstd-jni"))
        include(dependency("dev.notmyfault.serverlib:ServerLib:2.3.1"))
        include(dependency("io.papermc:paperlib"))
        relocate("net.kyori", "com.fastasyncworldedit.core.adventure") {
            include(dependency("net.kyori:adventure-nbt:4.9.3"))
        }
        relocate("org.lz4", "com.fastasyncworldedit.core.lz4") {
            include(dependency("org.lz4:lz4-java:1.8.0"))
        }
    }
    minimize {
        exclude(dependency("org.mozilla:rhino-runtime"))
    }
}
afterEvaluate {
    val reobf = extensions.getByName<NamedDomainObjectContainer<net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace>>("reobf")
    reobf.maybeCreate("shadowJar").run {
        mappings.set(tasks.getByName<net.minecraftforge.gradle.mcp.tasks.GenerateSRG>("createMcpToSrg").output)
    }
}

tasks.register<Jar>("deobfJar") {
    from(sourceSets["main"].output)
    archiveClassifier.set("dev")
}

artifacts {
    add("archives", tasks.named("deobfJar"))
}
