package app.koyomi.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.ui.convert.ConvertScreen
import app.koyomi.ui.month.MonthScreen
import app.koyomi.ui.settings.SettingsScreen
import app.koyomi.ui.skin.KoyomiTheme
import app.koyomi.ui.skin.backdrop
import app.koyomi.ui.skin.isDark
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import app.koyomi.ui.today.TodayScreen

/**
 * Las pantallas ya visitadas se quedan montadas, y solo se coloca la que toca. Volver a una es
 * inmediato y conserva por dónde iba; montarla de cero cada vez se nota en un teléfono modesto.
 */
@Composable
private fun Screens(current: Destination, modifier: Modifier) {
    var visited by rememberSaveable { mutableStateOf(listOf(current)) }
    if (current !in visited) visited = visited + current
    Layout(
        modifier = modifier,
        content = {
            for (destination in visited) {
                key(destination) {
                    Box {
                        when (destination) {
                            Destination.TODAY -> TodayScreen()
                            Destination.MONTH -> MonthScreen()
                            Destination.CONVERT -> ConvertScreen()
                            Destination.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Lo que no se coloca ni se dibuja ni recibe toques
            placeables.getOrNull(visited.indexOf(current))?.place(0, 0)
        }
    }
}

enum class Destination(val label: String, val gloss: Int) {
    TODAY("今日", R.string.nav_today),
    MONTH("月", R.string.nav_month),
    CONVERT("変換", R.string.nav_convert),
    SETTINGS("設定", R.string.nav_settings),
}

@Composable
fun AppRoot() {
    val app = LocalContext.current.applicationContext as KoyomiApp
    var current by rememberSaveable { mutableStateOf(Destination.TODAY) }

    // Un widget puede pedir abrir en otra pantalla (el del mes abre el mes)
    val requested by app.requestedDestination.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        Destination.entries.firstOrNull { it.name == requested }?.let { current = it }
        app.requestedDestination.value = null
    }

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
    val activity = LocalActivity.current as? ComponentActivity
    SideEffect {
        // Barras transparentes: detrás se ve el fondo del tema, también bajo los botones del sistema
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    KoyomiTheme(settings) {
        Column(Modifier.fillMaxSize().backdrop(palette()).safeDrawingPadding()) {
            Screens(current, Modifier.weight(1f))
            skin().NavBar(current) { current = it }
        }
    }
}
