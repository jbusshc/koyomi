package app.koyomi.engine.wareki

import java.time.LocalDate

/**
 * Las eras desde el calendario gregoriano. Tabla propia en vez de `JapaneseChronology`: que un
 * teléfono conozca 令和 depende de los datos que traiga, y cinco filas quitan la duda.
 * Una era nueva es una fila más, la última.
 */
enum class Era(val kanji: String, val kana: String, val start: LocalDate) {
    MEIJI("明治", "めいじ", LocalDate.of(1868, 1, 25)),
    TAISHO("大正", "たいしょう", LocalDate.of(1912, 7, 30)),
    SHOWA("昭和", "しょうわ", LocalDate.of(1926, 12, 25)),
    HEISEI("平成", "へいせい", LocalDate.of(1989, 1, 8)),
    REIWA("令和", "れいわ", LocalDate.of(2019, 5, 1)),
}
