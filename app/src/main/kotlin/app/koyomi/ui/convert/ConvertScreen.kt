package app.koyomi.ui.convert

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyomi.R
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.engine.kyureki.Eto
import app.koyomi.engine.reading.JaReading
import app.koyomi.engine.kyureki.Kyureki
import app.koyomi.engine.kyureki.Rokuyo
import app.koyomi.engine.kyureki.Sekki
import app.koyomi.engine.wareki.Era
import app.koyomi.engine.wareki.Wareki
import app.koyomi.ui.skin.palette
import app.koyomi.ui.skin.skin
import app.koyomi.ui.theme.Palette
import java.time.LocalDate
import java.time.YearMonth

private val YEARS = Wareki.FIRST_DATE.year..2100

/** De una fecha occidental a todo lo que un calendario japonés dice de ella, y de un año de era al occidental. */
@Composable
fun ConvertScreen() {
    val colors = palette()
    val skin = skin()
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var era by rememberSaveable { mutableStateOf(Era.entries.last()) }
    var eraYear by rememberSaveable { mutableIntStateOf(Wareki.of(LocalDate.now())?.year ?: 1) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = skin.screenPadding)) {
        Text("変換", color = colors.ink, fontSize = 28.sp, fontWeight = skin.strong, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp))

        Section("西暦 → 和暦", stringResource(R.string.convert_to_wareki), colors) {
            Stepper(date.year, "年", colors, big = 10) { year -> date = date.withValid(year.coerceIn(YEARS), date.monthValue) }
            Stepper(date.monthValue, "月", colors) { month -> date = date.withValid(date.year, wrap(month, 1, 12)) }
            Stepper(date.dayOfMonth, "日", colors) { day -> date = date.withDayOfMonth(wrap(day, 1, date.lengthOfMonth())) }
            Rule(colors)

            val arabic = JaDate.of(date)
            val kanji = JaDate.kanji(date)
            val eto = Eto.ofYear(date.year)
            Result("和暦", arabic.line(DateStyle.FULL), colors)
            Result("漢数字", kanji.year + kanji.monthDay, colors)
            Result("曜日", "${arabic.youbi.full}（${arabic.youbi.fullKana}）", colors)
            JaReading.date(date)?.let { Result("読み", it, colors) }
            Result("干支", "${eto.kanji}・${eto.yearName}（${eto.yearKana}）", colors)
            Shukujitsu.of(date)?.let { Result("祝日", it, colors, colors.sunday) }
            Kyureki.of(date)?.let { Result("旧暦", it.text.removePrefix("旧暦"), colors) }
            Rokuyo.of(date)?.let { Result("六曜", "${it.kanji}（${it.kana}）", colors) }
            Sekki.of(date)?.let { Result("節気", "${it.kanji}（${it.kana}）", colors) }
        }

        Section("和暦 → 西暦", stringResource(R.string.convert_to_seireki), colors) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (option in Era.entries.reversed()) {
                    Row(
                        Modifier.clip(skin.buttonShape).clickable { era = option }.padding(vertical = 8.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        skin.Mark(option == era, null)
                        Text(option.kanji, color = colors.ink, fontSize = 15.sp, modifier = Modifier.padding(start = 3.dp))
                    }
                }
            }
            Stepper(eraYear, "年", colors, big = 10) { eraYear = it.coerceIn(1, 99) }
            Rule(colors)

            val span = Wareki(era, eraYear).span()
            if (span == null) {
                // 令和99年 se puede escribir; 平成40年 no existió
                Result("西暦", "—", colors)
                Text(stringResource(R.string.convert_no_such_year, era.kanji, Wareki.lastYear(era) ?: 0), color = colors.inkSoft, fontSize = 13.sp)
            } else {
                Result("西暦", "${span.start.year}年", colors)
                if (span.start.year in 1000..9999) Result("読み", "${JaReading.warekiYear(Wareki(era, eraYear))} ＝ ${JaReading.seirekiYear(span.start.year)}", colors)
                Result("干支", Eto.ofYear(span.start.year).let { "${it.kanji}・${it.yearName}" }, colors)
                // El primer año y el último de una era no duran el año entero
                if (span.start.dayOfYear != 1 || span.endInclusive != span.endInclusive.withDayOfYear(span.endInclusive.lengthOfYear())) {
                    Result("期間", "${JaDate.of(span.start).monthDay}〜${JaDate.of(span.endInclusive).monthDay}", colors)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

private fun wrap(value: Int, min: Int, max: Int): Int = when {
    value < min -> max
    value > max -> min
    else -> value
}

/** Cambiar de mes o de año no puede dejar un 31 de febrero. */
private fun LocalDate.withValid(year: Int, month: Int): LocalDate =
    LocalDate.of(year, month, dayOfMonth.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()))

@Composable
private fun Section(title: String, gloss: String, colors: Palette, content: @Composable () -> Unit) {
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

@Composable
private fun Rule(colors: Palette) {
    Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).then(Modifier.border(1.dp, colors.rule)))
}

/** Un número con botones a los lados. [big]: un segundo par de botones que salta de tanto en tanto. */
@Composable
private fun Stepper(value: Int, unit: String, colors: Palette, big: Int? = null, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (big != null) Step("−$big", colors) { onChange(value - big) } else Spacer(Modifier.width(52.dp))
        Step("−", colors) { onChange(value - 1) }
        Text("$value$unit", color = colors.ink, fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.width(112.dp))
        Step("＋", colors) { onChange(value + 1) }
        if (big != null) Step("＋$big", colors) { onChange(value + big) } else Spacer(Modifier.width(52.dp))
    }
}

@Composable
private fun Step(label: String, colors: Palette, onClick: () -> Unit) {
    val skin = skin()
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(width = 48.dp, height = 40.dp)
            .clip(skin.buttonShape)
            .border(1.dp, colors.rule, skin.buttonShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.ink, fontSize = if (label.length > 1) 13.sp else 18.sp)
    }
}

@Composable
private fun Result(label: String, value: String, colors: Palette, valueColor: androidx.compose.ui.graphics.Color = colors.ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = colors.inkSoft, fontSize = 14.sp, modifier = Modifier.width(72.dp).padding(top = 2.dp))
        Text(value, color = valueColor, fontSize = 17.sp)
    }
}
