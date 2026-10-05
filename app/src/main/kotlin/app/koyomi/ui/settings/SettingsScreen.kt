package app.koyomi.ui.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koyomi.BuildConfig
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.calendar.DeviceCalendar
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.JaTime
import app.koyomi.engine.format.TimeStyle
import app.koyomi.engine.format.Youbi
import app.koyomi.settings.Settings
import app.koyomi.settings.SkinId
import app.koyomi.settings.ThemeMode
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import app.koyomi.ui.theme.Palette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val koyomi = app as KoyomiApp
    private val store = koyomi.settings

    val settings: StateFlow<Settings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val today: StateFlow<LocalDate> =
        koyomi.now.map { it.today }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), koyomi.now.value.today)

    @OptIn(ExperimentalCoroutinesApi::class)
    val calendars: StateFlow<List<DeviceCalendar>> =
        koyomi.now.map { it.calendarAccess }.distinctUntilChanged()
            .flatMapLatest { access -> if (access) koyomi.calendar.calendars() else flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSkin(skin: SkinId) = viewModelScope.launch { store.setSkin(skin) }

    fun setMode(mode: ThemeMode) = viewModelScope.launch { store.setMode(mode) }

    fun setDateStyle(style: DateStyle) = viewModelScope.launch { store.setDateStyle(style) }

    fun setTimeStyle(style: TimeStyle) = viewModelScope.launch { store.setTimeStyle(style) }

    fun setWeekStart(day: Youbi) = viewModelScope.launch { store.setWeekStart(day) }

    fun setCalendarShown(id: Long, shown: Boolean) = viewModelScope.launch { store.setCalendarHidden(id, !shown) }
}

// Cada ajuste va rotulado en japonés, con una línea pequeña en el idioma del teléfono: unos ajustes
// que no se entienden son una trampa, no inmersión.
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val colors = palette()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val date = JaDate.of(today)
    val sample = LocalTime.of(21, 30)

    val skin = skin()
    var licenses by rememberSaveable { mutableStateOf(false) }
    if (licenses) {
        LicensesScreen(onBack = { licenses = false })
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = skin.screenPadding)) {
        Text("設定", color = colors.ink, fontSize = 28.sp, fontWeight = skin.strong, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp))

        Section("テーマ", stringResource(R.string.settings_theme), colors) {
            Choice("スタンダード", null, settings.skin == SkinId.STANDARD, colors) { viewModel.setSkin(SkinId.STANDARD) }
            Choice("RPG", null, settings.skin == SkinId.JRPG, colors) { viewModel.setSkin(SkinId.JRPG) }
            Choice("和風", null, settings.skin == SkinId.TRADITIONAL, colors) { viewModel.setSkin(SkinId.TRADITIONAL) }
        }

        Section("明るさ", stringResource(R.string.settings_mode), colors) {
            Choice("端末に合わせる", stringResource(R.string.settings_mode_system), settings.mode == ThemeMode.SYSTEM, colors) { viewModel.setMode(ThemeMode.SYSTEM) }
            Choice("ライト", null, settings.mode == ThemeMode.LIGHT, colors) { viewModel.setMode(ThemeMode.LIGHT) }
            Choice("ダーク", null, settings.mode == ThemeMode.DARK, colors) { viewModel.setMode(ThemeMode.DARK) }
        }

        Section("日付の表示", stringResource(R.string.settings_date_style), colors) {
            Choice("シンプル", date.line(DateStyle.SIMPLE), settings.dateStyle == DateStyle.SIMPLE, colors) { viewModel.setDateStyle(DateStyle.SIMPLE) }
            Choice("和暦", date.line(DateStyle.FULL), settings.dateStyle == DateStyle.FULL, colors) { viewModel.setDateStyle(DateStyle.FULL) }
            Choice("和暦と西暦", date.line(DateStyle.FULL) + "\n" + date.seireki, settings.dateStyle == DateStyle.EDUCATIONAL, colors) {
                viewModel.setDateStyle(DateStyle.EDUCATIONAL)
            }
        }

        Section("時刻の表示", stringResource(R.string.settings_time_style), colors) {
            Choice("24時間", JaTime.digits(sample, TimeStyle.H24), settings.timeStyle == TimeStyle.H24, colors) { viewModel.setTimeStyle(TimeStyle.H24) }
            Choice("午前・午後", JaTime.digits(sample, TimeStyle.GOZEN_GOGO), settings.timeStyle == TimeStyle.GOZEN_GOGO, colors) {
                viewModel.setTimeStyle(TimeStyle.GOZEN_GOGO)
            }
        }

        Section("週の始まり", stringResource(R.string.settings_week_start), colors) {
            for (day in listOf(Youbi.NICHI, Youbi.GETSU)) {
                Choice(day.full, null, settings.weekStart == day, colors) { viewModel.setWeekStart(day) }
            }
        }

        if (calendars.isNotEmpty()) {
            Section("カレンダー", stringResource(R.string.settings_calendars), colors) {
                for (calendar in calendars) {
                    val shown = calendar.id !in settings.hiddenCalendars
                    CalendarRow(calendar, shown, colors) { viewModel.setCalendarShown(calendar.id, !shown) }
                }
            }
        }

        Section("このアプリについて", stringResource(R.string.settings_about), colors) {
            Text("Koyomi ${BuildConfig.VERSION_NAME}", color = colors.ink, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
            Text(stringResource(R.string.settings_privacy), color = colors.inkSoft, fontSize = 13.sp, lineHeight = 19.sp)
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(skin.buttonShape)
                    .clickable { licenses = true }
                    .padding(vertical = 10.dp),
            ) {
                Text("ライセンス", color = colors.ink, fontSize = 16.sp)
                Text(stringResource(R.string.settings_licenses), color = colors.inkSoft, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String, gloss: String, colors: Palette, content: @Composable () -> Unit) {
    val skin = skin()
    skin.Panel(
        Modifier.fillMaxWidth(),
        title = {
            Column {
                Text(title, color = colors.ink, fontSize = 17.sp, fontWeight = skin.strong)
                Text(gloss, color = colors.inkSoft, fontSize = 12.sp)
            }
        },
    ) { content() }
}

@Composable
private fun Choice(label: String, example: String?, selected: Boolean, colors: Palette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(skin().buttonShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        skin().Mark(selected, null)
        Column(Modifier.padding(start = 14.dp)) {
            Text(label, color = colors.ink, fontSize = 16.sp)
            if (example != null) Text(example, color = colors.inkSoft, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CalendarRow(calendar: DeviceCalendar, shown: Boolean, colors: Palette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(skin().buttonShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        skin().Mark(shown, if (calendar.color == 0) colors.ink else Color(calendar.color).copy(alpha = 1f))
        Column(Modifier.padding(start = 14.dp)) {
            Text(calendar.name, color = colors.ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (calendar.account != calendar.name) {
                Text(calendar.account, color = colors.inkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Lo que Koyomi usa de otros: las fuentes de los temas, con su licencia entera, y la tabla de festivos. */
@Composable
private fun LicensesScreen(onBack: () -> Unit) {
    val colors = palette()
    val skin = skin()
    val resources = LocalContext.current.resources
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = skin.screenPadding)) {
        Text(
            "← 設定",
            color = colors.inkSoft,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 20.dp).clip(skin.buttonShape).clickable(onClick = onBack).padding(vertical = 8.dp),
        )
        Text("ライセンス", color = colors.ink, fontSize = 28.sp, fontWeight = skin.strong, modifier = Modifier.padding(bottom = 4.dp))

        for ((name, use, license) in LICENSES) {
            val text = remember(license) { resources.openRawResource(license).bufferedReader().use { it.readText() } }
            Section(name, stringResource(use), colors) {
                // La licencia va tal cual, en la fuente del teléfono: es para leerla, no para lucirla
                Text(text, color = colors.inkSoft, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Default, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Section("国民の祝日", stringResource(R.string.license_holidays_use), colors) {
            Text(stringResource(R.string.license_holidays), color = colors.inkSoft, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

private val LICENSES = listOf(
    Triple("DotGothic16", R.string.license_dotgothic16_use, R.raw.license_dotgothic16),
    Triple("Zen Old Mincho", R.string.license_zen_old_mincho_use, R.raw.license_zen_old_mincho),
)
