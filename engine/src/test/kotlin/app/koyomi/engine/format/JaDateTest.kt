package app.koyomi.engine.format

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class JaDateTest {
    private val date = JaDate.of(LocalDate.of(2026, 10, 4))

    @Test
    fun `los tres estilos`() {
        date.line(DateStyle.SIMPLE) shouldBe "10月4日（日）"
        date.line(DateStyle.FULL) shouldBe "令和8年10月4日（日）"
        date.line(DateStyle.EDUCATIONAL) shouldBe "令和8年10月4日（日）"
        date.secondLine(DateStyle.FULL) shouldBe null
        date.secondLine(DateStyle.EDUCATIONAL) shouldBe "2026年10月4日"
    }

    @Test
    fun `las partes van sueltas para que el tema las componga`() {
        date.year shouldBe "令和8年"
        date.month shouldBe "10月"
        date.day shouldBe "4日"
        date.youbi shouldBe Youbi.NICHI
        date.youbi.full shouldBe "日曜日"
        date.youbi.fullKana shouldBe "にちようび"
    }

    @Test
    fun `sin era solo queda el año occidental`() {
        val old = JaDate.of(LocalDate.of(1870, 3, 5))
        old.line(DateStyle.FULL) shouldBe "1870年3月5日（土）"
        old.secondLine(DateStyle.EDUCATIONAL) shouldBe null
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "2026-10-04, 日",
        "2026-10-05, 月",
        "2026-10-06, 火",
        "2026-10-07, 水",
        "2026-10-08, 木",
        "2026-10-09, 金",
        "2026-10-10, 土",
        "2024-02-29, 木",
    )
    fun `día de la semana`(day: String, youbi: String) {
        Youbi.of(LocalDate.parse(day)).short shouldBe youbi
    }

    @Test
    fun `la semana japonesa empieza en domingo`() {
        Youbi.entries.joinToString("") { it.short } shouldBe "日月火水木金土"
    }

    @ParameterizedTest(name = "{0}-{1} → {2}")
    @CsvSource(
        "2026, 10, 令和8年10月",
        "2019, 1, 平成31年1月",
        "2019, 4, 平成31年4月",
        "2019, 5, 令和元年5月",
        "1989, 1, 平成元年1月",
    )
    fun `cabecera de mes`(year: Int, month: Int, expected: String) {
        JaDate.monthHeader(year, month) shouldBe expected
    }
}
