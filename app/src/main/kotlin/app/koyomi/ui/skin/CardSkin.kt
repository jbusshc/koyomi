package app.koyomi.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.ui.Destination
import app.koyomi.ui.theme.Palette

/**
 * Lo que comparten los temas de tarjetas redondeadas: Standard, 桜, 藍 y 夜景. Cada uno pone sus
 * colores, su cabecera de fecha y, si quiere, el relleno y el borde de sus tarjetas.
 */
abstract class CardSkin : KoyomiSkin {
    protected open val cardRadius: Dp = 20.dp
    protected open val cardBorderWidth: Dp = 1.dp

    protected open fun cardBorder(colors: Palette): Brush = SolidColor(colors.rule)
    protected open fun cardFill(colors: Palette): Brush = SolidColor(colors.surface)

    /** Cómo se marca el día de hoy en el mes. */
    protected open fun todayFill(colors: Palette): Color = colors.ink
    protected open fun todayInk(colors: Palette): Color = colors.background

    override val dot: Shape = CircleShape

    // Las tarjetas ya llevan su propio margen interior
    override val screenPadding = 16.dp
    override val buttonShape: Shape = RoundedCornerShape(10.dp)
    override val sheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    override val sheetHandle = true
    override val wafuMonth = false

    override fun sheetColor(colors: Palette): Color = colors.surface

    @Composable
    override fun Panel(modifier: Modifier, title: (@Composable RowScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
        val colors = palette()
        val shape = RoundedCornerShape(cardRadius)
        Column(
            modifier
                .padding(vertical = 6.dp)
                .clip(shape)
                .background(cardFill(colors))
                .border(cardBorderWidth, cardBorder(colors), shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = title,
                )
                Box(Modifier.padding(top = 10.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(colors.rule))
            }
            content()
        }
    }

    @Composable
    override fun DayNumber(day: Int, color: Color, isToday: Boolean) {
        val colors = palette()
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (isToday) todayFill(colors) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.toString(),
                color = if (isToday) todayInk(colors) else color,
                fontSize = 15.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }

    @Composable
    override fun Mark(selected: Boolean, color: Color?) {
        val colors = palette()
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(color ?: todayFill(colors)) else Modifier.border(1.5.dp, colors.inkSoft, CircleShape),
                ),
        )
    }

    @Composable
    override fun NavBar(current: Destination, onSelect: (Destination) -> Unit) {
        val colors = palette()
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
}
