package app.koyomi.engine.span

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class EventSpanTest {
    private val santiago = ZoneId.of("America/Santiago")
    private val oct4 = LocalDate.of(2026, 10, 4)

    private fun utcMillis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun millis(zone: ZoneId, date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @ParameterizedTest
    @ValueSource(strings = ["America/Santiago", "America/Los_Angeles", "UTC", "Europe/Madrid", "Asia/Tokyo", "Pacific/Kiritimati"])
    fun `un evento de todo el día cae en su día en cualquier zona`(zoneId: String) {
        val span = EventSpan.of(utcMillis(oct4), utcMillis(oct4.plusDays(1)), allDay = true, zone = ZoneId.of(zoneId))
        span.firstDay shouldBe oct4
        span.lastDay shouldBe oct4
        span.on(oct4) shouldBe DayPart.AllDay
        span.on(oct4.minusDays(1)) shouldBe null
        span.on(oct4.plusDays(1)) shouldBe null
    }

    @Test
    fun `un evento de todo el día de tres días`() {
        val span = EventSpan.of(utcMillis(oct4), utcMillis(oct4.plusDays(3)), allDay = true, zone = santiago)
        span.firstDay shouldBe oct4
        span.lastDay shouldBe oct4.plusDays(2)
    }

    @Test
    fun `un evento con hora dentro de un día`() {
        val span = EventSpan.of(millis(santiago, oct4, 9), millis(santiago, oct4, 10, 30), allDay = false, zone = santiago)
        span.on(oct4) shouldBe DayPart.Timed(LocalTime.of(9, 0), LocalTime.of(10, 30))
        span.lastDay shouldBe oct4
    }

    @Test
    fun `terminar a medianoche no ocupa el día siguiente`() {
        val span = EventSpan.of(millis(santiago, oct4, 22), millis(santiago, oct4.plusDays(1), 0), allDay = false, zone = santiago)
        span.lastDay shouldBe oct4
        span.on(oct4) shouldBe DayPart.Timed(LocalTime.of(22, 0), LocalTime.MIDNIGHT)
        span.on(oct4.plusDays(1)) shouldBe null
    }

    @Test
    fun `cruzar la medianoche aparece en los dos días, cada uno con su mitad`() {
        val span = EventSpan.of(millis(santiago, oct4, 22), millis(santiago, oct4.plusDays(1), 2), allDay = false, zone = santiago)
        span.on(oct4) shouldBe DayPart.Timed(LocalTime.of(22, 0), null)
        span.on(oct4.plusDays(1)) shouldBe DayPart.Timed(null, LocalTime.of(2, 0))
    }

    @Test
    fun `el día intermedio de un evento largo es todo el día`() {
        val span = EventSpan.of(millis(santiago, oct4, 18), millis(santiago, oct4.plusDays(2), 12), allDay = false, zone = santiago)
        span.on(oct4.plusDays(1)) shouldBe DayPart.AllDay
    }

    @Test
    fun `un evento sin duración solo tiene inicio`() {
        val at = millis(santiago, oct4, 14)
        EventSpan.of(at, at, allDay = false, zone = santiago).on(oct4) shouldBe DayPart.Timed(LocalTime.of(14, 0), null)
    }

    @Test
    fun `la misma instancia cae en días distintos según la zona`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val begin = millis(tokyo, oct4, 9)
        val end = millis(tokyo, oct4, 10)
        EventSpan.of(begin, end, allDay = false, zone = tokyo).firstDay shouldBe oct4
        // Las 9 de la mañana en Tokio son las 21 del día anterior en Santiago
        EventSpan.of(begin, end, allDay = false, zone = santiago).on(oct4.minusDays(1)) shouldBe
            DayPart.Timed(LocalTime.of(21, 0), LocalTime.of(22, 0))
    }

    @Test
    fun `el día del cambio de hora dura 23 horas y sigue siendo un solo día`() {
        // Chile adelanta el reloj la medianoche del 5 al 6 de septiembre de 2026
        val day = LocalDate.of(2026, 9, 6)
        val span = EventSpan.of(millis(santiago, day, 10), millis(santiago, day, 23, 30), allDay = false, zone = santiago)
        span.firstDay shouldBe day
        span.lastDay shouldBe day
    }
}
