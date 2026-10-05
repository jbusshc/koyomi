package app.koyomi.engine.holiday

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ShukujitsuTest {

    /** syukujitsu.csv de la Oficina del Gabinete (内閣府), pasado a UTF-8 y fechas ISO. */
    private val official: Map<LocalDate, String> =
        javaClass.getResourceAsStream("/syukujitsu.csv")!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }.associate { line ->
                val (date, name) = line.split(',', limit = 2)
                LocalDate.parse(date) to name
            }
        }

    // La tabla oficial llama «休日» a los dos tipos de descanso derivado
    private fun String.asOfficial() =
        if (this == Shukujitsu.FURIKAE || this == Shukujitsu.KOKUMIN_NO_KYUJITSU) "休日" else this

    @Test
    fun `las reglas coinciden con la tabla oficial, año por año`() {
        val years = official.keys.map { it.year }.filter { it >= 2022 }.distinct()
        years.size shouldBeGreaterThanOrEqual 6
        for (year in years) {
            Shukujitsu.inYear(year).mapValues { it.value.asOfficial() } shouldBe official.filterKeys { it.year == year }
        }
    }

    @Test
    fun `hasta 2021 se usa la tabla, con sus traslados`() {
        Shukujitsu.of(LocalDate.of(2021, 7, 23)) shouldBe "スポーツの日"
        Shukujitsu.of(LocalDate.of(2021, 10, 11)) shouldBe null
        Shukujitsu.of(LocalDate.of(2018, 12, 23)) shouldBe "天皇誕生日"
        Shukujitsu.inYear(1954) shouldBe emptyMap()
    }

    @Test
    fun `septiembre de 2026 encadena tres días`() {
        Shukujitsu.of(LocalDate.of(2026, 9, 21)) shouldBe "敬老の日"
        Shukujitsu.of(LocalDate.of(2026, 9, 22)) shouldBe Shukujitsu.KOKUMIN_NO_KYUJITSU
        Shukujitsu.of(LocalDate.of(2026, 9, 23)) shouldBe "秋分の日"
        Shukujitsu.of(LocalDate.of(2026, 9, 24)) shouldBe null
    }

    @Test
    fun `un festivo en domingo pasa al lunes`() {
        Shukujitsu.of(LocalDate.of(2027, 3, 21)) shouldBe "春分の日"
        Shukujitsu.of(LocalDate.of(2027, 3, 22)) shouldBe Shukujitsu.FURIKAE
    }

    @Test
    fun `los tres festivos de mayo en domingo empujan el descanso hasta el miércoles`() {
        // 2026-05-03 es domingo; el 4 y el 5 ya son festivos
        Shukujitsu.of(LocalDate.of(2026, 5, 6)) shouldBe Shukujitsu.FURIKAE
    }
}
