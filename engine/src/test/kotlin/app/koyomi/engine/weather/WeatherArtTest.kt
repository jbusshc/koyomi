package app.koyomi.engine.weather

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WeatherArtTest {

    @ParameterizedTest(name = "código {0} → {1}")
    @CsvSource(
        "0, CLEAR", "1, CLEAR", "2, PARTLY", "3, CLOUDY", "45, FOG", "48, FOG",
        "51, DRIZZLE", "55, DRIZZLE", "57, DRIZZLE", "61, RAIN", "65, RAIN", "67, RAIN",
        "71, SNOW", "77, SNOW", "85, SNOW", "80, SHOWER", "82, SHOWER", "95, THUNDER", "99, THUNDER",
    )
    fun `del código del servicio al cielo`(code: Int, sky: Sky) {
        Sky.of(code) shouldBe sky
    }

    @Test
    fun `un código desconocido se dibuja nublado`() {
        Sky.of(1234) shouldBe Sky.CLOUDY
    }

    @Test
    fun `todos los códigos de Tenki tienen cielo y palabra`() {
        val codes = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        for (code in codes) {
            Tenki.of(code).kanji shouldNotBe "不明"
            // Si Tenki sabe nombrarlo, el cielo no debe caer por defecto en «nublado» salvo el 3
            if (code != 3) Sky.of(code) shouldNotBe Sky.CLOUDY
        }
    }

    @Test
    fun `las cuadrículas de píxeles son de 16 por 16 y solo usan tonos conocidos`() {
        for (sky in Sky.entries) for (night in listOf(false, true)) {
            val grid = WeatherArt.pixels(sky, night)
            grid.size shouldBe WeatherArt.GRID
            for (line in grid) {
                line.length shouldBe WeatherArt.GRID
                for (cell in line) if (cell != '.') WeatherArt.toneOf(cell) shouldNotBe null
            }
            // Ningún icono está en blanco
            grid.any { row -> row.any { it != '.' } } shouldBe true
        }
    }

    @Test
    fun `cada cielo tiene piezas y todas caen dentro del cuadrado`() {
        for (sky in Sky.entries) for (night in listOf(false, true)) for (rays in listOf(false, true)) {
            val parts = WeatherArt.vector(sky, night, rays)
            parts.shouldNotBeEmpty()
            for (part in parts) {
                when (part) {
                    is Part.Disc -> {
                        (part.cx - part.r >= 0f && part.cx + part.r <= 1f && part.cy - part.r >= 0f && part.cy + part.r <= 1f) shouldBe true
                    }
                    is Part.Bar -> {
                        val half = part.width / 2
                        (minOf(part.x1, part.x2) - half >= -0.001f && maxOf(part.x1, part.x2) + half <= 1.001f) shouldBe true
                        (minOf(part.y1, part.y2) - half >= -0.001f && maxOf(part.y1, part.y2) + half <= 1.001f) shouldBe true
                    }
                    is Part.Crescent -> {
                        (part.cx - part.r >= 0f && part.cx + part.r <= 1f && part.cy - part.r >= 0f && part.cy + part.r <= 1f) shouldBe true
                    }
                }
            }
        }
    }

    @Test
    fun `de noche el sol se vuelve luna`() {
        WeatherArt.vector(Sky.CLEAR, night = false).any { it.tone == Tone.SUN } shouldBe true
        WeatherArt.vector(Sky.CLEAR, night = true).any { it.tone == Tone.SUN } shouldBe false
        WeatherArt.vector(Sky.CLEAR, night = true).any { it is Part.Crescent } shouldBe true
        WeatherArt.vector(Sky.PARTLY, night = true).any { it is Part.Crescent } shouldBe true
    }

    @Test
    fun `sin rayos el sol es un disco solo y más grande`() {
        val with = WeatherArt.vector(Sky.CLEAR, night = false, rays = true)
        val without = WeatherArt.vector(Sky.CLEAR, night = false, rays = false)
        without.size shouldBe 1
        with.size shouldBe 9
        (without.single() as Part.Disc).r shouldBe 0.3f
    }
}
