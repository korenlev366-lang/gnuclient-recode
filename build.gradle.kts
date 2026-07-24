import org.apache.commons.lang3.SystemUtils
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    java
    id("org.jetbrains.kotlin.jvm")
    id("gg.essential.loom") version "0.10.0.5"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val modid: String by project

/** Sibling checkout: `linux minecraft thing/ViaForgePlus`. */
val viaForgePlusRoot: File = rootDir.resolve("../ViaForgePlus")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

kotlin {
    jvmToolchain(8)
}

loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.gnuclient.json")
        mixinConfig("viaforgeplus.forge.mixins.json")
        // ViaForgePlus SRG access transformer (also FMLAT in jar manifest).
        accessTransformer(viaForgePlusRoot.resolve("src/main/resources/viaforgeplus_at.cfg"))
    }
    mixin {
        defaultRefmapName.set("mixins.gnuclient.refmap.json")
    }
}

sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
    java {
        srcDir(viaForgePlusRoot.resolve("src/main/java"))
    }
}

kotlin.sourceSets.getByName("main").kotlin {
    srcDir(viaForgePlusRoot.resolve("src/main/java"))
}

repositories {
    mavenCentral()
    maven("https://repo.polyfrost.cc/releases/")
    maven("https://repo.viaversion.com")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
        exclude(module = "gson")
        exclude(module = "guava")
        exclude(module = "jarjar")
        exclude(module = "commons-codec")
        exclude(module = "commons-io")
        exclude(module = "launchwrapper")
        exclude(module = "asm-commons")
        exclude(module = "slf4j-api")
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")

    // ViaForgePlus downgraded Via* jars + runtime deps (shaded into one inject).
    val viaLibs = viaForgePlusRoot.resolve("libs")
    require(viaLibs.isDirectory) {
        "ViaForgePlus libs missing at ${viaLibs.absolutePath} — clone/build ViaForgePlus next to gnuclient recode"
    }
    viaLibs.listFiles()?.filter { it.extension == "jar" }?.forEach { jar ->
        shadowImpl(files(jar))
    }
    shadowImpl("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23")
    shadowImpl("com.squareup.okhttp3:okhttp:4.9.1")
    shadowImpl("org.yaml:snakeyaml:2.2")
    shadowImpl("org.slf4j:slf4j-api:2.0.7")
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
    }
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(modid)
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        // Both mixin configs; do not set ViaForgePlus MixinLoader as FMLCorePlugin
        // (MixinTweaker already bootstraps Mixin — double-init breaks load).
        this["MixinConfigs"] = "mixins.gnuclient.json,viaforgeplus.forge.mixins.json"
        this["FMLAT"] = "viaforgeplus_at.cfg"
        this["ModSide"] = "CLIENT"
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("basePackage", baseGroup)
    // ViaForgePlus assets / AT — skip their mcmod + mixin json (gnuclient owns those).
    from(viaForgePlusRoot.resolve("src/main/resources")) {
        exclude("mcmod.info")
        exclude("viaforgeplus.forge.mixins.json")
    }
    filesMatching(listOf("mcmod.info", "mixins.gnuclient.json")) {
        expand(inputs.properties)
    }
    // Forge loads access transformers from META-INF/<name>.
    filesMatching("viaforgeplus_at.cfg") {
        path = "META-INF/viaforgeplus_at.cfg"
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude(
        "dummyThing",
        "LICENSE.txt",
        "META-INF/MUMFREY.RSA",
        "META-INF/maven/**",
        "org/**/*.html",
        "LICENSE.md",
        "pack.mcmeta",
        "**/module-info.class",
        "*.so",
        "*.dylib",
        "*.dll",
        "*.jnilib",
        "ibxm/**",
        "com/jcraft/**",
        "org/lwjgl/**",
        "net/java/**",
        "META-INF/proguard/**",
        "META-INF/versions/**",
        "META-INF/com.android.tools/**",
        "fabric.mod.json",
        "plugin.yml",
        "velocity-plugin.json"
    )
}

tasks.assemble.get().dependsOn(tasks.remapJar)
