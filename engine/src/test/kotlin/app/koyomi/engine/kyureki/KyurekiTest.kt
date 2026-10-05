package app.koyomi.engine.kyureki

import app.koyomi.engine.holiday.Shukujitsu
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class KyurekiTest {

    @ParameterizedTest(name = "旧正月 {0}")
    @CsvSource("2023-01-22", "2024-02-10", "2025-01-29", "2026-02-17", "2027-02-07", "2028-01-27")
    fun `el año nuevo lunar, en hora de Japón`(day: String) {
        val date = LocalDate.parse(day)
        Kyureki.of(date) shouldBe Kyureki(1, 1, false)
        // La víspera es el último día del mes 12
        Kyureki.of(date.minusDays(1))!!.month shouldBe 12
    }

    @ParameterizedTest(name = "{0} empieza 閏{1}月")
    @CsvSource("2020-05-23, 4", "2023-03-22, 2", "2025-07-25, 6", "2028-06-23, 5", "2031-04-22, 3")
    fun `los meses intercalares`(day: String, month: Int) {
        Kyureki.of(LocalDate.parse(day)) shouldBe Kyureki(month, 1, true)
    }

    @Test
    fun `se escribe con 閏 cuando toca`() {
        Kyureki(8, 24, false).text shouldBe "旧暦8月24日"
        Kyureki(6, 1, true).text shouldBe "旧暦閏6月1日"
    }

    @Test
    fun `fuera de la tabla no hay fecha antigua`() {
        Kyureki.of(LocalDate.of(1989, 12, 31)) shouldBe null
        Kyureki.of(LocalDate.of(2051, 1, 1)) shouldBe null
        Rokuyo.of(LocalDate.of(2051, 1, 1)) shouldBe null
    }

    @Test
    fun `toda fecha de la tabla tiene mes y día creíbles`() {
        var date = Kyureki.FIRST_DATE
        while (date <= Kyureki.LAST_DATE) {
            val old = Kyureki.of(date)
            old shouldNotBe null
            (old!!.month in 1..12 && old.day in 1..30) shouldBe true
            date = date.plusDays(1)
        }
    }

    @Test
    fun `六曜 sale del mes y el día antiguos`() {
        // El día 1 del mes 1 es siempre 先勝
        Rokuyo.of(LocalDate.of(2026, 2, 17)) shouldBe Rokuyo.SENSHO
        Rokuyo.of(LocalDate.of(2026, 2, 18)) shouldBe Rokuyo.TOMOBIKI
        // El 1 de enero de 2025 fue el día 2 del mes 12 antiguo
        Kyureki.of(LocalDate.of(2025, 1, 1)) shouldBe Kyureki(12, 2, false)
        Rokuyo.of(LocalDate.of(2025, 1, 1)) shouldBe Rokuyo.SENSHO
    }

    @Test
    fun `dentro de un mes los seis días van en orden`() {
        val start = LocalDate.of(2026, 2, 17)
        (0 until 12).map { Rokuyo.of(start.plusDays(it.toLong()))!!.kanji }.joinToString(" ") shouldBe
            "先勝 友引 先負 仏滅 大安 赤口 先勝 友引 先負 仏滅 大安 赤口"
    }

    @Test
    fun `los términos solares de 2026`() {
        Sekki.of(LocalDate.of(2026, 2, 4)) shouldBe Sekki.RISSHUN
        Sekki.of(LocalDate.of(2026, 6, 21)) shouldBe Sekki.GESHI
        Sekki.of(LocalDate.of(2026, 10, 8)) shouldBe Sekki.KANRO
        Sekki.of(LocalDate.of(2026, 12, 22)) shouldBe Sekki.TOJI
        Sekki.of(LocalDate.of(2026, 10, 4)) shouldBe null
        Sekki.entries.size shouldBe 24
    }

    @Test
    fun `los equinoccios coinciden con los festivos, que son oficiales`() {
        for (year in 1990..2027) {
            val holidays = Shukujitsu.inYear(year)
            holidays.filterValues { it == "春分の日" }.keys.single().let { Sekki.of(it) shouldBe Sekki.SHUNBUN }
            holidays.filterValues { it == "秋分の日" }.keys.single().let { Sekki.of(it) shouldBe Sekki.SHUBUN }
        }
    }

    @Test
    fun `el ciclo de sesenta años`() {
        Eto.ofYear(2026).kanji shouldBe "丙午"
        Eto.ofYear(2026).yearName shouldBe "午年"
        Eto.ofYear(2026).yearKana shouldBe "うまどし"
        Eto.ofYear(2024).kanji shouldBe "甲辰"
        Eto.ofYear(1984).kanji shouldBe "甲子"
        Eto.ofYear(2025).yearKana shouldBe "みどし"
    }
}
