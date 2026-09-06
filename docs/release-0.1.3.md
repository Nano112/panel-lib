panel-lib 0.1.3

The toolbar now starts with the mods using it. The Panels wordmark and permanent
usage hint are removed; layout controls live under the window icon at the right.
Mod Menu identifies panel-lib as a library.

This release also preserves other editors' input callbacks and restores the game
viewport when an editor such as Axiom takes focus. Panels can release their work
through an onClose callback. Fabric Language Kotlin 1.13.12 is now the declared
minimum, matching the Kotlin compiler used to build the library.

All six Minecraft targets build and pass the panel lifecycle tests: 1.21.8,
1.21.9, 1.21.10, 1.21.11, 26.1 and 26.2. Each download is for the Minecraft version
in its filename. Connector bundles panel-lib; its users need no separate install.

AI assistance was used for code and release text.
