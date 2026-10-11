package app.koyomi.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import app.koyomi.KoyomiApp
import app.koyomi.settings.Place
import app.koyomi.settings.SkinId
import app.koyomi.settings.ThemeMode
import app.koyomi.engine.weather.Sky
import app.koyomi.settings.Settings
import app.koyomi.ui.skin.skinOf
import app.koyomi.ui.weather.WeatherIcons
import app.koyomi.widget.WidgetLook
import app.koyomi.widget.WidgetBackground
import app.koyomi.widget.WidgetConfig
import app.koyomi.widget.WidgetLayout
import app.koyomi.widget.renderSummaryForDebug
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Pinta un widget a varios tamaños y deja los PNG en la carpeta de archivos de la app, para
 * revisarlos sin tener que redimensionar nada en la pantalla de inicio.
 *
 *   adb shell am broadcast -n app.koyomi/app.koyomi.debug.RenderReceiver \\
 *       --es kind auto|date|weather|agenda|month|icons --es sizes 168x115,347x244 --es skin RPG --es mode DARK --es place Tokio:35.68:139.69
 *
 * Los tamaños son los que da el lanzador, en dp, ancho por alto.
 */
class RenderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as KoyomiApp
        val pending = goAsync()
        val kind = intent.getStringExtra("kind") ?: "summary"
        val sizes = intent.getStringExtra("sizes").orEmpty().split(',').mapNotNull { size ->
            val (w, h) = size.split('x').takeIf { it.size == 2 } ?: return@mapNotNull null
            (w.toIntOrNull() ?: return@mapNotNull null) to (h.toIntOrNull() ?: return@mapNotNull null)
        }
        app.appScope.launch {
            try {
                var settings = app.settings.settings.first()
                intent.getStringExtra("skin")?.let { name -> SkinId.entries.firstOrNull { it.name.equals(name, true) }?.let { settings = settings.copy(skin = it) } }
                intent.getStringExtra("mode")?.let { name -> ThemeMode.entries.firstOrNull { it.name.equals(name, true) }?.let { settings = settings.copy(mode = it) } }
                if (intent.getBooleanExtra("rekichu", false)) settings = settings.copy(rekichu = true)
                intent.getStringExtra("place")?.split(':')?.takeIf { it.size == 3 }?.let { (name, lat, lon) ->
                    settings = settings.copy(weather = Place(name, "", lat.toDouble(), lon.toDouble()))
                }
                app.weather.refresh(settings.weather, force = true)

                val density = app.resources.displayMetrics.density
                val layout = WidgetLayout.entries.firstOrNull { it.name.equals(kind, true) } ?: WidgetLayout.AUTO
                val background = intent.getStringExtra("bg")?.let { name -> WidgetBackground.entries.firstOrNull { it.name.equals(name, true) } } ?: WidgetBackground.SOLID
                val config = WidgetConfig(layout = layout, background = background)
                val tag = listOf(kind, settings.skin.name, settings.mode.name, background.name.takeIf { background != WidgetBackground.SOLID }).filterNotNull().joinToString("_")
                if (kind == "icons") {
                    renderIcons(context, settings, density, File(context.filesDir, "${tag}.png"))
                    return@launch
                }
                for ((w, h) in sizes) {
                    val bitmap = Bitmap.createBitmap((w * density).roundToInt(), (h * density).roundToInt(), Bitmap.Config.ARGB_8888)
                    renderSummaryForDebug(app, bitmap, settings, config)
                    File(context.filesDir, "${tag}_${w}x$h.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Una hoja con todos los iconos del tiempo del tema: los nueve cielos y las dos lunas. */
    private fun renderIcons(context: Context, settings: Settings, density: Float, target: File) {
        val look = WidgetLook.of(context, settings)
        val skin = skinOf(settings.skin)
        val items = Sky.entries.map { it to false } + listOf(Sky.CLEAR to true, Sky.PARTLY to true)
        val columns = 6
        val rows = (items.size + columns - 1) / columns
        val cell = 72 * density
        val bitmap = Bitmap.createBitmap((columns * cell).roundToInt(), (rows * cell).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(look.colors.surface.toArgb())
        val sink = WeatherIcons.canvasSink(canvas)
        items.forEachIndexed { index, (sky, night) ->
            val x = (index % columns) * cell + 6 * density
            val y = (index / columns) * cell + 6 * density
            WeatherIcons.paint(sink, skin, look.colors, sky, night, x, y, cell - 12 * density)
        }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
