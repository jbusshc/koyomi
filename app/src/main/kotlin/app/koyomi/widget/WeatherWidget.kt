package app.koyomi.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.engine.weather.Tenki
import app.koyomi.settings.Place
import app.koyomi.weather.WeatherReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * El tiempo de la ciudad elegida en la pantalla de inicio: 曇り 21℃, la máxima y la mínima, y
 * mañana. Sigue el tema y el modo de la app, dibujado en una imagen como el de agenda.
 *
 * Con el widget puesto se pide el parte cada pocas horas, y solo si hay red. Sin widget, Koyomi
 * solo pide el tiempo cuando se abre la app.
 */
class WeatherWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        schedule(context)
        refresh(context, fetch = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(WEATHER_JOB)
    }

    /** El trabajo periódico: pide el parte y repinta. */
    class Refresh : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {
            val app = applicationContext as KoyomiApp
            app.appScope.launch {
                runCatching { update(app, fetch = true) }
                jobFinished(params, false)
            }
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean = false
    }

    companion object {
        private const val WEATHER_JOB = 2
        private const val EVERY_MS = 3 * 60 * 60 * 1000L

        /** Repinta con el parte que haya; con [fetch], lo pide antes si el que hay es viejo. */
        fun refresh(context: Context, fetch: Boolean = false) {
            val app = context.applicationContext as KoyomiApp
            if (ids(app).isEmpty()) return
            app.appScope.launch { runCatching { update(app, fetch) } }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WeatherWidget::class.java))

        private fun schedule(context: Context) {
            val job = JobInfo.Builder(WEATHER_JOB, ComponentName(context, Refresh::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(EVERY_MS)
                .build()
            runCatching { context.getSystemService(JobScheduler::class.java).schedule(job) }
        }

        private suspend fun update(app: KoyomiApp, fetch: Boolean) {
            val ids = ids(app)
            if (ids.isEmpty()) return
            val settings = app.settings.settings.first()
            val place = settings.weather
            if (fetch) app.weather.refresh(place)
            val report = app.weather.report.value?.takeIf { it.place == place?.key }
            val look = WidgetLook.of(app, settings)

            val manager = AppWidgetManager.getInstance(app)
            for (id in ids) {
                val views = RemoteViews(app.packageName, R.layout.widget_date_image)
                views.setImageViewBitmap(R.id.widget_image, image(app, look, place, report, manager.getAppWidgetOptions(id)))
                views.setContentDescription(R.id.widget_image, describe(place, report))
                views.setOnClickPendingIntent(R.id.widget_root, openApp(app))
                manager.updateAppWidget(id, views)
            }
        }

        private fun describe(place: Place?, report: WeatherReport?): String = when {
            place == null -> "天気"
            report == null -> place.name
            else -> "${place.name} ${Tenki.of(report.code).kanji} ${Tenki.temperature(report.temperature)}"
        }

        private fun image(context: Context, look: WidgetLook, place: Place?, report: WeatherReport?, options: Bundle): Bitmap {
            val density = context.resources.displayMetrics.density
            val bitmap = WidgetLook.bitmap(context, options, fallbackWidthDp = 250, fallbackHeightDp = 110)
            val canvas = Canvas(bitmap)
            val colors = look.colors
            val inset = look.frame(canvas, density)
            val left = inset
            val right = bitmap.width - inset
            val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = look.typeface }

            fun draw(value: String, baseline: Float, size: Float, color: Color, alignRight: Boolean = false, maxWidth: Float = right - left): Float {
                text.textSize = size * density
                text.color = color.toArgb()
                val shown = TextUtils.ellipsize(value, text, maxWidth, TextUtils.TruncateAt.END).toString()
                val width = text.measureText(shown)
                canvas.drawText(shown, if (alignRight) right - width else left, baseline, text)
                return width
            }

            // Si el widget es más alto que su contenido, este va centrado y no pegado arriba
            val spare = (bitmap.height - 2 * inset - 90 * density).coerceAtLeast(0f)
            var y = inset + spare / 2 + 13 * density
            if (place == null) {
                draw("天気", y, 13f, colors.inkSoft)
                draw(context.getString(R.string.widget_weather_no_place), y + 24 * density, 13f, colors.inkSoft)
                return bitmap
            }
            draw(place.name, y, 13f, colors.inkSoft)
            if (report == null) {
                draw("取得できません", y + 28 * density, 15f, colors.inkSoft)
                return bitmap
            }

            // La temperatura a la derecha, grande; la palabra del tiempo ocupa lo que quede
            y += 32 * density
            val temperature = draw(Tenki.temperature(report.temperature), y, 28f, colors.ink, alignRight = true)
            draw(Tenki.of(report.code).kanji, y, 22f, colors.ink, maxWidth = right - left - temperature - 8 * density)
            val bottom = bitmap.height - inset
            report.days.getOrNull(0)?.let { today ->
                y += 22 * density
                if (y <= bottom) draw("最高 ${Tenki.temperature(today.max)}　最低 ${Tenki.temperature(today.min)}", y, 13f, colors.ink)
            }
            report.days.getOrNull(1)?.let { next ->
                y += 20 * density
                if (y <= bottom) draw("明日　${Tenki.of(next.code).kanji}　${Tenki.temperature(next.max)} ／ ${Tenki.temperature(next.min)}", y, 13f, colors.inkSoft)
            }
            return bitmap
        }
    }
}
