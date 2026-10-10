package app.koyomi.widget

import android.app.job.JobParameters
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.engine.weather.Tenki
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * El tiempo de la ciudad elegida en la pantalla de inicio. Sigue el tema y el modo de la app y
 * enseña más cuanto más grande es: primero el icono y la temperatura, luego la palabra del parte
 * y la ciudad, la máxima y la mínima, y por fin los tres días.
 *
 * Con el widget puesto se pide el parte cada pocas horas, y solo si hay red. Sin widget, Koyomi
 * solo pide el tiempo cuando se abre la app.
 */
class WeatherWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetJobs.scheduleWeather(context)
        refresh(context, fetch = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)

    override fun onDisabled(context: Context) = WidgetJobs.release(context)

    /** El trabajo periódico: pide el parte y repinta los widgets que lo muestran. */
    class Refresh : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {
            val app = applicationContext as KoyomiApp
            app.appScope.launch {
                runCatching {
                    app.weather.refresh(app.settings.settings.first().weather)
                    WeatherWidget.refresh(app)
                    SummaryWidget.refresh(app)
                }
                jobFinished(params, false)
            }
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean = false
    }

    companion object {
        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        /** Repinta con el parte que haya; con [fetch], lo pide antes si el que hay es viejo. */
        fun refresh(context: Context, fetch: Boolean = false) {
            val app = context.applicationContext as KoyomiApp
            if (ids(app).isEmpty()) return
            app.appScope.launch { runCatching { update(app, fetch) } }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WeatherWidget::class.java))

        private suspend fun update(app: KoyomiApp, fetch: Boolean) {
            val ids = ids(app)
            if (ids.isEmpty()) return
            val settings = app.settings.settings.first()
            if (fetch) app.weather.refresh(settings.weather)

            val manager = AppWidgetManager.getInstance(app)
            for (id in ids) {
                val bitmap = WidgetLook.bitmap(app, manager.getAppWidgetOptions(id), fallbackWidthDp = 250, fallbackHeightDp = 110)
                renderSummary(app, bitmap, settings, setOf(Section.WEATHER))
                val report = app.weather.report.value?.takeIf { it.place == settings.weather?.key }
                val views = RemoteViews(app.packageName, R.layout.widget_date_image)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setContentDescription(
                    R.id.widget_image,
                    when {
                        settings.weather == null -> "天気"
                        report == null -> settings.weather.name
                        else -> "${settings.weather.name} ${Tenki.of(report.code).kanji} ${Tenki.temperature(report.temperature)}"
                    },
                )
                views.setOnClickPendingIntent(R.id.widget_root, openApp(app))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
