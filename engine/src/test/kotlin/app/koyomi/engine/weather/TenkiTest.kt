package app.koyomi.engine.weather

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class TenkiTest {

    @Test
    fun `lo más corriente`() {
        Tenki.of(0).kanji shouldBe "快晴"
        Tenki.of(1).kanji shouldBe "晴れ"
        Tenki.of(2).kanji shouldBe "晴れ時々曇り"
        Tenki.of(3).kanji shouldBe "曇り"
        Tenki.of(63).kanji shouldBe "雨"
        Tenki.of(73).kanji shouldBe "雪"
        Tenki.of(95).kanji shouldBe "雷雨"
        Tenki.of(3).kana shouldBe "くもり"
    }

    @Test
    fun `todos los códigos que da el servicio tienen palabra`() {
        val codes = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        for (code in codes) Tenki.of(code).kanji shouldNotBe "不明"
    }

    @Test
    fun `un código desconocido no rompe nada`() {
        Tenki.of(1234).kanji shouldBe "不明"
    }

    @Test
    fun `la temperatura va redondeada`() {
        Tenki.temperature(20.9) shouldBe "21℃"
        Tenki.temperature(17.0) shouldBe "17℃"
        Tenki.temperature(-2.6) shouldBe "-3℃"
        Tenki.temperature(-0.2) shouldBe "0℃"
    }

    @Test
    fun `el viento tiene ocho rumbos`() {
        Tenki.windFrom(0.0) shouldBe "北"
        Tenki.windFrom(359.0) shouldBe "北"
        Tenki.windFrom(45.0) shouldBe "北東"
        Tenki.windFrom(90.0) shouldBe "東"
        Tenki.windFrom(225.0) shouldBe "南西"
        Tenki.wind(3.4, 270.0) shouldBe "西 3m/s"
    }

    @Test
    fun `la lluvia va de diez en diez y el UV con su palabra`() {
        Tenki.chance(43) shouldBe "40%"
        Tenki.chance(0) shouldBe "0%"
        Tenki.chance(100) shouldBe "100%"
        Tenki.uv(1.2) shouldBe "1 弱い"
        Tenki.uv(7.0) shouldBe "7 強い"
        Tenki.uv(12.0) shouldBe "12 非常に強い"
    }
}
