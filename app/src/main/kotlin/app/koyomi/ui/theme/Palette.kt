package app.koyomi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Los colores del tema Standard, sueltos por ahora. Cuando llegue el segundo tema (H4) pasan a ser
// los tokens de su skin.
data class Palette(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val inkSoft: Color,
    val rule: Color,
    /** 日 y 祝日 */
    val sunday: Color,
    /** 土 */
    val saturday: Color,
) {
    companion object {
        val Light = Palette(
            background = Color(0xFFFAFAF7),
            surface = Color(0xFFFFFFFF),
            ink = Color(0xFF22252B),
            inkSoft = Color(0xFF6B6F78),
            rule = Color(0xFFE4E2DC),
            sunday = Color(0xFFC8372D),
            saturday = Color(0xFF2F5FA8),
        )
        val Dark = Palette(
            background = Color(0xFF121316),
            surface = Color(0xFF1B1D22),
            ink = Color(0xFFECEAE4),
            inkSoft = Color(0xFF9A9DA6),
            rule = Color(0xFF2B2E35),
            sunday = Color(0xFFE5766C),
            saturday = Color(0xFF7FA6E0),
        )
    }
}

@Composable
fun palette(): Palette = if (isSystemInDarkTheme()) Palette.Dark else Palette.Light
