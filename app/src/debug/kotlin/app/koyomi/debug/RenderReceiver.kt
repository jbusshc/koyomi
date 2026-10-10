package app.koyomi.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import app.koyomi.KoyomiApp
import app.koyomi.settings.Place
import app.koyomi.settings.SkinId
import app.koyomi.settings.ThemeMode
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
 *       --es kind summary --es sizes 168x115,347x244 --es skin RPG --es mode DARK --es place Tokio:35.68:139.69
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
                val tag = listOf(kind, settings.skin.name, settings.mode.name).joinToString("_")
                for ((w, h) in sizes) {
                    val bitmap = Bitmap.createBitmap((w * density).roundToInt(), (h * density).roundToInt(), Bitmap.Config.ARGB_8888)
                    renderSummaryForDebug(app, bitmap, settings, kind)
                    File(context.filesDir, "${tag}_${w}x$h.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
