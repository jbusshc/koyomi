package app.koyomi.engine.format

import java.time.LocalTime

enum class TimeStyle {
    /** 21時32分 · 21:32 */
    H24,

    /** 午後9時32分 · 午後9:32 */
    GOZEN_GOGO,
}

object JaTime {
    /** Con kanji, para un reloj: 21時32分, 午後9時32分. La hora en punto va sin minutos: 午後9時. */
    fun kanji(time: LocalTime, style: TimeStyle): String {
        val minutes = if (time.minute == 0) "" else "${time.minute}分"
        return "${prefix(time, style)}${hour(time, style)}時$minutes"
    }

    /** Con dos puntos, para una lista de eventos: 09:00, 午前9:00. */
    fun digits(time: LocalTime, style: TimeStyle): String {
        val minutes = time.minute.toString().padStart(2, '0')
        return when (style) {
            TimeStyle.H24 -> "${time.hour.toString().padStart(2, '0')}:$minutes"
            TimeStyle.GOZEN_GOGO -> "${prefix(time, style)}${hour(time, style)}:$minutes"
        }
    }

    /** 09:00〜10:30. Sin final (un evento sin duración): 09:00〜 */
    fun range(start: LocalTime, end: LocalTime?, style: TimeStyle): String =
        "${digits(start, style)}〜${end?.let { digits(it, style) } ?: ""}"

    private fun prefix(time: LocalTime, style: TimeStyle): String = when {
        style == TimeStyle.H24 -> ""
        time.hour < 12 -> "午前"
        else -> "午後"
    }

    // El mediodía es 午後0時 y la medianoche 午前0時: la forma sin ambigüedad
    private fun hour(time: LocalTime, style: TimeStyle): Int =
        if (style == TimeStyle.H24) time.hour else time.hour % 12
}
