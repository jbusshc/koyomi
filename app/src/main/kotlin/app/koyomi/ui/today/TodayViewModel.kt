package app.koyomi.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.koyomi.calendar.Agenda
import app.koyomi.calendar.CalendarRepository
import app.koyomi.calendar.DayEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class TodayUi(
    val today: LocalDate,
    /** Nulo sin permiso de calendario: la fecha se ve igual, sin eventos. */
    val events: Map<LocalDate, List<DayEvent>>?,
) {
    val tomorrow: LocalDate get() = today.plusDays(1)
}

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val calendar = CalendarRepository(app)

    // Sube al volver a la app: el permiso, la fecha o la zona pueden haber cambiado mientras tanto
    private val resumed = MutableStateFlow(0)

    fun onResume() {
        resumed.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUi> =
        combine(midnights(), resumed) { _, _ -> Triple(LocalDate.now(), ZoneId.systemDefault(), calendar.hasPermission()) }
            .distinctUntilChanged()
            .flatMapLatest { (today, zone, granted) ->
                if (!granted) return@flatMapLatest flowOf(TodayUi(today, null))
                val days = listOf(today, today.plusDays(1))
                calendar.instances(
                    from = today.atStartOfDay(zone).toInstant(),
                    to = today.plusDays(2).atStartOfDay(zone).toInstant(),
                ).map { TodayUi(today, Agenda.byDay(it, days, zone)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi(LocalDate.now(), null))

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
