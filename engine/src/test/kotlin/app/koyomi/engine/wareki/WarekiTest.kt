package app.koyomi.engine.wareki

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class WarekiTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(
        "2026-10-04, 令和8年",
        "2019-05-01, 令和元年",
        "2019-12-31, 令和元年",
        "2020-01-01, 令和2年",
        "2019-04-30, 平成31年",
        "1989-01-08, 平成元年",
        "1989-01-07, 昭和64年",
        "1926-12-25, 昭和元年",
        "1926-12-24, 大正15年",
        "1912-07-30, 大正元年",
        "1912-07-29, 明治45年",
        "1873-01-01, 明治6年",
    )
    fun `la era se decide por fecha exacta`(date: String, expected: String) {
        Wareki.of(LocalDate.parse(date))?.text shouldBe expected
    }

    @Test
    fun `de un año de era a los días que abarca`() {
        Wareki(Era.REIWA, 8).span() shouldBe LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31)
        Wareki(Era.REIWA, 1).span() shouldBe LocalDate.of(2019, 5, 1)..LocalDate.of(2019, 12, 31)
        Wareki(Era.HEISEI, 31).span() shouldBe LocalDate.of(2019, 1, 1)..LocalDate.of(2019, 4, 30)
        Wareki(Era.SHOWA, 64).span() shouldBe LocalDate.of(1989, 1, 1)..LocalDate.of(1989, 1, 7)
        Wareki(Era.HEISEI, 32).span() shouldBe null
        Wareki(Era.SHOWA, 0).span() shouldBe null
        Wareki.lastYear(Era.HEISEI) shouldBe 31
        Wareki.lastYear(Era.REIWA) shouldBe null
    }

    @Test
    fun `ida y vuelta entre año de era y fecha`() {
        for (era in Era.entries) {
            for (year in 1..(Wareki.lastYear(era) ?: 30)) {
                val span = Wareki(era, year).span()!!
                // 明治 empieza antes de 1873, cuando aún no hay correspondencia de fechas
                if (span.start >= Wareki.FIRST_DATE) Wareki.of(span.start) shouldBe Wareki(era, year)
                if (span.endInclusive >= Wareki.FIRST_DATE) Wareki.of(span.endInclusive) shouldBe Wareki(era, year)
            }
        }
    }

    @Test
    fun `antes del calendario gregoriano no hay era`() {
        Wareki.of(LocalDate.of(1872, 12, 31)) shouldBe null
    }

    @Test
    fun `toda fecha desde 1873 tiene era y un año de al menos 1`() {
        var date = Wareki.FIRST_DATE
        while (date.year <= 2100) {
            val wareki = Wareki.of(date)
            wareki shouldNotBe null
            wareki!!.year shouldBeGreaterThanOrEqual 1
            (wareki.era.start <= date) shouldBe true
            date = date.plusDays(1)
        }
    }
}
