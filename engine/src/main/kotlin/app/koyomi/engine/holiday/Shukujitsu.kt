package app.koyomi.engine.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

/**
 * Festivos nacionales de Japón (国民の祝日) y los días de descanso que derivan de ellos.
 *
 * Hasta 2021 salen de la tabla oficial de la Oficina del Gabinete (内閣府), porque la ley cambió
 * varias veces y hubo traslados puntuales (los olímpicos de 2020 y 2021, los de 2019 por el cambio
 * de era). Desde 2022 se calculan con la ley vigente. Antes de 1955 no hay datos.
 */
object Shukujitsu {
    const val FURIKAE = "振替休日"
    const val KOKUMIN_NO_KYUJITSU = "国民の休日"

    private const val FIRST_RULE_YEAR = 2022
    // Ruta entera: al publicar, las clases cambian de paquete y una relativa deja de valer
    private const val TABLE = "/app/koyomi/engine/holiday/shukujitsu-1955-2021.csv"

    private val table: Map<LocalDate, String> by lazy {
        Shukujitsu::class.java.getResourceAsStream(TABLE)!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }.associate { line ->
                val (date, name) = line.split(',', limit = 2)
                LocalDate.parse(date) to name
            }
        }
    }

    private val byYear = ConcurrentHashMap<Int, Map<LocalDate, String>>()

    /** El nombre del festivo, o nulo si es un día cualquiera. */
    fun of(date: LocalDate): String? = inYear(date.year)[date]

    fun inYear(year: Int): Map<LocalDate, String> = byYear.getOrPut(year) {
        if (year < FIRST_RULE_YEAR) table.filterKeys { it.year == year } else rules(year)
    }

    private fun rules(year: Int): Map<LocalDate, String> {
        val named = sortedMapOf<LocalDate, String>()
        fun fixed(month: Int, day: Int, name: String) { named[LocalDate.of(year, month, day)] = name }
        fun monday(month: Int, nth: Int, name: String) {
            named[LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(nth, DayOfWeek.MONDAY))] = name
        }

        fixed(1, 1, "元日")
        monday(1, 2, "成人の日")
        fixed(2, 11, "建国記念の日")
        fixed(2, 23, "天皇誕生日")
        fixed(3, equinoxDay(year, 20.8431), "春分の日")
        fixed(4, 29, "昭和の日")
        fixed(5, 3, "憲法記念日")
        fixed(5, 4, "みどりの日")
        fixed(5, 5, "こどもの日")
        monday(7, 3, "海の日")
        fixed(8, 11, "山の日")
        monday(9, 3, "敬老の日")
        fixed(9, equinoxDay(year, 23.2488), "秋分の日")
        monday(10, 2, "スポーツの日")
        fixed(11, 3, "文化の日")
        fixed(11, 23, "勤労感謝の日")

        val all = named.toSortedMap()
        // 振替休日: un festivo en domingo pasa al primer día siguiente que no sea festivo
        for (date in named.keys) {
            if (date.dayOfWeek != DayOfWeek.SUNDAY) continue
            var substitute = date.plusDays(1)
            while (substitute in named) substitute = substitute.plusDays(1)
            all[substitute] = FURIKAE
        }
        // 国民の休日: un día entre dos festivos también se descansa
        for (date in named.keys) {
            val between = date.plusDays(1)
            if (between !in all && between.plusDays(1) in named) {
                all[between] = KOKUMIN_NO_KYUJITSU
            }
        }
        return all
    }

    /**
     * Los equinoccios se anuncian oficialmente en febrero del año anterior; esta es la aproximación
     * habitual, válida de 1980 a 2099.
     */
    private fun equinoxDay(year: Int, base: Double): Int =
        (base + 0.242194 * (year - 1980)).toInt() - (year - 1980) / 4
}
