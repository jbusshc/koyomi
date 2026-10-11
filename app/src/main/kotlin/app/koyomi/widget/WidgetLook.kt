package app.koyomi.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import app.koyomi.R
import app.koyomi.settings.Settings
import app.koyomi.settings.SkinId
import app.koyomi.settings.ThemeOptions
import app.koyomi.settings.ThemeMode
import app.koyomi.ui.skin.PixelFrame
import app.koyomi.ui.skin.Seigaiha
import app.koyomi.ui.skin.YakeiSkin
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
    val options: ThemeOptions = ThemeOptions(),
    val background: WidgetBackground = WidgetBackground.SOLID,
) {
    /** Las marcas de evento, cuadradas en el tema de píxeles. */
    val squareDots: Boolean get() = skin == SkinId.JRPG

    /** Pinta el fondo y devuelve cuánto hay que dejar libre hasta el contenido por cada lado. */
    fun frame(canvas: Canvas, density: Float): Float {
        // Sin fondo: solo el margen; el texto se ayuda de una sombra para leerse sobre cualquier fondo de pantalla
        if (background == WidgetBackground.CLEAR) return 10 * density
        val glass = background == WidgetBackground.GLASS
        val layer = if (glass) canvas.saveLayerAlpha(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), GLASS_ALPHA) else -1
        val inset = when (skin) {
            SkinId.STANDARD -> standardFrame(canvas, density)
            SkinId.JRPG -> jrpgFrame(canvas, density)
            SkinId.TRADITIONAL -> traditionalFrame(canvas, density)
            SkinId.SAKURA -> sakuraFrame(canvas, density)
            SkinId.YAKEI -> yakeiFrame(canvas, density)
            SkinId.AI -> aiFrame(canvas, density)
        }
        if (glass) canvas.restoreToCount(layer)
        return inset
    }

    private fun standardFrame(canvas: Canvas, density: Float): Float {
        val radius = 20 * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (if (dark) colors.surface else colors.background).toArgb() }
        canvas.drawRoundRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), radius, radius, paint)
        return 16 * density
    }

    /** La misma ventana que en la app (PixelFrame), con el grosor y el color que haya elegido el usuario. */
    private fun jrpgFrame(canvas: Canvas, density: Float): Float {
        val t = PixelFrame.border(options.rpgFrame, density)
        // Los lanzadores recortan el widget con esquinas redondas: la ventana se dibuja algo hacia
        // dentro, para que el recorte caiga en lo transparente y no se coma el marco
        val margin = (6 * density).roundToInt().toFloat()
        val fill = Paint()
        for (rect in PixelFrame.rects(canvas.width.toFloat(), canvas.height.toFloat(), t, margin)) {
            fill.color = when (rect.role) {
                PixelFrame.Role.FRAME -> colors.frame
                PixelFrame.Role.SURFACE -> colors.surface
                PixelFrame.Role.HAIRLINE -> colors.frame.copy(alpha = 0.3f)
            }.toArgb()
            canvas.drawRect(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, fill)
        }
        return margin + PixelFrame.inset(t) + 6 * density
    }

    private fun gradientPaint(canvas: Canvas): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, canvas.height.toFloat(), colors.backdropTop.toArgb(), colors.backdropBottom.toArgb(), Shader.TileMode.CLAMP)
    }

    /** Pastel, muy redondeada, con unas flores de cerezo en la esquina. */
    private fun sakuraFrame(canvas: Canvas, density: Float): Float {
        val radius = 26 * density
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, gradientPaint(canvas))
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = colors.rule.toArgb()
        }
        canvas.drawRoundRect(density / 2, density / 2, w - density / 2, h - density / 2, radius, radius, line)
        flower(canvas, w - 24 * density, 22 * density, 18 * density, colors.accent.copy(alpha = 0.18f), 8f)
        flower(canvas, w - 62 * density, 44 * density, 10 * density, colors.accent.copy(alpha = 0.13f), 28f)
        return 16 * density
    }

    private fun flower(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Color, rotation: Float) {
        val petal = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
        for (index in 0 until 5) {
            canvas.save()
            canvas.rotate(rotation + index * 72f, cx, cy)
            canvas.drawOval(cx - radius * 0.3f, cy - radius, cx + radius * 0.3f, cy - radius * 0.05f, petal)
            canvas.restore()
        }
        canvas.drawCircle(cx, cy, radius * 0.12f, petal)
    }

    /** Un degradado de noche con el borde de neón de rosa a cian. */
    private fun yakeiFrame(canvas: Canvas, density: Float): Float {
        val radius = 22 * density
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, gradientPaint(canvas))
        val (pink, cyan) = YakeiSkin.neon(colors)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            shader = LinearGradient(0f, 0f, w, h, pink.copy(alpha = 0.85f).toArgb(), cyan.copy(alpha = 0.85f).toArgb(), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(density, density, w - density, h - density, radius, radius, border)
        return 16 * density
    }

    /** Añil con las olas 青海波 muy tenues detrás, y un filete. */
    private fun aiFrame(canvas: Canvas, density: Float): Float {
        val radius = 14 * density
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surface.toArgb() }
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, fill)
        canvas.save()
        val clip = Path().apply { addRoundRect(0f, 0f, w, h, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = colors.ink.copy(alpha = 0.075f).toArgb()
        }
        val r = 13 * density
        for (scale in Seigaiha.scales(w, h, r)) {
            canvas.drawCircle(scale.cx, scale.cy, r, fill)
            for (fraction in Seigaiha.RINGS) canvas.drawCircle(scale.cx, scale.cy, r * fraction, ring)
        }
        canvas.restore()
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = (if (dark) colors.accent.copy(alpha = 0.3f) else colors.rule).toArgb()
        }
        canvas.drawRoundRect(density / 2, density / 2, w - density / 2, h - density / 2, radius, radius, edge)
        return 14 * density
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
        private const val GLASS_ALPHA = 200

        /** Con el tema y el modo de [config] si los fija, y si no, los de la app. */
        fun of(context: Context, settings: Settings, config: WidgetConfig = WidgetConfig()): WidgetLook {
            val skin = config.skin ?: settings.skin
            val dark = when (config.mode ?: settings.mode) {
                ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val font = when (skin) {
                SkinId.STANDARD -> null
                SkinId.JRPG -> R.font.dotgothic16_regular
                SkinId.TRADITIONAL, SkinId.AI -> R.font.zen_old_mincho_subset
                SkinId.SAKURA, SkinId.YAKEI -> null
            }
            var colors = skinOf(skin).palette(dark, settings.options)
            if (config.background == WidgetBackground.CLEAR) colors = onWallpaper(colors)
            return WidgetLook(skin, dark, colors, font?.let { ResourcesCompat.getFont(context, it) }, settings.options, config.background)
        }

        /** Sin fondo propio, el texto es claro (con sombra): los rojos y azules se aclaran para leerse. */
        private fun onWallpaper(colors: Palette): Palette = colors.copy(
            surface = Color(0xFF1B1B1F),
            ink = Color.White,
            inkSoft = Color.White.copy(alpha = 0.82f),
            rule = Color.White.copy(alpha = 0.35f),
            sunday = Color(0xFFFF9A8F),
            saturday = Color(0xFF9CC2FF),
        )

        /** Una imagen del tamaño exacto del widget, en píxeles de pantalla. */
        fun bitmap(context: Context, options: Bundle, fallbackWidthDp: Int, fallbackHeightDp: Int): Bitmap {
            val density = context.resources.displayMetrics.density
            // En vertical el widget mide el ancho mínimo y el alto máximo que da el lanzador
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: fallbackWidthDp
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: fallbackHeightDp
            // Nunca más grande que la pantalla: el sistema rechaza imágenes de widget que pesan demasiado
            val screen = context.resources.displayMetrics
            val width = (widthDp * density).roundToInt().coerceIn(1, screen.widthPixels)
            val height = (heightDp * density).roundToInt().coerceIn(1, screen.heightPixels)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }
}
