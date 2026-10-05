package app.koyomi.engine.format

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class KansujiTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "0, 〇",
        "1, 一",
        "4, 四",
        "10, 十",
        "11, 十一",
        "20, 二十",
        "29, 二十九",
        "31, 三十一",
        "64, 六十四",
        "100, 百",
        "105, 百五",
        "110, 百十",
        "2026, 二千二十六",
    )
    fun `con unidades`(number: Int, kanji: String) {
        Kansuji.of(number) shouldBe kanji
    }

    @Test
    fun `cifra a cifra, como un año occidental`() {
        Kansuji.digits(2026) shouldBe "二〇二六"
        Kansuji.digits(1989) shouldBe "一九八九"
    }

    @Test
    fun `una fecha entera en kanji`() {
        val date = JaDate.kanji(LocalDate.of(2026, 10, 4))
        date.year shouldBe "令和八年"
        date.monthDay shouldBe "十月四日"
        date.seireki shouldBe "二〇二六年十月四日"
        JaDate.kanji(LocalDate.of(2026, 12, 29)).monthDay shouldBe "十二月二十九日"
        JaDate.kanji(LocalDate.of(2019, 5, 1)).year shouldBe "令和元年"
    }

    @Test
    fun `los nombres antiguos de los meses`() {
        Wafu.of(1).kanji shouldBe "睦月"
        Wafu.of(10).kanji shouldBe "神無月"
        Wafu.of(10).kana shouldBe "かんなづき"
        Wafu.of(12).kanji shouldBe "師走"
        Wafu.entries.size shouldBe 12
    }
}
