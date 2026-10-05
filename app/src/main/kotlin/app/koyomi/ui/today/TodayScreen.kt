package app.koyomi.ui.today

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalTime
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.koyomi.R
import app.koyomi.calendar.CalendarInstance
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.format.JaTime
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.ui.components.DayEvents
import app.koyomi.ui.components.openEvent
import app.koyomi.ui.components.rekichu
import app.koyomi.ui.components.youbiColor
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin

@Composable
fun TodayScreen(viewModel: TodayViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Tras dos negativas Android ya no enseña el diálogo: solo queda ir a los ajustes de la app
        if (!granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)) {
            context.openAppSettings()
        }
        viewModel.onPermissionResult()
    }

    TodayContent(
        state = state,
        onDateClick = viewModel::cycleDateStyle,
        onGrant = { request.launch(Manifest.permission.READ_CALENDAR) },
        onOpen = { context.openEvent(it) },
    )
}

@Composable
private fun TodayContent(state: TodayUi, onDateClick: () -> Unit, onGrant: () -> Unit, onOpen: (CalendarInstance) -> Unit) {
    val colors = palette()
    val skin = skin()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = skin.screenPadding),
    ) {
        skin.DateHeader(
            date = JaDate.of(state.today),
            style = state.settings.dateStyle,
            holiday = Shukujitsu.of(state.today),
            time = JaTime.kanji(now(), state.settings.timeStyle),
            // Sin onda: la fecha cambia de forma, y eso ya dice que se ha tocado
            modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDateClick),
        )

        if (state.settings.rekichu) {
            rekichu(state.today)?.let { Text(it, color = colors.inkSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) }
        }

        val events = state.events
        if (events == null) {
            PermissionPanel(onGrant)
        } else {
            skin.Panel(Modifier.fillMaxWidth(), null) {
                DayEvents(events[state.today].orEmpty(), state.settings.timeStyle, colors, onOpen)
            }
            val tomorrow = JaDate.of(state.tomorrow)
            val holiday = Shukujitsu.of(state.tomorrow)
            skin.Panel(
                Modifier.fillMaxWidth(),
                title = {
                    Text("明日", color = colors.ink, fontSize = 16.sp, fontWeight = skin.strong)
                    Text(tomorrow.line(DateStyle.SIMPLE), color = youbiColor(tomorrow.youbi, holiday != null, colors, other = colors.inkSoft), fontSize = 14.sp)
                    if (holiday != null) Text(holiday, color = colors.sunday, fontSize = 14.sp)
                },
            ) {
                DayEvents(events[state.tomorrow].orEmpty(), state.settings.timeStyle, colors, onOpen)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** La hora, al minuto. Solo corre con la app a la vista. */
@Composable
private fun now(): LocalTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val time by produceState(LocalTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalTime.now()
                // Hasta el siguiente cambio de minuto, y un poco más para no quedarse corto
                delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            }
        }
    }
    return time
}

@Composable
private fun PermissionPanel(onGrant: () -> Unit) {
    val colors = palette()
    val skin = skin()
    skin.Panel(
        Modifier.fillMaxWidth(),
        title = { Text("カレンダーへのアクセス", color = colors.ink, fontSize = 17.sp, fontWeight = skin.strong) },
    ) {
        Text(
            stringResource(R.string.calendar_permission_rationale),
            color = colors.inkSoft,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Column(
            Modifier
                .clip(skin.buttonShape)
                .background(colors.ink)
                .clickable(onClick = onGrant)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text("許可する", color = colors.background, fontSize = 16.sp, fontWeight = skin.strong)
            Text(stringResource(R.string.calendar_permission_allow), color = colors.background.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
