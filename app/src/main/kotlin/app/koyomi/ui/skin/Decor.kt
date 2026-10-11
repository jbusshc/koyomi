package app.koyomi.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.engine.format.Youbi
import app.koyomi.ui.theme.Palette

/** El fondo del tema: un color llano, o un degradado vertical si el tema lo trae. */
fun Modifier.backdrop(colors: Palette): Modifier =
    if (colors.backdropTop == colors.backdropBottom) {
        background(colors.background)
    } else {
        background(Brush.verticalGradient(listOf(colors.backdropTop, colors.backdropBottom)))
    }

/** El día de la semana como protagonista: un cuadrado teñido con su kanji grande. */
@Composable
fun WeekdayBadge(
    youbi: Youbi,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size * 0.28f),
    fill: Brush? = null,
    textColor: Color = color,
    border: Color? = color.copy(alpha = 0.35f),
) {
    val tinted = if (fill != null) Modifier.background(fill) else Modifier.background(color.copy(alpha = 0.14f))
    val outlined = if (border != null) Modifier.border(1.dp, border, shape) else Modifier
    Box(modifier.size(size).clip(shape).then(tinted).then(outlined), contentAlignment = Alignment.Center) {
        Text(youbi.short, color = textColor, fontSize = (size.value * 0.52f).sp, fontWeight = FontWeight.Medium)
    }
}

/** Cinco pétalos de cerezo alrededor de [center], de radio [radius]. */
fun DrawScope.sakuraFlower(center: Offset, radius: Float, color: Color, rotation: Float = 0f) {
    for (index in 0 until 5) {
        rotate(rotation + index * 72f, center) {
            drawOval(
                color,
                topLeft = Offset(center.x - radius * 0.3f, center.y - radius),
                size = Size(radius * 0.6f, radius * 0.95f),
            )
        }
    }
    drawCircle(color.copy(alpha = (color.alpha * 1.6f).coerceAtMost(1f)), radius * 0.12f, center)
}

/**
 * El patrón 青海波 sobre el fondo del elemento. Se dibuja una sola vez en un mapa de bits al
 * tamaño que tenga y después solo se copia, así no cuesta nada en cada fotograma.
 */
fun Modifier.seigaiha(background: Color, line: Color, radius: Dp = 12.dp, strokeWidth: Dp = 1.dp): Modifier =
    drawWithCache {
        val width = size.width.toInt().coerceAtLeast(1)
        val height = size.height.toInt().coerceAtLeast(1)
        val image = ImageBitmap(width, height)
        CanvasDrawScope().draw(this, layoutDirection, ComposeCanvas(image), Size(width.toFloat(), height.toFloat())) {
            val r = radius.toPx()
            for (scale in Seigaiha.scales(width.toFloat(), height.toFloat(), r)) {
                val center = Offset(scale.cx, scale.cy)
                drawCircle(background, r, center)
                for (ring in Seigaiha.RINGS) drawCircle(line, r * ring, center, style = Stroke(width = strokeWidth.toPx()))
            }
        }
        onDrawBehind { drawImage(image) }
    }
