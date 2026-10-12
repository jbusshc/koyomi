package app.koyomi.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.koyomi.settings.SkinId
import app.koyomi.settings.ThemeMode
import kotlinx.coroutines.flow.first

/** Qué cuenta un widget Resumen. AUTO decide por el tamaño; los demás fijan la disposición. */
enum class WidgetLayout { AUTO, DATE, WEATHER, AGENDA, MONTH }

/** Cómo es el fondo: pleno, de cristal (translúcido) o ninguno, con el texto sobre el fondo de pantalla. */
enum class WidgetBackground { SOLID, GLASS, CLEAR }

/**
 * Lo que el usuario elige para un widget en concreto. El tema y el modo, nulos, siguen a la app.
 */
data class WidgetConfig(
    val layout: WidgetLayout = WidgetLayout.AUTO,
    val skin: SkinId? = null,
    val mode: ThemeMode? = null,
    val background: WidgetBackground = WidgetBackground.SOLID,
    val showWeather: Boolean = true,
    val showForecast: Boolean = true,
    val showEvents: Boolean = true,
    val showMonth: Boolean = true,
    val showNotes: Boolean = true,
    /** Cada cuántos minutos se pide el tiempo y se repinta. 0: solo a mano (↻) o al abrir la app. */
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
    /** El botón ↻ en la esquina. */
    val showRefresh: Boolean = true,
) {
    fun encode(): String {
        val flags = listOf(showWeather, showForecast, showEvents, showMonth, showNotes).joinToString("") { if (it) "1" else "0" }
        return listOf(layout.name, skin?.name.orEmpty(), mode?.name.orEmpty(), background.name, flags, refreshMinutes.toString(), if (showRefresh) "1" else "0").joinToString("|")
    }

    companion object {
        const val DEFAULT_REFRESH_MINUTES = 180

        /** Las frecuencias que se ofrecen, en minutos; 0 es «a mano». */
        val REFRESH_CHOICES = listOf(30, 60, 180, 360, 720, 0)

        fun decode(value: String?): WidgetConfig {
            val parts = value?.split('|') ?: return WidgetConfig()
            // Las configuraciones de antes de 0.17 tenían cinco partes
            if (parts.size != 5 && parts.size != 7) return WidgetConfig()
            val flags = parts[4].padEnd(5, '1')
            return WidgetConfig(
                layout = WidgetLayout.entries.firstOrNull { it.name == parts[0] } ?: WidgetLayout.AUTO,
                skin = SkinId.entries.firstOrNull { it.name == parts[1] },
                mode = ThemeMode.entries.firstOrNull { it.name == parts[2] },
                background = WidgetBackground.entries.firstOrNull { it.name == parts[3] } ?: WidgetBackground.SOLID,
                showWeather = flags[0] == '1',
                showForecast = flags[1] == '1',
                showEvents = flags[2] == '1',
                showMonth = flags[3] == '1',
                showNotes = flags[4] == '1',
                refreshMinutes = parts.getOrNull(5)?.toIntOrNull()?.takeIf { it in REFRESH_CHOICES } ?: DEFAULT_REFRESH_MINUTES,
                showRefresh = parts.getOrNull(6) != "0",
            )
        }
    }
}

/** Qué secciones entran en un widget con esta configuración. */
internal fun WidgetConfig.sections(): Set<Section> = when (layout) {
    WidgetLayout.AUTO -> buildSet {
        if (showWeather) add(Section.WEATHER)
        add(Section.DATE)
        if (showEvents) add(Section.EVENTS)
    }
    WidgetLayout.WEATHER -> setOf(Section.WEATHER)
    WidgetLayout.AGENDA -> setOf(Section.DATE, Section.EVENTS)
    WidgetLayout.DATE, WidgetLayout.MONTH -> setOf(Section.DATE)
}

private val Context.widgetStore by preferencesDataStore("widgets")

/** La configuración de cada widget puesto, por su número. Un widget sin entrada usa la de fábrica. */
class WidgetConfigStore(private val context: Context) {
    private fun key(id: Int) = stringPreferencesKey("w$id")

    suspend fun get(id: Int): WidgetConfig = WidgetConfig.decode(context.widgetStore.data.first()[key(id)])

    suspend fun set(id: Int, config: WidgetConfig) {
        context.widgetStore.edit { it[key(id)] = config.encode() }
    }

    suspend fun remove(ids: IntArray) {
        context.widgetStore.edit { prefs -> ids.forEach { prefs.remove(key(it)) } }
    }
}
