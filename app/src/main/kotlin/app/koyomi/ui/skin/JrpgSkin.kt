package app.koyomi.ui.skin

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.R
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDateText
import app.koyomi.ui.Destination
import app.koyomi.ui.components.youbiColor
import app.koyomi.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * La app como el menú de un RPG japonés de los de antes: ventanas con marco, fuente de píxeles y
 * un cursor que señala. Todo dibujado aquí, con rectángulos: ni un marco ni un icono sale de un
 * juego real. La fuente es DotGothic16 (SIL OFL 1.1).
 */
object JrpgSkin : KoyomiSkin {
    private val dark = Palette(
        background = Color(0xFF05060F),
        surface = Color(0xFF101642),
        ink = Color(0xFFF4F4F0),
        inkSoft = Color(0xFFA9B0D6),
        rule = Color(0xFF3A4180),
        sunday = Color(0xFFFF8A80),
        saturday = Color(0xFF82B1FF),
        accent = Color(0xFFFFD54A),
        frame = Color(0xFFF4F4F0),
    )
    private val light = Palette(
        background = Color(0xFFD8D2BC),
        surface = Color(0xFFF3EEDC),
        ink = Color(0xFF1B1E3C),
        inkSoft = Color(0xFF5F6385),
        rule = Color(0xFFBDB69C),
        sunday = Color(0xFFC0392B),
        saturday = Color(0xFF2A56A8),
        accent = Color(0xFFC77700),
        frame = Color(0xFF1B1E3C),
    )

    override fun palette(dark: Boolean): Palette = if (dark) this.dark else light

    override val font: FontFamily = FontFamily(Font(R.font.dotgothic16_regular))
    override val strong: FontWeight = FontWeight.Normal
    override val dot: Shape = RectangleShape
    override val buttonShape: Shape = RectangleShape
    override val screenPadding = 12.dp
    override val sheetShape: Shape = RectangleShape

    override val sheetHandle = false
    override val wafuMonth = false

    override fun sheetColor(colors: Palette): Color = colors.background

    @Composable
    override fun Panel(modifier: Modifier, title: (@Composable RowScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
        val colors = palette()
        Column(modifier.padding(vertical = 5.dp).window(colors).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = title)
                Box(Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth().height(2.dp).background(colors.rule))
            }
            content()
        }
    }

    @Composable
    override fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, modifier: Modifier) {
        val colors = palette()
        val main = date.monthDay
        val youbi = "（${date.youbi.short}）"
        val shown = typed(main.length + youbi.length)
        Column(modifier.padding(top = 16.dp, bottom = 5.dp).window(colors).padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (style != DateStyle.SIMPLE) Text(date.year, color = colors.inkSoft, fontSize = 18.sp)
            // La fecha se escribe letra a letra; lo que falta ya ocupa su sitio, para que nada salte
            Text(
                buildAnnotatedString {
                    val youbiColor = youbiColor(date.youbi, holiday != null, colors)
                    (main + youbi).forEachIndexed { index, char ->
                        val color = if (index < main.length) colors.ink else youbiColor
                        withStyle(SpanStyle(color = if (index < shown) color else Color.Transparent)) { append(char) }
                    }
                },
                fontSize = 40.sp,
                maxLines = 1,
            )
            date.secondLine(style)?.let { Text(it, color = colors.inkSoft, fontSize = 14.sp) }
            if (holiday != null) Text(holiday, color = colors.sunday, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }

    @Composable
    override fun DayNumber(day: Int, color: Color, isToday: Boolean) {
        val colors = palette()
        Box(
            Modifier.size(28.dp).background(if (isToday) colors.ink else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.toString(), color = if (isToday) colors.surface else color, fontSize = 16.sp)
        }
    }

    @Composable
    override fun Mark(selected: Boolean, color: Color?) {
        val colors = palette()
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when {
                selected -> Cursor(color ?: colors.accent)
                // Un calendario que no se enseña conserva su color, en hueco
                color != null -> Box(Modifier.size(10.dp).border(2.dp, color))
            }
        }
    }

    @Composable
    override fun NavBar(current: Destination, onSelect: (Destination) -> Unit) {
        val colors = palette()
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp).window(colors).padding(vertical = 4.dp)) {
            for (destination in Destination.entries) {
                val selected = destination == current
                Row(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(destination) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(16.dp)) { if (selected) Cursor(colors.accent, blink = true) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(destination.label, color = if (selected) colors.ink else colors.inkSoft, fontSize = 17.sp)
                        Text(stringResource(destination.gloss), color = colors.inkSoft, fontSize = 10.sp)
                    }
                    // El mismo hueco al otro lado: la etiqueta queda centrada con cursor o sin él
                    Box(Modifier.width(16.dp))
                }
            }
        }
    }

    /** El marco de una ventana: borde grueso con las esquinas a escalón y una línea fina por dentro. */
    private fun Modifier.window(colors: Palette): Modifier = drawBehind {
        // El «píxel» del tema, en píxeles enteros de pantalla para que los bordes salgan nítidos
        val u = 2.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
        val thin = (u / 2).roundToInt().coerceAtLeast(1).toFloat()
        steppedRect(inset = 0f, cut = u, color = colors.frame)
        steppedRect(inset = 2 * u, cut = u, color = colors.surface)
        steppedRect(inset = 3 * u, cut = 0f, color = colors.frame.copy(alpha = 0.3f))
        steppedRect(inset = 3 * u + thin, cut = 0f, color = colors.surface)
    }

    private fun DrawScope.steppedRect(inset: Float, cut: Float, color: Color) {
        drawRect(color, Offset(inset + cut, inset), Size(size.width - 2 * (inset + cut), size.height - 2 * inset))
        drawRect(color, Offset(inset, inset + cut), Size(size.width - 2 * inset, size.height - 2 * (inset + cut)))
    }

    /** ▶ hecho de columnas de píxeles. La fuente no lo trae, y así casa con el marco. */
    @Composable
    private fun Cursor(color: Color, blink: Boolean = false) {
        val alpha = if (blink) {
            val transition = rememberInfiniteTransition(label = "cursor")
            val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "blink")
            if (phase < 0.6f) 1f else 0f
        } else {
            1f
        }
        Canvas(Modifier.size(width = 10.dp, height = 18.dp).alpha(alpha)) {
            val px = size.width / 5
            for (column in 0 until 5) {
                val height = (9 - 2 * column) * px
                drawRect(color, Offset(column * px, (size.height - height) / 2), Size(px, height))
            }
        }
    }

    // La fecha solo se escribe letra a letra la primera vez que se ve, al abrir la app
    private var introPlayed = false

    @Composable
    private fun typed(length: Int): Int {
        var shown by remember { mutableIntStateOf(if (introPlayed) length else 0) }
        LaunchedEffect(length) {
            while (shown < length) {
                delay(55)
                shown++
            }
            introPlayed = true
        }
        return shown.coerceAtMost(length)
    }
}
