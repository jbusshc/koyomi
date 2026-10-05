package app.koyomi.calendar

import app.koyomi.engine.span.DayPart
import app.koyomi.engine.span.EventSpan
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Un evento visto desde un día concreto. */
data class DayEvent(val instance: CalendarInstance, val part: DayPart)

object Agenda {
    /**
     * Reparte las instancias entre [days]. Un evento de varios días sale en cada uno que toca.
     * Dentro del día: primero los de todo el día, luego por hora de inicio.
     */
    fun byDay(instances: List<CalendarInstance>, days: List<LocalDate>, zone: ZoneId): Map<LocalDate, List<DayEvent>> {
        val spans = instances.map { it to EventSpan.of(it.beginMillis, it.endMillis, it.allDay, zone) }
        return days.associateWith { day ->
            spans.mapNotNull { (instance, span) -> span.on(day)?.let { DayEvent(instance, it) } }
                .sortedWith(compareBy<DayEvent>({ it.part.order() }, { it.instance.beginMillis }, { it.instance.title }))
        }
    }

    private fun DayPart.order(): LocalTime? = when (this) {
        DayPart.AllDay -> null
        is DayPart.Timed -> start ?: LocalTime.MIN
    }
}
