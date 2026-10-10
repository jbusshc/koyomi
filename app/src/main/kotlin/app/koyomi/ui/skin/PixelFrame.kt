package app.koyomi.ui.skin

import app.koyomi.settings.FrameWeight
import kotlin.math.roundToInt

/**
 * La ventana del tema RPG como una lista de rectángulos. La pintan igual la app (Compose) y los
 * widgets (un lienzo de Android), y así los dos marcos no pueden desajustarse.
 */
object PixelFrame {
    enum class Role { FRAME, SURFACE, HAIRLINE }

    class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val role: Role)

    /** El grosor del borde en dp. El «píxel» del tema. */
    fun borderDp(weight: FrameWeight): Float = when (weight) {
        FrameWeight.THIN -> 1.5f
        FrameWeight.NORMAL -> 2.5f
        FrameWeight.THICK -> 4f
    }

    /** El grosor del borde en píxeles enteros de pantalla, para que los bordes salgan nítidos. */
    fun border(weight: FrameWeight, density: Float): Float = (borderDp(weight) * density).roundToInt().coerceAtLeast(1).toFloat()

    /**
     * Una ventana de [width] por [height] píxeles con un borde de [t]: marco con las esquinas a
     * escalón, el fondo, y una línea fina por dentro. [margin] la separa del borde del lienzo.
     */
    fun rects(width: Float, height: Float, t: Float, margin: Float = 0f): List<Rect> {
        val hair = (t / 2).roundToInt().coerceAtLeast(1).toFloat()
        val list = ArrayList<Rect>(8)
        fun stepped(inset: Float, cut: Float, role: Role) {
            val i = margin + inset
            list += Rect(i + cut, i, width - 2 * (i + cut), height - 2 * i, role)
            list += Rect(i, i + cut, width - 2 * i, height - 2 * (i + cut), role)
        }
        stepped(0f, t, Role.FRAME)
        stepped(t, t, Role.SURFACE)
        stepped(2 * t, 0f, Role.HAIRLINE)
        stepped(2 * t + hair, 0f, Role.SURFACE)
        return list
    }

    /** Lo que hay que dejar libre desde el borde de la ventana hasta el contenido, sin el margen. */
    fun inset(t: Float): Float = 2 * t + (t / 2).roundToInt().coerceAtLeast(1)
}
