package app.koyomi.settings

/** El color de las ventanas del tema RPG. */
enum class RpgWindow { NAVY, BLACK, PLUM, FOREST }

/** Lo gruesa que es la línea del marco. */
enum class FrameWeight { THIN, NORMAL, THICK }

/**
 * Lo que el usuario puede ajustar de los temas, aparte de cuál elige. Viaja con los ajustes, y por
 * eso llega igual a las pantallas y a los widgets.
 */
data class ThemeOptions(
    val rpgWindow: RpgWindow = RpgWindow.NAVY,
    val rpgFrame: FrameWeight = FrameWeight.THIN,
)
