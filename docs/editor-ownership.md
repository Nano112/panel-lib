# Editor ownership

Panel-lib 0.1.2 suspends its overlay while Axiom's editor is active. Open panels retain their state and resume when Axiom closes. Axiom is optional; panel-lib observes `EditorUI.isActive()` without changing Axiom's state.

Each panel-lib frame and input callback selects its own ImGui context and restores the previous context afterwards. The library preserves the existing GLFW cursor-enter and monitor callbacks. Its GLFW backend polls cursor presence and monitor changes instead of replacing another mod's handlers.

Suspension clears captured input and hides secondary panel-lib windows. It leaves the main viewport's backend data intact so the overlay can resume. Key releases are consumed only when panel-lib captured the corresponding press. `PanelHandle.onClose` lets consumers cancel work and release resources on every close path.

## Verification

`./gradlew build publishToMavenLocal` passed on Minecraft 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1 and 26.2, with 12 unit tests passing per target.

Packaged in-game checks used Minecraft 26.2, Axiom 6.0.5, SchematioConnector and MCInspector on macOS. Four round trips between the two editors preserved the GLFW callback addresses and restored each editor's viewport dimensions. Closing Connector's preview composer released its source, decoder job and offscreen target.

The runtime checks caught two defects during development: sending mouse alias keys through `AddKeyEvent` caused an ImGui assertion, and calling `DestroyPlatformWindows` during suspension cleared the main viewport data needed on resume. The fixes use keyboard-only release events and hide secondary windows. The same handoff checks passed afterwards.

Runtime compatibility on the other five Minecraft targets has not been retested in this change. Connector's `axiom-poc/scripts/connector_checks.py` reproduces the in-game checks through MCInspector.
