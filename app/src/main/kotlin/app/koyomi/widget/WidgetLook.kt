package app.koyomi.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import app.koyomi.R
import app.koyomi.settings.Settings
import app.koyomi.settings.SkinId
import app.koyomi.settings.ThemeMode
import app.koyomi.ui.skin.skinOf
import app.koyomi.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * Cómo se ve un widget con el tema y el modo de la app. Un widget solo sabe escribir con las
 * fuentes del teléfono, así que lo que lleva fuente o marco propios se dibuja en una imagen: aquí
 * está lo que esas imágenes comparten.
 */
class WidgetLook(
    val skin: SkinId,
    val dark: Boolean,
    val colors: Palette,
    /** Nulo: la fuente del teléfono. */
    val typeface: Typeface?,
) {
    /** Las marcas de evento, cuadradas en el tema de píxeles. */
    val squareDots: Boolean get() = skin == SkinId.JRPG

    /** Pinta el fondo y devuelve cuánto hay que dejar libre hasta el contenido por cada lado. */
    fun frame(canvas: Canvas, density: Float): Float = when (skin) {
        SkinId.STANDARD -> standardFrame(canvas, density)
        SkinId.JRPG -> jrpgFrame(canvas, density)
        SkinId.TRADITIONAL -> traditionalFrame(canvas, density)
    }

    private fun standardFrame(canvas: Canvas, density: Float): Float {
        val radius = 20 * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (if (dark) colors.surface else colors.background).toArgb() }
        canvas.drawRoundRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), radius, radius, paint)
        return 16 * density
    }

    /** El mismo marco que en la app (JrpgSkin.window): borde con esquinas a escalón y línea fina dentro. */
    private fun jrpgFrame(canvas: Canvas, density: Float): Float {
        val u = (2 * density).roundToInt().coerceAtLeast(1).toFloat()
        val thin = (u / 2).roundToInt().coerceAtLeast(1).toFloat()
        // Los lanzadores recortan el widget con esquinas redondas: la ventana se dibuja algo hacia
        // dentro, para que el recorte caiga en lo transparente y no se coma el marco
        val margin = (6 * density).roundToInt().toFloat()
        val fill = Paint()
        fun stepped(frameInset: Float, cut: Float, color: Color) {
            val inset = margin + frameInset
            fill.color = color.toArgb()
            canvas.drawRect(inset + cut, inset, canvas.width - inset - cut, canvas.height - inset, fill)
            canvas.drawRect(inset, inset + cut, canvas.width - inset, canvas.height - inset - cut, fill)
        }
        stepped(0f, u, colors.frame)
        stepped(2 * u, u, colors.surface)
        stepped(3 * u, 0f, colors.frame.copy(alpha = 0.3f))
        stepped(3 * u + thin, 0f, colors.surface)
        return margin + 6 * u
    }

    /** Una hoja de papel con un filete fino y un sello en la esquina. */
    private fun traditionalFrame(canvas: Canvas, density: Float): Float {
        val radius = 14 * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = colors.background.toArgb()
        canvas.drawRoundRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), radius, radius, paint)
        paint.color = colors.rule.toArgb()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        val line = 5 * density
        canvas.drawRoundRect(line, line, canvas.width - line, canvas.height - line, radius - line / 2, radius - line / 2, paint)
        paint.style = Paint.Style.FILL
        paint.color = colors.accent.toArgb()
        val seal = 5 * density
        canvas.drawRect(canvas.width - 10 * density - seal, 10 * density, canvas.width - 10 * density, 10 * density + seal, paint)
        return 14 * density
    }

    companion object {
        fun of(context: Context, settings: Settings): WidgetLook {
            val dark = when (settings.mode) {
                ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val font = when (settings.skin) {
                SkinId.STANDARD -> null
                SkinId.JRPG -> R.font.dotgothic16_regular
                SkinId.TRADITIONAL -> R.font.zen_old_mincho_subset
            }
            return WidgetLook(settings.skin, dark, skinOf(settings.skin).palette(dark), font?.let { ResourcesCompat.getFont(context, it) })
        }

        /** Una imagen del tamaño exacto del widget, en píxeles de pantalla. */
        fun bitmap(context: Context, options: Bundle, fallbackWidthDp: Int, fallbackHeightDp: Int): Bitmap {
            val density = context.resources.displayMetrics.density
            // En vertical el widget mide el ancho mínimo y el alto máximo que da el lanzador
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: fallbackWidthDp
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: fallbackHeightDp
            return Bitmap.createBitmap((widthDp * density).roundToInt(), (heightDp * density).roundToInt(), Bitmap.Config.ARGB_8888)
        }
    }
}
