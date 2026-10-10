package app.koyomi

import android.app.Application
import app.koyomi.calendar.Agenda
import app.koyomi.calendar.CalendarRepository
import app.koyomi.calendar.DayEvent
import app.koyomi.settings.SettingsStore
import app.koyomi.widget.AgendaWidget
import app.koyomi.widget.ClockWidget
import app.koyomi.weather.WeatherRepository
import app.koyomi.widget.DateWidget
import app.koyomi.widget.SummaryWidget
import app.koyomi.widget.WeatherWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Lo que cambia sin que la app haga nada: el día, la zona horaria y el permiso de calendario. */
data class Now(val today: LocalDate, val zone: ZoneId, val calendarAccess: Boolean)

class KoyomiApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val calendar by lazy { CalendarRepository(this) }
    val settings by lazy { SettingsStore(this) }
    val weather by lazy { WeatherRepository(this) }

    // Sube al volver a la app: el permiso, la fecha o la zona pueden haber cambiado mientras tanto
    private val resumed = MutableStateFlow(0)

    val now: StateFlow<Now> by lazy {
        combine(midnights(), resumed) { _, _ -> readNow() }.stateIn(appScope, SharingStarted.Eagerly, readNow())
    }

    override fun onCreate() {
        super.onCreate()
        // Los widgets siguen los ajustes (tema, modo, formatos, calendarios): al cambiarlos, se repintan
        appScope.launch {
            settings.settings.distinctUntilChanged().drop(1).collect { refreshWidgets() }
        }
        // El tiempo: al elegir ciudad se pide el parte, y cuando llega se repinta su widget
        appScope.launch {
            settings.settings.map { it.weather }.distinctUntilChanged().drop(1).collect { weather.refresh(it) }
        }
        appScope.launch {
            weather.report.drop(1).collect {
                WeatherWidget.refresh(this@KoyomiApp)
                SummaryWidget.refresh(this@KoyomiApp)
            }
        }
    }

    fun onResume() {
        resumed.value++
        refreshWidgets()
        // Con la app a la vista, el parte se pone al día si tiene más de una hora
        appScope.launch { weather.refresh(settings.settings.first().weather) }
    }

    private fun refreshWidgets() {
        DateWidget.refresh(this)
        AgendaWidget.refresh(this)
        ClockWidget.refresh(this)
        WeatherWidget.refresh(this)
        SummaryWidget.refresh(this)
    }

    /**
     * Los eventos de cada día entre [first] y [last], al día con el calendario y los ajustes.
     * Nulo sin permiso de calendario.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun agenda(first: LocalDate, last: LocalDate): Flow<Map<LocalDate, List<DayEvent>>?> {
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
        val hidden = settings.settings.map { it.hiddenCalendars }.distinctUntilChanged()
        return combine(now, hidden) { now, hiddenCalendars -> Triple(now.zone, now.calendarAccess, hiddenCalendars) }
            .distinctUntilChanged()
            .flatMapLatest { (zone, access, hiddenCalendars) ->
                if (!access) return@flatMapLatest flowOf(null)
                calendar.instances(
                    from = first.atStartOfDay(zone).toInstant(),
                    to = last.plusDays(1).atStartOfDay(zone).toInstant(),
                ).map { instances -> Agenda.byDay(instances.filter { it.calendarId !in hiddenCalendars }, days, zone) }
            }
    }

    private fun readNow() = Now(LocalDate.now(), ZoneId.systemDefault(), calendar.hasPermission())

    /** Emite ahora y justo después de cada medianoche. */
    private fun midnights(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            val now = ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, next).toMillis() + 500)
        }
    }
}
