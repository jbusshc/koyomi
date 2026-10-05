package app.koyomi.ui.components

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.calendar.CalendarInstance
import app.koyomi.calendar.DayEvent
import app.koyomi.engine.format.JaTime
import app.koyomi.engine.format.TimeStyle
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.kyureki.Kyureki
import app.koyomi.engine.kyureki.Rokuyo
import app.koyomi.engine.kyureki.Sekki
import app.koyomi.engine.span.DayPart
import app.koyomi.ui.skin.skin
import app.koyomi.ui.theme.Palette
import java.time.LocalDate
import java.time.ZoneId

/** Los eventos de un día, o 予定なし si no hay ninguno. */
@Composable
fun DayEvents(events: List<DayEvent>, timeStyle: TimeStyle, colors: Palette, onOpen: (CalendarInstance) -> Unit) {
    if (events.isEmpty()) {
        Text("予定なし", color = colors.inkSoft, fontSize = 16.sp, modifier = Modifier.padding(vertical = 12.dp))
    } else {
        Column { events.forEach { EventRow(it, timeStyle, colors, onOpen) } }
    }
}

@Composable
fun EventRow(event: DayEvent, timeStyle: TimeStyle, colors: Palette, onOpen: (CalendarInstance) -> Unit) {
    val instance = event.instance
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen(instance) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 7.dp, end = 12.dp)
                .size(8.dp)
                .clip(skin().dot)
                .background(eventColor(instance, colors)),
        )
        Text(
            text = when (val part = event.part) {
                DayPart.AllDay -> "終日"
                is DayPart.Timed -> JaTime.range(part.start, part.end, timeStyle)
            },
            color = colors.inkSoft,
            fontSize = 14.sp,
            // 午前11:00〜午後1:00 ocupa bastante más que 11:00〜13:00
            modifier = Modifier.width(if (timeStyle == TimeStyle.H24) 104.dp else 148.dp).padding(top = 2.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                instance.title.ifBlank { "（タイトルなし）" },
                color = colors.ink,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (instance.location.isNotBlank()) {
                Text(instance.location, color = colors.inkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun eventColor(instance: CalendarInstance, colors: Palette): Color =
    if (instance.color == 0) colors.inkSoft else Color(instance.color).copy(alpha = 1f)

/** 暦注 de un día: 大安 · 旧暦8月24日 · 寒露. Nulo fuera de lo que cubren las tablas. */
fun rekichu(day: LocalDate): String? =
    listOfNotNull(Rokuyo.of(day)?.kanji, Kyureki.of(day)?.text, Sekki.of(day)?.kanji).joinToString(" · ").ifEmpty { null }

/** La convención de un calendario japonés: domingos y festivos en rojo, sábados en azul. */
fun youbiColor(youbi: Youbi, holiday: Boolean, colors: Palette, other: Color = colors.ink): Color = when {
    holiday || youbi == Youbi.NICHI -> colors.sunday
    youbi == Youbi.DO -> colors.saturday
    else -> other
}

/** Ver un evento es cosa de la app de calendario del teléfono: Koyomi solo lee. */
fun Context.openEvent(instance: CalendarInstance) {
    launch(
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, instance.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, instance.beginMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, instance.endMillis),
    )
}

/** Crear también: se abre su formulario con el día ya puesto, y así no hace falta permiso de escritura. */
fun Context.newEvent(day: LocalDate) {
    val start = day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    launch(
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 3_600_000),
    )
}

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Sin app de calendario que lo abra no hay nada que hacer
    }
}
