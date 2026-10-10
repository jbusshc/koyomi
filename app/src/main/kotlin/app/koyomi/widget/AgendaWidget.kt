package app.koyomi.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * La fecha y lo que queda por delante, en la pantalla de inicio: los eventos de hoy que aún no han
 * terminado y, si caben, los de mañana. Tocarlo abre la app. Sigue el tema y el modo de la app.
 *
 * Se dibuja entero en una imagen, con la misma fuente y el mismo marco que el tema. Se pone al día
 * cuando cambia el calendario, cuando termina un evento, al cambiar de día y al volver de la app.
 */
class AgendaWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, goAsync())

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, goAsync())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> render(context, goAsync())
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(nextRefresh(context))
        WidgetJobs.release(context)
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

    /** El aviso del sistema de que el calendario cambió: repinta los widgets que lo muestran. */
    class CalendarChanged : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {
            refresh(this)
            SummaryWidget.refresh(this)
            return false
        }

        override fun onStopJob(params: JobParameters): Boolean = false
    }

    companion object {
        private const val ACTION_REFRESH = "app.koyomi.AGENDA_REFRESH"

        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        /** Vuelve a pintar el widget, si hay alguno puesto. */
        fun refresh(context: Context) {
            val app = context.applicationContext as KoyomiApp
            if (ids(app).isEmpty()) return
            app.appScope.launch { runCatching { draw(app) } }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AgendaWidget::class.java))

        private suspend fun draw(app: KoyomiApp) {
            val ids = ids(app)
            if (ids.isEmpty()) return
            val settings = app.settings.settings.first()
            val look = WidgetLook.of(app, settings)
            val snapshot = loadAgenda(app, settings, ZonedDateTime.now())
            val rows = agendaRows(app, look, settings, snapshot)

            val manager = AppWidgetManager.getInstance(app)
            for (id in ids) {
                val bitmap = image(app, look, settings, snapshot.today, rows, manager.getAppWidgetOptions(id))
                val views = RemoteViews(app.packageName, R.layout.widget_date_image)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setContentDescription(R.id.widget_image, describe(snapshot.today, rows))
                views.setOnClickPendingIntent(R.id.widget_root, openApp(app))
                manager.updateAppWidget(id, views)
            }

            // Lo siguiente que cambia la lista. No despierta al teléfono: si está dormido, se pinta al encenderse
            app.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, snapshot.nextChangeMillis(), nextRefresh(app))
            WidgetJobs.watchCalendar(app)
        }

        private fun image(context: Context, look: WidgetLook, settings: Settings, today: LocalDate, rows: List<Row>, options: Bundle): Bitmap {
            val density = context.resources.displayMetrics.density
            val bitmap = WidgetLook.bitmap(context, options, fallbackWidthDp = 300, fallbackHeightDp = 160)
            val canvas = Canvas(bitmap)
            val colors = look.colors
            val inset = look.frame(canvas, density)
            val left = inset
            val right = bitmap.width - inset
            val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = look.typeface }
            val shape = Paint(Paint.ANTI_ALIAS_FLAG)

            fun draw(value: String, x: Float, baseline: Float, size: Float, color: Color, maxWidth: Float = Float.MAX_VALUE, alignRight: Boolean = false): Float {
                text.textSize = size * density
                text.color = color.toArgb()
                val shown = if (maxWidth == Float.MAX_VALUE) value else TextUtils.ellipsize(value, text, maxWidth, TextUtils.TruncateAt.END).toString()
                val width = text.measureText(shown)
                canvas.drawText(shown, if (alignRight) x - width else x, baseline, text)
                return width
            }

            // Cabecera: la fecha a la izquierda, la era o el festivo a la derecha
            val date = JaDate.of(today)
            val holiday = Shukujitsu.of(today)
            var y = inset + 19 * density
            val dateWidth = draw(date.monthDay, left, y, 19f, colors.ink)
            draw("（${date.youbi.short}）", left + dateWidth, y, 15f, look.youbiColor(date.youbi, holiday != null))
            val aside = holiday ?: date.year.takeIf { settings.dateStyle != DateStyle.SIMPLE }
            if (aside != null) draw(aside, right, y, 12f, if (holiday != null) colors.sunday else colors.inkSoft, alignRight = true)
            y += 9 * density
            shape.color = colors.rule.toArgb()
            canvas.drawRect(left, y, right, y + density, shape)
            y += 6 * density

            // Tantas filas como quepan; la hora ocupa lo que la más larga, y el título, el resto
            val rowHeight = 23 * density
            val capacity = ((bitmap.height - inset - y) / rowHeight).toInt().coerceAtLeast(1)
            var shown = rows.take(capacity)
            // Un «明日» al final, sin nada debajo, sobra
            if (shown.lastOrNull() is Row.Label) shown = shown.dropLast(1)
            text.textSize = 13 * density
            val timeWidth = shown.filterIsInstance<Row.Event>().maxOfOrNull { text.measureText(it.time) } ?: 0f
            val dot = 6 * density
            for (row in shown) {
                val baseline = y + rowHeight * 0.5f + 5 * density
                when (row) {
                    is Row.Event -> {
                        shape.color = row.color.toArgb()
                        val top = y + (rowHeight - dot) / 2
                        if (look.squareDots) canvas.drawRect(left, top, left + dot, top + dot, shape) else canvas.drawOval(left, top, left + dot, top + dot, shape)
                        val timeX = left + dot + 8 * density
                        draw(row.time, timeX, baseline, 13f, colors.inkSoft)
                        val titleX = timeX + timeWidth + 10 * density
                        draw(row.title, titleX, baseline, 15f, colors.ink, maxWidth = right - titleX)
                    }
                    is Row.Label -> {
                        val width = draw(row.text, left, baseline, 13f, colors.ink)
                        draw(row.detail, left + width + 8 * density, baseline, 12f, row.detailColor)
                    }
                    is Row.Note -> draw(row.text, left, baseline, 14f, colors.inkSoft, maxWidth = right - left)
                }
                y += rowHeight
            }
            return bitmap
        }

        private fun describe(today: LocalDate, rows: List<Row>): String = buildString {
            append(JaDate.of(today).line(DateStyle.SIMPLE))
            for (row in rows) {
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
            3,
            Intent(context, AgendaWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
