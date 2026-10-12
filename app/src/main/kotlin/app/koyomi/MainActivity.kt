package app.koyomi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.koyomi.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) remember(intent)
        setContent { AppRoot() }
    }

    // Un widget toca una app ya abierta: llega aquí en vez de a onCreate
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        remember(intent)
    }

    private fun remember(intent: Intent?) {
        (application as KoyomiApp).requestedDestination.value = intent?.getStringExtra(EXTRA_DESTINATION)
    }

    companion object {
        /** La pantalla en que abrir la app: el nombre de un Destination. */
        const val EXTRA_DESTINATION = "app.koyomi.DESTINATION"
    }
}
