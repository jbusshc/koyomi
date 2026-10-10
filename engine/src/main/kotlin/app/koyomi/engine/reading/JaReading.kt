package app.koyomi.engine.reading

import app.koyomi.engine.format.Youbi
import app.koyomi.engine.wareki.Wareki
import java.time.LocalDate
import java.time.LocalTime

/**
 * Cómo se dice lo que Koyomi escribe: la fecha y la hora en hiragana. Lo irregular (los días del
 * 1 al 10, el 14, el 20 y el 24; los minutos que cambian de sonido) va en tablas explícitas, no en
 * reglas, porque es justo lo que quien estudia necesita ver bien.
 */
object JaReading {
    private val DIGITS = listOf("", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう")
    private val TENS = listOf("", "じゅう", "にじゅう", "さんじゅう", "よんじゅう", "ごじゅう", "ろくじゅう", "ななじゅう", "はちじゅう", "きゅうじゅう")

    private val MONTHS = listOf(
        "", "いちがつ", "にがつ", "さんがつ", "しがつ", "ごがつ", "ろくがつ",
        "しちがつ", "はちがつ", "くがつ", "じゅうがつ", "じゅういちがつ", "じゅうにがつ",
    )

    /** Los días con lectura propia. El resto es el número y 「にち」. */
    private val DAYS = mapOf(
        1 to "ついたち", 2 to "ふつか", 3 to "みっか", 4 to "よっか", 5 to "いつか",
        6 to "むいか", 7 to "なのか", 8 to "ようか", 9 to "ここのか", 10 to "とおか",
        14 to "じゅうよっか", 20 to "はつか", 24 to "にじゅうよっか",
    )

    private val MINUTE_ONES = listOf("", "いっぷん", "にふん", "さんぷん", "よんぷん", "ごふん", "ろっぷん", "ななふん", "はっぷん", "きゅうふん")
    private val HUNDREDS = listOf("", "ひゃく", "にひゃく", "さんびゃく", "よんひゃく", "ごひゃく", "ろっぴゃく", "ななひゃく", "はっぴゃく", "きゅうひゃく")
    private val THOUSANDS = listOf("", "せん", "にせん", "さんぜん", "よんせん", "ごせん", "ろくせん", "ななせん", "はっせん", "きゅうせん")

    /** De 1 a 99. [ones] cambia la lectura de las unidades: el 4, el 7 y el 9 se dicen distinto según lo que cuentan. */
    private fun below100(n: Int, ones: (Int) -> String): String {
        require(n in 0..99) { "Fuera de rango: $n" }
        val tens = n / 10
        val unit = n % 10
        return TENS[tens] + if (unit == 0) "" else ones(unit)
    }

    private fun plain(unit: Int) = DIGITS[unit]

    /** Un año: el 4 es 「よ」, como en よねん. */
    private fun forYear(unit: Int) = if (unit == 4) "よ" else DIGITS[unit]

    /** Un día cualquiera o una hora: el 4 es 「よ」, el 7 「しち」 y el 9 「く」. */
    private fun forHour(unit: Int) = when (unit) {
        4 -> "よ"
        7 -> "しち"
        9 -> "く"
        else -> DIGITS[unit]
    }

    /** れいわ・はちねん, れいわ・がんねん: la era y el año, por separado. */
    fun warekiYearParts(wareki: Wareki): List<String> =
        listOf(wareki.era.kana, if (wareki.year == 1) "がんねん" else below100(wareki.year, ::forYear) + "ねん")

    /** れいわ はちねん, れいわ がんねん */
    fun warekiYear(wareki: Wareki): String = warekiYearParts(wareki).joinToString(" ")

    /** にせんにじゅうろくねん. De 1000 a 9999. */
    fun seirekiYear(year: Int): String {
        require(year in 1000..9999) { "Fuera de rango: $year" }
        val rest = year % 100
        val low = if (rest == 0) "" else below100(rest, ::forYear)
        return THOUSANDS[year / 1000] + HUNDREDS[year / 100 % 10] + low + "ねん"
    }

    fun month(month: Int): String = MONTHS[month]

    fun day(day: Int): String {
        require(day in 1..31) { "Fuera de rango: $day" }
        return DAYS[day] ?: (below100(day, ::forHour) + "にち")
    }

    /** La hora en punto: じゅうじ, よじ, れいじ. De 0 a 23. */
    fun hour(hour: Int): String {
        require(hour in 0..23) { "Fuera de rango: $hour" }
        return if (hour == 0) "れいじ" else below100(hour, ::forHour) + "じ"
    }

    /** Los minutos: いっぷん, じゅっぷん, よんじゅうろっぷん. Vacío en punto. */
    fun minute(minute: Int): String {
        require(minute in 0..59) { "Fuera de rango: $minute" }
        if (minute == 0) return ""
        val tens = minute / 10
        val unit = minute % 10
        // 10, 20, 30…: 「じゅっぷん」 con el número delante salvo en el 10
        if (unit == 0) return (if (tens == 1) "" else DIGITS[tens]) + "じゅっぷん"
        return TENS[tens] + MINUTE_ONES[unit]
    }

    /** ごぜん・くじ・さんじゅっぷん. En 12 horas, como se lee en voz alta. */
    fun timeParts(time: LocalTime): List<String> {
        val half = if (time.hour < 12) "ごぜん" else "ごご"
        val hour = if (time.hour == 0) 0 else if (time.hour % 12 == 0) 12 else time.hour % 12
        return listOf(half, hour(hour), minute(time.minute)).filter { it.isNotEmpty() }
    }

    fun time(time: LocalTime): String = timeParts(time).joinToString(" ")

    /** Cada palabra de la fecha: れいわ・はちねん・じゅうがつ・とおか・どようび. Nulo fuera del calendario que Koyomi cubre. */
    fun dateParts(date: LocalDate): List<String>? {
        if (date.year !in 1000..9999) return null
        val year = Wareki.of(date)?.let(::warekiYearParts) ?: listOf(seirekiYear(date.year))
        return year + listOf(month(date.monthValue), day(date.dayOfMonth), Youbi.of(date).fullKana)
    }

    /** れいわ はちねん じゅうがつ とおか どようび */
    fun date(date: LocalDate): String? = dateParts(date)?.joinToString(" ")

    /** Sin el año: じゅうがつ とおか どようび. Para donde no cabe más. */
    fun monthDay(date: LocalDate): String =
        listOf(month(date.monthValue), day(date.dayOfMonth), Youbi.of(date).fullKana).joinToString(" ")
}
