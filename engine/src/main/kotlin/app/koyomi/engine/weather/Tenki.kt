package app.koyomi.engine.weather

/**
 * El tiempo, en las palabras de un parte japonés. Parte del código WMO que dan los servicios
 * meteorológicos (0 despejado, 3 cubierto, 61 lluvia débil…).
 */
data class Tenki(val kanji: String, val kana: String) {
    companion object {
        private val UNKNOWN = Tenki("不明", "ふめい")

        fun of(wmoCode: Int): Tenki = when (wmoCode) {
            0 -> Tenki("快晴", "かいせい")
            1 -> Tenki("晴れ", "はれ")
            2 -> Tenki("晴れ時々曇り", "はれ ときどき くもり")
            3 -> Tenki("曇り", "くもり")
            45, 48 -> Tenki("霧", "きり")
            51, 53, 55 -> Tenki("霧雨", "きりさめ")
            56, 57 -> Tenki("着氷性の霧雨", "ちゃくひょうせいの きりさめ")
            61 -> Tenki("小雨", "こさめ")
            63 -> Tenki("雨", "あめ")
            65 -> Tenki("大雨", "おおあめ")
            66, 67 -> Tenki("着氷性の雨", "ちゃくひょうせいの あめ")
            71 -> Tenki("小雪", "こゆき")
            73 -> Tenki("雪", "ゆき")
            75 -> Tenki("大雪", "おおゆき")
            77 -> Tenki("霧雪", "むせつ")
            80, 81 -> Tenki("にわか雨", "にわかあめ")
            82 -> Tenki("激しいにわか雨", "はげしい にわかあめ")
            85, 86 -> Tenki("にわか雪", "にわかゆき")
            95 -> Tenki("雷雨", "らいう")
            96, 99 -> Tenki("雹を伴う雷雨", "ひょうを ともなう らいう")
            else -> UNKNOWN
        }

        /** 24℃, -3℃: redondeado, como en un parte */
        fun temperature(celsius: Double): String = "${Math.round(celsius)}℃"
    }
}
