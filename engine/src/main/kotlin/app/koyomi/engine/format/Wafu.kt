package app.koyomi.engine.format

/** Los nombres tradicionales de los meses (和風月名). Hoy se usan con el mes del calendario actual. */
enum class Wafu(val kanji: String, val kana: String) {
    MUTSUKI("睦月", "むつき"),
    KISARAGI("如月", "きさらぎ"),
    YAYOI("弥生", "やよい"),
    UZUKI("卯月", "うづき"),
    SATSUKI("皐月", "さつき"),
    MINAZUKI("水無月", "みなづき"),
    FUMIZUKI("文月", "ふみづき"),
    HAZUKI("葉月", "はづき"),
    NAGATSUKI("長月", "ながつき"),
    KANNAZUKI("神無月", "かんなづき"),
    SHIMOTSUKI("霜月", "しもつき"),
    SHIWASU("師走", "しわす");

    companion object {
        fun of(month: Int): Wafu = entries[month - 1]
    }
}
