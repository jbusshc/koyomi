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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.R
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDateText
import app.koyomi.engine.weather.Tone
import app.koyomi.settings.ThemeOptions
import app.koyomi.ui.Destination
import app.koyomi.ui.theme.Palette

/**
 * 藍: el azul del tinte de añil. Una franja con el patrón 青海波 lleva la fecha, en la mincho de
 * la app y con dorado; las tarjetas son sobrias, de esquina corta y filete dorado.
 */
object AiSkin : CardSkin() {
    private val kinari = Color(0xFFF3EFE4)
    private val gold = Color(0xFFE9D8A6)

    // La franja es de añil en los dos modos: el texto que lleva es siempre claro
    private val bannerDark = Color(0xFF173660)
    private val bannerLight = Color(0xFF1B3A66)

    private val light = Palette(
        background = Color(0xFFF4F1E8),
        surface = Color(0xFFFBFAF4),
        ink = Color(0xFF11284A),
        inkSoft = Color(0xFF58698A),
        rule = Color(0xFFD9D5C6),
        sunday = Color(0xFFC2412D),
        saturday = Color(0xFF1F5AA8),
        accent = Color(0xFF1B4B8C),
        frame = Color(0xFFD9D5C6),
    )
    private val dark = Palette(
        background = Color(0xFF0C1B33),
        surface = Color(0xFF132B4F),
        ink = kinari,
        inkSoft = Color(0xFF9FB4D3),
        rule = Color(0xFF28456F),
        sunday = Color(0xFFF08C7A),
        saturday = Color(0xFF8DB7F0),
        accent = gold,
        frame = Color(0xFF28456F),
        backdropTop = Color(0xFF0A1830),
        backdropBottom = Color(0xFF10294B),
    )

    override fun palette(dark: Boolean, options: ThemeOptions): Palette = if (dark) this.dark else light

    override val font: FontFamily = FontFamily(Font(R.font.zen_old_mincho_subset))
    override val strong: FontWeight = FontWeight.Normal
    override val iconStyle = IconStyle.SOFT
    override val cardRadius = 12.dp
    override val buttonShape = RoundedCornerShape(6.dp)

    override fun cardBorder(colors: Palette): Brush = SolidColor(
        if (colors.background.luminance() < 0.5f) gold.copy(alpha = 0.30f) else colors.rule,
    )

    override fun todayFill(colors: Palette): Color = colors.accent
    override fun todayInk(colors: Palette): Color = if (colors.background.luminance() < 0.5f) Color(0xFF0C1B33) else kinari

    /** El color de la franja y el de las líneas de las olas, según el modo. */
    fun banner(colors: Palette): Pair<Color, Color> {
        val night = colors.background.luminance() < 0.5f
        return if (night) bannerDark to Color(0xFF3B6CA8).copy(alpha = 0.55f) else bannerLight to Color(0xFF5C86C4).copy(alpha = 0.5f)
    }

    override fun toneColor(tone: Tone, colors: Palette): Color {
        val night = colors.background.luminance() < 0.5f
        return when (tone) {
            Tone.SUN, Tone.BOLT -> if (night) gold else Color(0xFFC98B1E)
            Tone.MOON -> if (night) kinari else Color(0xFF5B6F93)
            Tone.CLOUD -> if (night) Color(0xFFCBD6E6) else Color(0xFF7D93B5)
            Tone.SHADE -> if (night) Color(0xFF6B86AD) else Color(0xFFB6C4D8)
            Tone.RAIN -> if (night) Color(0xFF8DB7F0) else Color(0xFF1F5AA8)
            Tone.SNOW -> if (night) Color.White else Color(0xFF7FA3D6)
            Tone.FOG -> if (night) Color(0xFF8FA6C7) else Color(0xFF9FB0CC)
        }
    }

    @Composable
    override fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, time: String, modifier: Modifier) {
        val colors = palette()
        val (bannerColor, wave) = banner(colors)
        val shape = RoundedCornerShape(14.dp)
        // En la franja oscura, el rojo y el azul del tema claro no se leen: se usan sus versiones claras
        val onBanner = if (date.youbi.ordinal == 0 || holiday != null) Color(0xFFF08C7A) else if (date.youbi.ordinal == 6) Color(0xFF8DB7F0) else gold
        Box(
            modifier
                .padding(top = 36.dp, bottom = 22.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(bannerColor)
                .seigaiha(background = bannerColor, line = wave, radius = 13.dp)
                .border(1.dp, gold.copy(alpha = 0.35f), shape),
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    if (style != DateStyle.SIMPLE) Text(date.year, color = gold, fontSize = 18.sp)
                    Text(date.monthDay, color = kinari, fontSize = 42.sp, maxLines = 1)
                    date.secondLine(style)?.let { Text(it, color = kinari.copy(alpha = 0.7f), fontSize = 14.sp) }
                    if (holiday != null) Text(holiday, color = Color(0xFFF08C7A), fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(time, color = kinari, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
                }
                // El día de la semana, como un sello dorado
                Box(
                    Modifier
                        .size(width = 70.dp, height = 78.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bannerColor)
                        .border(2.dp, onBanner, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.youbi.short, color = onBanner, fontSize = 38.sp)
                        Text("曜日", color = onBanner.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }
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
                        Box(Modifier.padding(top = 4.dp).width(22.dp).height(2.dp).background(if (selected) colors.accent else Color.Transparent))
                    }
                }
            }
        }
    }
}
