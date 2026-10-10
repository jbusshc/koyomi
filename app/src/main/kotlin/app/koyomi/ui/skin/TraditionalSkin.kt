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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.lerp
import app.koyomi.engine.weather.Tone
import app.koyomi.settings.ThemeOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.R
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.JaDateText
import app.koyomi.engine.format.Wafu
import app.koyomi.ui.Destination
import app.koyomi.ui.components.youbiColor
import app.koyomi.ui.theme.Palette

/**
 * Papel, tinta y bermellón: la fecha escrita en vertical con números en kanji, en una mincho de
 * trazo antiguo, y el nombre viejo del mes al lado. Los colores son los tradicionales (伝統色):
 * 生成り para el papel, 墨 para la tinta, 朱 para lo que destaca y 藍 para el sábado.
 * La fuente es Zen Old Mincho (SIL OFL 1.1), recortada a los caracteres de uso corriente.
 */
object TraditionalSkin : KoyomiSkin {
    private val light = Palette(
        background = Color(0xFFF4EEDD),
        surface = Color(0xFFFBF7EA),
        ink = Color(0xFF2B2A27),
        inkSoft = Color(0xFF7A7467),
        rule = Color(0xFFD6CCB4),
        sunday = Color(0xFFC73E3A),
        saturday = Color(0xFF2A4D7A),
        accent = Color(0xFFC73E3A),
        frame = Color(0xFFD6CCB4),
    )
    private val dark = Palette(
        background = Color(0xFF1C1B19),
        surface = Color(0xFF27251F),
        ink = Color(0xFFEDE6D3),
        inkSoft = Color(0xFFA39C8A),
        rule = Color(0xFF3D3A31),
        sunday = Color(0xFFE2695F),
        saturday = Color(0xFF86A5D6),
        accent = Color(0xFFE2695F),
        frame = Color(0xFF3D3A31),
    )

    override fun palette(dark: Boolean, options: ThemeOptions): Palette = if (dark) this.dark else light

    override val font: FontFamily = FontFamily(Font(R.font.zen_old_mincho_subset))
    override val strong: FontWeight = FontWeight.Normal
    override val dot: Shape = CircleShape
    override val buttonShape: Shape = RoundedCornerShape(2.dp)
    override val screenPadding = 24.dp
    override val sheetShape: Shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
    override val sheetHandle = true
    override val wafuMonth = true
    override val iconStyle = IconStyle.INK

    // Sin colores de colores: el sol es el 朱 de un sello, la lluvia el 藍, y lo demás, tinta aguada
    override fun toneColor(tone: Tone, colors: Palette): Color = when (tone) {
        Tone.SUN, Tone.BOLT -> colors.accent
        Tone.MOON -> colors.ink
        Tone.CLOUD -> lerp(colors.background, colors.ink, 0.5f)
        Tone.SHADE -> lerp(colors.background, colors.ink, 0.25f)
        Tone.RAIN -> colors.saturday
        Tone.SNOW -> lerp(colors.background, colors.ink, 0.4f)
        Tone.FOG -> lerp(colors.background, colors.ink, 0.35f)
    }

    override fun sheetColor(colors: Palette): Color = colors.surface

    @Composable
    override fun Panel(modifier: Modifier, title: (@Composable RowScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
        val colors = palette()
        Column(modifier) {
            if (title != null) {
                Row(
                    Modifier.padding(top = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Un sello pequeño delante de cada título
                    Box(Modifier.size(7.dp).background(colors.accent))
                    title()
                }
                Box(Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(colors.rule))
            }
            content()
        }
    }

    @Composable
    override fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, time: String, modifier: Modifier) {
        val colors = palette()
        // En vertical los números van en kanji: 十月四日
        val kanji = JaDate.kanji(date.date)
        val wafu = Wafu.of(date.date.monthValue)
        Row(modifier.padding(top = 36.dp, bottom = 20.dp), verticalAlignment = Alignment.Top) {
            // A la izquierda, lo que acompaña: el nombre antiguo del mes, la fecha occidental y el festivo
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(wafu.kanji, color = colors.ink, fontSize = 26.sp)
                Text(wafu.kana, color = colors.inkSoft, fontSize = 13.sp)
                Text(time, color = colors.ink, fontSize = 22.sp, modifier = Modifier.padding(top = 18.dp))
                date.secondLine(style)?.let { Text(it, color = colors.inkSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp)) }
                if (holiday != null) Text(holiday, color = colors.sunday, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
            }
            // Y la fecha, que se lee de derecha a izquierda: la era, el día y el día de la semana
            Vertical(kanji.youbi.full, 17.sp, youbiColor(kanji.youbi, holiday != null, colors, other = colors.inkSoft), Modifier.padding(top = 8.dp))
            Vertical(kanji.monthDay, 40.sp, colors.ink, Modifier.padding(horizontal = 14.dp))
            if (style != DateStyle.SIMPLE) Vertical(kanji.year, 19.sp, colors.inkSoft, Modifier.padding(top = 8.dp))
        }
    }

    /** Texto de arriba abajo, una letra por línea. Vale para fechas: no hay signos que girar. */
    @Composable
    private fun Vertical(text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
        val style = TextStyle(
            fontSize = size,
            lineHeight = size * 1.12f,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            for (char in text) Text(char.toString(), color = color, style = style)
        }
    }

    @Composable
    override fun DayNumber(day: Int, color: Color, isToday: Boolean) {
        val colors = palette()
        // Hoy lleva un sello rojo, como el de un calendario de pared
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (isToday) colors.accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.toString(), color = if (isToday) colors.surface else color, fontSize = 16.sp)
        }
    }

    @Composable
    override fun Mark(selected: Boolean, color: Color?) {
        val colors = palette()
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(color ?: colors.accent) else Modifier.border(1.dp, colors.inkSoft, CircleShape)),
            )
        }
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
                            .padding(top = 10.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(destination.label, color = if (selected) colors.ink else colors.inkSoft, fontSize = 17.sp)
                        Text(stringResource(destination.gloss), color = colors.inkSoft.copy(alpha = if (selected) 1f else 0.7f), fontSize = 10.sp)
                        Box(Modifier.padding(top = 4.dp).width(18.dp).height(2.dp).background(if (selected) colors.accent else Color.Transparent))
                    }
                }
            }
        }
    }
}
