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
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** El parte de un día: código WMO y temperaturas en grados Celsius, y lo que sepa el servicio de más. */
data class DayForecast(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    /** Probabilidad de lluvia, en %. */
    val rain: Int? = null,
    val sunrise: LocalTime? = null,
    val sunset: LocalTime? = null,
    val uv: Double? = null,
)

/** Una hora del parte, en la hora local del sitio. */
data class HourForecast(val time: LocalDateTime, val temperature: Double, val code: Int, val isDay: Boolean, val rain: Int?)

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
    /** Sensación térmica, humedad (%), y viento (m/s y grados de donde sopla). */
    val feelsLike: Double? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val windDegrees: Double? = null,
    /** Las próximas horas, desde la actual. */
    val hours: List<HourForecast> = emptyList(),
    /** La diferencia horaria del sitio con UTC, para saber qué hora es allí. */
    val utcOffsetSeconds: Int = 0,
) {
    /** La fecha y hora de ahora en el sitio del parte. */
    fun placeNow(nowMillis: Long = System.currentTimeMillis()): LocalDateTime =
        LocalDateTime.ofEpochSecond(nowMillis / 1000, 0, java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds))

    /** Las horas que aún no han pasado, empezando por la actual. */
    fun hoursAhead(nowMillis: Long = System.currentTimeMillis()): List<HourForecast> {
        val from = placeNow(nowMillis).withMinute(0).withSecond(0).withNano(0)
        return hours.filter { !it.time.isBefore(from) }
    }

    /** Hoy y los días siguientes: un parte guardado de ayer no enseña ayer. */
    fun daysAhead(nowMillis: Long = System.currentTimeMillis()): List<DayForecast> {
        val today = placeNow(nowMillis).toLocalDate()
        return days.filter { !it.date.isBefore(today) }
    }
}

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
            loadCache()
            val current = _report.value?.takeIf { it.place == place.key }
            if (current == null) _report.value = null
            // Un parte guardado por una versión anterior, sin horas, se pide de nuevo
            if (!force && current != null && current.hours.isNotEmpty() && System.currentTimeMillis() - current.fetchedAt < FRESH_MS) return@withContext

            val fetched = runCatching { fetch(place) }
            _failed.value = fetched.isFailure
            fetched.getOrNull()?.let { (report, json) ->
                _report.value = report
                runCatching { cache.writeText(json) }
            }
        }
    }

    /**
     * Pone en memoria el último parte guardado, sin pedir nada. Los widgets lo necesitan cuando el
     * sistema despierta la app solo para repintar: sin esto salían sin tiempo hasta la siguiente petición.
     */
    suspend fun ensureLoaded() = lock.withLock { withContext(Dispatchers.IO) { loadCache() } }

    private fun loadCache() {
        if (_report.value == null) _report.value = runCatching { parseCache(cache.readText()) }.getOrNull()
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
            "%s?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_direction_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day&forecast_hours=24" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
                "&wind_speed_unit=ms&timezone=auto&forecast_days=7",
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
        fun org.json.JSONArray?.double(i: Int): Double? = this?.takeIf { i < it.length() && !it.isNull(i) }?.getDouble(i)
        fun org.json.JSONArray?.int(i: Int): Int? = double(i)?.let { Math.round(it).toInt() }
        fun org.json.JSONArray?.clock(i: Int): LocalTime? = this?.takeIf { i < it.length() && !it.isNull(i) }?.getString(i)?.let { runCatching { LocalDateTime.parse(it).toLocalTime() }.getOrNull() }

        val hourly = body.optJSONObject("hourly")
        val hourTimes = hourly?.optJSONArray("time")
        return WeatherReport(
            place = body.getString(KEY_PLACE),
            fetchedAt = body.getLong(KEY_FETCHED),
            temperature = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            // Un parte guardado antes de que se pidiera este dato se toma por de día
            isDay = current.optInt("is_day", 1) == 1,
            feelsLike = if (current.has("apparent_temperature")) current.optDouble("apparent_temperature") else null,
            utcOffsetSeconds = body.optInt("utc_offset_seconds", 0),
            humidity = if (current.has("relative_humidity_2m")) current.optInt("relative_humidity_2m") else null,
            windSpeed = if (current.has("wind_speed_10m")) current.optDouble("wind_speed_10m") else null,
            windDegrees = if (current.has("wind_direction_10m")) current.optDouble("wind_direction_10m") else null,
            days = List(dates.length()) { i ->
                DayForecast(
                    date = LocalDate.parse(dates.getString(i)),
                    code = daily.getJSONArray("weather_code").getInt(i),
                    max = daily.getJSONArray("temperature_2m_max").getDouble(i),
                    min = daily.getJSONArray("temperature_2m_min").getDouble(i),
                    rain = daily.optJSONArray("precipitation_probability_max").int(i),
                    sunrise = daily.optJSONArray("sunrise").clock(i),
                    sunset = daily.optJSONArray("sunset").clock(i),
                    uv = daily.optJSONArray("uv_index_max").double(i),
                )
            },
            hours = List(hourTimes?.length() ?: 0) { i ->
                HourForecast(
                    time = LocalDateTime.parse(hourTimes!!.getString(i)),
                    temperature = hourly.optJSONArray("temperature_2m").double(i) ?: 0.0,
                    code = hourly.optJSONArray("weather_code").int(i) ?: 0,
                    isDay = (hourly.optJSONArray("is_day").int(i) ?: 1) == 1,
                    rain = hourly.optJSONArray("precipitation_probability").int(i),
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
