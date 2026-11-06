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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.ui.components.youbiColor
import app.koyomi.weather.HourForecast
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
                // Actualizar a mano, como en cualquier app del tiempo
                Text(
                    "↻",
                    color = colors.ink,
                    fontSize = 20.sp,
                    modifier = Modifier.clip(skin.buttonShape).clickable { scope.launch { app.weather.refresh(place, force = true) } }.padding(horizontal = 10.dp, vertical = 2.dp),
                )
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
        val days = shown.daysAhead()
        val today = days.firstOrNull()
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(Sky.of(shown.code), night = !shown.isDay, size = 68.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(tenki.kanji, color = colors.ink, fontSize = 22.sp)
                Text(tenki.kana, color = colors.inkSoft, fontSize = 12.sp)
                if (today != null) {
                    Text("最高 ${Tenki.temperature(today.max)}　最低 ${Tenki.temperature(today.min)}", color = colors.inkSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(Tenki.temperature(shown.temperature), color = colors.ink, fontSize = 38.sp)
        }

        // Lo que se espera de un parte: sensación, humedad, viento y lluvia de hoy
        val facts = listOfNotNull(
            shown.feelsLike?.let { "体感" to Tenki.temperature(it) },
            shown.humidity?.let { "湿度" to "$it%" },
            if (shown.windSpeed != null && shown.windDegrees != null) "風" to Tenki.wind(shown.windSpeed, shown.windDegrees) else null,
            today?.rain?.let { "降水確率" to Tenki.chance(it) },
        )
        if (facts.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                facts.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEach { (name, value) -> Fact(name, value, Modifier.weight(1f)) }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }

        val hours = shown.hoursAhead().take(24)
        if (hours.isNotEmpty()) {
            Rule()
            Text("時間ごと", color = colors.inkSoft, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                hours.forEachIndexed { index, hour -> HourColumn(if (index == 0) "今" else "${hour.time.hour}時", hour) }
            }
        }

        if (days.isNotEmpty()) {
            Rule()
            val low = days.minOf { it.min }
            val high = days.maxOf { it.max }
            days.forEachIndexed { index, day -> DayRow(index, day, low, high) }
        }

        // El sol y los rayos UV de hoy
        val sun = listOfNotNull(
            today?.sunrise?.let { "日の出 ${"%d:%02d".format(it.hour, it.minute)}" },
            today?.sunset?.let { "日の入 ${"%d:%02d".format(it.hour, it.minute)}" },
            today?.uv?.let { "紫外線 ${Tenki.uv(it)}" },
        )
        if (sun.isNotEmpty()) {
            Rule()
            Text(sun.joinToString("　"), color = colors.inkSoft, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Rule() {
    val colors = palette()
    Box(Modifier.padding(top = 12.dp, bottom = 10.dp).fillMaxWidth().height(1.dp).background(colors.rule))
}

@Composable
private fun Fact(name: String, value: String, modifier: Modifier) {
    val colors = palette()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = colors.inkSoft, fontSize = 12.sp, modifier = Modifier.width(60.dp))
        Text(value, color = colors.ink, fontSize = 15.sp)
    }
}

/** Una hora: su nombre, el icono, la temperatura y, si es probable, la lluvia. */
@Composable
private fun HourColumn(label: String, hour: HourForecast) {
    val colors = palette()
    Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = colors.inkSoft, fontSize = 12.sp)
        WeatherIcon(Sky.of(hour.code), night = !hour.isDay, size = 30.dp, modifier = Modifier.padding(vertical = 4.dp))
        Text(Tenki.temperature(hour.temperature), color = colors.ink, fontSize = 14.sp)
        val rain = hour.rain
        Text(if (rain != null && rain >= 20) Tenki.chance(rain) else " ", color = colors.saturday, fontSize = 11.sp)
    }
}

/** Un día del parte: nombre, icono, lluvia y la franja entre la mínima y la máxima de la semana. */
@Composable
private fun DayRow(index: Int, day: DayForecast, low: Double, high: Double) {
    val colors = palette()
    val youbi = Youbi.of(day.date)
    val name = when (index) {
        0 -> "今日"
        1 -> "明日"
        else -> "${day.date.monthValue}/${day.date.dayOfMonth}"
    }
    val span = (high - low).coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(58.dp)) {
            Text(name, color = colors.ink, fontSize = 14.sp)
            Text(youbi.short, color = youbiColor(youbi, Shukujitsu.of(day.date) != null, colors, colors.inkSoft), fontSize = 11.sp)
        }
        WeatherIcon(Sky.of(day.code), night = false, size = 30.dp)
        Text(
            day.rain?.takeIf { it >= 10 }?.let { Tenki.chance(it) } ?: "",
            color = colors.saturday,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        Text(Tenki.temperature(day.min), color = colors.inkSoft, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
        // La franja: dónde cae este día entre la mínima y la máxima de toda la semana
        val start = ((day.min - low) / span).toFloat().coerceIn(0f, 1f)
        val end = ((day.max - low) / span).toFloat().coerceIn(start, 1f)
        Row(Modifier.weight(1f).padding(horizontal = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.rule)) {
            if (start > 0f) Box(Modifier.weight(start))
            Box(Modifier.weight((end - start).coerceAtLeast(0.04f)).fillMaxHeight().background(colors.accent))
            if (end < 1f) Box(Modifier.weight(1f - end))
        }
        Text(Tenki.temperature(day.max), color = colors.ink, fontSize = 14.sp, modifier = Modifier.width(38.dp))
    }
}
