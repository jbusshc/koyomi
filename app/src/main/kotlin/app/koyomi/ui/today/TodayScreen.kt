package app.koyomi.ui.today

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koyomi.R
import app.koyomi.calendar.CalendarInstance
import app.koyomi.calendar.DayEvent
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.JaTime
import app.koyomi.engine.format.TimeStyle
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.engine.span.DayPart
import app.koyomi.ui.theme.Palette
import app.koyomi.ui.theme.palette
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(viewModel: TodayViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val context = LocalContext.current
    val activity = LocalActivity.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Tras dos negativas Android ya no enseña el diálogo: solo queda ir a los ajustes de la app
        if (!granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)) {
            context.openAppSettings()
        }
        viewModel.onResume()
    }

    TodayContent(
        state = state,
        onGrant = { request.launch(Manifest.permission.READ_CALENDAR) },
        onOpen = { context.openEvent(it) },
    )
}

@Composable
private fun TodayContent(state: TodayUi, onGrant: () -> Unit, onOpen: (CalendarInstance) -> Unit) {
    val colors = palette()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
    ) {
        item { DateHeader(state.today, colors) }

        val events = state.events
        if (events == null) {
            item { PermissionCard(colors, onGrant) }
        } else {
            dayEvents(events[state.today].orEmpty(), colors, onOpen)
            item { DayLabel(state.tomorrow, colors) }
            dayEvents(events[state.tomorrow].orEmpty(), colors, onOpen)
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dayEvents(
    events: List<DayEvent>,
    colors: Palette,
    onOpen: (CalendarInstance) -> Unit,
) {
    if (events.isEmpty()) {
        item { Text("予定なし", color = colors.inkSoft, fontSize = 16.sp, modifier = Modifier.padding(vertical = 12.dp)) }
    } else {
        items(events) { EventRow(it, colors, onOpen) }
    }
}

@Composable
private fun DateHeader(today: LocalDate, colors: Palette) {
    val date = JaDate.of(today)
    val holiday = Shukujitsu.of(today)
    Column(Modifier.padding(top = 56.dp, bottom = 32.dp)) {
        Text(date.year, color = colors.inkSoft, fontSize = 20.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(date.monthDay, color = colors.ink, fontSize = 48.sp, fontWeight = FontWeight.Medium)
            Text(
                "（${date.youbi.short}）",
                color = youbiColor(date.youbi, holiday != null, colors),
                fontSize = 28.sp,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        Text(date.seireki, color = colors.inkSoft, fontSize = 14.sp)
        if (holiday != null) {
            Text(holiday, color = colors.sunday, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun DayLabel(day: LocalDate, colors: Palette) {
    val date = JaDate.of(day)
    val holiday = Shukujitsu.of(day)
    Column(Modifier.padding(top = 28.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("明日", color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(date.line(DateStyle.SIMPLE), color = youbiColor(date.youbi, holiday != null, colors).takeIf { it != colors.ink } ?: colors.inkSoft, fontSize = 14.sp)
            if (holiday != null) Text(holiday, color = colors.sunday, fontSize = 14.sp)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(colors.rule))
    }
}

@Composable
private fun EventRow(event: DayEvent, colors: Palette, onOpen: (CalendarInstance) -> Unit) {
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
                .clip(CircleShape)
                .background(if (instance.color == 0) colors.inkSoft else Color(instance.color).copy(alpha = 1f)),
        )
        Text(
            text = when (val part = event.part) {
                DayPart.AllDay -> "終日"
                is DayPart.Timed -> JaTime.range(part.start, part.end, TimeStyle.H24)
            },
            color = colors.inkSoft,
            fontSize = 14.sp,
            modifier = Modifier.width(104.dp).padding(top = 2.dp),
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

@Composable
private fun PermissionCard(colors: Palette, onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("カレンダーへのアクセス", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(R.string.calendar_permission_rationale), color = colors.inkSoft, fontSize = 14.sp, lineHeight = 20.sp)
        Column(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.ink)
                .clickable(onClick = onGrant)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text("許可する", color = colors.background, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.calendar_permission_allow), color = colors.background.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

/** La convención de un calendario japonés: domingos y festivos en rojo, sábados en azul. */
private fun youbiColor(youbi: Youbi, holiday: Boolean, colors: Palette): Color = when {
    holiday || youbi == Youbi.NICHI -> colors.sunday
    youbi == Youbi.DO -> colors.saturday
    else -> colors.ink
}

private fun Context.openEvent(instance: CalendarInstance) {
    val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, instance.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, instance.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, instance.endMillis)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Sin app de calendario que lo abra: el evento ya se ve aquí
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

@Preview
@Composable
private fun TodayPreview() {
    val today = LocalDate.of(2026, 10, 11)
    fun event(title: String, part: DayPart, color: Long, location: String = "") =
        DayEvent(CalendarInstance(0, title, 0, 0, part == DayPart.AllDay, location, color.toInt()), part)
    TodayContent(
        state = TodayUi(
            today,
            mapOf(
                today to listOf(
                    event("Cumpleaños de Ana", DayPart.AllDay, 0xFF7CB342),
                    event("日本語の勉強", DayPart.Timed(LocalTime.of(9, 0), LocalTime.of(10, 30)), 0xFF039BE5, "図書館"),
                    event("Quedar con Luis", DayPart.Timed(LocalTime.of(22, 0), null), 0xFFE67C73),
                ),
                today.plusDays(1) to emptyList(),
            ),
        ),
        onGrant = {},
        onOpen = {},
    )
}

@Preview
@Composable
private fun NoPermissionPreview() = TodayContent(TodayUi(LocalDate.of(2026, 10, 4), null), onGrant = {}, onOpen = {})
