package app.koyomi.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.RemoteViews
import app.koyomi.KoyomiApp
import app.koyomi.R
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * Las vistas previas del selector de widgets con el tema, el modo y los datos de verdad (Android 15
 * en adelante; antes, el selector enseña la muestra fija de cada widget). Se renuevan al cambiar
 * los ajustes, no en cada pintada: el sistema limita cuántas veces se pueden poner.
 */
internal object WidgetPreviews {
    private data class Size(val provider: Class<*>, val widthDp: Int, val heightDp: Int, val clock: Boolean = false)

    private val sizes = listOf(
        Size(SummaryWidget::class.java, 280, 130),
        Size(SummaryCompactWidget::class.java, 130, 130),
        Size(SummaryLargeWidget::class.java, 280, 280),
        Size(ClockWidget::class.java, 220, 90, clock = true),
    )

    suspend fun update(app: KoyomiApp) {
        if (Build.VERSION.SDK_INT < 35) return
        val settings = app.settings.settings.first()
        val manager = AppWidgetManager.getInstance(app)
        val density = app.resources.displayMetrics.density
        val now = ZonedDateTime.now()
        for (size in sizes) {
            runCatching {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    (size.widthDp * density).roundToInt(),
                    (size.heightDp * density).roundToInt(),
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
                val views: RemoteViews = if (size.clock) {
                    ClockWidget.clockViews(app, settings, WidgetConfig(), bitmap, size.heightDp.toFloat(), now)
                } else {
                    renderSummary(app, bitmap, settings, WidgetConfig(showRefresh = false))
                    RemoteViews(app.packageName, R.layout.widget_date_image).apply { setImageViewBitmap(R.id.widget_image, bitmap) }
                }
                manager.setWidgetPreview(ComponentName(app, size.provider), android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views)
            }
        }
    }
}
