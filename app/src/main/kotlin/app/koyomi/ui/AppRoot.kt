package app.koyomi.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.ui.month.MonthScreen
import app.koyomi.ui.settings.SettingsScreen
import app.koyomi.ui.skin.KoyomiTheme
import app.koyomi.ui.skin.isDark
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import app.koyomi.ui.today.TodayScreen

enum class Destination(val label: String, val gloss: Int) {
    TODAY("今日", R.string.nav_today),
    MONTH("月", R.string.nav_month),
    SETTINGS("設定", R.string.nav_settings),
}

@Composable
fun AppRoot() {
    val app = LocalContext.current.applicationContext as KoyomiApp
    var current by rememberSaveable { mutableStateOf(Destination.TODAY) }

    LifecycleResumeEffect(Unit) {
        app.onResume()
        onPauseOrDispose { }
    }
    // Atrás vuelve a 今日 antes de salir de la app
    BackHandler(enabled = current != Destination.TODAY) { current = Destination.TODAY }

    // Hasta leer los ajustes no se pinta nada: mejor un instante en blanco que un destello del tema que no es
    val settings = app.settings.settings.collectAsStateWithLifecycle(initialValue = null).value ?: return

    // Los iconos de la barra de estado siguen al modo de la app, que puede no ser el del teléfono
    val dark = isDark(settings.mode)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    KoyomiTheme(settings) {
        Column(Modifier.fillMaxSize().background(palette().background).safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                when (current) {
                    Destination.TODAY -> TodayScreen()
                    Destination.MONTH -> MonthScreen()
                    Destination.SETTINGS -> SettingsScreen()
                }
            }
            skin().NavBar(current) { current = it }
        }
    }
}
