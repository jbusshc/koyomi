package app.koyomi.engine.weather

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** El cielo, reducido a lo que se puede dibujar. */
enum class Sky {
    CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SHOWER, SNOW, THUNDER;

    companion object {
        /** Del código WMO que da el servicio meteorológico. Lo que no se conoce se dibuja nublado. */
        fun of(wmoCode: Int): Sky = when (wmoCode) {
            0, 1 -> CLEAR
            2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67 -> RAIN
            80, 81, 82 -> SHOWER
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDER
            else -> CLOUDY
        }
    }
}

/** Qué es cada cosa del dibujo. El color lo decide cada tema. */
enum class Tone { SUN, MOON, CLOUD, SHADE, RAIN, SNOW, BOLT, FOG }

/** Una pieza de un dibujo, en un cuadrado de 0 a 1. */
sealed interface Part {
    val tone: Tone

    data class Disc(val cx: Float, val cy: Float, val r: Float, override val tone: Tone) : Part

    /** Un trazo de punta redonda: un rayo, una gota, un tramo de rayo. */
    data class Bar(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float, override val tone: Tone) : Part

    /** Un disco al que le falta un mordisco: la luna. */
    data class Crescent(val cx: Float, val cy: Float, val r: Float, override val tone: Tone) : Part
}

/**
 * Los iconos del tiempo, como datos. El motor no sabe dibujar: solo dice qué piezas lleva cada
 * cielo y dónde. Los temas redondos las pintan como formas; los de píxeles, como una cuadrícula
 * de 16×16 dibujada a mano.
 */
object WeatherArt {
    const val GRID = 16

    /**
     * El dibujo como piezas. [rays]: con rayos el sol es un disco pequeño; sin ellos, un disco
     * llano y más grande, como el de una bandera.
     */
    fun vector(sky: Sky, night: Boolean, rays: Boolean = true): List<Part> = when (sky) {
        Sky.CLEAR -> if (night) {
            listOf(Part.Crescent(0.5f, 0.5f, 0.3f, Tone.MOON), Part.Disc(0.8f, 0.22f, 0.035f, Tone.SNOW), Part.Disc(0.74f, 0.76f, 0.025f, Tone.SNOW))
        } else {
            sun(0.5f, 0.5f, if (rays) 0.2f else 0.3f, rays)
        }
        Sky.PARTLY -> luminary(night, 0.36f, 0.36f, if (rays) 0.16f else 0.2f, rays) + cloud(0.2f, 0.4f, 0.7f, Tone.CLOUD)
        Sky.CLOUDY -> cloud(0.3f, 0.16f, 0.54f, Tone.SHADE) + cloud(0.1f, 0.3f, 0.76f, Tone.CLOUD)
        Sky.FOG -> cloud(0.2f, 0.12f, 0.6f, Tone.SHADE) + listOf(
            Part.Bar(0.16f, 0.66f, 0.84f, 0.66f, 0.06f, Tone.FOG),
            Part.Bar(0.28f, 0.78f, 0.84f, 0.78f, 0.06f, Tone.FOG),
            Part.Bar(0.16f, 0.9f, 0.7f, 0.9f, 0.06f, Tone.FOG),
        )
        Sky.DRIZZLE -> cloud(0.15f, 0.12f, 0.7f, Tone.CLOUD) + listOf(
            Part.Bar(0.34f, 0.72f, 0.31f, 0.8f, 0.05f, Tone.RAIN),
            Part.Bar(0.5f, 0.8f, 0.47f, 0.88f, 0.05f, Tone.RAIN),
            Part.Bar(0.66f, 0.72f, 0.63f, 0.8f, 0.05f, Tone.RAIN),
        )
        Sky.RAIN -> cloud(0.15f, 0.1f, 0.7f, Tone.CLOUD) + listOf(
            Part.Bar(0.35f, 0.66f, 0.29f, 0.84f, 0.06f, Tone.RAIN),
            Part.Bar(0.53f, 0.66f, 0.47f, 0.84f, 0.06f, Tone.RAIN),
            Part.Bar(0.71f, 0.66f, 0.65f, 0.84f, 0.06f, Tone.RAIN),
        )
        Sky.SHOWER -> sun(0.3f, 0.27f, if (rays) 0.13f else 0.19f, rays) + cloud(0.22f, 0.2f, 0.66f, Tone.CLOUD) + listOf(
            Part.Bar(0.4f, 0.66f, 0.35f, 0.82f, 0.055f, Tone.RAIN),
            Part.Bar(0.58f, 0.66f, 0.53f, 0.82f, 0.055f, Tone.RAIN),
            Part.Bar(0.74f, 0.66f, 0.69f, 0.82f, 0.055f, Tone.RAIN),
        )
        Sky.SNOW -> cloud(0.15f, 0.1f, 0.7f, Tone.CLOUD) + listOf(
            Part.Disc(0.32f, 0.7f, 0.045f, Tone.SNOW),
            Part.Disc(0.5f, 0.78f, 0.045f, Tone.SNOW),
            Part.Disc(0.68f, 0.7f, 0.045f, Tone.SNOW),
            Part.Disc(0.41f, 0.9f, 0.045f, Tone.SNOW),
            Part.Disc(0.59f, 0.9f, 0.045f, Tone.SNOW),
        )
        Sky.THUNDER -> cloud(0.15f, 0.08f, 0.7f, Tone.CLOUD) + listOf(
            Part.Bar(0.56f, 0.56f, 0.43f, 0.75f, 0.07f, Tone.BOLT),
            Part.Bar(0.43f, 0.75f, 0.57f, 0.75f, 0.07f, Tone.BOLT),
            Part.Bar(0.57f, 0.75f, 0.44f, 0.95f, 0.07f, Tone.BOLT),
        )
    }

    private fun luminary(night: Boolean, cx: Float, cy: Float, r: Float, rays: Boolean): List<Part> =
        if (night) listOf(Part.Crescent(cx, cy, r * 1.3f, Tone.MOON)) else sun(cx, cy, r, rays)

    private fun sun(cx: Float, cy: Float, r: Float, rays: Boolean): List<Part> = buildList {
        add(Part.Disc(cx, cy, r, Tone.SUN))
        if (!rays) return@buildList
        for (i in 0 until 8) {
            val angle = i * PI / 4
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            add(Part.Bar(cx + c * r * 1.4f, cy + s * r * 1.4f, cx + c * r * 1.85f, cy + s * r * 1.85f, r * 0.26f, Tone.SUN))
        }
    }

    /** Tres bultos y una base redondeada. [w] es el ancho; el alto sale de él. */
    private fun cloud(x: Float, y: Float, w: Float, tone: Tone): List<Part> {
        val h = w * 0.62f
        return listOf(
            Part.Disc(x + w * 0.28f, y + h * 0.62f, h * 0.38f, tone),
            Part.Disc(x + w * 0.52f, y + h * 0.42f, h * 0.42f, tone),
            Part.Disc(x + w * 0.76f, y + h * 0.66f, h * 0.32f, tone),
            Part.Bar(x + w * 0.22f, y + h * 0.82f, x + w * 0.8f, y + h * 0.82f, h * 0.36f, tone),
        )
    }

    /** Qué tono lleva una casilla de las cuadrículas. Nulo: está vacía. */
    fun toneOf(cell: Char): Tone? = when (cell) {
        'S' -> Tone.SUN
        'M' -> Tone.MOON
        'C' -> Tone.CLOUD
        'c' -> Tone.SHADE
        'R' -> Tone.RAIN
        'W' -> Tone.SNOW
        'B' -> Tone.BOLT
        'F' -> Tone.FOG
        else -> null
    }

    /** La cuadrícula de 16×16, de arriba abajo. */
    fun pixels(sky: Sky, night: Boolean): List<String> = when (sky) {
        Sky.CLEAR -> if (night) CLEAR_NIGHT else CLEAR_DAY
        Sky.PARTLY -> if (night) PARTLY_NIGHT else PARTLY_DAY
        Sky.CLOUDY -> CLOUDY
        Sky.FOG -> FOG
        Sky.DRIZZLE -> DRIZZLE
        Sky.RAIN -> RAIN
        Sky.SHOWER -> SHOWER
        Sky.SNOW -> SNOW
        Sky.THUNDER -> THUNDER
    }

    private val CLEAR_DAY = listOf(
        ".......SS.......",
        ".......SS.......",
        "..S..........S..",
        "...S........S...",
        ".....SSSSSS.....",
        "....SSSSSSSS....",
        "....SSSSSSSS....",
        "SS..SSSSSSSS..SS",
        "SS..SSSSSSSS..SS",
        "....SSSSSSSS....",
        "....SSSSSSSS....",
        ".....SSSSSS.....",
        "...S........S...",
        "..S..........S..",
        ".......SS.......",
        ".......SS.......",
    )

    private val CLEAR_NIGHT = listOf(
        "................",
        "................",
        "......MMM..S....",
        "....MMM...SSS...",
        "...MMM.....S....",
        "..MMMM..S.......",
        "..MMMM..........",
        "..MMMM..........",
        "..MMMM.......S..",
        "..MMMM..........",
        "..MMMMM.........",
        "...MMMMMM.......",
        "....MMMMMMM.....",
        "......MMM.......",
        "................",
        "................",
    )

    private val PARTLY_DAY = listOf(
        "....SS..........",
        ".S..SS..S.......",
        "...SSSS.........",
        "..SSSSSS........",
        "S.SSSSSS........",
        "S.SSSSSS........",
        "..SSSSSS........",
        "...SSSS..CCC....",
        ".......CCCCCCC..",
        "....CC.CCCCCCCC.",
        "...CCCCCCCCCCCCC",
        "..CCCCCCCCCCCCCC",
        "..CCCCCCCCCCCCCC",
        "...CCCCCCCCCCCC.",
        "....cccccccccc..",
        "................",
    )

    private val PARTLY_NIGHT = listOf(
        "................",
        "....MMM.........",
        "..MMM...........",
        ".MMM............",
        ".MMM............",
        ".MMM............",
        "..MMM...........",
        "...MMMM..CCC....",
        ".......CCCCCCC..",
        "....CC.CCCCCCCC.",
        "...CCCCCCCCCCCCC",
        "..CCCCCCCCCCCCCC",
        "..CCCCCCCCCCCCCC",
        "...CCCCCCCCCCCC.",
        "....cccccccccc..",
        "................",
    )

    private val CLOUDY = listOf(
        "................",
        "................",
        "................",
        "................",
        "........CCC.....",
        "......CCCCCCC...",
        "..CCC.CCCCCCCC..",
        ".CCCCCCCCCCCCCC.",
        "CCCCCCCCCCCCCCCC",
        "CCCCCCCCCCCCCCCC",
        ".CCCCCCCCCCCCCC.",
        "..cccccccccccc..",
        "................",
        "................",
        "................",
        "................",
    )

    private val FOG = listOf(
        "................",
        "................",
        "................",
        "...FFFFFFFFF....",
        "................",
        "....FFFFFFFFFF..",
        "................",
        "..FFFFFFFFFFF...",
        "................",
        ".....FFFFFFFF...",
        "................",
        "...FFFFFFFFFF...",
        "................",
        "................",
        "................",
        "................",
    )

    // Arriba la nube, y debajo lo que cae de ella
    private val CLOUD_TOP = listOf(
        "................",
        "........CCC.....",
        "......CCCCCCC...",
        "..CCC.CCCCCCCC..",
        ".CCCCCCCCCCCCCC.",
        "CCCCCCCCCCCCCCCC",
        "CCCCCCCCCCCCCCCC",
        ".CCCCCCCCCCCCCC.",
        "..cccccccccccc..",
    )

    private val DRIZZLE = CLOUD_TOP + listOf(
        "................",
        "....R...R...R...",
        "................",
        "..R...R...R.....",
        "................",
        "................",
        "................",
    )

    private val RAIN = CLOUD_TOP + listOf(
        "................",
        "....R...R...R...",
        "....R...R...R...",
        "...R...R...R....",
        "...R...R...R....",
        "................",
        "................",
    )

    private val SHOWER = CLOUD_TOP + listOf(
        "................",
        "...R..R..R..R...",
        "...R..R..R..R...",
        "..R..R..R..R....",
        "..R..R..R..R....",
        "..R..R..R..R....",
        "................",
    )

    private val SNOW = CLOUD_TOP + listOf(
        "................",
        "...W...W...W....",
        "................",
        ".W...W...W...W..",
        "................",
        "...W...W...W....",
        "................",
    )

    private val THUNDER = CLOUD_TOP + listOf(
        ".......BBB......",
        "......BBB.......",
        ".....BBB........",
        "....BBBBBB......",
        ".......BBB......",
        "......BB........",
        ".....B..........",
    )
}
