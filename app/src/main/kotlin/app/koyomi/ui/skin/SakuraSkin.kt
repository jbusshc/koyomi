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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * 桜: pastel y suave, de papelería japonesa moderna. Tarjetas muy redondeadas, el rosa del cerezo
 * como único acento, píldoras para lo pequeño y el día de la semana en un círculo.
 */
object SakuraSkin : CardSkin() {
    private val light = Palette(
        background = Color(0xFFFFF7F7),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF4A3840),
        inkSoft = Color(0xFF8E7A82),
        rule = Color(0xFFF4DCE2),
        sunday = Color(0xFFD94B6B),
        saturday = Color(0xFF5878B8),
        accent = Color(0xFFE8668A),
        frame = Color(0xFFF4DCE2),
        backdropTop = Color(0xFFFFEFF3),
        backdropBottom = Color(0xFFFFFAF8),
    )
    private val dark = Palette(
        background = Color(0xFF1C1318),
        surface = Color(0xFF2B1D24),
        ink = Color(0xFFF8E9EE),
        inkSoft = Color(0xFFB79BA6),
        rule = Color(0xFF4A3340),
        sunday = Color(0xFFFF7D9B),
        saturday = Color(0xFF8FB0F0),
        accent = Color(0xFFFF8FB0),
        frame = Color(0xFF4A3340),
        backdropTop = Color(0xFF26181F),
        backdropBottom = Color(0xFF1A1216),
    )

    override fun palette(dark: Boolean, options: ThemeOptions): Palette = if (dark) this.dark else light

    override val font: FontFamily = FontFamily.Default
    override val strong: FontWeight = FontWeight.SemiBold
    override val iconStyle = IconStyle.SOFT
    override val cardRadius = 26.dp

    override fun todayFill(colors: Palette): Color = colors.accent
    override fun todayInk(colors: Palette): Color = Color.White

    override fun toneColor(tone: Tone, colors: Palette): Color {
        val night = colors.background.luminance() < 0.5f
        return when (tone) {
            Tone.SUN, Tone.BOLT -> if (night) Color(0xFFFFB86B) else Color(0xFFFFA94D)
            Tone.MOON -> if (night) Color(0xFFFFE6A8) else Color(0xFFE9B949)
            Tone.CLOUD -> if (night) Color(0xFFD9C8D2) else Color(0xFFC7B6C0)
            Tone.SHADE -> if (night) Color(0xFF8E7884) else Color(0xFFE3D7DC)
            Tone.RAIN -> if (night) Color(0xFF7FB2FF) else Color(0xFF5B9BE0)
            Tone.SNOW -> if (night) Color(0xFFF0F6FF) else Color(0xFF9ACBF0)
            Tone.FOG -> if (night) Color(0xFFA68F9B) else Color(0xFFC9BCC3)
        }
    }

    /** Una píldora: lo pequeño que acompaña a la fecha. */
    @Composable
    private fun Pill(text: String, color: Color) {
        Text(
            text,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f)).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }

    @Composable
    override fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, time: String, modifier: Modifier) {
        val colors = palette()
        val petals = colors.accent
        Column(
            modifier
                .padding(top = 44.dp, bottom = 22.dp)
                .drawBehind {
                    // Dos flores de cerezo, muy suaves, en la esquina; el círculo del día va debajo, sin tocarlas
                    val d = density
                    sakuraFlower(Offset(size.width - 34 * d, 22 * d), 24 * d, petals.copy(alpha = 0.17f), 8f)
                    sakuraFlower(Offset(size.width - 98 * d, 40 * d), 12 * d, petals.copy(alpha = 0.12f), 28f)
                },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (style != DateStyle.SIMPLE) Pill(date.year, colors.accent)
                if (holiday != null) Pill(holiday, colors.sunday)
            }
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(date.monthDay, color = colors.ink, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    date.secondLine(style)?.let { Text(it, color = colors.inkSoft, fontSize = 14.sp) }
                    Text(time, color = colors.inkSoft, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
                }
                WeekdayBadge(date.youbi, youbiColor(date.youbi, holiday != null, colors, other = colors.accent), size = 78.dp, shape = CircleShape)
            }
        }
    }

    @Composable
    override fun NavBar(current: Destination, onSelect: (Destination) -> Unit) {
        val colors = palette()
        val shape = RoundedCornerShape(30.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.rule, shape)
                .padding(6.dp),
        ) {
            for (destination in Destination.entries) {
                val selected = destination == current
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(destination) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            destination.label,
                            color = if (selected) colors.accent else colors.inkSoft,
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(stringResource(destination.gloss), color = colors.inkSoft.copy(alpha = if (selected) 0.9f else 0.65f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
