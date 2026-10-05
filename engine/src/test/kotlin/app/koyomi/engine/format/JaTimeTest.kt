package app.koyomi.engine.format

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalTime

class JaTimeTest {

    @ParameterizedTest(name = "{0} → {1} · {2}")
    @CsvSource(
        "21:32, 21時32分, 午後9時32分",
        "09:05, 9時5分, 午前9時5分",
        "09:00, 9時, 午前9時",
        "00:00, 0時, 午前0時",
        "12:00, 12時, 午後0時",
        "12:30, 12時30分, 午後0時30分",
        "23:59, 23時59分, 午後11時59分",
    )
    fun `con kanji`(time: String, h24: String, gozenGogo: String) {
        JaTime.kanji(LocalTime.parse(time), TimeStyle.H24) shouldBe h24
        JaTime.kanji(LocalTime.parse(time), TimeStyle.GOZEN_GOGO) shouldBe gozenGogo
    }

    @ParameterizedTest(name = "{0} → {1} · {2}")
    @CsvSource(
        "21:32, 21:32, 午後9:32",
        "09:00, 09:00, 午前9:00",
        "00:05, 00:05, 午前0:05",
        "12:00, 12:00, 午後0:00",
    )
    fun `con dos puntos`(time: String, h24: String, gozenGogo: String) {
        JaTime.digits(LocalTime.parse(time), TimeStyle.H24) shouldBe h24
        JaTime.digits(LocalTime.parse(time), TimeStyle.GOZEN_GOGO) shouldBe gozenGogo
    }

    @Test
    fun `rangos con 〜`() {
        JaTime.range(LocalTime.of(9, 0), LocalTime.of(10, 30), TimeStyle.H24) shouldBe "09:00〜10:30"
        JaTime.range(LocalTime.of(14, 0), null, TimeStyle.H24) shouldBe "14:00〜"
        JaTime.range(null, LocalTime.of(2, 0), TimeStyle.H24) shouldBe "〜02:00"
        JaTime.range(LocalTime.of(11, 0), LocalTime.of(13, 0), TimeStyle.GOZEN_GOGO) shouldBe "午前11:00〜午後1:00"
    }
}
