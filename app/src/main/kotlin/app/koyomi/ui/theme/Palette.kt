package app.koyomi.ui.theme

import androidx.compose.ui.graphics.Color

/** Los colores de un tema en uno de sus dos modos. */
data class Palette(
    val background: Color,
    /** Lo que se levanta del fondo: una tarjeta, el panel de un día, la ventana de un tema con marcos. */
    val surface: Color,
    val ink: Color,
    val inkSoft: Color,
    val rule: Color,
    /** 日 y 祝日 */
    val sunday: Color,
    /** 土 */
    val saturday: Color,
    /** Lo que señala: el cursor, la opción elegida. */
    val accent: Color,
    /** El marco de las ventanas, en los temas que las tienen. */
    val frame: Color,
)
