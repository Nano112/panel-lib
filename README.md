# panel-lib

One shared [Dear ImGui](https://github.com/ocornut/imgui) overlay for all your Fabric client mods: a single toolbar,
dockspace, theme, font/icon set and widget kit. Mods contribute panels, menu entries and keybinds through a small
API; the game itself is embedded in the leftover space so you can keep playing with panels open.

[![build](https://github.com/Nano112/panel-lib/actions/workflows/build.yml/badge.svg)](https://github.com/Nano112/panel-lib/actions/workflows/build.yml)
Minecraft 1.21.8 – 1.21.11, 26.1, 26.2 · Fabric · Kotlin · MIT

## Install (for players)

Mods that use panel-lib bundle it. To install it standalone, drop the jar for your Minecraft version from
[Releases](https://github.com/Nano112/panel-lib/releases) into `mods/` (needs Fabric API + Fabric Language Kotlin).

- **K** toggles the overlay · click the game area to play, **Esc** returns to the panels · **Layout ▾** resets the layout.
- `config/panellib.json`: `accent` (`#hex`), `font_size` (14), `embed_game`, `external_windows` (**experimental**, off:
  ImGui multi-viewport so panels dragged out of the game become OS windows — renders, but hover/layout in this mode still need work).

## Use it in your mod

```kotlin
// build.gradle.kts — GitHub Packages (token with read:packages) or mavenLocal after `publishToMavenLocal`
repositories {
    maven("https://maven.pkg.github.com/Nano112/panel-lib") {
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).get()
            password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).get()
        }
    }
}
dependencies {
    modImplementation("dev.harrison:panel-lib-mc$mcVersion:0.1.0")
    include("dev.harrison:panel-lib-mc$mcVersion:0.1.0")   // bundle it
}
```

```json
// fabric.mod.json
"entrypoints": { "panellib": [ { "adapter": "kotlin", "value": "my.mod.MyPanels" } ] },
"depends": { "panellib": "*" }
```

```kotlin
object MyPanels : PanelLibEntrypoint {
    override fun init(api: PanelLibApi) {
        val mod = api.registerMod("mymod", "My Mod", Icons.CUBE)
        val stats = mod.panel("stats", "Stats", Icons.TABLE) {      // panel-lib owns the window; you draw the content
            Widgets.kvRow("Entities", world.entityCount.toString())
            if (Widgets.primaryButton("Refresh")) refresh()
        }
        mod.menuItem("Reset", Icons.REFRESH) { reset() }
        mod.keybind("stats", GLFW.GLFW_KEY_J) { stats.toggle() }   // lang key: key.mymod.stats
        api.setTheme(Themes.withAccent(api.theme, "#FF5DA2"))       // optional, global
    }
}
```

`ImGui.*` is available inside render callbacks. Widgets: `primary/secondary/ghost/dangerButton`, `h1/h2`, `kvRow`,
`badge`, `dot`, `statusText`, `emptyState`, `textField`, `tabBar`, `table`; `api.confirm(...)` for modals;
`api.registerIcons(...)` for extra Font Awesome glyphs.

## How it works

- Input is forwarded to ImGui only while the overlay owns it (text input active / hovering a window); game hotkeys
  keep working. A vanilla screen suspends the overlay.
- Rendering hooks the present call (`RenderSystem.flipFrame` / `GlSurface.present`) with a core-profile-safe GL3
  renderer that restores every GL state it touches (Sodium/Iris safe).
- **Embedded game**: Minecraft is given the central dock node's size through its own resize path
  (`Window.onFramebufferResize`) and the frame is composited into that rectangle — correct aspect, GUI scale, HUD.

## Build

```bash
./gradlew :fabric:1.21.11:build           # one version + tests
./gradlew :fabric:buildAllVersions        # all versions → build/libs/<version>/
./gradlew publishToMavenLocal -x test     # for local consumers
```

Sources are written for the active version (1.21.11); other versions use Stonecutter `//? if` splits.
CI builds every version on push; tagging `v*` publishes jars to Releases and Maven artifacts to GitHub Packages.
