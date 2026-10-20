plugins {
    id("dev.kikugie.stonecutter")
}

// The version the working tree is currently switched to.
// Change with: ./gradlew "Set active project to <version>"  (group: stonecutter)
stonecutter active "1.21.11"

val buildableVersions = listOf("1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1", "26.2", "26.3")
tasks.register("buildAllVersions") {
    group = "build"
    description = "Builds the fabric jar for every supported Minecraft version."
    buildableVersions.forEach { dependsOn(":fabric:$it:buildAndCollect") }
}

// `./gradlew :fabric:runClient` runs ONLY the active version (a bare `runClient`
// would otherwise name-match every per-version subproject and launch them all).
val activeVersion: String = (stonecutter.current ?: stonecutter.vcsVersion).project
tasks.register("runClient") {
    group = "fabric"
    description = "Runs the active Minecraft version's client ($activeVersion)."
    dependsOn(":fabric:$activeVersion:runClient")
}

stonecutter parameters {
    replacements {
        // Mojang mappings renamed ResourceLocation -> Identifier in 1.21.11.
        // Sources are kept in 1.21.11+ form; Stonecutter swaps back for older builds.
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
        // 26.3 moved the render backend out of blaze3d into com.mojang.renderpearl.
        string(current.parsed >= "26.3") {
            replace("com.mojang.blaze3d.opengl.GlSurface", "com.mojang.renderpearl.backend.opengl.GlSurface")
            // SDL3 key mappings: KEYSYM became KEYBOARD (key codes are now SDL scancodes).
            replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
        }
    }
}
