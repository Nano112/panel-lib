package dev.harrison.panellib

import dev.harrison.panellib.api.PanelLibApi

/** Static access to the API for code that runs after init (the entrypoint receives the same object). */
object PanelLib {
    @Volatile internal var instance: PanelLibApi? = null

    @JvmStatic fun api(): PanelLibApi = instance ?: error("panel-lib is not initialised yet (use the 'panellib' entrypoint)")
    @JvmStatic fun isReady(): Boolean = instance != null
}
