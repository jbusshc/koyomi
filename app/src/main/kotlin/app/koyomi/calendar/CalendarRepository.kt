package app.koyomi.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Una repetición concreta de un evento, tal como la da el calendario del sistema. */
data class CalendarInstance(
    val eventId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String,
    /** ARGB del calendario o del evento; 0 si el proveedor no da ninguno. */
    val color: Int,
)

/**
 * Solo lectura, y sin copia propia: cada consulta va al calendario del sistema. Se usa `Instances`
 * y no `Events` porque ya trae las repeticiones expandidas para el rango pedido.
 */
class CalendarRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Las instancias que tocan el rango, y de nuevo cada vez que el calendario cambia. */
    fun instances(from: Instant, to: Instant): Flow<List<CalendarInstance>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().map { query(from, to) }.flowOn(Dispatchers.IO)

    private fun query(from: Instant, to: Instant): List<CalendarInstance> {
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, from.toEpochMilli()) }
            .also { ContentUris.appendId(it, to.toEpochMilli()) }
            .build()
        val cursor = try {
            resolver.query(uri, PROJECTION, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")
        } catch (_: SecurityException) {
            // El permiso se puede retirar con la app abierta
            null
        } ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        CalendarInstance(
                            eventId = it.getLong(0),
                            title = it.getString(1).orEmpty(),
                            beginMillis = it.getLong(2),
                            endMillis = it.getLong(3),
                            allDay = it.getInt(4) == 1,
                            location = it.getString(5).orEmpty(),
                            color = it.getInt(6),
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.EVENT_LOCATION,
            Instances.DISPLAY_COLOR,
        )
    }
}
