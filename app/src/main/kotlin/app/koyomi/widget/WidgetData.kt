package app.koyomi.widget

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import app.koyomi.KoyomiApp
import app.koyomi.MainActivity
import app.koyomi.calendar.Agenda
import app.koyomi.calendar.DayEvent
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.JaTime
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.engine.span.DayPart
import app.koyomi.settings.Settings
import app.koyomi.ui.Destination
import app.koyomi.ui.skin.skinOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZonedDateTime

/** Una línea de la lista de eventos de un widget. */
internal sealed interface Row {
    data class Label(val text: String, val detail: String, val detailColor: Color) : Row
    data class Event(val color: Color, val time: String, val title: String) : Row
    data class Note(val text: String) : Row
}

/** Cuántos días por delante se miran para llenar la lista cuando hoy no hay (o no queda) nada. */
private const val LOOKAHEAD_DAYS = 7

/** Los eventos que un widget necesita: lo que queda de hoy y lo que viene los próximos días. */
internal class AgendaSnapshot(
    /** Falso si no hay permiso de calendario: no es lo mismo que no tener eventos. */
    val access: Boolean,
    val today: LocalDate,
    val zone: ZoneId,
    val nowMillis: Long,
    val left: List<DayEvent>,
    /** Hoy hubo eventos, aunque ya hayan terminado todos. */
    val hadToday: Boolean,
    /** Los próximos días que tienen eventos, en orden. */
    val upcoming: List<Pair<LocalDate, List<DayEvent>>>,
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
    val nowMillis = now.toInstant().toEpochMilli()
    if (!app.calendar.hasPermission()) return AgendaSnapshot(false, today, now.zone, nowMillis, emptyList(), false, emptyList())

    val days = (0..LOOKAHEAD_DAYS).map { today.plusDays(it.toLong()) }
    val instances = app.calendar.instancesOnce(
        from = today.atStartOfDay(now.zone).toInstant(),
        to = today.plusDays(LOOKAHEAD_DAYS + 1L).atStartOfDay(now.zone).toInstant(),
    ).filter { it.calendarId !in settings.hiddenCalendars }
    val byDay = Agenda.byDay(instances, days, now.zone)
    val todays = byDay[today].orEmpty()
    // De hoy, lo que no ha terminado: lo pasado ya no hace falta en la pantalla de inicio
    val left = todays.filter { it.part == DayPart.AllDay || it.instance.endMillis > nowMillis }
    val upcoming = days.drop(1).mapNotNull { day -> byDay[day].orEmpty().takeIf { it.isNotEmpty() }?.let { day to it } }
    return AgendaSnapshot(true, today, now.zone, nowMillis, left, todays.isNotEmpty(), upcoming)
}

/** Los días del mes de [now] que tienen algún evento. */
internal suspend fun loadBusyDays(app: KoyomiApp, settings: Settings, now: ZonedDateTime): Set<LocalDate> {
    if (!app.calendar.hasPermission()) return emptySet()
    val first = now.toLocalDate().withDayOfMonth(1)
    val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it.month == first.month }.toList()
    val instances = app.calendar.instancesOnce(
        from = first.atStartOfDay(now.zone).toInstant(),
        to = first.plusMonths(1).atStartOfDay(now.zone).toInstant(),
    ).filter { it.calendarId !in settings.hiddenCalendars }
    return Agenda.byDay(instances, days, now.zone).filterValues { it.isNotEmpty() }.keys
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
    if (snapshot.left.isEmpty()) {
        // «Nada hoy» y «ya está todo hecho» no son lo mismo
        add(Row.Note(if (snapshot.hadToday) "今日の予定は終わり" else "今日は予定なし"))
    } else {
        snapshot.left.forEach { add(event(it)) }
    }
    for ((day, events) in snapshot.upcoming) {
        val date = JaDate.of(day)
        val color = look.youbiColor(date.youbi, Shukujitsu.of(day) != null)
        val distance = ChronoUnit.DAYS.between(snapshot.today, day)
        when (distance) {
            1L -> add(Row.Label("明日", date.line(DateStyle.SIMPLE), color))
            2L -> add(Row.Label("明後日", date.line(DateStyle.SIMPLE), color))
            else -> add(Row.Label(date.monthDay, "（${date.youbi.short}）", color))
        }
        events.forEach { add(event(it)) }
    }
}

/** «11月3日　文化の日（あと23日）»: el próximo festivo, o nulo si no hay ninguno a la vista. */
internal fun nextHoliday(today: LocalDate): String? {
    for (offset in 1..400L) {
        val day = today.plusDays(offset)
        val name = Shukujitsu.of(day) ?: continue
        return "${JaDate.of(day).monthDay}　$name（あと${offset}日）"
    }
    return null
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

    /**
     * El calendario cambia con la app cerrada (una sincronización, un evento creado en otra app).
     * El aviso vale para una vez: cada pintada lo vuelve a pedir.
     */
    fun watchCalendar(app: KoyomiApp) {
        if (!app.calendar.hasPermission()) return
        val job = JobInfo.Builder(CALENDAR_JOB, ComponentName(app, SummaryWidget.CalendarChanged::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(1_000)
            .setTriggerContentMaxDelay(10_000)
            .build()
        runCatching { app.getSystemService(JobScheduler::class.java).schedule(job) }
    }

    /**
     * Pide el parte cada [minutes] (el más corto de los widgets puestos), y solo con red y batería.
     * Todos en «a mano» (0): no hay trabajo periódico.
     */
    fun scheduleWeather(context: Context, minutes: List<Int>) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val every = minutes.filter { it > 0 }.minOrNull()
        if (every == null) {
            scheduler.cancel(WEATHER_JOB)
            return
        }
        // El sistema no deja periodos de menos de 15 minutos
        val period = maxOf(every, 15) * 60_000L
        val job = JobInfo.Builder(WEATHER_JOB, ComponentName(context, SummaryWidget.WeatherRefresh::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            // Con batería baja no se pide, y con margen para que el sistema lo junte con otros trabajos
            .setRequiresBatteryNotLow(true)
            .setPeriodic(period, maxOf(period / 4, 5 * 60_000L))
            .build()
        // Si ya estaba con ese periodo no se toca: reprogramarlo cada pintada retrasaría su siguiente turno
        val current = scheduler.getPendingJob(WEATHER_JOB)
        if (current?.intervalMillis == period) return
        runCatching { scheduler.schedule(job) }
    }

    /** Los avisos se retiran cuando ya no queda ningún widget que los use. */
    fun release(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!SummaryWidget.hasWidgets(context)) {
            scheduler.cancel(CALENDAR_JOB)
            scheduler.cancel(WEATHER_JOB)
        }
    }
}

/**
 * Tocar un widget abre la app, o la trae al frente si ya estaba abierta. Con [destination], en esa
 * pantalla (el widget del mes abre el mes).
 */
internal fun openApp(context: Context, destination: Destination? = null): PendingIntent = PendingIntent.getActivity(
    context,
    // Un código por destino: si no, el sistema los tomaría por el mismo aviso y se pisarían
    10 + (destination?.ordinal ?: -1) + 1,
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply { if (destination != null) putExtra(MainActivity.EXTRA_DESTINATION, destination.name) },
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)
