package app.koyomi.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.job.JobParameters
import android.app.job.JobService
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.engine.weather.Tenki
import app.koyomi.engine.reading.JaReading
import app.koyomi.settings.LearnMode
import app.koyomi.settings.Settings
import app.koyomi.settings.SkinId
import app.koyomi.ui.Destination
import app.koyomi.ui.components.rekichu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Todo en uno, y más cuanto más grande es. Pegado a la pantalla de inicio en un hueco pequeño
 * enseña solo el tiempo; al crecer suma la fecha, luego la máxima y la mínima y el próximo evento,
 * y en los tamaños altos, los tres días del parte y la lista de lo que viene.
 *
 * Sigue el tema y el modo de la app (o los que se elijan para cada widget) y se dibuja en una
 * imagen, con su fuente y su marco. Se pone al día cuando cambia el calendario, cuando termina un
 * evento, cuando llega un parte nuevo, al volver de la app, al tocar su botón ↻ y cada cierto
 * tiempo, el que se elija.
 *
 * Es abierta porque el selector de widgets ofrece tres tamaños de salida (2×2, 4×2 y 4×4) que son
 * el mismo widget con otra medida inicial.
 */
open class SummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, goAsync(), fetch = true)
    }

    // Al cambiar de tamaño es cuando decide qué enseñar
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, goAsync())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FETCH -> render(context, goAsync(), fetch = true, force = true)
            ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> render(context, goAsync())
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val app = context.applicationContext as KoyomiApp
        app.appScope.launch { app.widgets.remove(ids) }
    }

    override fun onDisabled(context: Context) {
        // Este proveedor se quedó sin widgets, pero puede haber de los otros tamaños
        if (hasWidgets(context)) return
        context.getSystemService(AlarmManager::class.java).cancel(nextRefresh(context))
        WidgetJobs.release(context)
    }

    private fun render(context: Context, pending: PendingResult?, fetch: Boolean = false, force: Boolean = false) {
        val app = context.applicationContext as KoyomiApp
        app.appScope.launch {
            try {
                draw(app, fetch, force)
            } finally {
                pending?.finish()
            }
        }
    }

    /** El aviso del sistema de que el calendario cambió: repinta los widgets. */
    class CalendarChanged : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {
            SummaryWidget.refresh(this)
            return false
        }

        override fun onStopJob(params: JobParameters): Boolean = false
    }

    /** El trabajo periódico: pide el parte y repinta los widgets. */
    class WeatherRefresh : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {
            val app = applicationContext as KoyomiApp
            app.appScope.launch {
                runCatching {
                    app.weather.refresh(app.settings.settings.first().weather, force = true)
                    SummaryWidget.refresh(app)
                }
                jobFinished(params, false)
            }
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean = false
    }

    companion object {
        private const val ACTION_REFRESH = "app.koyomi.SUMMARY_REFRESH"

        /** El botón ↻: pide el parte ahora, sea cual sea su edad, y repinta. */
        private const val ACTION_FETCH = "app.koyomi.SUMMARY_FETCH"

        private val providers = listOf(SummaryWidget::class.java, SummaryCompactWidget::class.java, SummaryLargeWidget::class.java)

        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        fun refresh(context: Context) {
            val app = context.applicationContext as KoyomiApp
            if (ids(app).isEmpty()) return
            app.appScope.launch { runCatching { draw(app, fetch = false, force = false) } }
        }

        /** Los widgets puestos, de los tres tamaños de salida. */
        internal fun ids(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return providers.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }.toIntArray()
        }

        private suspend fun draw(app: KoyomiApp, fetch: Boolean, force: Boolean) {
            val ids = ids(app)
            if (ids.isEmpty()) return
            val settings = app.settings.settings.first()
            val configs = ids.associateWith { app.widgets.get(it) }
            // Con todos en «a mano», el tiempo solo se pide al tocar ↻ o al abrir la app
            val automatic = configs.values.any { it.refreshMinutes > 0 }
            if (fetch && (automatic || force)) app.weather.refresh(settings.weather, force = force)

            val manager = AppWidgetManager.getInstance(app)
            var next = 0L
            for (id in ids) {
                val config = configs.getValue(id)
                val bitmap = WidgetLook.bitmap(app, manager.getAppWidgetOptions(id), manager.getAppWidgetInfo(id))
                val (data, snapshot) = renderSummary(app, bitmap, settings, config)
                snapshot?.nextChangeMillis()?.let { next = if (next == 0L) it else minOf(next, it) }
                val views = RemoteViews(app.packageName, R.layout.widget_date_image)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setContentDescription(R.id.widget_image, describe(data))
                views.setOnClickPendingIntent(R.id.widget_root, openApp(app, if (config.layout == WidgetLayout.MONTH) Destination.MONTH else null))
                // El botón ↻ es una esquina que no se ve, bajo el dibujo del icono
                views.setViewVisibility(R.id.widget_refresh, if (config.showRefresh && config.layout != WidgetLayout.DATE) View.VISIBLE else View.GONE)
                views.setOnClickPendingIntent(R.id.widget_refresh, fetchNow(app))
                manager.updateAppWidget(id, views)
            }

            // Lo siguiente que cambia: un evento que termina o, como tarde, el cambio de día (también
            // en los widgets que no enseñan eventos, o la fecha se quedaría en la de ayer). No
            // despierta al teléfono
            val midnight = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 2_000
            next = if (next == 0L) midnight else minOf(next, midnight)
            app.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next, nextRefresh(app))
            WidgetJobs.watchCalendar(app)
            WidgetJobs.scheduleWeather(app, configs.values.map { it.refreshMinutes })
        }

        private fun describe(data: SummaryData): String = buildString {
            append(data.date.line(DateStyle.SIMPLE))
            val report = data.report
            if (report != null && data.place != null) append("、${data.place.name} ${Tenki.of(report.code).kanji} ${Tenki.temperature(report.temperature)}")
            for (row in data.rows) {
                append("、")
                append(
                    when (row) {
                        is Row.Event -> "${row.time} ${row.title}"
                        is Row.Label -> row.text
                        is Row.Note -> row.text
                    },
                )
            }
        }

        private fun nextRefresh(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            5,
            Intent(context, SummaryWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun fetchNow(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            6,
            Intent(context, SummaryWidget::class.java).setAction(ACTION_FETCH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** El mismo Resumen, que el selector de widgets ofrece en 2×2. */
class SummaryCompactWidget : SummaryWidget()

/** El mismo Resumen, que el selector de widgets ofrece en 4×4. */
class SummaryLargeWidget : SummaryWidget()

/**
 * Reúne lo que enseña un widget con la configuración [config] y lo pinta en [bitmap]. Devuelve los
 * datos y los eventos, que dicen cuándo toca volver a pintar. También lo usan la pantalla de
 * configuración (su vista previa) y la prueba de tamaños de la build de depuración.
 */
internal suspend fun renderSummary(
    app: KoyomiApp,
    bitmap: Bitmap,
    settings: Settings,
    config: WidgetConfig,
): Pair<SummaryData, AgendaSnapshot?> {
    val look = WidgetLook.of(app, settings, config)
    val sections = config.sections()
    val now = ZonedDateTime.now()
    val today = now.toLocalDate()
    val snapshot = if (Section.EVENTS in sections) loadAgenda(app, settings, now) else null
    val busy = if (config.layout == WidgetLayout.MONTH && config.showEvents) loadBusyDays(app, settings, now) else emptySet()

    val place = settings.weather
    val data = SummaryData(
        // El tema 和風 escribe los números en kanji, como en la app
        date = if (look.skin == SkinId.TRADITIONAL) JaDate.kanji(today) else JaDate.of(today),
        holiday = Shukujitsu.of(today),
        dateStyle = settings.dateStyle,
        place = place,
        report = app.weather.report.value?.takeIf { it.place == place?.key },
        weatherFailed = app.weather.failed.value,
        rows = snapshot?.let { agendaRows(app, look, settings, it) }.orEmpty(),
        note = if (settings.rekichu) rekichu(today) else null,
        reading = if (settings.learn != LearnMode.OFF) JaReading.monthDay(today) else null,
        noPlaceHint = app.getString(R.string.widget_weather_no_place),
        weekStart = settings.weekStart,
        busyDays = busy,
        nextHoliday = nextHoliday(today),
    )
    SummaryPainter(bitmap, look, app.resources.displayMetrics.density, data, sections, config).paint()
    return data to snapshot
}

/** Para la prueba de tamaños de la build de depuración. */
internal suspend fun renderSummaryForDebug(app: KoyomiApp, bitmap: Bitmap, settings: Settings, config: WidgetConfig) {
    renderSummary(app, bitmap, settings, config)
}
