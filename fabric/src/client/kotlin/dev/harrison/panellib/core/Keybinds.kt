package dev.harrison.panellib.core

import com.mojang.blaze3d.platform.InputConstants
import dev.harrison.panellib.framework.Overlay
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
*///?} else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
//?}
import net.minecraft.client.KeyMapping
//? if >=1.21.9 {
import net.minecraft.resources.Identifier
//?}
import org.lwjgl.glfw.GLFW

/** All key mappings: the overlay toggle plus everything consumers register through the API. */
object Keybinds : KeybindRegistrar {
    //? if >=1.21.9 {
    private val CATEGORY: KeyMapping.Category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("panellib", "main"))
    //?}

    lateinit var overlay: KeyMapping; private set
    private val actions = ArrayList<Pair<KeyMapping, () -> Unit>>()

    fun registerBuiltins(overlayKey: Int = GLFW.GLFW_KEY_K) {
        overlay = bind("key.panellib.overlay", overlayKey)
    }

    override fun register(modId: String, name: String, defaultKey: Int, action: () -> Unit): KeyMapping {
        val km = bind("key.$modId.$name", defaultKey)
        actions += km to action
        return km
    }

    /** Drain presses. Call every client tick. */
    fun handleInput() {
        while (overlay.consumeClick()) Overlay.toggle()
        for ((km, action) in actions) while (km.consumeClick()) {
            dev.harrison.panellib.framework.PanelLibLog.LOGGER.debug("[panel-lib] keybind {} fired", km.name)
            action()
        }
    }

    private fun bind(translationKey: String, defaultKey: Int): KeyMapping {
        val mapping = KeyMapping(
            translationKey, InputConstants.Type.KEYSYM, defaultKey,
            //? if >=1.21.9 {
            CATEGORY
            //?} else {
            /*"key.category.panellib"
            *///?}
        )
        //? if >=26.1 {
        /*return KeyMappingHelper.registerKeyMapping(mapping)
        *///?} else {
        return KeyBindingHelper.registerKeyBinding(mapping)
        //?}
    }
}
