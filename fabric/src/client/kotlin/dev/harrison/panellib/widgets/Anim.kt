package dev.harrison.panellib.widgets

/**
 * Per-widget hover-fade state (~125 ms full fade at default speed). Read [peek] before drawing,
 * call [advance] with this frame's hover state after drawing. Render-thread only.
 */
object Anim {
    private val values = HashMap<Int, Float>()

    fun peek(id: Int): Float = values[id] ?: 0f

    fun advance(id: Int, target: Boolean, dt: Float, speed: Float = 8f): Float {
        val cur = values[id] ?: 0f
        val goal = if (target) 1f else 0f
        val step = (speed * dt).coerceAtLeast(0f)
        val next = (cur + (goal - cur).coerceIn(-step, step)).coerceIn(0f, 1f)
        if (next == 0f && !target) values.remove(id) else values[id] = next
        return next
    }

    fun clear() = values.clear()
    fun size(): Int = values.size
}
