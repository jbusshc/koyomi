package app.koyomi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.ui.month.MonthScreen
import app.koyomi.ui.settings.SettingsScreen
import app.koyomi.ui.theme.Palette
import app.koyomi.ui.theme.palette
import app.koyomi.ui.today.TodayScreen

enum class Destination(val label: String, val gloss: Int) {
    TODAY("今日", R.string.nav_today),
    MONTH("月", R.string.nav_month),
    SETTINGS("設定", R.string.nav_settings),
}

@Composable
fun AppRoot() {
    val colors = palette()
    val app = LocalContext.current.applicationContext as KoyomiApp
    var current by rememberSaveable { mutableStateOf(Destination.TODAY) }

    LifecycleResumeEffect(Unit) {
        app.onResume()
        onPauseOrDispose { }
    }
    // Atrás vuelve a 今日 antes de salir de la app
    BackHandler(enabled = current != Destination.TODAY) { current = Destination.TODAY }

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (current) {
                Destination.TODAY -> TodayScreen()
                Destination.MONTH -> MonthScreen()
                Destination.SETTINGS -> SettingsScreen()
            }
        }
        NavBar(current, colors) { current = it }
    }
}

@Composable
private fun NavBar(current: Destination, colors: Palette, onSelect: (Destination) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
        Row(Modifier.fillMaxWidth()) {
            for (destination in Destination.entries) {
                val selected = destination == current
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(destination) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        destination.label,
                        color = if (selected) colors.ink else colors.inkSoft,
                        fontSize = 17.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(stringResource(destination.gloss), color = colors.inkSoft.copy(alpha = if (selected) 1f else 0.7f), fontSize = 10.sp)
                }
            }
        }
    }
}
