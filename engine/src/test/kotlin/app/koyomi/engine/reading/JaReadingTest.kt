package app.koyomi.engine.reading

import app.koyomi.engine.wareki.Era
import app.koyomi.engine.wareki.Wareki
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalTime

class JaReadingTest {

    @ParameterizedTest(name = "{0}日 → {1}")
    @CsvSource(
        "1, ついたち", "2, ふつか", "3, みっか", "4, よっか", "5, いつか", "6, むいか", "7, なのか",
        "8, ようか", "9, ここのか", "10, とおか", "11, じゅういちにち", "12, じゅうににち",
        "13, じゅうさんにち", "14, じゅうよっか", "15, じゅうごにち", "16, じゅうろくにち",
        "17, じゅうしちにち", "18, じゅうはちにち", "19, じゅうくにち", "20, はつか",
        "21, にじゅういちにち", "24, にじゅうよっか", "27, にじゅうしちにち", "29, にじゅうくにち",
        "30, さんじゅうにち", "31, さんじゅういちにち",
    )
    fun `los días`(day: Int, reading: String) {
        JaReading.day(day) shouldBe reading
    }

    @ParameterizedTest(name = "{0}月 → {1}")
    @CsvSource(
        "1, いちがつ", "4, しがつ", "7, しちがつ", "9, くがつ", "10, じゅうがつ", "11, じゅういちがつ", "12, じゅうにがつ",
    )
    fun `los meses`(month: Int, reading: String) {
        JaReading.month(month) shouldBe reading
    }

    @Test
    fun `los años de era`() {
        JaReading.warekiYear(Wareki(Era.REIWA, 8)) shouldBe "れいわ はちねん"
        JaReading.warekiYear(Wareki(Era.REIWA, 1)) shouldBe "れいわ がんねん"
        JaReading.warekiYear(Wareki(Era.REIWA, 4)) shouldBe "れいわ よねん"
        JaReading.warekiYear(Wareki(Era.HEISEI, 24)) shouldBe "へいせい にじゅうよねん"
        JaReading.warekiYear(Wareki(Era.HEISEI, 31)) shouldBe "へいせい さんじゅういちねん"
        JaReading.warekiYear(Wareki(Era.SHOWA, 64)) shouldBe "しょうわ ろくじゅうよねん"
        JaReading.warekiYear(Wareki(Era.SHOWA, 20)) shouldBe "しょうわ にじゅうねん"
    }

    @Test
    fun `los años occidentales`() {
        JaReading.seirekiYear(2026) shouldBe "にせんにじゅうろくねん"
        JaReading.seirekiYear(2024) shouldBe "にせんにじゅうよねん"
        JaReading.seirekiYear(2000) shouldBe "にせんねん"
        JaReading.seirekiYear(1989) shouldBe "せんきゅうひゃくはちじゅうきゅうねん"
        JaReading.seirekiYear(1868) shouldBe "せんはっぴゃくろくじゅうはちねん"
        JaReading.seirekiYear(1600) shouldBe "せんろっぴゃくねん"
        JaReading.seirekiYear(3333) shouldBe "さんぜんさんびゃくさんじゅうさんねん"
    }

    @ParameterizedTest(name = "{0}時 → {1}")
    @CsvSource(
        "0, れいじ", "1, いちじ", "2, にじ", "3, さんじ", "4, よじ", "5, ごじ", "6, ろくじ", "7, しちじ",
        "8, はちじ", "9, くじ", "10, じゅうじ", "11, じゅういちじ", "12, じゅうにじ", "14, じゅうよじ",
        "17, じゅうしちじ", "19, じゅうくじ", "21, にじゅういちじ", "23, にじゅうさんじ",
    )
    fun `las horas`(hour: Int, reading: String) {
        JaReading.hour(hour) shouldBe reading
    }

    @ParameterizedTest(name = "{0}分 → {1}")
    @CsvSource(
        "0, ''", "1, いっぷん", "2, にふん", "3, さんぷん", "4, よんぷん", "5, ごふん", "6, ろっぷん",
        "7, ななふん", "8, はっぷん", "9, きゅうふん", "10, じゅっぷん", "11, じゅういっぷん",
        "13, じゅうさんぷん", "15, じゅうごふん", "16, じゅうろっぷん", "18, じゅうはっぷん",
        "20, にじゅっぷん", "21, にじゅういっぷん", "30, さんじゅっぷん", "40, よんじゅっぷん",
        "46, よんじゅうろっぷん", "50, ごじゅっぷん", "59, ごじゅうきゅうふん",
    )
    fun `los minutos`(minute: Int, reading: String) {
        JaReading.minute(minute) shouldBe reading
    }

    @Test
    fun `la hora entera, en doce horas`() {
        JaReading.time(LocalTime.of(19, 46)) shouldBe "ごご しちじ よんじゅうろっぷん"
        JaReading.time(LocalTime.of(9, 0)) shouldBe "ごぜん くじ"
        JaReading.time(LocalTime.of(0, 5)) shouldBe "ごぜん れいじ ごふん"
        JaReading.time(LocalTime.of(12, 30)) shouldBe "ごご じゅうにじ さんじゅっぷん"
        JaReading.time(LocalTime.of(23, 59)) shouldBe "ごご じゅういちじ ごじゅうきゅうふん"
    }

    @Test
    fun `una fecha entera`() {
        JaReading.date(LocalDate.of(2026, 10, 10)) shouldBe "れいわ はちねん じゅうがつ とおか どようび"
        JaReading.date(LocalDate.of(2026, 10, 4)) shouldBe "れいわ はちねん じゅうがつ よっか にちようび"
        JaReading.date(LocalDate.of(2019, 5, 1)) shouldBe "れいわ がんねん ごがつ ついたち すいようび"
        // Antes de 1873 no hay era: se dice el año occidental
        JaReading.date(LocalDate.of(1870, 3, 5)) shouldBe "せんはっぴゃくななじゅうねん さんがつ いつか どようび"
        JaReading.monthDay(LocalDate.of(2026, 10, 10)) shouldBe "じゅうがつ とおか どようび"
    }

    @Test
    fun `todas las fechas de la tabla se pueden leer`() {
        var date = LocalDate.of(1873, 1, 1)
        while (date.year <= 2100) {
            JaReading.date(date)!!.isNotBlank() shouldBe true
            date = date.plusDays(1)
        }
        for (hour in 0..23) for (minute in 0..59) JaReading.time(LocalTime.of(hour, minute)).isNotBlank() shouldBe true
    }
}
