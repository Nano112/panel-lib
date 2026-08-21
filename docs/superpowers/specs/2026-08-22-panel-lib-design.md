# panel-lib — design (sub-project 2 of the MC-Inspector program)

Date: 2026-08-22 · Status: building

## What

A standalone Fabric client library mod (`panellib`, `dev.harrison.panellib`, Kotlin) that owns ONE Dear ImGui
overlay for the whole client. Any mod contributes panels, menu entries and keybinds into it; every contributor
shares the same toolbar, dockspace, theme, fonts and icons, so "all my mods merge into the same UI".

Extracted from SchematioConnector's `ui/{framework,theme,widgets}` + input/present mixins (the proven Apple-GL-safe
renderer, Sodium/Iris-safe state restore, flipFrame/GlSurface present hooks, version-split input mixins), with the
schemat.io-specific bits removed and a new neutral, modern default theme.

## Decisions

| Topic | Decision |
|---|---|
| Versions | Stonecutter 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.2; active 1.21.11 (same as Schematio / MC-Inspector) |
| Attach | Fabric entrypoint `"panellib"` → `PanelLibEntrypoint.init(api: PanelLibApi)`; the same API object is also reachable statically (`PanelLib.api()`) after init |
| Overlay | One global toggle key (default **K**, rebindable, category "panel-lib"); per-mod keybinds registered through the API open the overlay to a panel |
| Toolbar | Wordmark `▣ Panels` · one `<Mod> ▾` menu per contributor (its panels as checkable items + custom menu items) · right-aligned `Layout ▾` (reset layout, close all) |
| Dock | Transparent pass-through central node (game visible + clickable); panels dock to edges; layout persisted in `panellib-imgui.ini`; fresh ini → default right split 38% |
| Theme | `Theme` data class of tokens (ImVec4) + metrics; `Themes.DEFAULT` = new neutral dark theme; override via `PanelLibApi.setTheme(Theme)` or `Theme.copy(accent=…)`; config `config/panellib.json` can set `accent` hex |
| Fonts/icons | Inter Regular/SemiBold 17/20/24 + Font Awesome 6 solid (curated glyph set, extendable by `api.registerIcons(codepoints)` before first frame) |
| Widgets | `Widgets` (buttons, headings, badge, empty state, status, text field, tab bar), `Anim`, `ConfirmModal` |
| Input | Mixins on `KeyboardHandler`/`MouseHandler` forward to ImGui only while the overlay is focused AND ImGui wants capture (game hotkeys/movement keep working otherwise); Escape closes top panel then overlay |
| Renderer | Custom GL3 renderer (from Schematio) at `RenderSystem.flipFrame` HEAD (<26.2) / `GlSurface.present` HEAD (26.2) |
| Deps | `io.github.spair:imgui-java-{binding,lwjgl3,natives-*}:1.89.0` bundled via `include` (lwjgl group excluded); consumers only `modImplementation` panel-lib |
| Publishing | `publishToMavenLocal` as `dev.harrison:panel-lib-mc<mcVersion>:<version>`; MC-Inspector consumes from mavenLocal and `include`s it |

## Public API (`dev.harrison.panellib.api`)

```kotlin
fun interface PanelLibEntrypoint { fun init(api: PanelLibApi) }

interface PanelLibApi {
    fun registerMod(modId: String, displayName: String, icon: String? = null): ModHandle
    fun setTheme(theme: Theme)
    val theme: Theme
    fun registerIcons(codepoints: Collection<Int>)      // before first frame; merged into the atlas
    fun openOverlay(); fun closeOverlay(); fun toggleOverlay(); val isOverlayOpen: Boolean
    fun confirm(title: String, message: String, confirmLabel: String = "Confirm", danger: Boolean = false, onConfirm: () -> Unit)
}

interface ModHandle {
    val modId: String
    fun panel(id: String, title: String, icon: String? = null, flags: Int = 0, render: () -> Unit): PanelHandle
    fun menuItem(label: String, icon: String? = null, action: () -> Unit)
    fun menuSeparator()
    fun keybind(name: String, defaultKey: Int, action: () -> Unit): KeyMapping  // translation key key.<modId>.<name>
}

interface PanelHandle {
    val id: String          // "<modId>:<id>" — unique across mods, stable for imgui.ini
    val title: String
    fun open(); fun close(); fun toggle(); val isOpen: Boolean
}
```

Panels are rendered by panel-lib: `ImGui.begin("$title###$id", pOpen, flags)` then the consumer's `render()`;
closing via the title-bar X closes the panel. Consumers never call begin/end for their panel window.

## Module layout (`../panel-lib`)

```
settings.gradle.kts, build.gradle.kts, gradle.properties, fabric/{stonecutter.gradle.kts,build.gradle.kts,versions/*}
fabric/src/main/resources/fabric.mod.json                     (client env, entrypoint panellib client init)
fabric/src/client/resources/panellib.client.mixins.json
fabric/src/client/resources/assets/panellib/fonts/{Inter-Regular,Inter-SemiBold,fa-solid-900}.ttf
fabric/src/client/resources/assets/panellib/lang/en_us.json   (keybind names)
fabric/src/client/java/dev/harrison/panellib/mixin/{KeyboardMixin,MouseMixin,RenderSystemMixin,GlSurfaceMixin}.java
fabric/src/client/kotlin/dev/harrison/panellib/
  PanelLibClient.kt            ClientModInitializer: config, keybinds, entrypoints → api, tick (consumeClick), shutdown
  PanelLib.kt                  static access: api()
  api/{PanelLibApi,PanelLibEntrypoint,ModHandle,PanelHandle}.kt
  core/{Registry.kt (mods/panels/menu items), PanelLibApiImpl.kt, Keybinds.kt, PanelLibConfig.kt}
  framework/{ImGuiManager,ImGuiGl3Renderer,Overlay,DockHost,Toolbar,PanelManager}.kt
  theme/{Theme.kt, Themes.kt (DEFAULT), ThemeApplier.kt (push/pop all ImGuiCol), Fonts.kt, Icons.kt}
  widgets/{Widgets,Anim,ConfirmModal}.kt
fabric/src/test/kotlin/...    PanelManagerTest, RegistryTest, ThemeApplierTest (every ImGuiCol slot mapped), IconsTest, AnimTest
```

## Default theme ("Graphite")

Neutral dark surfaces with one cool accent; VS Code / Linear feel. Tokens (ARGB):
bg `#0F1115`, surface `#161920`, surfaceAlt `#1C2029`, surfaceHover `#242A36`, surfaceRaised `#222834`,
border `#2B3240`, borderSubtle `#1F2530`, text `#E6E8EE`, textSecondary `#A6ADBB`, textMuted `#7B8494`, textFaint `#4F5868`,
accent `#5B8DEF`, accentHover `#7AA5FF`, accentDim `#2A4475`, accentMuted `#405B8DEF`,
success `#3FB950`, danger `#F06A6A`, warning `#D9A421`, info `#58A6FF`, scrim `#A00B0D12`, stripe `#06FFFFFF`.
Metrics: window padding 12, frame padding 9×6, item spacing 8×6, rounding 6/5/4, border 1px, no frame borders
(inputs re-enable), scrollbar 10.

## Dogfood

MC-Inspector registers a contributor "Inspector" with an **MCP Status** panel (port, tool-call count, last tool,
uptime) and a keybind; the agent opens the overlay via `press_key k`, screenshots it, clicks panel-lib widgets via
`mouse_click`, and verifies through `read_logs`. This is the validation for this sub-project.

## Out of scope

Multi-viewport, theme hot-reload UI, a settings panel for panel-lib itself (later), migrating Schematio.
