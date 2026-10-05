package app.koyomi.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.TimeStyle
import app.koyomi.engine.format.Youbi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val dateStyle: DateStyle = DateStyle.EDUCATIONAL,
    val timeStyle: TimeStyle = TimeStyle.H24,
    /** 日 o 月: el primer día de la semana en la vista mensual. */
    val weekStart: Youbi = Youbi.NICHI,
    /** Calendarios del teléfono que Koyomi no enseña. */
    val hiddenCalendars: Set<Long> = emptySet(),
)

private val Context.dataStore by preferencesDataStore("settings")

/** Solo preferencias: aquí no se guarda nada del calendario salvo qué calendarios ocultar. */
class SettingsStore(private val context: Context) {
    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val defaults = Settings()
        Settings(
            dateStyle = enumOr(prefs[DATE_STYLE], defaults.dateStyle),
            timeStyle = enumOr(prefs[TIME_STYLE], defaults.timeStyle),
            weekStart = enumOr(prefs[WEEK_START], defaults.weekStart),
            hiddenCalendars = prefs[HIDDEN_CALENDARS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        )
    }

    suspend fun setDateStyle(style: DateStyle) = context.dataStore.edit { it[DATE_STYLE] = style.name }

    suspend fun setTimeStyle(style: TimeStyle) = context.dataStore.edit { it[TIME_STYLE] = style.name }

    suspend fun setWeekStart(day: Youbi) = context.dataStore.edit { it[WEEK_START] = day.name }

    suspend fun setCalendarHidden(id: Long, hidden: Boolean) = context.dataStore.edit { prefs ->
        val current = prefs[HIDDEN_CALENDARS].orEmpty()
        prefs[HIDDEN_CALENDARS] = if (hidden) current + id.toString() else current - id.toString()
    }

    private companion object {
        val DATE_STYLE = stringPreferencesKey("date_style")
        val TIME_STYLE = stringPreferencesKey("time_style")
        val WEEK_START = stringPreferencesKey("week_start")
        val HIDDEN_CALENDARS = stringSetPreferencesKey("hidden_calendars")

        inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
