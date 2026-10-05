package app.koyomi.ui.weather

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.KoyomiApp
import app.koyomi.R
import app.koyomi.settings.Place
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import kotlinx.coroutines.launch

// Para elegir sin escribir y sin red. Las coordenadas son las del centro de cada ciudad
private val PRESETS = listOf(
    Place("東京", "日本", 35.68, 139.69),
    Place("大阪", "日本", 34.69, 135.50),
    Place("京都", "日本", 35.01, 135.77),
    Place("札幌", "北海道・日本", 43.06, 141.35),
    Place("福岡", "日本", 33.59, 130.40),
    Place("那覇", "沖縄県・日本", 26.21, 127.68),
)

/** De qué ciudad se quiere el tiempo: una de las de siempre, o cualquiera buscada por nombre. */
@Composable
fun PlaceScreen(current: Place?, onPick: (Place?) -> Unit, onBack: () -> Unit) {
    val colors = palette()
    val skin = skin()
    val app = LocalContext.current.applicationContext as KoyomiApp
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    fun search() {
        if (query.isBlank() || searching) return
        keyboard?.hide()
        searching = true
        scope.launch {
            val found = app.weather.search(query)
            failed = found.isFailure
            results = found.getOrNull()
            searching = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = skin.screenPadding)) {
        Text(
            "← 設定",
            color = colors.inkSoft,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 20.dp).clip(skin.buttonShape).clickable(onClick = onBack).padding(vertical = 8.dp),
        )
        Text("天気の場所", color = colors.ink, fontSize = 28.sp, fontWeight = skin.strong, modifier = Modifier.padding(bottom = 4.dp))

        Block("検索", stringResource(R.string.place_search)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = colors.ink, fontSize = 18.sp),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.weight(1f).border(1.dp, colors.rule, skin.buttonShape).padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Text(
                    "探す",
                    color = colors.ink,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp).clip(skin.buttonShape).clickable { search() }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            when {
                searching -> Text("…", color = colors.inkSoft, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                failed -> Text("取得できません", color = colors.inkSoft, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                results?.isEmpty() == true -> Text("見つかりません", color = colors.inkSoft, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                else -> results?.forEach { PlaceRow(it, it.key == current?.key) { onPick(it) } }
            }
        }

        Block("日本の都市", stringResource(R.string.place_presets)) {
            for (place in PRESETS) PlaceRow(place, place.key == current?.key) { onPick(place) }
        }

        Block("表示しない", stringResource(R.string.place_off)) {
            Row(
                Modifier.fillMaxWidth().clip(skin.buttonShape).clickable { onPick(null) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                skin.Mark(current == null, null)
                Text("天気を表示しない", color = colors.ink, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Block(title: String, gloss: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = palette()
    val skin = skin()
    skin.Panel(
        Modifier.fillMaxWidth(),
        title = {
            Column {
                Text(title, color = colors.ink, fontSize = 17.sp, fontWeight = skin.strong)
                Text(gloss, color = colors.inkSoft, fontSize = 12.sp)
            }
        },
        content = content,
    )
}

@Composable
private fun PlaceRow(place: Place, selected: Boolean, onClick: () -> Unit) {
    val colors = palette()
    val skin = skin()
    Row(
        Modifier.fillMaxWidth().clip(skin.buttonShape).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        skin.Mark(selected, null)
        Column(Modifier.padding(start = 14.dp)) {
            Text(place.name, color = colors.ink, fontSize = 16.sp)
            if (place.detail.isNotBlank()) Text(place.detail, color = colors.inkSoft, fontSize = 13.sp)
        }
        Box(Modifier.weight(1f))
    }
}
