package app.koyomi.ui.skin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDateText
import app.koyomi.engine.weather.Tone
import app.koyomi.settings.ThemeOptions
import app.koyomi.ui.components.youbiColor
import app.koyomi.ui.theme.Palette

/** Limpio y con mucho aire: tarjetas planas, la fuente del teléfono y el día de la semana en grande. */
object StandardSkin : CardSkin() {
    private val light = Palette(
        background = Color(0xFFFAFAF7),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF22252B),
        inkSoft = Color(0xFF6B6F78),
        rule = Color(0xFFE4E2DC),
        sunday = Color(0xFFC8372D),
        saturday = Color(0xFF2F5FA8),
        accent = Color(0xFF22252B),
        frame = Color(0xFFE4E2DC),
    )
    private val dark = Palette(
        background = Color(0xFF121316),
        surface = Color(0xFF1B1D22),
        ink = Color(0xFFECEAE4),
        inkSoft = Color(0xFF9A9DA6),
        rule = Color(0xFF2B2E35),
        sunday = Color(0xFFE5766C),
        saturday = Color(0xFF7FA6E0),
        accent = Color(0xFFECEAE4),
        frame = Color(0xFF2B2E35),
    )

    override fun palette(dark: Boolean, options: ThemeOptions): Palette = if (dark) this.dark else light

    override val font: FontFamily = FontFamily.Default
    override val strong: FontWeight = FontWeight.Medium
    override val iconStyle = IconStyle.SOFT

    // Colores de un parte del tiempo: el sol ámbar, la lluvia azul, la nube gris azulada
    override fun toneColor(tone: Tone, colors: Palette): Color {
        val night = colors.background.luminance() < 0.5f
        return when (tone) {
            Tone.SUN, Tone.BOLT -> if (night) Color(0xFFFFC24B) else Color(0xFFF5A524)
            Tone.MOON -> if (night) Color(0xFFFFE08A) else Color(0xFFE5B93C)
            Tone.CLOUD -> if (night) Color(0xFFC4CFDB) else Color(0xFF9FB1C6)
            Tone.SHADE -> if (night) Color(0xFF7E8A9A) else Color(0xFFC5D0DC)
            Tone.RAIN -> if (night) Color(0xFF5DA9FF) else Color(0xFF3D8BDA)
            Tone.SNOW -> if (night) Color(0xFFDDEBFA) else Color(0xFF6BB6E8)
            Tone.FOG -> if (night) Color(0xFF8E98A6) else Color(0xFFA3AEBB)
        }
    }

    @Composable
    override fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, time: String, modifier: Modifier) {
        val colors = palette()
        val youbiColor = youbiColor(date.youbi, holiday != null, colors)
        Row(modifier.padding(top = 48.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                if (style != DateStyle.SIMPLE) Text(date.year, color = colors.inkSoft, fontSize = 20.sp)
                Text(date.monthDay, color = colors.ink, fontSize = 46.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                date.secondLine(style)?.let { Text(it, color = colors.inkSoft, fontSize = 14.sp) }
                if (holiday != null) Text(holiday, color = colors.sunday, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                Text(time, color = colors.ink, fontSize = 22.sp, modifier = Modifier.padding(top = 14.dp))
            }
            // El día de la semana, como protagonista: lo primero que se busca en un calendario
            WeekdayBadge(date.youbi, youbiColor, size = 76.dp)
        }
    }
}
