package app.koyomi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.ui.skin.skin
import app.koyomi.ui.theme.Palette

/** Un grupo de opciones con su título en japonés y su glosa, dentro de la tarjeta del tema. */
@Composable
fun OptionSection(title: String, gloss: String, colors: Palette, content: @Composable () -> Unit) {
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

/** Una opción: la marca del tema (marcada o no), su nombre, un ejemplo debajo y, si se quiere, unas muestras de color. */
@Composable
fun OptionChoice(label: String, example: String?, selected: Boolean, colors: Palette, swatches: List<Color> = emptyList(), onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(skin().buttonShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        skin().Mark(selected, null)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(label, color = colors.ink, fontSize = 16.sp)
            if (example != null) Text(example, color = colors.inkSoft, fontSize = 13.sp)
        }
        // Cómo es cada tema, de un vistazo: su fondo, su tarjeta, su acento y su tinta
        for (swatch in swatches) {
            Box(Modifier.padding(start = 4.dp).size(16.dp).clip(CircleShape).background(swatch).border(1.dp, colors.rule, CircleShape))
        }
    }
}
