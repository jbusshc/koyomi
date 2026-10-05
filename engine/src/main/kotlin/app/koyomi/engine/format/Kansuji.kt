package app.koyomi.engine.format

/** Números en kanji (漢数字), como se escriben en vertical. */
object Kansuji {
    private const val DIGITS = "〇一二三四五六七八九"
    private val UNITS = listOf(1000 to "千", 100 to "百", 10 to "十")

    /** Con sus unidades: 十, 二十一, 百五. Para días, meses y años de era. De 0 a 9999. */
    fun of(number: Int): String {
        require(number in 0..9999) { "Fuera de rango: $number" }
        if (number == 0) return "〇"
        var rest = number
        return buildString {
            for ((value, unit) in UNITS) {
                val count = rest / value
                rest %= value
                // 十 y no 一十: el uno delante de la unidad no se escribe
                if (count > 1) append(DIGITS[count])
                if (count > 0) append(unit)
            }
            if (rest > 0) append(DIGITS[rest])
        }
    }

    /** Cifra a cifra: 二〇二六. Así se escribe un año occidental. */
    fun digits(number: Int): String {
        require(number >= 0) { "Fuera de rango: $number" }
        return number.toString().map { DIGITS[it - '0'] }.joinToString("")
    }
}
