package app.koyomi.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

/** Una repetición concreta de un evento, tal como la da el calendario del sistema. */
data class CalendarInstance(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String,
    /** ARGB del calendario o del evento; 0 si el proveedor no da ninguno. */
    val color: Int,
)

/** Uno de los calendarios del teléfono (una cuenta puede tener varios). */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int)

/**
 * Solo lectura, y sin copia propia: cada consulta va al calendario del sistema. Se usa `Instances`
 * y no `Events` porque ya trae las repeticiones expandidas para el rango pedido.
 */
class CalendarRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Las instancias que tocan el rango, y de nuevo cada vez que el calendario cambia. */
    fun instances(from: Instant, to: Instant): Flow<List<CalendarInstance>> = changes().map { queryInstances(from, to) }.flowOn(Dispatchers.IO)

    /** Lo mismo, una sola vez: para un widget, que se pinta y termina. */
    suspend fun instancesOnce(from: Instant, to: Instant): List<CalendarInstance> = withContext(Dispatchers.IO) { queryInstances(from, to) }

    /** Los calendarios que el teléfono tiene a la vista, que son los que pueden traer eventos. */
    fun calendars(): Flow<List<DeviceCalendar>> = changes().map { queryCalendars() }.flowOn(Dispatchers.IO)

    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun queryInstances(from: Instant, to: Instant): List<CalendarInstance> {
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, from.toEpochMilli()) }
            .also { ContentUris.appendId(it, to.toEpochMilli()) }
            .build()
        return query(uri, INSTANCE_COLUMNS, "${Instances.VISIBLE} = 1", "${Instances.BEGIN} ASC") {
            CalendarInstance(
                eventId = it.getLong(0),
                calendarId = it.getLong(1),
                title = it.getString(2).orEmpty(),
                beginMillis = it.getLong(3),
                endMillis = it.getLong(4),
                allDay = it.getInt(5) == 1,
                location = it.getString(6).orEmpty(),
                color = it.getInt(7),
            )
        }
    }

    private fun queryCalendars(): List<DeviceCalendar> =
        query(Calendars.CONTENT_URI, CALENDAR_COLUMNS, "${Calendars.VISIBLE} = 1", "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}") {
            DeviceCalendar(id = it.getLong(0), name = it.getString(1).orEmpty(), account = it.getString(2).orEmpty(), color = it.getInt(3))
        }

    private fun <T> query(uri: Uri, columns: Array<String>, where: String, order: String, row: (Cursor) -> T): List<T> {
        val cursor = try {
            resolver.query(uri, columns, where, null, order)
        } catch (_: SecurityException) {
            // El permiso se puede retirar con la app abierta
            null
        } ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(row(it))
            }
        }
    }

    private companion object {
        val INSTANCE_COLUMNS = arrayOf(
            Instances.EVENT_ID,
            Instances.CALENDAR_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.EVENT_LOCATION,
            Instances.DISPLAY_COLOR,
        )
        val CALENDAR_COLUMNS = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR,
        )
    }
}
