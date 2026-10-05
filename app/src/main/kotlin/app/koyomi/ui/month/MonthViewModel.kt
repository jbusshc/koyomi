package app.koyomi.ui.month

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.koyomi.KoyomiApp
import app.koyomi.calendar.DayEvent
import app.koyomi.engine.format.MonthGrid
import app.koyomi.engine.format.Youbi
import app.koyomi.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

class MonthViewModel(app: Application) : AndroidViewModel(app) {
    private val koyomi = app as KoyomiApp

    val today: StateFlow<LocalDate> =
        koyomi.now.map { it.today }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), koyomi.now.value.today)

    val settings: StateFlow<Settings> =
        koyomi.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** Los eventos de las seis semanas que enseña la hoja del mes. Nulo sin permiso. */
    fun events(month: YearMonth, weekStart: Youbi): Flow<Map<LocalDate, List<DayEvent>>?> {
        val weeks = MonthGrid.weeks(month, weekStart)
        return koyomi.agenda(weeks.first().first(), weeks.last().last())
    }

    fun events(day: LocalDate): Flow<List<DayEvent>?> = koyomi.agenda(day, day).map { it?.get(day) }
}
