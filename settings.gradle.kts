pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // Same Stonecutter setup as farmstead-footprints: one source tree, one subproject per Minecraft version and loader
    id("dev.kikugie.stonecutter") version "0.10"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /**
         * Creates `versions/{project}-{loader}` subprojects, each built by `build.{loader}.gradle.kts`.
         * M1 targets only NeoForge 1.21.1 (the capture server of the reels); Fabric 1.21.1 comes in M3,
         * the rest of the Footprints version set in M4 (PLAN.md §4, §12).
         */
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        match("1.21.1", "neoforge")
        vcsVersion = "1.21.1-neoforge"
    }
}

rootProject.name = "farmstead-bombrun"
