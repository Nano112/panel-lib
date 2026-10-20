panel-lib 0.1.6

Adds Minecraft 26.3. That version replaced GLFW with SDL3, and imgui-java has no SDL3
platform backend, so panel-lib 0.1.6 feeds ImGui's input directly on 26.3:

- Window size, framebuffer scale and frame timing come from SDL.
- Mouse buttons, wheel, modifiers and keys map from SDL codes. Letter and digit keys
  follow the keyboard layout; other keys use their physical position.
- Hover and capture follow the game window's mouse focus. Cursor shapes use
  Minecraft's own cursors and are left alone while the mouse is grabbed.
- Clipboard text goes through Minecraft's keyboard handler.

Detached panel windows need ImGui's multi-viewport backend, which only exists for GLFW.
On 26.3 the external-windows setting is ignored and panels stay inside the game window.
Minecraft 1.21.8 through 26.2 keep the unchanged GLFW backend.

All seven Minecraft targets pass 105 tests and packaged-JAR verification. Packaged
Schematio Connector clients rendered panels and schematic previews on 26.3, 26.2 and
1.21.11, including 26.3 with Axiom 6.1.3, Litematica 0.29.1 and MaLiLib 0.30.2
installed. Scroll direction, IME input and borderless fullscreen on 26.3 have not
been checked by hand yet.

Mods can bundle 0.1.6, or players can install its matching standalone JAR alongside
mods bundling older panel-lib versions. Restart Minecraft to load the updated library.
Requires Fabric API and Fabric Language Kotlin. Java 21 for 1.21.x; Java 25 for 26.x.
