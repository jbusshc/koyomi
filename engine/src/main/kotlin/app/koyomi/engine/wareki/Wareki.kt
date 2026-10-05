package app.koyomi.engine.wareki

import java.time.LocalDate

/** Un año de era: 令和8年. [year] 1 es 元年. */
data class Wareki(val era: Era, val year: Int) {

    /** 令和8年, 令和元年 */
    val text: String get() = "${era.kanji}${if (year == 1) "元" else year.toString()}年"

    companion object {
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
