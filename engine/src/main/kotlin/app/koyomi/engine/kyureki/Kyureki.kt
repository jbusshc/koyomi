package app.koyomi.engine.kyureki

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.TreeMap

/** Una fecha del calendario lunisolar antiguo (旧暦). [leap]: el mes es intercalar (閏月). */
data class Kyureki(val month: Int, val day: Int, val leap: Boolean) {
    /** 旧暦8月24日, 旧暦閏6月1日 */
    val text: String get() = "旧暦${if (leap) "閏" else ""}${month}月${day}日"

    companion object {
        // La ruta va entera: al publicar, las clases cambian de nombre y de paquete, y una ruta
        // relativa a la clase deja de encontrar el archivo.
        // Una línea por mes lunar: día en que empieza, número de mes, 1 si es intercalar.
        // La calcula tools/kyureki/make_kyureki.py; cubre de 1990 a 2050.
        private val months: TreeMap<LocalDate, Pair<Int, Boolean>> by lazy {
            TreeMap<LocalDate, Pair<Int, Boolean>>().apply {
                Kyureki::class.java.getResourceAsStream("/app/koyomi/engine/kyureki/kyureki.csv")!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val (start, month, leap) = line.split(',')
                        put(LocalDate.parse(start), month.toInt() to (leap == "1"))
                    }
                }
            }
        }

        val FIRST_DATE: LocalDate = LocalDate.of(1990, 1, 1)
        val LAST_DATE: LocalDate = LocalDate.of(2050, 12, 31)

        /** Nulo fuera de lo que cubre la tabla. */
        fun of(date: LocalDate): Kyureki? {
            if (date < FIRST_DATE || date > LAST_DATE) return null
            val (start, month) = months.floorEntry(date) ?: return null
            return Kyureki(month.first, ChronoUnit.DAYS.between(start, date).toInt() + 1, month.second)
        }
    }
}

/**
 * Los seis días que se repiten en los calendarios japoneses y dicen si la jornada es propicia.
 * No hay astronomía en ellos: salen de sumar el mes y el día del calendario antiguo.
 */
enum class Rokuyo(val kanji: String, val kana: String) {
    TAIAN("大安", "たいあん"),
    SHAKKO("赤口", "しゃっこう"),
    SENSHO("先勝", "せんしょう"),
    TOMOBIKI("友引", "ともびき"),
    SENBU("先負", "せんぶ"),
    BUTSUMETSU("仏滅", "ぶつめつ");

    companion object {
        fun of(date: LocalDate): Rokuyo? = Kyureki.of(date)?.let { entries[(it.month + it.day) % 6] }
    }
}

/** Los veinticuatro términos solares (二十四節気), de 15° en 15° del recorrido del Sol desde el 春分. */
enum class Sekki(val kanji: String, val kana: String) {
    SHUNBUN("春分", "しゅんぶん"),
    SEIMEI("清明", "せいめい"),
    KOKUU("穀雨", "こくう"),
    RIKKA("立夏", "りっか"),
    SHOMAN("小満", "しょうまん"),
    BOSHU("芒種", "ぼうしゅ"),
    GESHI("夏至", "げし"),
    SHOSHO("小暑", "しょうしょ"),
    TAISHO("大暑", "たいしょ"),
    RISSHU("立秋", "りっしゅう"),
    SHOSHO_AUTUMN("処暑", "しょしょ"),
    HAKURO("白露", "はくろ"),
    SHUBUN("秋分", "しゅうぶん"),
    KANRO("寒露", "かんろ"),
    SOKO("霜降", "そうこう"),
    RITTO("立冬", "りっとう"),
    SHOSETSU("小雪", "しょうせつ"),
    TAISETSU("大雪", "たいせつ"),
    TOJI("冬至", "とうじ"),
    SHOKAN("小寒", "しょうかん"),
    DAIKAN("大寒", "だいかん"),
    RISSHUN("立春", "りっしゅん"),
    USUI("雨水", "うすい"),
    KEICHITSU("啓蟄", "けいちつ");

    companion object {
        private val days: Map<LocalDate, Sekki> by lazy {
            Sekki::class.java.getResourceAsStream("/app/koyomi/engine/kyureki/sekki.csv")!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() }.associate { line ->
                    val (day, index) = line.split(',')
                    LocalDate.parse(day) to entries[index.toInt()]
                }
            }
        }

        /** El término que empieza ese día, o nulo si es un día cualquiera. De 1990 a 2050. */
        fun of(date: LocalDate): Sekki? = days[date]
    }
}

/** El año en el ciclo de sesenta (干支): 2026 es 丙午. */
data class Eto(val stem: Char, val branch: Char, val branchKana: String) {
    /** 丙午 */
    val kanji: String get() = "$stem$branch"

    /** 午年, como se dice a diario: «el año del caballo» */
    val yearName: String get() = "${branch}年"

    /** うまどし */
    val yearKana: String get() = "${branchKana}どし"

    companion object {
        private const val STEMS = "甲乙丙丁戊己庚辛壬癸"
        private const val BRANCHES = "子丑寅卯辰巳午未申酉戌亥"
        private val BRANCH_KANA = listOf("ね", "うし", "とら", "う", "たつ", "み", "うま", "ひつじ", "さる", "とり", "いぬ", "い")

        /** Por año del calendario actual, que es como se usa hoy en Japón (cambia el 1 de enero). */
        fun ofYear(year: Int): Eto {
            val stem = Math.floorMod(year - 4, 10)
            val branch = Math.floorMod(year - 4, 12)
            return Eto(STEMS[stem], BRANCHES[branch], BRANCH_KANA[branch])
        }
    }
}
