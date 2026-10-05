package app.koyomi.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.koyomi.KoyomiApp
import app.koyomi.MainActivity
import app.koyomi.R
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * La fecha de hoy en la pantalla de inicio del teléfono: 令和8年 / 10月4日 / 日曜日, y el festivo si
 * lo hay. Tocarla abre la app.
 *
 * Se pone al día al cambiar de día, al cambiar la hora o la zona del teléfono, al salir de la app
 * y, por si acaso, cada pocas horas. No lee el calendario: solo necesita el reloj.
 */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, goAsync())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // El cambio de día llega por una alarma propia; lo demás lo reparte AppWidgetProvider
            ACTION_NEW_DAY, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> render(context, goAsync())
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(newDay(context))
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
        private const val ACTION_NEW_DAY = "app.koyomi.WIDGET_NEW_DAY"

        /** Vuelve a pintar el widget, si hay alguno puesto. Barato: se puede llamar al volver a la app. */
        fun refresh(context: Context) {
            val app = context.applicationContext as KoyomiApp
            if (ids(app).isEmpty()) return
            app.appScope.launch { runCatching { draw(app) } }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DateWidget::class.java))

        private suspend fun draw(app: KoyomiApp) {
            val ids = ids(app)
            if (ids.isEmpty()) return
            val style = app.settings.settings.first().dateStyle
            val today = LocalDate.now()
            val date = JaDate.of(today)
            val holiday = Shukujitsu.of(today)

            val views = RemoteViews(app.packageName, R.layout.widget_date)
            views.setViewVisibility(R.id.widget_year, if (style == DateStyle.SIMPLE) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_year, if (style == DateStyle.EDUCATIONAL) "${date.year} · ${date.seirekiYear}" else date.year)
            views.setTextViewText(R.id.widget_date, date.monthDay)
            views.setTextViewText(R.id.widget_youbi, listOfNotNull(date.youbi.full, holiday).joinToString(" · "))
            views.setTextColor(
                R.id.widget_youbi,
                app.getColor(
                    when {
                        holiday != null || date.youbi == Youbi.NICHI -> R.color.widget_sunday
                        date.youbi == Youbi.DO -> R.color.widget_saturday
                        else -> R.color.widget_ink_soft
                    },
                ),
            )
            val open = PendingIntent.getActivity(
                app,
                1,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            AppWidgetManager.getInstance(app).updateAppWidget(ids, views)

            // A medianoche toca otra fecha. No despierta al teléfono: si está dormido, se pinta al encenderse.
            val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            app.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, midnight + 2_000, newDay(app))
        }

        private fun newDay(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, DateWidget::class.java).setAction(ACTION_NEW_DAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
