package app.koyomi.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koyomi.R
import app.koyomi.calendar.DayEvent
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.MonthGrid
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.settings.Settings
import app.koyomi.ui.components.DayEvents
import app.koyomi.ui.components.eventColor
import app.koyomi.ui.components.newEvent
import app.koyomi.ui.components.openEvent
import app.koyomi.ui.components.youbiColor
import app.koyomi.ui.theme.Palette
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

// Cien años hacia cada lado del mes en que se abre la pantalla
private const val CENTER = 1200

@Composable
fun MonthScreen(viewModel: MonthViewModel = viewModel()) {
    val colors = palette()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val origin = remember { YearMonth.from(today) }
    val pager = rememberPagerState(initialPage = CENTER) { CENTER * 2 }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    val shown = origin.plusMonths((pager.currentPage - CENTER).toLong())
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        MonthHeader(
            month = shown,
            style = settings.dateStyle,
            isCurrent = shown == YearMonth.from(today),
            colors = colors,
            onToday = { scope.launch { pager.animateScrollToPage(CENTER + monthsBetween(origin, YearMonth.from(today))) } },
        )
        skin().Panel(Modifier.weight(1f).fillMaxWidth(), null) {
            WeekdayRow(settings.weekStart, colors)
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val month = origin.plusMonths((page - CENTER).toLong())
                val events by remember(month, settings.weekStart) { viewModel.events(month, settings.weekStart) }
                    .collectAsStateWithLifecycle(initialValue = null)
                MonthPage(month, settings.weekStart, today, events.orEmpty(), colors, onDay = { selected = it })
            }
        }
    }

    selected?.let { day ->
        DaySheet(day, settings, viewModel, colors, onDismiss = { selected = null })
    }
}

private fun monthsBetween(from: YearMonth, to: YearMonth): Int = (to.year - from.year) * 12 + (to.monthValue - from.monthValue)

@Composable
private fun MonthHeader(month: YearMonth, style: DateStyle, isCurrent: Boolean, colors: Palette, onToday: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToday)
            .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (style == DateStyle.SIMPLE) "${month.year}年${month.monthValue}月" else JaDate.monthHeader(month.year, month.monthValue),
                color = colors.ink,
                fontSize = 28.sp,
                fontWeight = skin().strong,
            )
            if (style == DateStyle.EDUCATIONAL) Text("${month.year}年", color = colors.inkSoft, fontSize = 14.sp)
        }
        // Lejos de hoy, una forma visible de volver
        if (!isCurrent) Text("今日へ", color = colors.sunday, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun WeekdayRow(weekStart: Youbi, colors: Palette) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        for (youbi in MonthGrid.weekdays(weekStart)) {
            Text(
                youbi.short,
                color = youbiColor(youbi, holiday = false, colors, other = colors.inkSoft),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthPage(
    month: YearMonth,
    weekStart: Youbi,
    today: LocalDate,
    events: Map<LocalDate, List<DayEvent>>,
    colors: Palette,
    onDay: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        for (week in MonthGrid.weeks(month, weekStart)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (day in week) {
                    DayCell(
                        day = day,
                        inMonth = YearMonth.from(day) == month,
                        isToday = day == today,
                        events = events[day].orEmpty(),
                        colors = colors,
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { onDay(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, isToday: Boolean, events: List<DayEvent>, colors: Palette, modifier: Modifier) {
    val holiday = Shukujitsu.of(day)
    val color = youbiColor(Youbi.of(day), holiday != null, colors)
    // Los días de los meses vecinos se ven, pero apagados
    val fade = if (inMonth) 1f else 0.35f
    Column(modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        skin().DayNumber(day.dayOfMonth, color.copy(alpha = fade), isToday)
        if (holiday != null) {
            Text(
                holiday,
                color = colors.sunday.copy(alpha = fade),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                // スポーツの日 no cabe en una celda: se deja asomar a las vecinas antes que cortarlo
                modifier = Modifier.wrapContentWidth(unbounded = true),
            )
        }
        if (events.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (event in events.take(3)) {
                    Box(Modifier.size(5.dp).clip(skin().dot).background(eventColor(event.instance, colors).copy(alpha = fade)))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(day: LocalDate, settings: Settings, viewModel: MonthViewModel, colors: Palette, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val events by remember(day) { viewModel.events(day) }.collectAsStateWithLifecycle(initialValue = null)
    val date = JaDate.of(day)
    val holiday = Shukujitsu.of(day)
    val skin = skin()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = skin.sheetColor(colors),
        contentColor = colors.ink,
        shape = skin.sheetShape,
    ) {
        skin.Panel(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = skin.screenPadding, end = skin.screenPadding, bottom = 24.dp),
            null,
        ) {
            if (settings.dateStyle != DateStyle.SIMPLE) Text(date.year, color = colors.inkSoft, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(date.monthDay, color = colors.ink, fontSize = 28.sp, fontWeight = skin.strong)
                Text("（${date.youbi.short}）", color = youbiColor(date.youbi, holiday != null, colors), fontSize = 20.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            if (holiday != null) Text(holiday, color = colors.sunday, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(colors.rule))

            events?.let { DayEvents(it, settings.timeStyle, colors, onOpen = { instance -> context.openEvent(instance) }) }

            Column(
                Modifier
                    .padding(top = 12.dp)
                    .clip(skin.buttonShape)
                    .clickable { context.newEvent(day) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Text("＋ 予定を追加", color = colors.ink, fontSize = 15.sp)
                Text(stringResource(R.string.add_event_hint), color = colors.inkSoft, fontSize = 12.sp)
            }
        }
    }
}
