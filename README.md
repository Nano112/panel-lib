# panel-lib

One shared Dear ImGui overlay for all your Fabric client mods: a single toolbar, dockspace, theme, font/icon
set and widget kit. Each mod contributes panels, menu entries and keybinds through a tiny API; everything
renders into the same workspace and persists its layout in `panellib-imgui.ini`.

Supported: Minecraft 1.21.8 – 1.21.11, 26.1, 26.2 (Stonecutter). Kotlin, Fabric Language Kotlin, imgui-java 1.89.0.
Extracted from SchematioConnector's overlay (Apple-GL-safe renderer, Sodium/Iris-safe GL state restore).

## Using it from a mod

`build.gradle.kts` (artifact published to mavenLocal: `./gradlew publishToMavenLocal` in this repo):

```kotlin
repositories { mavenLocal() }
dependencies {
    modImplementation("dev.harrison:panel-lib-mc$mcVersion:0.1.0")
    include("dev.harrison:panel-lib-mc$mcVersion:0.1.0")   // bundle so users only install your mod
}
```

`fabric.mod.json`:

```json
"entrypoints": { "panellib": [ { "adapter": "kotlin", "value": "my.mod.MyPanels" } ] },
"depends": { "panellib": "*" }
```

```kotlin
object MyPanels : PanelLibEntrypoint {
    override fun init(api: PanelLibApi) {
        val mod = api.registerMod("mymod", "My Mod", Icons.CUBE)
        val stats = mod.panel("stats", "Stats", Icons.TABLE) {
            Widgets.kvRow("Entities", world.entityCount.toString())
            if (Widgets.primaryButton("Refresh")) refresh()
        }
        mod.menuItem("Reset counters", Icons.REFRESH) { reset() }
        mod.keybind("stats", GLFW.GLFW_KEY_J) { stats.toggle() }   // lang: key.mymod.stats
    }
}
```

panel-lib owns the window (`ImGui.begin/end`, close button, docking) — the render lambda draws only the
content. Panel ids become `<modId>:<id>` and are stable keys for the saved layout. Consumers may call
`ImGui.*` directly inside render callbacks (the binding is an `api` dependency).

## Overlay behaviour

- **K** toggles the overlay (rebindable, category "panel-lib"). Opening any panel also shows the overlay.
- The centre of the dockspace is pass-through: the game stays visible and clickable; panels dock to the edges.
- Escape closes the topmost panel, then hides the overlay.
- Keys reach ImGui only while a text input is active or a modal is open, so game hotkeys keep working.
- While a vanilla screen is open the overlay suspends (input and cursor go to Minecraft) and resumes after.
- The cursor position ImGui uses is Minecraft's (`MouseHandler`), so synthetic input injected at that level
  (automation such as MC-Inspector's MCP host) drives the overlay too.

## Theme

`Themes.GRAPHITE` is the default: neutral cool-dark surfaces, 1px borders, one blue accent used only for
interactive emphasis. Override globally:

```kotlin
api.setTheme(Themes.withAccent(Themes.DEFAULT, "#FF5DA2"))        // derived hover/dim/muted
api.setTheme(api.theme.copy(fontSize = 19f, windowRounding = 8f))  // any token
```

Users can also set `"accent": "#hex"` and `"font_size"` in `config/panellib.json`.

Widgets (`dev.harrison.panellib.widgets.Widgets`): `primaryButton`, `secondaryButton`, `ghostButton`,
`dangerButton`, `iconButton`, `toggleButton`, `h1/h2/semibold/muted/faint/label`, `kvRow`, `badge`, `dot`,
`statusText`, `emptyState`, `textField`, `tabBar`, `table`; plus `ConfirmModal` via `api.confirm(...)`.
Icons: `Icons.*` (Font Awesome 6 Solid subset) — add more codepoints with `api.registerIcons(...)` in your entrypoint.

## Build

```bash
./gradlew :fabric:1.21.11:build          # one version + tests
./gradlew :fabric:buildAllVersions       # every version -> build/libs/<version>/
./gradlew publishToMavenLocal -x test    # for consumers
```

Consumers that re-publish the same version must evict Loom's remapped copy
(`rm -rf .gradle/loom-cache/remapped_mods/remapped/dev/harrison/panel-lib-mc*`) — see MC-Inspector's
`scripts/refresh-panellib.sh`.
