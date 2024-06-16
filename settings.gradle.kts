rootProject.name = "FastAsyncWorldEdit"

include("worldedit-libs")
/*
listOf("legacy", "1_19", "1_19_3","1_19_4").forEach {
    include("worldedit-bukkit:adapters:adapter-$it")
}
*/
listOf("core", "cli", "fabric").forEach {
    include("worldedit-libs:$it")
    include("worldedit-$it")
}
include("worldedit-libs:core:ap")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "EngineHub"
            url = uri("https://maven.enginehub.org/repo/")
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
