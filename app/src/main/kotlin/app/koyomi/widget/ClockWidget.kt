package app.koyomi.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.TimeStyle
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * La hora en japonés en la pantalla de inicio: 午後9時32分, o 21時32分, con la fecha debajo.
 *
 * Los minutos los pone el propio sistema (un TextClock), que es la única forma de tener un reloj al
 * minuto sin despertar a la app sesenta veces por hora. Lo que el sistema no sabe escribir en
 * japonés, 午前 y 午後, es un texto aparte que se cambia a mediodía y a medianoche.
 *
 * El precio: el texto va en la fuente del teléfono en todos los temas. El marco y los colores sí
 * son los del tema.
 */
class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, goAsync())

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, goAsync())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_HALF_DAY, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> render(context, goAsync())
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(halfDay(context))
    }

    private fun render(context: Context, pending: PendingResult?) {
        val app = context.applicationContext as KoyomiApp
        app.appScope.launch {
            try {
                draw(app)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val ACTION_HALF_DAY = "app.koyomi.CLOCK_HALF_DAY"

        fun refresh(context: Context) {
            val app = context.applicationContext as KoyomiApp
            if (ids(app).isEmpty()) return
            app.appScope.launch { runCatching { draw(app) } }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ClockWidget::class.java))

        private suspend fun draw(app: KoyomiApp) {
            val ids = ids(app)
            if (ids.isEmpty()) return
            val settings = app.settings.settings.first()
            val look = WidgetLook.of(app, settings)
            val colors = look.colors
            val now = ZonedDateTime.now()
            val today = now.toLocalDate()
            val date = JaDate.of(today)
            val holiday = Shukujitsu.of(today)
            val gozenGogo = settings.timeStyle == TimeStyle.GOZEN_GOGO

            val manager = AppWidgetManager.getInstance(app)
            val density = app.resources.displayMetrics.density
            for (id in ids) {
                val views = RemoteViews(app.packageName, R.layout.widget_clock)
                val frame = WidgetLook.bitmap(app, manager.getAppWidgetOptions(id), fallbackWidthDp = 250, fallbackHeightDp = 80)
                look.frame(Canvas(frame), density)
                views.setImageViewBitmap(R.id.widget_image, frame)

                // K es la hora de 0 a 11: mediodía es 午後0時, como en el resto de la app
                val pattern: CharSequence = if (gozenGogo) "K時mm分" else "H時mm分"
                views.setCharSequence(R.id.widget_clock, "setFormat12Hour", pattern)
                views.setCharSequence(R.id.widget_clock, "setFormat24Hour", pattern)
                views.setTextColor(R.id.widget_clock, colors.ink.toArgb())
                views.setViewVisibility(R.id.widget_ampm, if (gozenGogo) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_ampm, if (now.hour < 12) "午前" else "午後")
                views.setTextColor(R.id.widget_ampm, colors.inkSoft.toArgb())

                val line = if (settings.dateStyle == DateStyle.SIMPLE) date.line(DateStyle.SIMPLE) else date.line(DateStyle.FULL)
                views.setTextViewText(R.id.widget_clock_date, listOfNotNull(line, holiday).joinToString(" · "))
                views.setTextColor(
                    R.id.widget_clock_date,
                    when {
                        holiday != null || date.youbi == Youbi.NICHI -> colors.sunday
                        date.youbi == Youbi.DO -> colors.saturday
                        else -> colors.inkSoft
                    }.toArgb(),
                )
                views.setOnClickPendingIntent(R.id.widget_root, openApp(app))
                manager.updateAppWidget(id, views)
            }

            // Lo que no cambia solo: 午前/午後 a mediodía y la fecha a medianoche
            val noon = today.atTime(12, 0).atZone(now.zone)
            val next = if (now < noon) noon else today.plusDays(1).atStartOfDay(now.zone)
            app.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next.toInstant().toEpochMilli() + 1_000, halfDay(app))
        }

        private fun halfDay(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            4,
            Intent(context, ClockWidget::class.java).setAction(ACTION_HALF_DAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
