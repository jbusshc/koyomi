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
import app.koyomi.settings.RpgWindow
import app.koyomi.settings.ThemeOptions
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
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

/**
 * Lo que comparten los temas de píxeles: la fuente DotGothic16 (SIL OFL 1.1), ventanas con marco,
 * un cursor que señala y nada redondo. Todo dibujado aquí, con rectángulos: ni un marco ni un
 * icono sale de un juego real. Cada tema pone sus colores y su marco.
 */
abstract class PixelSkin : KoyomiSkin {
    override val font: FontFamily = FontFamily(Font(R.font.dotgothic16_regular))
    override val strong: FontWeight = FontWeight.Normal
    override val dot: Shape = RectangleShape
    override val buttonShape: Shape = RectangleShape
    override val screenPadding = 12.dp
    override val sheetShape: Shape = RectangleShape

    override val sheetHandle = false
    override val wafuMonth = false
    override val iconStyle = IconStyle.PIXEL

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
    override fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, time: String, modifier: Modifier) {
        val colors = palette()
        val main = date.monthDay
        val youbi = "（${date.youbi.short}）"
        val shown = typed(main.length + youbi.length)
        Column(modifier.padding(top = 16.dp, bottom = 5.dp).window(colors).padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Arriba, como en una ventana de estado: el año a un lado y la hora al otro
            Row(Modifier.fillMaxWidth()) {
                Text(if (style != DateStyle.SIMPLE) date.year else "", color = colors.inkSoft, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(time, color = colors.accent, fontSize = 18.sp)
            }
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

    /** El marco de la ventana, con el grosor que haya elegido el usuario. */
    @Composable
    private fun Modifier.window(colors: Palette): Modifier {
        val weight = LocalThemeOptions.current.rpgFrame
        return drawBehind {
            val t = PixelFrame.border(weight, density)
            for (rect in PixelFrame.rects(size.width, size.height, t)) {
                val color = when (rect.role) {
                    PixelFrame.Role.FRAME -> colors.frame
                    PixelFrame.Role.SURFACE -> colors.surface
                    PixelFrame.Role.HAIRLINE -> colors.frame.copy(alpha = 0.3f)
                }
                drawRect(color, Offset(rect.x, rect.y), Size(rect.w, rect.h))
            }
        }
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

/**
 * La app como el menú de un RPG japonés de los de antes: ventanas con marco doble, letras claras y
 * cursor dorado. El color de las ventanas y lo grueso del marco los elige el usuario.
 */
object JrpgSkin : PixelSkin() {
    private class Window(
        val background: Color,
        val surface: Color,
        val rule: Color,
        val inkSoft: Color,
        // Claro: el pergamino y la tinta que acompañan a esa ventana
        val paper: Color,
        val paperSurface: Color,
        val paperRule: Color,
        val ink: Color,
        val paperInkSoft: Color,
    )

    private val windows = mapOf(
        RpgWindow.NAVY to Window(
            Color(0xFF05060F), Color(0xFF101642), Color(0xFF3A4180), Color(0xFFA9B0D6),
            Color(0xFFD8D2BC), Color(0xFFF3EEDC), Color(0xFFBDB69C), Color(0xFF1B1E3C), Color(0xFF5F6385),
        ),
        RpgWindow.BLACK to Window(
            Color(0xFF000000), Color(0xFF0B0B0E), Color(0xFF3C3C46), Color(0xFFA8A8B8),
            Color(0xFFDADADA), Color(0xFFF6F6F4), Color(0xFFBDBDBD), Color(0xFF111111), Color(0xFF585858),
        ),
        RpgWindow.PLUM to Window(
            Color(0xFF0B050E), Color(0xFF2B1236), Color(0xFF5E2F70), Color(0xFFCDA9DB),
            Color(0xFFDDD0E3), Color(0xFFF6EEF8), Color(0xFFC4B1CD), Color(0xFF2B1236), Color(0xFF6E5479),
        ),
        RpgWindow.FOREST to Window(
            Color(0xFF030A06), Color(0xFF0E2A1B), Color(0xFF2F6B4A), Color(0xFFA8D2BC),
            Color(0xFFCEDBD0), Color(0xFFEEF5EF), Color(0xFFB0C4B5), Color(0xFF0E2A1B), Color(0xFF4F6B5B),
        ),
    )

    override fun palette(dark: Boolean, options: ThemeOptions): Palette {
        val window = windows.getValue(options.rpgWindow)
        return if (dark) {
            Palette(
                background = window.background,
                surface = window.surface,
                ink = Color(0xFFF4F4F0),
                inkSoft = window.inkSoft,
                rule = window.rule,
                sunday = Color(0xFFFF8A80),
                saturday = Color(0xFF82B1FF),
                accent = Color(0xFFFFD54A),
                frame = Color(0xFFF4F4F0),
            )
        } else {
            Palette(
                background = window.paper,
                surface = window.paperSurface,
                ink = window.ink,
                inkSoft = window.paperInkSoft,
                rule = window.paperRule,
                sunday = Color(0xFFC0392B),
                saturday = Color(0xFF2A56A8),
                accent = Color(0xFFC77700),
                frame = window.ink,
            )
        }
    }
}
