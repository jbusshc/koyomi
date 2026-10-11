package app.koyomi.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.settings.Settings
import app.koyomi.settings.SkinId
import app.koyomi.settings.ThemeMode
import app.koyomi.ui.components.OptionChoice
import app.koyomi.ui.components.OptionSection
import app.koyomi.ui.skin.KoyomiTheme
import app.koyomi.ui.skin.backdrop
import app.koyomi.ui.skin.isDark
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import app.koyomi.ui.skin.skinOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * La pantalla de un widget recién puesto (o vuelto a configurar): qué enseña, con qué tema y con
 * qué fondo, con una vista previa que se pinta con el mismo pincel que el widget de verdad.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Si se sale sin guardar, el widget no se pone
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val app = applicationContext as KoyomiApp
        val manager = AppWidgetManager.getInstance(this)
        val isClock = manager.getAppWidgetInfo(widgetId)?.provider?.className == ClockWidget::class.java.name
        val options = manager.getAppWidgetOptions(widgetId)

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(Settings())
            var config by remember { mutableStateOf<WidgetConfig?>(null) }
            LaunchedEffect(Unit) { config = app.widgets.get(widgetId) }
            val current = config
            KoyomiTheme(settings) {
                Box(Modifier.fillMaxSize().backdrop(palette()).safeDrawingPadding()) {
                    if (current != null) {
                        ConfigScreen(
                            app = app,
                            settings = settings,
                            config = current,
                            isClock = isClock,
                            previewWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: if (isClock) 250 else 300,
                            previewHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: if (isClock) 80 else 180,
                            onChange = { config = it },
                            onSave = { save(app, it) },
                        )
                    }
                }
            }
        }
    }

    private fun save(app: KoyomiApp, config: WidgetConfig) {
        app.appScope.launch {
            app.widgets.set(widgetId, config)
            // Pinta ya: al volver a configurar no hay otro aviso del sistema que lo haga
            SummaryWidget.refresh(app)
            ClockWidget.refresh(app)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    app: KoyomiApp,
    settings: Settings,
    config: WidgetConfig,
    isClock: Boolean,
    previewWidthDp: Int,
    previewHeightDp: Int,
    onChange: (WidgetConfig) -> Unit,
    onSave: (WidgetConfig) -> Unit,
) {
    val colors = palette()
    val skin = skin()
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density

    // La vista previa: el mismo pincel que el widget, sobre un fondo que deja ver el cristal
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(config, settings) {
        val width = (previewWidthDp * density).roundToInt().coerceAtLeast(1)
        val height = (previewHeightDp * density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (isClock) {
            val look = WidgetLook.of(app, settings, config)
            look.frame(android.graphics.Canvas(bitmap), density)
        } else {
            renderSummary(app, bitmap, settings, config)
        }
        preview = bitmap
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("ウィジェット", color = colors.ink, fontSize = 24.sp, fontWeight = skin.strong, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.widget_config_title), color = colors.inkSoft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 14.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(skin.buttonShape)
                .background(Brush.linearGradient(listOf(Color(0xFF3B5B8C), Color(0xFFB3689A), Color(0xFFF2B27A))))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(minOf(previewHeightDp, 260).dp),
                )
            }
        }

        Column(Modifier.padding(top = 14.dp)) {
            if (!isClock) {
                OptionSection("内容", stringResource(R.string.wc_layout), colors) {
                    OptionChoice("おまかせ", stringResource(R.string.wc_layout_auto), config.layout == WidgetLayout.AUTO, colors) { onChange(config.copy(layout = WidgetLayout.AUTO)) }
                    OptionChoice("日付", stringResource(R.string.wc_layout_date), config.layout == WidgetLayout.DATE, colors) { onChange(config.copy(layout = WidgetLayout.DATE)) }
                    OptionChoice("天気", stringResource(R.string.wc_layout_weather), config.layout == WidgetLayout.WEATHER, colors) { onChange(config.copy(layout = WidgetLayout.WEATHER)) }
                    OptionChoice("予定", stringResource(R.string.wc_layout_agenda), config.layout == WidgetLayout.AGENDA, colors) { onChange(config.copy(layout = WidgetLayout.AGENDA)) }
                    OptionChoice("月", stringResource(R.string.wc_layout_month), config.layout == WidgetLayout.MONTH, colors) { onChange(config.copy(layout = WidgetLayout.MONTH)) }
                }

                val toggles = togglesFor(config.layout)
                if (toggles.isNotEmpty()) {
                    Box(Modifier.height(12.dp))
                    OptionSection("表示", stringResource(R.string.wc_show), colors) {
                        for (toggle in toggles) {
                            OptionChoice(toggle.label, stringResource(toggle.gloss), toggle.value(config), colors) { onChange(toggle.set(config, !toggle.value(config))) }
                        }
                    }
                }
                Box(Modifier.height(12.dp))
            }

            OptionSection("テーマ", stringResource(R.string.wc_theme), colors) {
                OptionChoice("アプリに合わせる", stringResource(R.string.wc_same_as_app), config.skin == null, colors) { onChange(config.copy(skin = null)) }
                val shownMode = config.mode ?: settings.mode
                for ((id, name, gloss) in themeRows) {
                    val palette = skinOf(id).palette(isDark(shownMode), settings.options)
                    OptionChoice(
                        name,
                        stringResource(gloss),
                        config.skin == id,
                        colors,
                        listOf(palette.background, palette.surface, palette.accent, palette.ink),
                    ) { onChange(config.copy(skin = id)) }
                }
            }

            Box(Modifier.height(12.dp))
            OptionSection("明るさ", stringResource(R.string.wc_mode), colors) {
                OptionChoice("アプリに合わせる", stringResource(R.string.wc_same_as_app), config.mode == null, colors) { onChange(config.copy(mode = null)) }
                OptionChoice("ライト", stringResource(R.string.wc_light), config.mode == ThemeMode.LIGHT, colors) { onChange(config.copy(mode = ThemeMode.LIGHT)) }
                OptionChoice("ダーク", stringResource(R.string.wc_dark), config.mode == ThemeMode.DARK, colors) { onChange(config.copy(mode = ThemeMode.DARK)) }
            }

            Box(Modifier.height(12.dp))
            OptionSection("背景", stringResource(R.string.wc_background), colors) {
                OptionChoice("ソリッド", stringResource(R.string.wc_bg_solid), config.background == WidgetBackground.SOLID, colors) { onChange(config.copy(background = WidgetBackground.SOLID)) }
                OptionChoice("ガラス", stringResource(R.string.wc_bg_glass), config.background == WidgetBackground.GLASS, colors) { onChange(config.copy(background = WidgetBackground.GLASS)) }
                OptionChoice("透明", stringResource(R.string.wc_bg_clear), config.background == WidgetBackground.CLEAR, colors) { onChange(config.copy(background = WidgetBackground.CLEAR)) }
            }
        }

        Box(
            Modifier
                .padding(top = 20.dp, bottom = 12.dp)
                .fillMaxWidth()
                .clip(skin.buttonShape)
                .background(colors.accent)
                .clickable { onSave(config) }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.wc_save), color = colors.background, fontSize = 16.sp, fontWeight = skin.strong)
        }
    }
}

private class Toggle(val label: String, val gloss: Int, val value: (WidgetConfig) -> Boolean, val set: (WidgetConfig, Boolean) -> WidgetConfig)

private val showWeather = Toggle("天気", R.string.wc_show_weather, { it.showWeather }) { c, v -> c.copy(showWeather = v) }
private val showForecast = Toggle("三日間", R.string.wc_show_forecast, { it.showForecast }) { c, v -> c.copy(showForecast = v) }
private val showEvents = Toggle("予定", R.string.wc_show_events, { it.showEvents }) { c, v -> c.copy(showEvents = v) }
private val showMonth = Toggle("月", R.string.wc_show_month, { it.showMonth }) { c, v -> c.copy(showMonth = v) }
private val showNotes = Toggle("暦注と読み", R.string.wc_show_notes, { it.showNotes }) { c, v -> c.copy(showNotes = v) }

/** Lo que se puede encender o apagar en cada disposición. */
private fun togglesFor(layout: WidgetLayout): List<Toggle> = when (layout) {
    WidgetLayout.AUTO -> listOf(showWeather, showForecast, showEvents, showMonth, showNotes)
    WidgetLayout.WEATHER -> listOf(showForecast)
    WidgetLayout.AGENDA -> listOf(showMonth, showNotes)
    WidgetLayout.DATE -> listOf(showNotes)
    WidgetLayout.MONTH -> listOf(showEvents)
}

private val themeRows = listOf(
    Triple(SkinId.STANDARD, "スタンダード", R.string.theme_standard),
    Triple(SkinId.SAKURA, "桜", R.string.theme_sakura),
    Triple(SkinId.AI, "藍", R.string.theme_ai),
    Triple(SkinId.YAKEI, "夜景", R.string.theme_yakei),
    Triple(SkinId.TRADITIONAL, "和風", R.string.theme_traditional),
    Triple(SkinId.JRPG, "RPG", R.string.theme_rpg),
)
