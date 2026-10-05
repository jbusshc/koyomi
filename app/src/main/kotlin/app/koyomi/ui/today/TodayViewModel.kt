package app.koyomi.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.koyomi.KoyomiApp
import app.koyomi.calendar.DayEvent
import app.koyomi.engine.format.DateStyle
import app.koyomi.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayUi(
    val today: LocalDate,
    val settings: Settings,
    /** Nulo sin permiso de calendario: la fecha se ve igual, sin eventos. */
    val events: Map<LocalDate, List<DayEvent>>?,
) {
    val tomorrow: LocalDate get() = today.plusDays(1)
}

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val koyomi = app as KoyomiApp

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUi> =
        koyomi.now.map { it.today }.distinctUntilChanged()
            .flatMapLatest { today ->
                combine(koyomi.agenda(today, today.plusDays(1)), koyomi.settings.settings) { events, settings ->
                    TodayUi(today, settings, events)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi(LocalDate.now(), Settings(), null))

    fun onPermissionResult() = koyomi.onResume()

    /** Tocar la fecha pasa al siguiente nivel de detalle. */
    fun cycleDateStyle() {
        val styles = DateStyle.entries
        val next = styles[(state.value.settings.dateStyle.ordinal + 1) % styles.size]
        viewModelScope.launch { koyomi.settings.setDateStyle(next) }
    }
}
