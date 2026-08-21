package dev.harrison.panellib.theme

/** Built-in themes. */
object Themes {
    /**
     * "Graphite" — the panel-lib default. Neutral cool-dark surfaces, thin 1px borders, a single
     * blue accent used only for interactive emphasis (never as a surface), calm semantic colours.
     */
    val GRAPHITE = Theme(
        name = "Graphite",
        bg = rgba("#0F1115"),
        surface = rgba("#161920"),
        surfaceAlt = rgba("#1C2029"),
        surfaceHover = rgba("#242A36"),
        surfaceRaised = rgba("#222834"),
        border = rgba("#2B3240"),
        borderSubtle = rgba("#1F2530"),
        text = rgba("#E6E8EE"),
        textSecondary = rgba("#A6ADBB"),
        textMuted = rgba("#7B8494"),
        textFaint = rgba("#4F5868"),
        accent = rgba("#5B8DEF"),
        accentHover = rgba("#7AA5FF"),
        accentDim = rgba("#2A4475"),
        accentMuted = rgba("#405B8DEF"),
        success = rgba("#3FB950"),
        danger = rgba("#F06A6A"),
        warning = rgba("#D9A421"),
        info = rgba("#58A6FF"),
        scrim = rgba("#A00B0D12"),
        stripe = rgba("#06FFFFFF"),
    )

    val DEFAULT: Theme get() = GRAPHITE

    /** Derive a theme from [base] with a different accent; hover/dim/muted are computed. */
    fun withAccent(base: Theme, accentHex: String): Theme {
        val a = rgba(accentHex)
        return base.copy(
            accent = a,
            accentHover = lerp(a, base.text, 0.22f),
            accentDim = lerp(a, base.bg, 0.55f),
            accentMuted = a.withAlpha(0.25f),
        )
    }
}
