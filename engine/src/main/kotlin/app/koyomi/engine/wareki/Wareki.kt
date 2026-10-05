package app.koyomi.engine.wareki

import app.koyomi.engine.format.Kansuji
import java.time.LocalDate

/** Un año de era: 令和8年. [year] 1 es 元年. */
data class Wareki(val era: Era, val year: Int) {

    /** 令和8年, 令和元年 */
    val text: String get() = "${era.kanji}${if (year == 1) "元" else year.toString()}年"

    /** 令和八年, 令和元年: con el año en kanji, para escribirlo en vertical */
    val kanjiText: String get() = "${era.kanji}${if (year == 1) "元" else Kansuji.of(year)}年"

    /**
     * Los días que abarca este año de era, o nulo si no existió (平成40年). El primero y el último
     * de una era no duran el año entero: 平成31年 va del 1 de enero al 30 de abril de 2019.
     */
    fun span(): ClosedRange<LocalDate>? {
        val last = lastYear(era)
        if (year < 1 || (last != null && year > last)) return null
        val seireki = era.start.year + year - 1
        val start = if (year == 1) era.start else LocalDate.of(seireki, 1, 1)
        val next = Era.entries.getOrNull(era.ordinal + 1)
        val end = if (next != null && next.start.year == seireki) next.start.minusDays(1) else LocalDate.of(seireki, 12, 31)
        return start..end
    }

    companion object {
        /** El último año que tuvo una era; nulo en la actual, que sigue abierta. */
        fun lastYear(era: Era): Int? = Era.entries.getOrNull(era.ordinal + 1)?.let { it.start.year - era.start.year + 1 }

        /** Japón adoptó el calendario gregoriano este día; antes, las fechas no se corresponden. */
        val FIRST_DATE: LocalDate = LocalDate.of(1873, 1, 1)

        /** La era se decide por fecha exacta: 2019-04-30 es 平成31年 y 2019-05-01 es 令和元年. */
        fun of(date: LocalDate): Wareki? {
            if (date < FIRST_DATE) return null
            val era = Era.entries.last { it.start <= date }
            return Wareki(era, date.year - era.start.year + 1)
        }
    }
}
