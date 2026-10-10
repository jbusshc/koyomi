package app.koyomi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.reading.JaReading
import app.koyomi.settings.LearnMode
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Las palabras de una lectura, que al faltar sitio pasan enteras a la línea siguiente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Words(words: List<String>, modifier: Modifier = Modifier) {
    val colors = palette()
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (word in words) Text(word, color = colors.ink, fontSize = 17.sp)
    }
}

/** La fecha completa en el idioma del teléfono: «sábado, 10 de octubre de 2026». */
fun meaningOf(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

/**
 * Las líneas de ayuda para quien estudia: la lectura de la fecha en kana, la de la hora si se da, y
 * con [LearnMode.MEANING], qué dice todo eso en el idioma del teléfono.
 */
@Composable
fun LearningLines(date: LocalDate, time: LocalTime?, mode: LearnMode, modifier: Modifier = Modifier) {
    val colors = palette()
    Column(modifier.fillMaxWidth()) {
        JaReading.dateParts(date)?.let { Words(it) }
        if (time != null) Words(JaReading.timeParts(time), Modifier.padding(top = 4.dp))
        if (mode == LearnMode.MEANING) {
            Text(meaningOf(date), color = colors.inkSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            // El día de la semana, aparte: es lo primero que se aprende y lo que más se confunde
            Text("${Youbi.of(date).full} · ${date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())}", color = colors.inkSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
