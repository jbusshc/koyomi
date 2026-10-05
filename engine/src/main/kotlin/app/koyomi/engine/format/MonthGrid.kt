package app.koyomi.engine.format

import java.time.LocalDate
import java.time.YearMonth

object MonthGrid {
    const val WEEKS = 6

    /** Los siete días en el orden de la cabecera: 日月火水木金土, o 月火水木金土日. */
    fun weekdays(weekStart: Youbi): List<Youbi> = List(7) { Youbi.entries[(weekStart.ordinal + it) % 7] }

    /**
     * Las semanas que enseña la hoja de un mes, con los días del mes anterior y del siguiente que
     * la completan. Siempre seis, para que la cuadrícula no cambie de alto de un mes a otro.
     */
    fun weeks(month: YearMonth, weekStart: Youbi): List<List<LocalDate>> {
        val first = month.atDay(1)
        val lead = (Youbi.of(first).ordinal - weekStart.ordinal + 7) % 7
        val start = first.minusDays(lead.toLong())
        return List(WEEKS) { week -> List(7) { day -> start.plusDays((week * 7 + day).toLong()) } }
    }
}
