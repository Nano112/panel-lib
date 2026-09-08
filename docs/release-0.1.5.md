panel-lib 0.1.5

Fixes toolbar and panel clicks returning focus to Minecraft after Escape, including
clients sharing the overlay between MC-Inspector and Schematio.

- Cursor enter/leave notifications now run only when presence changes. Repeating an
  enter event every frame replayed the GLFW backend's stale cursor position.
- Current cursor events are queued before ImGui begins the frame, so hover/capture
  and rendered controls agree about where the cursor is.
- Cursor forwarding updates the backend position and preserves desktop coordinates
  for detached windows. Synthetic input also identifies the main viewport.
- Game focus releases ImGui mouse buttons through its input queue.

Three native ImGui regression tests cover game-to-panel hover, returning from game
focus, and held-button release. They fail with the previous input ordering. All six
Minecraft targets pass 90 tests and packaged-JAR verification. A live 26.2 client with
MC-Inspector 0.1.1 and Schematio Connector 1.3.4 passed ten consecutive Escape-to-menu
cycles, alternating between both dropdowns.

Mods can bundle 0.1.5, or players can install its matching standalone JAR alongside
mods bundling older panel-lib versions. Restart Minecraft to load the updated library.
Requires Fabric API and Fabric Language Kotlin. Java 21 for 1.21.x; Java 25 for 26.x.
