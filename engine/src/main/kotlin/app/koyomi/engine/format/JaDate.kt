package app.koyomi.engine.format

import app.koyomi.engine.wareki.Wareki
import java.time.LocalDate

enum class DateStyle {
    /** 10月4日（日） */
    SIMPLE,

    /** 令和8年10月4日（日） */
    FULL,

    /** Como [FULL], con la fecha occidental debajo: 2026年10月4日 */
    EDUCATIONAL,
}

/**
 * Una fecha por partes, para que cada tema las componga a su manera (una línea, una ventana,
 * en vertical). No depende del `Locale` del teléfono: sale igual en cualquier idioma.
 */
data class JaDateText(
    /** 令和8年. Nulo antes de 1873, cuando solo vale el año occidental. */
    val wareki: Wareki?,
    /** 2026年 */
    val seirekiYear: String,
    /** 10月 */
    val month: String,
    /** 4日 */
    val day: String,
    val youbi: Youbi,
) {
    /** 10月4日 */
    val monthDay: String get() = month + day

    /** 令和8年, o 2026年 si no hay era */
    val year: String get() = wareki?.text ?: seirekiYear

    /** 2026年10月4日 */
    val seireki: String get() = seirekiYear + monthDay

    /** La línea principal. Paréntesis de ancho completo y sin espacios, como se escribe en Japón. */
    fun line(style: DateStyle): String = when (style) {
        DateStyle.SIMPLE -> "$monthDay（${youbi.short}）"
        DateStyle.FULL, DateStyle.EDUCATIONAL -> "$year$monthDay（${youbi.short}）"
    }

    /** La línea occidental del estilo educativo; nula si no aporta nada. */
    fun secondLine(style: DateStyle): String? =
        if (style == DateStyle.EDUCATIONAL && wareki != null) seireki else null
}

object JaDate {
    fun of(date: LocalDate): JaDateText = JaDateText(
        wareki = Wareki.of(date),
        seirekiYear = "${date.year}年",
        month = "${date.monthValue}月",
        day = "${date.dayOfMonth}日",
        youbi = Youbi.of(date),
    )

    /** Cabecera de un mes: 令和8年10月. En enero de 2019 es 平成31年1月 y en mayo, 令和元年5月. */
    fun monthHeader(year: Int, month: Int): String {
        val wareki = Wareki.of(LocalDate.of(year, month, 1).let { it.withDayOfMonth(it.lengthOfMonth()) })
        return "${wareki?.text ?: "${year}年"}${month}月"
    }
}
