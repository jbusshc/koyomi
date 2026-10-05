package app.koyomi.ui.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.engine.format.JaTime
import app.koyomi.engine.format.TimeStyle
import app.koyomi.engine.weather.Tenki
import app.koyomi.settings.Place
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** El tiempo de la ciudad elegida, con el vocabulario de un parte japonés. */
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
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tenki.kanji, color = colors.ink, fontSize = 26.sp, modifier = Modifier.weight(1f))
            Text(Tenki.temperature(shown.temperature), color = colors.ink, fontSize = 30.sp)
        }
        Text(tenki.kana, color = colors.inkSoft, fontSize = 13.sp)
        shown.days.getOrNull(0)?.let { today ->
            Text(
                "最高 ${Tenki.temperature(today.max)}　最低 ${Tenki.temperature(today.min)}",
                color = colors.ink,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        shown.days.getOrNull(1)?.let { next ->
            Text(
                "明日　${Tenki.of(next.code).kanji}　${Tenki.temperature(next.max)} ／ ${Tenki.temperature(next.min)}",
                color = colors.inkSoft,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
