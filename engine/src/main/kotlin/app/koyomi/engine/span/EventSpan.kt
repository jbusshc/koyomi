package app.koyomi.engine.span

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Lo que un evento ocupa de un día concreto. */
sealed interface DayPart {
    /** Todo el día: un evento sin hora, o el día intermedio de uno que dura varios. */
    data object AllDay : DayPart

    /** [start] nulo: viene del día anterior. [end] nulo: sigue al día siguiente, o no tiene duración. */
    data class Timed(val start: LocalTime?, val end: LocalTime?) : DayPart
}

/**
 * Los días que ocupa una instancia del calendario del sistema. Aquí viven las tres reglas que un
 * calendario suele equivocar:
 *
 * - Un evento de todo el día se guarda como medianoche UTC: sus fechas se leen en UTC, no en la
 *   zona del teléfono, o en América aparece el día anterior.
 * - El final es exclusivo: un evento que termina a las 00:00 no ocupa ese día.
 * - Los límites de día salen de la zona horaria, nunca de sumar 24 horas.
 */
class EventSpan private constructor(
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    private val start: LocalDateTime?,
    private val end: LocalDateTime?,
) {
    operator fun contains(day: LocalDate): Boolean = day in firstDay..lastDay

    /** Nulo si el evento no toca ese día. */
    fun on(day: LocalDate): DayPart? {
        if (day !in this) return null
        if (start == null || end == null) return DayPart.AllDay
        val startsToday = day == firstDay
        val endsToday = day == lastDay
        return when {
            !startsToday && !endsToday -> DayPart.AllDay
            start == end -> DayPart.Timed(start.toLocalTime(), null)
            else -> DayPart.Timed(
                start = start.toLocalTime().takeIf { startsToday },
                end = end.toLocalTime().takeIf { endsToday },
            )
        }
    }

    companion object {
        fun of(beginMillis: Long, endMillis: Long, allDay: Boolean, zone: ZoneId): EventSpan {
            val begin = Instant.ofEpochMilli(beginMillis)
            val end = Instant.ofEpochMilli(maxOf(endMillis, beginMillis))
            // El último instante que el evento ocupa de verdad
            val last = if (end > begin) end.minusMillis(1) else begin
            if (allDay) {
                return EventSpan(
                    firstDay = begin.atZone(ZoneOffset.UTC).toLocalDate(),
                    lastDay = last.atZone(ZoneOffset.UTC).toLocalDate(),
                    start = null,
                    end = null,
                )
            }
            return EventSpan(
                firstDay = begin.atZone(zone).toLocalDate(),
                lastDay = last.atZone(zone).toLocalDate(),
                start = begin.atZone(zone).toLocalDateTime(),
                end = end.atZone(zone).toLocalDateTime(),
            )
        }
    }
}
