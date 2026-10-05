package app.koyomi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDate
import app.koyomi.engine.holiday.Shukujitsu
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Today(LocalDate.now()) }
    }
}

// Provisional (H0): solo comprueba que el motor llega a la pantalla. La pantalla 今日 de verdad,
// con eventos, es el hito H2.
@Composable
private fun Today(today: LocalDate) {
    val dark = isSystemInDarkTheme()
    val ink = if (dark) Color(0xFFECEAE4) else Color(0xFF22252B)
    val holidayRed = if (dark) Color(0xFFE5766C) else Color(0xFFC8372D)
    val date = JaDate.of(today)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF121316) else Color(0xFFFAFAF7))
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(date.year, color = ink, fontSize = 22.sp)
        Text(date.line(DateStyle.SIMPLE), color = ink, fontSize = 40.sp, fontWeight = FontWeight.Medium)
        Text(date.seireki, color = ink.copy(alpha = 0.6f), fontSize = 16.sp)
        Shukujitsu.of(today)?.let { Text(it, color = holidayRed, fontSize = 18.sp) }
    }
}

@Preview
@Composable
private fun TodayPreview() = Today(LocalDate.of(2026, 10, 12))
