package dev.harrison.panellib.framework

import net.fabricmc.loader.api.FabricLoader

/** Observe a foreign editor; never toggle its state or install callbacks into it. */
object EditorOwnership {
    private val axiomActive by lazy {
        if (!FabricLoader.getInstance().isModLoaded("axiom")) null
        else runCatching {
            Class.forName("com.moulberry.axiom.editor.EditorUI").getMethod("isActive")
        }.onFailure { PanelLibLog.LOGGER.warn("Cannot inspect Axiom editor ownership", it) }.getOrNull()
    }

    @JvmStatic fun isForeignEditorActive(): Boolean =
        axiomActive?.let { runCatching { it.invoke(null) == true }.getOrDefault(true) } ?: false
}
