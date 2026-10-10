package app.koyomi.ui.weather

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import app.koyomi.engine.weather.Part
import app.koyomi.engine.weather.Sky
import app.koyomi.engine.weather.WeatherArt
import app.koyomi.ui.skin.IconStyle
import app.koyomi.ui.skin.KoyomiSkin
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import app.koyomi.ui.theme.Palette
import kotlin.math.floor
import kotlin.math.roundToInt

/** Dónde se pinta un icono: el lienzo de Compose o el de un widget. Todo en píxeles. */
interface IconSink {
    fun disc(cx: Float, cy: Float, r: Float, color: Color)
    fun bar(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color)
    fun crescent(cx: Float, cy: Float, r: Float, color: Color)
    fun rect(x: Float, y: Float, w: Float, h: Float, color: Color)
}

/** El mordisco de la luna: un disco algo más pequeño, hacia arriba a la derecha. */
private const val BITE_X = 0.5f
private const val BITE_Y = -0.3f
private const val BITE_R = 0.8f

object WeatherIcons {
    /** Pinta el icono de [sky] con el estilo y los colores de [skin], en un cuadrado de [size] píxeles. */
    fun paint(sink: IconSink, skin: KoyomiSkin, colors: Palette, sky: Sky, night: Boolean, x: Float, y: Float, size: Float) {
        if (skin.iconStyle == IconStyle.PIXEL) {
            // Casillas de un número entero de píxeles, para que los bordes salgan nítidos
            val cell = floor(size / WeatherArt.GRID).coerceAtLeast(1f)
            val span = cell * WeatherArt.GRID
            val left = (x + (size - span) / 2).roundToInt().toFloat()
            val top = (y + (size - span) / 2).roundToInt().toFloat()
            WeatherArt.pixels(sky, night).forEachIndexed { row, line ->
                line.forEachIndexed { col, char ->
                    val tone = WeatherArt.toneOf(char) ?: return@forEachIndexed
                    sink.rect(left + col * cell, top + row * cell, cell, cell, skin.toneColor(tone, colors))
                }
            }
            return
        }
        for (part in WeatherArt.vector(sky, night, rays = skin.iconStyle == IconStyle.SOFT)) {
            val color = skin.toneColor(part.tone, colors)
            when (part) {
                is Part.Disc -> sink.disc(x + part.cx * size, y + part.cy * size, part.r * size, color)
                is Part.Bar -> sink.bar(x + part.x1 * size, y + part.y1 * size, x + part.x2 * size, y + part.y2 * size, part.width * size, color)
                is Part.Crescent -> sink.crescent(x + part.cx * size, y + part.cy * size, part.r * size, color)
            }
        }
    }

    /** Para pintar en el lienzo de un widget. */
    fun canvasSink(canvas: Canvas): IconSink = CanvasSink(canvas)
}

private class ComposeSink(val scope: DrawScope) : IconSink {
    override fun disc(cx: Float, cy: Float, r: Float, color: Color) = scope.drawCircle(color, r, Offset(cx, cy))

    override fun bar(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color) =
        scope.drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = width, cap = StrokeCap.Round)

    override fun crescent(cx: Float, cy: Float, r: Float, color: Color) {
        val outer = androidx.compose.ui.graphics.Path().apply { addOval(Rect(Offset(cx, cy), r)) }
        val bite = androidx.compose.ui.graphics.Path().apply { addOval(Rect(Offset(cx + r * BITE_X, cy + r * BITE_Y), r * BITE_R)) }
        scope.drawPath(androidx.compose.ui.graphics.Path.combine(PathOperation.Difference, outer, bite), color)
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) = scope.drawRect(color, Offset(x, y), Size(w, h))
}

private class CanvasSink(val canvas: Canvas) : IconSink {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun disc(cx: Float, cy: Float, r: Float, color: Color) {
        paint.style = Paint.Style.FILL
        paint.color = color.toArgb()
        canvas.drawCircle(cx, cy, r, paint)
    }

    override fun bar(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = width
        paint.color = color.toArgb()
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    override fun crescent(cx: Float, cy: Float, r: Float, color: Color) {
        val outer = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val bite = Path().apply { addCircle(cx + r * BITE_X, cy + r * BITE_Y, r * BITE_R, Path.Direction.CW) }
        outer.op(bite, Path.Op.DIFFERENCE)
        paint.style = Paint.Style.FILL
        paint.color = color.toArgb()
        canvas.drawPath(outer, paint)
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) {
        paint.style = Paint.Style.FILL
        paint.color = color.toArgb()
        // Las casillas de píxeles no se suavizan: así quedan limpias a cualquier tamaño
        paint.isAntiAlias = false
        canvas.drawRect(x, y, x + w, y + h, paint)
        paint.isAntiAlias = true
    }
}

/** El icono del tiempo con el estilo del tema activo. */
@Composable
fun WeatherIcon(sky: Sky, night: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val skin = skin()
    val colors = palette()
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        WeatherIcons.paint(ComposeSink(this), skin, colors, sky, night, 0f, 0f, this.size.minDimension)
    }
}
