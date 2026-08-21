package dev.harrison.panellib.theme

/**
 * Curated Font Awesome 6 Free Solid glyphs (PUA codepoints, stable within FA6). Usage:
 * `"${Icons.SEARCH}  Search"`. Consumers may add more via [register] BEFORE the first frame
 * (i.e. from their `panellib` entrypoint); the atlas rasterises exactly the registered set.
 */
object Icons {
    const val SEARCH = "\uf002"; const val FOLDER = "\uf07b"; const val USERS = "\uf0c0"; const val SHARE = "\uf1e0"
    const val UPLOAD = "\uf093"; const val BOLT = "\uf0e7"; const val GEAR = "\uf013"; const val CODE_BRANCH = "\uf126"
    const val DIAGRAM = "\uf542"; const val XMARK = "\uf00d"; const val REFRESH = "\uf021"; const val TRASH = "\uf1f8"
    const val TAG = "\uf02b"; const val CHECK = "\uf00c"; const val CHEVRON_LEFT = "\uf053"; const val CHEVRON_RIGHT = "\uf054"
    const val CHEVRON_UP = "\uf077"; const val CHEVRON_DOWN = "\uf078"; const val EXTERNAL = "\uf35d"; const val WARNING = "\uf071"
    const val INFO_CIRCLE = "\uf05a"; const val CHECK_CIRCLE = "\uf058"; const val XMARK_CIRCLE = "\uf057"; const val DOWNLOAD = "\uf019"
    const val EYE = "\uf06e"; const val CUBE = "\uf1b2"; const val PEN = "\uf304"; const val COPY = "\uf0c5"
    const val GLOBE = "\uf0ac"; const val CLOCK = "\uf017"; const val USER = "\uf007"; const val HEART = "\uf004"
    const val LAYER_GROUP = "\uf5fd"; const val TABLE = "\uf0ce"; const val LIST = "\uf03a"; const val FILTER = "\uf0b0"
    const val PLAY = "\uf04b"; const val PAUSE = "\uf04c"; const val STOP = "\uf04d"; const val CIRCLE = "\uf111"
    const val PLUG = "\uf1e6"; const val NETWORK = "\uf6ff"; const val BUG = "\uf188"; const val TERMINAL = "\uf120"
    const val ROBOT = "\uf544"; const val WINDOW = "\uf2d0"; const val GRIP = "\uf58e"; const val ELLIPSIS = "\uf141"
    const val ARROW_UP = "\uf062"; const val ARROW_DOWN = "\uf063"; const val ARROW_RIGHT = "\uf061"; const val ARROW_LEFT = "\uf060"

    val BUILT_IN: List<String> = listOf(
        SEARCH, FOLDER, USERS, SHARE, UPLOAD, BOLT, GEAR, CODE_BRANCH, DIAGRAM, XMARK, REFRESH, TRASH, TAG, CHECK,
        CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_UP, CHEVRON_DOWN, EXTERNAL, WARNING, INFO_CIRCLE, CHECK_CIRCLE, XMARK_CIRCLE,
        DOWNLOAD, EYE, CUBE, PEN, COPY, GLOBE, CLOCK, USER, HEART, LAYER_GROUP, TABLE, LIST, FILTER, PLAY, PAUSE, STOP,
        CIRCLE, PLUG, NETWORK, BUG, TERMINAL, ROBOT, WINDOW, GRIP, ELLIPSIS, ARROW_UP, ARROW_DOWN, ARROW_RIGHT, ARROW_LEFT,
    )

    private val extra = LinkedHashSet<Int>()

    /** Add codepoints to the atlas. Must be called before the atlas is built (entrypoint time). */
    fun register(codepoints: Collection<Int>) { extra += codepoints }

    fun allCodepoints(): List<Int> = (BUILT_IN.map { it.codePointAt(0) } + extra).distinct().sorted()

    /**
     * imgui-java glyph ranges: flat shorts `[lo1, hi1, ..., 0]`, one degenerate pair per glyph,
     * ascending, zero-terminated. Values ≥0x8000 become negative Shorts on purpose (raw 16-bit).
     */
    fun glyphRanges(): ShortArray {
        val cps = allCodepoints()
        return ShortArray(cps.size * 2 + 1).also { out ->
            cps.forEachIndexed { i, cp -> out[i * 2] = cp.toShort(); out[i * 2 + 1] = cp.toShort() }
        }
    }
}
