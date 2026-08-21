plugins {
    id("fabric-loom")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

val mcVersion: String = stonecutter.current.version
val loaderVersion: String = property("deps.fabric_loader") as String
val fabricApiVersion: String = property("deps.fabric_api") as String
val flkVersion: String = property("deps.flk") as String
val loaderMin: String = property("deps.fabric_loader_min") as String
val flkMin: String = property("deps.flk_min") as String
val mcCompat: String = property("mod.mc_compat") as String
val imguiVersion: String = property("imgui_version") as String

val is26x: Boolean = mcVersion.substringBefore('.').toInt() >= 26
val javaVer: Int = if (is26x) 25 else 21

base {
    archivesName.set("panel-lib-mc$mcVersion")
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
}

dependencies {
    val modImpl = if (is26x) "implementation" else "modImplementation"

    minecraft("com.mojang:minecraft:$mcVersion")
    if (!is26x) {
        add("mappings", loom.officialMojangMappings())
    }
    add(modImpl, "net.fabricmc:fabric-loader:$loaderVersion")
    add(modImpl, "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    add(modImpl, "net.fabricmc:fabric-language-kotlin:$flkVersion")

    // Dear ImGui. Loom `include` is non-transitive: binding, backend and every native must be
    // listed. MC ships its own LWJGL suite, so exclude the whole org.lwjgl group from
    // imgui-java-lwjgl3 (its BOM would otherwise force-upgrade LWJGL and crash at runtime).
    // `api`: consumers call ImGui directly inside their panel render callbacks.
    include(api("io.github.spair:imgui-java-binding:$imguiVersion")!!)
    include(implementation("io.github.spair:imgui-java-lwjgl3:$imguiVersion") {
        exclude(group = "org.lwjgl")
    }!!)
    include(implementation("io.github.spair:imgui-java-natives-windows:$imguiVersion")!!)
    include(implementation("io.github.spair:imgui-java-natives-linux:$imguiVersion")!!)
    include(implementation("io.github.spair:imgui-java-natives-macos:$imguiVersion")!!)

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.named("testRuntimeClasspath") {
    exclude(group = "io.github.spair", module = "imgui-java-lwjgl3")
    exclude(group = "io.github.spair", module = "imgui-java-natives-windows")
    exclude(group = "io.github.spair", module = "imgui-java-natives-linux")
    exclude(group = "io.github.spair", module = "imgui-java-natives-macos")
    exclude(group = "org.lwjgl")
}

loom {
    splitEnvironmentSourceSets()
    mods {
        register("panellib") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
    runs {
        named("client") {
            client()
            configName = "Fabric Client ($mcVersion)"
            ideConfigGenerated(stonecutter.current.isActive)
            runDir("../../run")
        }
        named("server") {
            server()
            configName = "Fabric Server ($mcVersion)"
            ideConfigGenerated(stonecutter.current.isActive)
            runDir("../../run")
        }
    }
}

tasks.withType<ProcessResources>().configureEach {
    inputs.property("version", project.version)
    inputs.property("minecraft_compat", mcCompat)
    inputs.property("loader_min", loaderMin)
    inputs.property("flk_min", flkMin)
    inputs.property("java_version", javaVer)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_compat" to mcCompat,
            "loader_min" to loaderMin,
            "flk_min" to flkMin,
            "java_version" to javaVer.toString()
        )
    }
    filesMatching("*.mixins.json") {
        expand("mixin_java" to "JAVA_$javaVer")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVer)
    targetCompatibility = JavaVersion.toVersion(javaVer)
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVer)
    }
}

kotlin {
    jvmToolchain(javaVer)
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.named(if ("remapJar" in tasks.names) "remapJar" else "jar"))
    into(rootProject.layout.buildDirectory.dir("libs/${project.version}"))
    dependsOn("build")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Consumers depend on `dev.harrison:panel-lib-mc<mcVersion>:<version>` from mavenLocal.
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "panel-lib-mc$mcVersion"
            from(components["java"])
        }
    }
}
