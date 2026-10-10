package app.koyomi.weather

import android.content.Context
import app.koyomi.settings.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale

/** El parte de un día: código WMO y temperaturas en grados Celsius. */
data class DayForecast(val date: LocalDate, val code: Int, val max: Double, val min: Double)

data class WeatherReport(
    /** Para qué sitio es: si el usuario cambia de ciudad, el parte guardado ya no vale. */
    val place: String,
    val fetchedAt: Long,
    val temperature: Double,
    val code: Int,
    /** Si en ese sitio es de día ahora: decide entre el sol y la luna. */
    val isDay: Boolean,
    /** Hoy y los días siguientes, en la fecha local del sitio. */
    val days: List<DayForecast>,
)

/**
 * Lo único de Koyomi que usa internet. Pide el tiempo a Open-Meteo con las coordenadas de la
 * ciudad elegida, redondeadas, y nada más: ni ubicación del teléfono, ni cuenta, ni identificador.
 * Este paquete no ve el calendario.
 */
class WeatherRepository(context: Context) {
    private val cache = File(context.filesDir, "weather.json")
    private val lock = Mutex()

    private val _report = MutableStateFlow<WeatherReport?>(null)
    val report: StateFlow<WeatherReport?> = _report

    /** La última petición falló (sin red, casi siempre). El parte anterior, si lo hay, se sigue enseñando. */
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    /**
     * Deja el parte al día para [place]. No pide nada si el que hay es del mismo sitio y reciente.
     * Sin sitio elegido, olvida lo que hubiera.
     */
    suspend fun refresh(place: Place?, force: Boolean = false) = lock.withLock {
        withContext(Dispatchers.IO) {
            if (place == null) {
                cache.delete()
                _report.value = null
                _failed.value = false
                return@withContext
            }
            if (_report.value == null) _report.value = runCatching { parseCache(cache.readText()) }.getOrNull()
            val current = _report.value?.takeIf { it.place == place.key }
            if (current == null) _report.value = null
            if (!force && current != null && System.currentTimeMillis() - current.fetchedAt < FRESH_MS) return@withContext

            val fetched = runCatching { fetch(place) }
            _failed.value = fetched.isFailure
            fetched.getOrNull()?.let { (report, json) ->
                _report.value = report
                runCatching { cache.writeText(json) }
            }
        }
    }

    /** Ciudades que casan con [query], con el nombre en japonés cuando lo hay. */
    suspend fun search(query: String): Result<List<Place>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$GEOCODING?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=8&language=ja&format=json"
            val results = JSONObject(get(url)).optJSONArray("results") ?: return@runCatching emptyList()
            List(results.length()) { i ->
                val item = results.getJSONObject(i)
                Place(
                    name = item.getString("name"),
                    detail = listOf(item.optString("admin1"), item.optString("country")).filter { it.isNotBlank() }.joinToString("・"),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude"),
                )
            }
        }
    }

    private fun fetch(place: Place): Pair<WeatherReport, String> {
        // Dos decimales: algo más de un kilómetro. Basta para el tiempo de una ciudad
        val url = String.format(
            Locale.ROOT,
            "%s?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3",
            FORECAST,
            place.latitude,
            place.longitude,
        )
        val body = JSONObject(get(url))
        // Se guarda la respuesta con dos datos propios: de dónde es y cuándo se pidió
        body.put(KEY_PLACE, place.key).put(KEY_FETCHED, System.currentTimeMillis())
        val json = body.toString()
        return parseCache(json) to json
    }

    private fun parseCache(json: String): WeatherReport {
        val body = JSONObject(json)
        val current = body.getJSONObject("current")
        val daily = body.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        return WeatherReport(
            place = body.getString(KEY_PLACE),
            fetchedAt = body.getLong(KEY_FETCHED),
            temperature = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            // Un parte guardado antes de que se pidiera este dato se toma por de día
            isDay = current.optInt("is_day", 1) == 1,
            days = List(dates.length()) { i ->
                DayForecast(
                    date = LocalDate.parse(dates.getString(i)),
                    code = daily.getJSONArray("weather_code").getInt(i),
                    max = daily.getJSONArray("temperature_2m_max").getDouble(i),
                    min = daily.getJSONArray("temperature_2m_min").getDouble(i),
                )
            },
        )
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val FORECAST = "https://api.open-meteo.com/v1/forecast"
        const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"
        const val KEY_PLACE = "koyomi_place"
        const val KEY_FETCHED = "koyomi_fetched_at"

        /** Con la app abierta no se pide más de una vez por hora. */
        const val FRESH_MS = 60 * 60 * 1000L
    }
}
