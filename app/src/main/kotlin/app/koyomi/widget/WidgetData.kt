package app.koyomi.widget

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import app.koyomi.KoyomiApp
import app.koyomi.calendar.Agenda
import app.koyomi.calendar.DayEvent
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.JaTime
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.engine.span.DayPart
import app.koyomi.settings.Settings
import app.koyomi.ui.skin.skinOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Una línea de la lista de eventos de un widget. */
internal sealed interface Row {
    data class Label(val text: String, val detail: String, val detailColor: Color) : Row
    data class Event(val color: Color, val time: String, val title: String) : Row
    data class Note(val text: String) : Row
}

/** Los eventos que un widget necesita: lo que queda de hoy y lo de mañana. */
internal class AgendaSnapshot(
    /** Falso si no hay permiso de calendario: no es lo mismo que no tener eventos. */
    val access: Boolean,
    val today: LocalDate,
    val zone: ZoneId,
    val nowMillis: Long,
    val left: List<DayEvent>,
    val tomorrow: List<DayEvent>,
) {
    /** Cuándo cambia lo que enseña: que termine un evento de hoy, o la medianoche. */
    fun nextChangeMillis(): Long {
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextEnd = left.filter { it.part != DayPart.AllDay }.map { it.instance.endMillis }.filter { it > nowMillis }.minOrNull()
        return minOf(midnight, nextEnd ?: midnight) + 2_000
    }
}

internal suspend fun loadAgenda(app: KoyomiApp, settings: Settings, now: ZonedDateTime): AgendaSnapshot {
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)
    val nowMillis = now.toInstant().toEpochMilli()
    if (!app.calendar.hasPermission()) return AgendaSnapshot(false, today, now.zone, nowMillis, emptyList(), emptyList())

    val instances = app.calendar.instancesOnce(
        from = today.atStartOfDay(now.zone).toInstant(),
        to = today.plusDays(2).atStartOfDay(now.zone).toInstant(),
    ).filter { it.calendarId !in settings.hiddenCalendars }
    val byDay = Agenda.byDay(instances, listOf(today, tomorrow), now.zone)
    // De hoy, lo que no ha terminado: lo pasado ya no hace falta en la pantalla de inicio
    val left = byDay[today].orEmpty().filter { it.part == DayPart.AllDay || it.instance.endMillis > nowMillis }
    return AgendaSnapshot(true, today, now.zone, nowMillis, left, byDay[tomorrow].orEmpty())
}

internal fun agendaRows(context: Context, look: WidgetLook, settings: Settings, snapshot: AgendaSnapshot): List<Row> = buildList {
    val colors = look.colors
    if (!snapshot.access) {
        add(Row.Note(context.getString(app.koyomi.R.string.widget_agenda_no_access)))
        return@buildList
    }
    fun event(event: DayEvent) = Row.Event(
        color = skinOf(look.skin).calendarColor(event.instance.color, colors),
        time = when (val part = event.part) {
            DayPart.AllDay -> "終日"
            is DayPart.Timed -> JaTime.range(part.start, part.end, settings.timeStyle)
        },
        title = event.instance.title.ifBlank { "（タイトルなし）" },
    )
    if (snapshot.left.isEmpty()) add(Row.Note("予定なし")) else snapshot.left.forEach { add(event(it)) }
    if (snapshot.tomorrow.isNotEmpty()) {
        val tomorrow = snapshot.today.plusDays(1)
        val date = JaDate.of(tomorrow)
        val color = look.youbiColor(date.youbi, Shukujitsu.of(tomorrow) != null)
        add(Row.Label("明日", date.line(DateStyle.SIMPLE), color))
        snapshot.tomorrow.forEach { add(event(it)) }
    }
}

/** El color de un día de la semana en un widget: domingos y festivos en rojo, sábados en azul. */
internal fun WidgetLook.youbiColor(youbi: Youbi, holiday: Boolean): Color = when {
    holiday || youbi == Youbi.NICHI -> colors.sunday
    youbi == Youbi.DO -> colors.saturday
    else -> colors.inkSoft
}

/** Los avisos del sistema que mantienen al día a los widgets que hay: el calendario y el tiempo. */
internal object WidgetJobs {
    private const val CALENDAR_JOB = 1
    private const val WEATHER_JOB = 2
    private const val WEATHER_EVERY_MS = 3 * 60 * 60 * 1000L

    /**
     * El calendario cambia con la app cerrada (una sincronización, un evento creado en otra app).
     * El aviso vale para una vez: cada pintada lo vuelve a pedir.
     */
    fun watchCalendar(app: KoyomiApp) {
        if (!app.calendar.hasPermission()) return
        val job = JobInfo.Builder(CALENDAR_JOB, ComponentName(app, AgendaWidget.CalendarChanged::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(1_000)
            .setTriggerContentMaxDelay(10_000)
            .build()
        runCatching { app.getSystemService(JobScheduler::class.java).schedule(job) }
    }

    /** Pide el parte cada pocas horas, y solo con red. Sin ningún widget del tiempo, no se pide. */
    fun scheduleWeather(context: Context) {
        val job = JobInfo.Builder(WEATHER_JOB, ComponentName(context, WeatherWidget.Refresh::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(WEATHER_EVERY_MS)
            .build()
        runCatching { context.getSystemService(JobScheduler::class.java).schedule(job) }
    }

    /** Los avisos se retiran cuando ya no queda ningún widget que los use. */
    fun release(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!AgendaWidget.hasWidgets(context) && !SummaryWidget.hasWidgets(context)) scheduler.cancel(CALENDAR_JOB)
        if (!WeatherWidget.hasWidgets(context) && !SummaryWidget.hasWidgets(context)) scheduler.cancel(WEATHER_JOB)
    }
}
