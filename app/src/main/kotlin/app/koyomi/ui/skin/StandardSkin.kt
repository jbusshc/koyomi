package app.koyomi.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import app.koyomi.engine.weather.Tone
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDateText
import app.koyomi.ui.Destination
import app.koyomi.ui.components.youbiColor
import app.koyomi.ui.theme.Palette

/** Limpio y con mucho aire: superficies planas, la fuente del teléfono y el rojo de festivo como único acento. */
object StandardSkin : KoyomiSkin {
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

    override fun palette(dark: Boolean): Palette = if (dark) this.dark else light

    override val font: FontFamily = FontFamily.Default
    override val strong: FontWeight = FontWeight.Medium
    override val dot: Shape = CircleShape
    // Las tarjetas ya llevan su propio margen interior
    override val screenPadding = 16.dp
    override val buttonShape: Shape = RoundedCornerShape(8.dp)
    override val sheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    override val sheetHandle = true
    override val wafuMonth = false
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

    override fun sheetColor(colors: Palette): Color = colors.surface

    @Composable
    override fun Panel(modifier: Modifier, title: (@Composable RowScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
        val colors = palette()
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier
                .padding(vertical = 6.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.rule, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = title,
                )
                Box(Modifier.padding(top = 10.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(colors.rule))
            }
            content()
        }
    }

    @Composable
    override fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, time: String, modifier: Modifier) {
        val colors = palette()
        Column(modifier.padding(top = 56.dp, bottom = 32.dp)) {
            if (style != DateStyle.SIMPLE) Text(date.year, color = colors.inkSoft, fontSize = 20.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(date.monthDay, color = colors.ink, fontSize = 48.sp, fontWeight = FontWeight.Medium)
                Text(
                    "（${date.youbi.short}）",
                    color = youbiColor(date.youbi, holiday != null, colors),
                    fontSize = 28.sp,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            date.secondLine(style)?.let { Text(it, color = colors.inkSoft, fontSize = 14.sp) }
            if (holiday != null) {
                Text(holiday, color = colors.sunday, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Text(time, color = colors.ink, fontSize = 22.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }

    @Composable
    override fun DayNumber(day: Int, color: Color, isToday: Boolean) {
        val colors = palette()
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (isToday) colors.ink else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.toString(),
                color = if (isToday) colors.background else color,
                fontSize = 15.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }

    @Composable
    override fun Mark(selected: Boolean, color: Color?) {
        val colors = palette()
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(color ?: colors.ink) else Modifier.border(1.5.dp, colors.inkSoft, CircleShape)),
        )
    }

    @Composable
    override fun NavBar(current: Destination, onSelect: (Destination) -> Unit) {
        val colors = palette()
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
            Row(Modifier.fillMaxWidth()) {
                for (destination in Destination.entries) {
                    val selected = destination == current
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(destination) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            destination.label,
                            color = if (selected) colors.ink else colors.inkSoft,
                            fontSize = 17.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(stringResource(destination.gloss), color = colors.inkSoft.copy(alpha = if (selected) 1f else 0.7f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
