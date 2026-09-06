# Modrinth project setup

Name: panel-lib

Summary: Shared dockable panels for Minecraft mods.

Project type: mod. Loader: Fabric. Client required, server unsupported. License:
MIT. Source and issues: https://github.com/Nano112/panel-lib.

Use the README's player installation section as the project description. List
Fabric API and Fabric Language Kotlin as required dependencies on every version.
Publish each Minecraft jar as a separate version, for example 0.1.3+mc26.2.
The Connector version already bundles this library.

Disclose AI-generated code and AI-generated text. The project's development uses
AI assistance; no claim about the proportion of original human-written code is
made here. Public eligibility must match the actual project history under
[Modrinth's content rules](https://modrinth.com/legal/rules). Use real screenshots
of panels in Minecraft for the gallery.

The GitHub release workflow publishes jars and the public Maven repository.
Modrinth publication additionally needs an owning account, a project ID and an
API token with version-create permission. Those are not configured in this repo.
