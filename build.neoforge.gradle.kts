plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("neoforge-mutex")
}

version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = "${sc.properties["mod.file"]}-neoforge"

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // Unit tests cover the plain-Java core (flight plan, patterns, budget, packing, validator, config)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val modId = sc.properties["mod.id"]

neoForge {
    version = sc.properties["deps.neo_loader"]

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            gameDirectory = rootProject.file("run/${sc.current.project}/client")
            client()
            // `-PquickPlay=localhost:25600` joins a server straight away, `-PmcUser=Name` picks the offline name
            providers.gradleProperty("quickPlay").orNull?.let { programArguments.addAll("--quickPlayMultiplayer", it) }
            providers.gradleProperty("mcUser").orNull?.let { programArguments.addAll("--username", it) }
            // `-PclientSize=1920x1080` sets the window size (screenshots, video capture)
            providers.gradleProperty("clientSize").orNull?.split("x")?.let { programArguments.addAll("--width", it[0], "--height", it[1]) }
        }

        // `./gradlew runDemo`: makes a fresh world, calls one raid in view, saves screenshots and quits (CI pictures)
        register("demo") {
            gameDirectory = rootProject.file("run/${sc.current.project}/demo")
            client()
            systemProperty("farmstead_bombrun.demo", "true")
            programArguments.addAll("--width", "1280", "--height", "720", "--username", "BombRunDemo")
        }

        register("server") {
            gameDirectory = rootProject.file("run/${sc.current.project}/server")
            server()
            programArguments.add("--nogui") // the Swing console window crashes under heavy RCON/log traffic
        }

        // `./gradlew runGameTestServer`: runs the mod's GameTests headless and exits (PLAN.md §10)
        register("gameTestServer") {
            gameDirectory = rootProject.file("run/${sc.current.project}/gametest")
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
    }
}

// Loader-specific code lives in `<package>/platform/<loader>/`; other loaders' packages are left out
sourceSets.main {
    java {
        exclude("**/platform/fabric/**")
        exclude("**/platform/forge/**")
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        fun MutableMap<String, String>.register(key: String, value: String) {
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", sc.properties["mod.id"])
            register("name", sc.properties["mod.name"])
            register("homepage", sc.properties["mod.homepage"])
            register("sources", sc.properties["mod.sources"])
            register("issues", sc.properties["mod.issues"])
            register("version", sc.properties["mod.version"])
            register("minecraft", sc.properties["mod.mc_compat"])
            register("icon_key", if (sc.current.parsed >= "26.1") "iconFile" else "logoFile")
        }

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }

        exclude("fabric.mod.json", "META-INF/mods.toml")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    // Includes the license file in the built mod
    withType<Jar> {
        inputs.property("mod_id", modId)
        from("../../LICENSE") { rename { "${it}_$modId" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", sc.properties["mod.version"])
        from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${sc.properties["mod.version"]}"))
    }
}
