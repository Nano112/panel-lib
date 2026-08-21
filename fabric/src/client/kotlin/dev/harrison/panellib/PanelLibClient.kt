package dev.harrison.panellib

import dev.harrison.panellib.api.PanelLibEntrypoint
import dev.harrison.panellib.core.Keybinds
import dev.harrison.panellib.core.PanelLibApiImpl
import dev.harrison.panellib.core.PanelLibConfig
import dev.harrison.panellib.core.Registry
import dev.harrison.panellib.framework.ImGuiManager
import dev.harrison.panellib.framework.PanelManager
import dev.harrison.panellib.framework.Toolbar
import dev.harrison.panellib.theme.Theme
import dev.harrison.panellib.theme.Themes
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

object PanelLibClient : ClientModInitializer {
    const val MOD_ID = "panellib"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitializeClient() {
        val loader = FabricLoader.getInstance()
        val config = PanelLibConfig.load(loader.configDir.resolve("panellib.json"))
        var theme = Themes.DEFAULT.copy(fontSize = config.fontSize)
        config.accent?.let { runCatching { theme = Themes.withAccent(theme, it) }.onFailure { e -> LOGGER.warn("[panel-lib] bad accent in config: {}", e.message) } }
        Theme.current = theme

        Keybinds.registerBuiltins()
        val registry = Registry(PanelManager, Keybinds)
        Toolbar.bind(registry)
        val api = PanelLibApiImpl(registry)
        PanelLib.instance = api

        // Let every mod contribute. Entrypoints run now so icons/panels exist before the first frame.
        for (container in loader.getEntrypointContainers("panellib", PanelLibEntrypoint::class.java)) {
            val id = container.provider.metadata.id
            try {
                container.entrypoint.init(api)
            } catch (t: Throwable) {
                LOGGER.error("[panel-lib] entrypoint of '{}' failed", id, t)
            }
        }
        LOGGER.info("[panel-lib] ready: {} mod(s), {} panel(s)", registry.mods().size, registry.panels().size)

        ClientTickEvents.END_CLIENT_TICK.register { Keybinds.handleInput() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { ImGuiManager.shutdown() }
    }
}
