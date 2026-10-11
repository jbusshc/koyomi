package app.koyomi.ui.skin

/**
 * 青海波: las olas concéntricas del estampado japonés. Aquí solo la geometría, para que la
 * pinten igual la app (Compose) y los widgets (un lienzo de Android).
 *
 * Cada «escama» es un círculo de radio [radius] con tres anillos; las filas se desplazan media
 * escama y se solapan, y de cada una solo se ve su parte de arriba. Hay que pintarlas de arriba
 * abajo, rellenando cada círculo con el color del fondo antes de trazar sus anillos.
 */
object Seigaiha {
    /** Los radios de los anillos de una escama, de fuera adentro, en proporción al radio. */
    val RINGS = floatArrayOf(1f, 0.68f, 0.36f)

    class Scale(val cx: Float, val cy: Float)

    /** Los centros de las escamas que cubren un lienzo de [width] por [height], de arriba abajo. */
    fun scales(width: Float, height: Float, radius: Float): List<Scale> {
        val list = ArrayList<Scale>()
        val rowStep = radius / 2f
        var row = 0
        var y = -radius
        while (y <= height + radius) {
            // Las filas impares se corren una escama entera hacia un lado
            var x = if (row % 2 == 0) 0f else radius
            while (x <= width + 2 * radius) {
                list += Scale(x - radius, y)
                x += 2 * radius
            }
            y += rowStep
            row++
        }
        return list
    }
}
