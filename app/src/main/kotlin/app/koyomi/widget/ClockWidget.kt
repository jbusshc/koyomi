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
import app.koyomi.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * La hora en japonés en la pantalla de inicio: 午後9時32分, o 21時32分, con la fecha debajo y, si se
 * quiere, la hora de otra ciudad y la próxima alarma.
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
            ACTION_HALF_DAY, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> render(context, goAsync())
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val app = context.applicationContext as KoyomiApp
        app.appScope.launch { app.widgets.remove(ids) }
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
            val now = ZonedDateTime.now()
            val today = now.toLocalDate()

            val manager = AppWidgetManager.getInstance(app)
            for (id in ids) {
                val bitmap = WidgetLook.bitmap(app, manager.getAppWidgetOptions(id), manager.getAppWidgetInfo(id))
                val heightDp = bitmap.height / app.resources.displayMetrics.density
                manager.updateAppWidget(id, clockViews(app, settings, app.widgets.get(id), bitmap, heightDp, now))
            }

            // Lo que no cambia solo: 午前/午後 a mediodía y la fecha a medianoche
            val noon = today.atTime(12, 0).atZone(now.zone)
            val next = if (now < noon) noon else today.plusDays(1).atStartOfDay(now.zone)
            app.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next.toInstant().toEpochMilli() + 1_000, halfDay(app))
        }

        /**
         * El reloj ya compuesto: el marco pintado en [bitmap] y los textos con los colores del tema.
         * Lo usan el widget puesto y la vista previa del selector.
         */
        internal fun clockViews(app: KoyomiApp, settings: Settings, config: WidgetConfig, bitmap: android.graphics.Bitmap, heightDp: Float, now: ZonedDateTime): RemoteViews {
            val look = WidgetLook.of(app, settings, config)
            val colors = look.colors
            val density = app.resources.displayMetrics.density
            val today = now.toLocalDate()
            val date = JaDate.of(today)
            val holiday = Shukujitsu.of(today)
            val gozenGogo = settings.timeStyle == TimeStyle.GOZEN_GOGO

            val views = RemoteViews(app.packageName, R.layout.widget_clock)
            look.frame(Canvas(bitmap), density)
            views.setImageViewBitmap(R.id.widget_image, bitmap)

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
            // En un widget de una sola fila solo cabe la hora
            views.setViewVisibility(R.id.widget_clock_date, if (heightDp >= 76f) View.VISIBLE else View.GONE)
            views.setTextColor(
                R.id.widget_clock_date,
                when {
                    holiday != null || date.youbi == Youbi.NICHI -> colors.sunday
                    date.youbi == Youbi.DO -> colors.saturday
                    else -> colors.inkSoft
                }.toArgb(),
            )

            // Lo de más: si el widget es bajo no caben, y se quita primero la alarma
            val zone = config.zone?.takeIf { heightDp >= 100f }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            if (zone != null) {
                views.setViewVisibility(R.id.widget_zone_row, View.VISIBLE)
                views.setTextViewText(R.id.widget_zone_label, ClockZones.label(zone, now))
                views.setTextColor(R.id.widget_zone_label, colors.inkSoft.toArgb())
                views.setString(R.id.widget_zone_clock, "setTimeZone", zone.id)
                // Siempre en 24 horas: 午前/午後 es un texto fijo y no sirve para otra ciudad
                views.setCharSequence(R.id.widget_zone_clock, "setFormat12Hour", "H時mm分")
                views.setCharSequence(R.id.widget_zone_clock, "setFormat24Hour", "H時mm分")
                views.setTextColor(R.id.widget_zone_clock, colors.ink.toArgb())
            } else {
                views.setViewVisibility(R.id.widget_zone_row, View.GONE)
            }

            val alarm = if (config.showAlarm && heightDp >= 120f) nextAlarmText(app, now) else null
            if (alarm != null) {
                views.setViewVisibility(R.id.widget_alarm, View.VISIBLE)
                views.setTextViewText(R.id.widget_alarm, alarm)
                views.setTextColor(R.id.widget_alarm, colors.inkSoft.toArgb())
            } else {
                views.setViewVisibility(R.id.widget_alarm, View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_root, openApp(app))
            return views
        }

        /** «⏰ 7時30分», con el día si no es hoy; nulo si no hay alarma puesta. */
        private fun nextAlarmText(app: KoyomiApp, now: ZonedDateTime): String? {
            val info = app.getSystemService(AlarmManager::class.java).nextAlarmClock ?: return null
            val at = Instant.ofEpochMilli(info.triggerTime).atZone(now.zone)
            val time = "%d時%02d分".format(at.hour, at.minute)
            val day = when {
                at.toLocalDate() == now.toLocalDate() -> ""
                at.toLocalDate() == now.toLocalDate().plusDays(1) -> "明日 "
                else -> "（${Youbi.of(at.toLocalDate()).short}） "
            }
            return "⏰ $day$time"
        }

        private fun halfDay(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            4,
            Intent(context, ClockWidget::class.java).setAction(ACTION_HALF_DAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Las ciudades que se ofrecen para el segundo reloj, con su nombre en japonés. */
internal object ClockZones {
    val choices: List<Pair<String, String>> = listOf(
        "Asia/Tokyo" to "東京",
        "Asia/Seoul" to "ソウル",
        "Asia/Shanghai" to "上海",
        "Australia/Sydney" to "シドニー",
        "Europe/London" to "ロンドン",
        "Europe/Paris" to "パリ",
        "Europe/Madrid" to "マドリード",
        "America/New_York" to "ニューヨーク",
        "America/Los_Angeles" to "ロサンゼルス",
        "America/Mexico_City" to "メキシコシティ",
        "America/Bogota" to "ボゴタ",
        "America/Lima" to "リマ",
        "America/Santiago" to "サンティアゴ",
        "America/Argentina/Buenos_Aires" to "ブエノスアイレス",
        "America/Sao_Paulo" to "サンパウロ",
        "UTC" to "UTC",
    )

    /** «ニューヨーク　−13時間»: la ciudad y cuánto se aleja de la hora de aquí. */
    fun label(zone: ZoneId, now: ZonedDateTime): String {
        val name = choices.firstOrNull { it.first == zone.id }?.second ?: zone.id.substringAfterLast('/').replace('_', ' ')
        val here = now.offset.totalSeconds
        val there = now.withZoneSameInstant(zone).offset.totalSeconds
        val hours = (there - here) / 3600f
        val shown = if (hours % 1f == 0f) hours.toInt().toString() else "%.1f".format(hours)
        val sign = if (hours > 0) "+" else if (hours < 0) "" else "±"
        return "$name　$sign${shown.replace('-', '−')}時間"
    }
}
