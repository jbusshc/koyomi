package app.koyomi.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.koyomi.KoyomiApp
import app.koyomi.MainActivity
import app.koyomi.R
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.JaDateText
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.settings.SkinId
import app.koyomi.ui.theme.Palette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * La fecha de hoy en la pantalla de inicio del teléfono: 令和8年 / 10月4日 / 日曜日, y el festivo si
 * lo hay. Tocarla abre la app. Sigue el tema y el modo de la app.
 *
 * Se pone al día al cambiar de día, al cambiar la hora o la zona del teléfono, al salir de la app
 * y, por si acaso, cada pocas horas. No lee el calendario: solo necesita el reloj.
 */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, goAsync())

    // Los temas que van en imagen se dibujan a la medida exacta del widget: al cambiarla, se dibujan otra vez
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, goAsync())

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

    /** Lo que el widget enseña, ya resuelto: las dos formas de pintarlo parten de lo mismo. */
    private class Content(val date: JaDateText, style: DateStyle, holiday: String?, val colors: Palette) {
        val year: String? = when (style) {
            DateStyle.SIMPLE -> null
            DateStyle.FULL -> date.year
            DateStyle.EDUCATIONAL -> "${date.year} · ${date.seirekiYear}"
        }
        val youbi: String = listOfNotNull(date.youbi.full, holiday).joinToString(" · ")
        val youbiColor: Color = when {
            holiday != null || date.youbi == Youbi.NICHI -> colors.sunday
            date.youbi == Youbi.DO -> colors.saturday
            else -> colors.inkSoft
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
            val settings = app.settings.settings.first()
            val look = WidgetLook.of(app, settings)
            val today = LocalDate.now()
            // El tema tradicional escribe los números en kanji, como en la app
            val date = if (settings.skin == SkinId.TRADITIONAL) JaDate.kanji(today) else JaDate.of(today)
            val content = Content(date, settings.dateStyle, Shukujitsu.of(today), look.colors)

            val manager = AppWidgetManager.getInstance(app)
            for (id in ids) {
                val views = when (settings.skin) {
                    SkinId.STANDARD -> standard(app, content, look.dark)
                    else -> image(app, content, look, manager.getAppWidgetOptions(id))
                }
                views.setOnClickPendingIntent(R.id.widget_root, openApp(app))
                manager.updateAppWidget(id, views)
            }

            // A medianoche toca otra fecha. No despierta al teléfono: si está dormido, se pinta al encenderse.
            val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            app.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, midnight + 2_000, newDay(app))
        }

        /** Texto de verdad sobre un fondo liso: nítido a cualquier tamaño y sin gastar memoria. */
        private fun standard(context: Context, content: Content, dark: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_date)
            views.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
            views.setViewVisibility(R.id.widget_year, if (content.year == null) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_year, content.year.orEmpty())
            views.setTextColor(R.id.widget_year, content.colors.inkSoft.toArgb())
            views.setTextViewText(R.id.widget_date, content.date.monthDay)
            views.setTextColor(R.id.widget_date, content.colors.ink.toArgb())
            views.setTextViewText(R.id.widget_youbi, content.youbi)
            views.setTextColor(R.id.widget_youbi, content.youbiColor.toArgb())
            return views
        }

        /** Los temas con fuente propia, dibujados enteros. Vale porque la fecha cambia una vez al día. */
        private fun image(context: Context, content: Content, look: WidgetLook, options: Bundle): RemoteViews {
            val density = context.resources.displayMetrics.density
            val bitmap = WidgetLook.bitmap(context, options, fallbackWidthDp = 160, fallbackHeightDp = 80)
            val canvas = Canvas(bitmap)
            val colors = content.colors
            val inset = look.frame(canvas, density)

            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = look.typeface
                textAlign = Paint.Align.CENTER
            }
            val small = 13 * density
            val inner = bitmap.width - 2 * inset
            val lines = (if (content.year != null) 1 else 0) + 1
            // La fecha ocupa lo que dejan las líneas pequeñas, sin pasarse de ancho
            var big = (bitmap.height - 2 * inset - lines * small * 1.5f).coerceIn(14 * density, 44 * density)
            text.textSize = big
            val dateWidth = text.measureText(content.date.monthDay)
            if (dateWidth > inner) big *= inner / dateWidth

            val total = lines * small * 1.5f + big * 1.15f
            var y = (bitmap.height - total) / 2
            val centre = bitmap.width / 2f
            fun line(value: String, size: Float, color: Color, advance: Float) {
                text.textSize = size
                text.color = color.toArgb()
                // Una línea pequeña que no cabe se encoge antes que cortarse
                val measured = text.measureText(value)
                if (measured > inner) text.textSize = size * inner / measured
                canvas.drawText(value, centre, y + advance * 0.5f - (text.ascent() + text.descent()) / 2, text)
                y += advance
            }
            content.year?.let { line(it, small, colors.inkSoft, small * 1.5f) }
            line(content.date.monthDay, big, colors.ink, big * 1.15f)
            line(content.youbi, small, content.youbiColor, small * 1.5f)

            val views = RemoteViews(context.packageName, R.layout.widget_date_image)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            views.setContentDescription(R.id.widget_image, listOfNotNull(content.year, content.date.monthDay, content.youbi).joinToString(" "))
            return views
        }

        private fun newDay(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, DateWidget::class.java).setAction(ACTION_NEW_DAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Tocar un widget abre la app, o la trae al frente si ya estaba abierta. */
internal fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    1,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)
