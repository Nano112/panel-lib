package dev.harrison.panellib.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/** `config/panellib.json`: `{ "accent": "#5B8DEF" | null, "font_size": 17, "embed_game": true }`. */
data class PanelLibConfig(val accent: String? = null, val fontSize: Float = 17f, val embedGame: Boolean = true) {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

        fun load(path: Path): PanelLibConfig {
            if (!Files.exists(path)) {
                Files.createDirectories(path.parent)
                Files.writeString(path, PanelLibConfig().toJson())
                return PanelLibConfig()
            }
            return parse(Files.readString(path))
        }

        fun parse(json: String): PanelLibConfig {
            val o = JsonParser.parseString(json).asJsonObject
            return PanelLibConfig(
                accent = o.get("accent")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() },
                fontSize = o.get("font_size")?.takeUnless { it.isJsonNull }?.asFloat ?: 17f,
                embedGame = o.get("embed_game")?.takeUnless { it.isJsonNull }?.asBoolean ?: true,
            )
        }
    }

    fun toJson(): String = gson.toJson(JsonObject().apply { addProperty("accent", accent); addProperty("font_size", fontSize); addProperty("embed_game", embedGame) })
}
