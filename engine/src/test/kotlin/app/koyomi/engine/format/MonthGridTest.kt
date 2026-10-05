package app.koyomi.engine.format

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {
    private val october = YearMonth.of(2026, 10)

    @Test
    fun `octubre de 2026 empezando en domingo`() {
        val weeks = MonthGrid.weeks(october, Youbi.NICHI)
        weeks.size shouldBe 6
        // El 1 es jueves: la primera fila arranca el domingo 27 de septiembre
        weeks.first().first() shouldBe LocalDate.of(2026, 9, 27)
        weeks.first()[4] shouldBe LocalDate.of(2026, 10, 1)
        weeks[1].first() shouldBe LocalDate.of(2026, 10, 4)
        weeks.last().last() shouldBe LocalDate.of(2026, 11, 7)
    }

    @Test
    fun `empezando en lunes`() {
        val weeks = MonthGrid.weeks(october, Youbi.GETSU)
        weeks.first().first() shouldBe LocalDate.of(2026, 9, 28)
        weeks.first()[3] shouldBe LocalDate.of(2026, 10, 1)
    }

    @Test
    fun `un mes que empieza en el primer día de la semana no deja fila vacía delante`() {
        // Febrero de 2026 empieza en domingo y dura cuatro semanas justas
        val weeks = MonthGrid.weeks(YearMonth.of(2026, 2), Youbi.NICHI)
        weeks.first().first() shouldBe LocalDate.of(2026, 2, 1)
        weeks[3].last() shouldBe LocalDate.of(2026, 2, 28)
    }

    @Test
    fun `cada fila empieza en el día elegido y las fechas son consecutivas`() {
        for (start in Youbi.entries) {
            val days = MonthGrid.weeks(october, start).flatten()
            days.size shouldBe 42
            days.zipWithNext().all { (a, b) -> b == a.plusDays(1) } shouldBe true
            Youbi.of(days.first()) shouldBe start
            (october.atDay(1) in days && october.atEndOfMonth() in days) shouldBe true
        }
    }

    @Test
    fun `cabecera de la semana`() {
        MonthGrid.weekdays(Youbi.NICHI).joinToString("") { it.short } shouldBe "日月火水木金土"
        MonthGrid.weekdays(Youbi.GETSU).joinToString("") { it.short } shouldBe "月火水木金土日"
    }
}
