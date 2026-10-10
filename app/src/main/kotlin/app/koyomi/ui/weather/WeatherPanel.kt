package app.koyomi.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.engine.format.JaTime
import app.koyomi.engine.format.TimeStyle
import app.koyomi.engine.weather.Sky
import app.koyomi.engine.weather.Tenki
import app.koyomi.settings.Place
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import app.koyomi.weather.DayForecast
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private val DAY_LABELS = listOf("今日", "明日", "明後日")

/** El tiempo de la ciudad elegida: un icono con el estilo del tema, la palabra de un parte y tres días. */
@Composable
fun WeatherPanel(place: Place, timeStyle: TimeStyle) {
    val colors = palette()
    val skin = skin()
    val app = LocalContext.current.applicationContext as KoyomiApp
    val report by app.weather.report.collectAsStateWithLifecycle()
    val failed by app.weather.failed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val shown = report?.takeIf { it.place == place.key }

    skin.Panel(
        Modifier.fillMaxWidth(),
        title = {
            Text("天気", color = colors.ink, fontSize = 16.sp, fontWeight = skin.strong)
            Text(place.name, color = colors.inkSoft, fontSize = 14.sp)
            if (shown != null) {
                val at = Instant.ofEpochMilli(shown.fetchedAt).atZone(ZoneId.systemDefault()).toLocalTime()
                Text("${JaTime.digits(at, timeStyle)} 現在", color = colors.inkSoft, fontSize = 12.sp)
            }
        },
    ) {
        if (shown == null) {
            // Aún no hay parte: o está de camino, o no hay red. Tocar lo vuelve a pedir
            Text(
                if (failed) "取得できません" else "…",
                color = colors.inkSoft,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (failed) {
                Text(
                    stringResource(R.string.weather_retry),
                    color = colors.inkSoft,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(skin.buttonShape).clickable { scope.launch { app.weather.refresh(place, force = true) } }.padding(vertical = 8.dp),
                )
            }
            return@Panel
        }

        val tenki = Tenki.of(shown.code)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(Sky.of(shown.code), night = !shown.isDay, size = 68.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(tenki.kanji, color = colors.ink, fontSize = 22.sp)
                Text(tenki.kana, color = colors.inkSoft, fontSize = 12.sp)
            }
            Text(Tenki.temperature(shown.temperature), color = colors.ink, fontSize = 34.sp)
        }

        Box(Modifier.padding(top = 12.dp, bottom = 10.dp).fillMaxWidth().height(1.dp).background(colors.rule))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.days.take(DAY_LABELS.size).forEachIndexed { index, day ->
                DayColumn(DAY_LABELS[index], day, Modifier.weight(1f))
            }
        }
    }
}

/** Un día del parte: su nombre, el icono, y la máxima sobre la mínima. */
@Composable
private fun DayColumn(label: String, day: DayForecast, modifier: Modifier) {
    val colors = palette()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = colors.inkSoft, fontSize = 12.sp, textAlign = TextAlign.Center)
        WeatherIcon(Sky.of(day.code), night = false, size = 36.dp, modifier = Modifier.padding(vertical = 6.dp))
        Text(Tenki.temperature(day.max), color = colors.ink, fontSize = 15.sp)
        Text(Tenki.temperature(day.min), color = colors.inkSoft, fontSize = 13.sp)
    }
}
