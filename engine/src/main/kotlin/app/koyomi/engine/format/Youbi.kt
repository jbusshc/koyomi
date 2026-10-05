package app.koyomi.engine.format

import java.time.DayOfWeek
import java.time.LocalDate

/** Día de la semana, en el orden de un calendario japonés: empieza en domingo. */
enum class Youbi(val short: String, val kana: String) {
    NICHI("日", "にち"),
    GETSU("月", "げつ"),
    KA("火", "か"),
    SUI("水", "すい"),
    MOKU("木", "もく"),
    KIN("金", "きん"),
    DO("土", "ど");

    /** 日曜日 */
    val full: String get() = "${short}曜日"

    /** にちようび */
    val fullKana: String get() = "${kana}ようび"

    companion object {
        fun of(day: DayOfWeek): Youbi = entries[day.value % 7]

        fun of(date: LocalDate): Youbi = of(date.dayOfWeek)
    }
}
