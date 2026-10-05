package app.koyomi.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.TimeStyle
import app.koyomi.engine.format.Youbi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

enum class SkinId { STANDARD, JRPG, TRADITIONAL, RETRO }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Una ciudad para el tiempo. Lo único de los ajustes que llega a salir del teléfono, y solo sus coordenadas. */
data class Place(val name: String, val detail: String, val latitude: Double, val longitude: Double) {
    /** Las coordenadas con la precisión con que se piden: dos sitios con la misma clave son el mismo parte. */
    val key: String get() = String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude)
}

data class Settings(
    val skin: SkinId = SkinId.STANDARD,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dateStyle: DateStyle = DateStyle.EDUCATIONAL,
    val timeStyle: TimeStyle = TimeStyle.H24,
    /** 日 o 月: el primer día de la semana en la vista mensual. */
    val weekStart: Youbi = Youbi.NICHI,
    /** 暦注: si junto a la fecha salen el 六曜, la fecha antigua y los términos solares. */
    val rekichu: Boolean = false,
    /** De dónde se enseña el tiempo. Nulo: no se enseña, y Koyomi no usa internet. */
    val weather: Place? = null,
    /** Calendarios del teléfono que Koyomi no enseña. */
    val hiddenCalendars: Set<Long> = emptySet(),
)

private val Context.dataStore by preferencesDataStore("settings")

/** Solo preferencias: aquí no se guarda nada del calendario salvo qué calendarios ocultar. */
class SettingsStore(private val context: Context) {
    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val defaults = Settings()
        Settings(
            skin = enumOr(prefs[SKIN], defaults.skin),
            mode = enumOr(prefs[MODE], defaults.mode),
            dateStyle = enumOr(prefs[DATE_STYLE], defaults.dateStyle),
            timeStyle = enumOr(prefs[TIME_STYLE], defaults.timeStyle),
            weekStart = enumOr(prefs[WEEK_START], defaults.weekStart),
            rekichu = prefs[REKICHU] ?: defaults.rekichu,
            weather = prefs[WEATHER_NAME]?.let { name ->
                Place(name, prefs[WEATHER_DETAIL].orEmpty(), prefs[WEATHER_LAT] ?: return@let null, prefs[WEATHER_LON] ?: return@let null)
            },
            hiddenCalendars = prefs[HIDDEN_CALENDARS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        )
    }

    suspend fun setSkin(skin: SkinId) = context.dataStore.edit { it[SKIN] = skin.name }

    suspend fun setMode(mode: ThemeMode) = context.dataStore.edit { it[MODE] = mode.name }

    suspend fun setDateStyle(style: DateStyle) = context.dataStore.edit { it[DATE_STYLE] = style.name }

    suspend fun setTimeStyle(style: TimeStyle) = context.dataStore.edit { it[TIME_STYLE] = style.name }

    suspend fun setWeekStart(day: Youbi) = context.dataStore.edit { it[WEEK_START] = day.name }

    suspend fun setRekichu(shown: Boolean) = context.dataStore.edit { it[REKICHU] = shown }

    suspend fun setWeather(place: Place?) = context.dataStore.edit { prefs ->
        if (place == null) {
            listOf(WEATHER_NAME, WEATHER_DETAIL, WEATHER_LAT, WEATHER_LON).forEach { prefs.remove(it) }
        } else {
            prefs[WEATHER_NAME] = place.name
            prefs[WEATHER_DETAIL] = place.detail
            prefs[WEATHER_LAT] = place.latitude
            prefs[WEATHER_LON] = place.longitude
        }
    }

    suspend fun setCalendarHidden(id: Long, hidden: Boolean) = context.dataStore.edit { prefs ->
        val current = prefs[HIDDEN_CALENDARS].orEmpty()
        prefs[HIDDEN_CALENDARS] = if (hidden) current + id.toString() else current - id.toString()
    }

    private companion object {
        val SKIN = stringPreferencesKey("skin")
        val MODE = stringPreferencesKey("mode")
        val DATE_STYLE = stringPreferencesKey("date_style")
        val TIME_STYLE = stringPreferencesKey("time_style")
        val WEEK_START = stringPreferencesKey("week_start")
        val REKICHU = booleanPreferencesKey("rekichu")
        val WEATHER_NAME = stringPreferencesKey("weather_name")
        val WEATHER_DETAIL = stringPreferencesKey("weather_detail")
        val WEATHER_LAT = doublePreferencesKey("weather_lat")
        val WEATHER_LON = doublePreferencesKey("weather_lon")
        val HIDDEN_CALENDARS = stringSetPreferencesKey("hidden_calendars")

        inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
