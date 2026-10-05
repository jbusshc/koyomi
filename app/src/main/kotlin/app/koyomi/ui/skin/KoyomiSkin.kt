package app.koyomi.ui.skin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDateText
import app.koyomi.settings.Settings
import app.koyomi.settings.SkinId
import app.koyomi.settings.ThemeMode
import app.koyomi.ui.Destination
import app.koyomi.ui.theme.Palette

/**
 * Un tema de Koyomi. No es solo una paleta: decide también cómo se dibuja cada pieza, porque una
 * ventana de RPG con su cursor no sale de cambiarle los colores a una pantalla plana.
 *
 * Las pantallas piden «un panel» o «la cabecera de la fecha» y el skin activo decide cómo se ve.
 * Si un tema nuevo pide una pieza que no está aquí, se añade aquí, no en la pantalla.
 */
interface KoyomiSkin {
    fun palette(dark: Boolean): Palette

    val font: FontFamily

    /** El peso del texto que destaca. Una fuente de píxeles no tiene negrita: fingirla la emborrona. */
    val strong: FontWeight

    /** La marca de un evento: su punto de color. */
    val dot: Shape

    /** El margen de la pantalla a cada lado. */
    val screenPadding: Dp

    val buttonShape: Shape

    /** El panel que sube desde abajo con un día del mes. */
    val sheetShape: Shape

    fun sheetColor(colors: Palette): Color

    /** El asa redondeada del panel: en un tema de píxeles desentona. */
    val sheetHandle: Boolean

    /** Si el mes se acompaña de su nombre antiguo (神無月). */
    val wafuMonth: Boolean

    /**
     * El color con que se marca algo de un calendario del teléfono. Casi siempre el suyo; un tema
     * de paleta cerrada lo cambia por uno de los propios.
     */
    fun calendarColor(argb: Int, colors: Palette): Color = if (argb == 0) colors.inkSoft else Color(argb).copy(alpha = 1f)

    /** Un bloque de contenido, con título o sin él. */
    @Composable
    fun Panel(modifier: Modifier, title: (@Composable RowScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit)

    /** La fecha de hoy, en grande: lo que más se ve de la app. [time]: la hora, ya escrita (午後9時32分). */
    @Composable
    fun DateHeader(date: JaDateText, style: DateStyle, holiday: String?, time: String, modifier: Modifier)

    /** El número de un día en la hoja del mes. */
    @Composable
    fun DayNumber(day: Int, color: Color, isToday: Boolean)

    /** La marca de una opción, elegida o no. [color] nulo: el del tema. */
    @Composable
    fun Mark(selected: Boolean, color: Color?)

    @Composable
    fun NavBar(current: Destination, onSelect: (Destination) -> Unit)
}

val LocalSkin = staticCompositionLocalOf<KoyomiSkin> { StandardSkin }
val LocalPalette = staticCompositionLocalOf { StandardSkin.palette(dark = false) }

@Composable
fun skin(): KoyomiSkin = LocalSkin.current

@Composable
fun palette(): Palette = LocalPalette.current

fun skinOf(id: SkinId): KoyomiSkin = when (id) {
    SkinId.STANDARD -> StandardSkin
    SkinId.JRPG -> JrpgSkin
    SkinId.TRADITIONAL -> TraditionalSkin
    SkinId.RETRO -> RetroSkin
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun KoyomiTheme(settings: Settings, content: @Composable () -> Unit) {
    val skin = skinOf(settings.skin)
    val colors = skin.palette(isDark(settings.mode))
    CompositionLocalProvider(
        LocalSkin provides skin,
        LocalPalette provides colors,
        LocalContentColor provides colors.ink,
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = skin.font),
        content = content,
    )
}
