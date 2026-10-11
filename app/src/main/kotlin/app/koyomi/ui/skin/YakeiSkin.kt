package app.koyomi.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDateText
import app.koyomi.engine.weather.Tone
import app.koyomi.settings.ThemeOptions
import app.koyomi.ui.Destination
import app.koyomi.ui.components.youbiColor
import app.koyomi.ui.theme.Palette

/**
 * 夜景: una ciudad de noche, de portada de city pop. Un degradado de violeta a magenta, tarjetas
 * de cristal con el borde de neón rosa a cian, y la fecha con su resplandor. De día, la misma
 * ciudad en pasteles.
 */
object YakeiSkin : CardSkin() {
    private val light = Palette(
        background = Color(0xFFFFEEF6),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF2A1B4D),
        inkSoft = Color(0xFF6E5F95),
        rule = Color(0xFFE6D9F6),
        sunday = Color(0xFFE0307F),
        saturday = Color(0xFF2F7FE0),
        accent = Color(0xFFE0307F),
        frame = Color(0xFFE6D9F6),
        backdropTop = Color(0xFFFFE3F0),
        backdropBottom = Color(0xFFD9E8FF),
    )
    private val dark = Palette(
        background = Color(0xFF120A2A),
        surface = Color(0xFF1B1235),
        ink = Color(0xFFF6F1FF),
        inkSoft = Color(0xFFB4A8D6),
        rule = Color(0xFF3A2C66),
        sunday = Color(0xFFFF6FA8),
        saturday = Color(0xFF5CE1E6),
        accent = Color(0xFFFF4FA3),
        frame = Color(0xFF3A2C66),
        backdropTop = Color(0xFF0B0720),
        backdropBottom = Color(0xFF2A0B45),
    )

    override fun palette(dark: Boolean, options: ThemeOptions): Palette = if (dark) this.dark else light

    /** Los dos neones: rosa y cian (de día, sus versiones más hondas). */
    fun neon(colors: Palette): Pair<Color, Color> {
        val night = colors.background.luminance() < 0.5f
        return if (night) Color(0xFFFF4FA3) to Color(0xFF2DE2E6) else Color(0xFFE0307F) to Color(0xFF2F7FE0)
    }

    override fun cardBorder(colors: Palette): Brush = neon(colors).let { (a, b) -> Brush.linearGradient(listOf(a.copy(alpha = 0.85f), b.copy(alpha = 0.85f))) }

    // Cristal: deja entrever el degradado de detrás
    override fun cardFill(colors: Palette): Brush = androidx.compose.ui.graphics.SolidColor(colors.surface.copy(alpha = if (colors.background.luminance() < 0.5f) 0.82f else 0.9f))

    override val font: FontFamily = FontFamily.Default
    override val strong: FontWeight = FontWeight.SemiBold
    override val iconStyle = IconStyle.SOFT
    override val cardRadius = 22.dp

    override fun todayFill(colors: Palette): Color = neon(colors).first
    override fun todayInk(colors: Palette): Color = Color.White
    override fun sheetColor(colors: Palette): Color = if (colors.background.luminance() < 0.5f) Color(0xFF1B1235) else Color.White

    override fun toneColor(tone: Tone, colors: Palette): Color {
        val night = colors.background.luminance() < 0.5f
        return when (tone) {
            Tone.SUN, Tone.BOLT -> if (night) Color(0xFFFFC857) else Color(0xFFFFA630)
            Tone.MOON -> if (night) Color(0xFFFDF2B0) else Color(0xFFE8B84A)
            Tone.CLOUD -> if (night) Color(0xFFCBBFEA) else Color(0xFF9D8FC4)
            Tone.SHADE -> if (night) Color(0xFF8272B8) else Color(0xFFC9BFE6)
            Tone.RAIN -> if (night) Color(0xFF5CE1E6) else Color(0xFF2F7FE0)
            Tone.SNOW -> if (night) Color(0xFFFFFFFF) else Color(0xFF86AEF0)
            Tone.FOG -> if (night) Color(0xFF9C90C9) else Color(0xFFB6AAD8)
        }
    }

    @Composable
    override fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, time: String, modifier: Modifier) {
        val colors = palette()
        val (pink, cyan) = neon(colors)
        val night = colors.background.luminance() < 0.5f
        val glow = LocalTextStyle.current.copy(shadow = if (night) Shadow(pink.copy(alpha = 0.75f), Offset.Zero, blurRadius = 30f) else null)
        Row(modifier.padding(top = 48.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                if (style != DateStyle.SIMPLE) Text(date.year, color = cyan, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(date.monthDay, color = colors.ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = glow)
                date.secondLine(style)?.let { Text(it, color = colors.inkSoft, fontSize = 14.sp) }
                if (holiday != null) Text(holiday, color = colors.sunday, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                Text(time, color = cyan, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
            }
            WeekdayBadge(
                youbi = date.youbi,
                color = youbiColor(date.youbi, holiday != null, colors, other = cyan),
                size = 78.dp,
                fill = Brush.linearGradient(listOf(pink, if (night) Color(0xFF7A3CFF) else cyan)),
                textColor = Color.White,
                border = null,
            )
        }
    }

    @Composable
    override fun NavBar(current: Destination, onSelect: (Destination) -> Unit) {
        val colors = palette()
        val (pink, cyan) = neon(colors)
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(shape)
                .background(colors.surface.copy(alpha = 0.9f))
                .border(1.dp, Brush.linearGradient(listOf(pink.copy(alpha = 0.8f), cyan.copy(alpha = 0.8f))), shape)
                .padding(5.dp),
        ) {
            for (destination in Destination.entries) {
                val selected = destination == current
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(21.dp))
                        .then(if (selected) Modifier.background(Brush.linearGradient(listOf(pink.copy(alpha = 0.9f), cyan.copy(alpha = 0.9f)))) else Modifier)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(destination) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            destination.label,
                            color = if (selected) Color.White else colors.inkSoft,
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(stringResource(destination.gloss), color = if (selected) Color.White.copy(alpha = 0.9f) else colors.inkSoft.copy(alpha = 0.65f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
