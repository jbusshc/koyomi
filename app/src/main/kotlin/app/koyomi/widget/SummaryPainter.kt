package app.koyomi.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.koyomi.engine.format.DateStyle
import app.koyomi.engine.format.JaDateText
import app.koyomi.engine.format.MonthGrid
import app.koyomi.engine.format.Youbi
import app.koyomi.engine.holiday.Shukujitsu
import app.koyomi.engine.weather.Sky
import app.koyomi.engine.weather.Tenki
import app.koyomi.settings.Place
import app.koyomi.ui.skin.skinOf
import app.koyomi.ui.weather.WeatherIcons
import app.koyomi.weather.WeatherReport

/** Lo que un widget puede enseñar, y en qué orden se va sumando al crecer: el tiempo, la fecha, los eventos. */
internal enum class Section { WEATHER, DATE, EVENTS }

/** Todo lo que el pintor necesita, ya resuelto. */
internal class SummaryData(
    val date: JaDateText,
    val holiday: String?,
    val dateStyle: DateStyle,
    val place: Place?,
    val report: WeatherReport?,
    /** La última petición del tiempo falló y no hay parte que enseñar. */
    val weatherFailed: Boolean,
    val rows: List<Row>,
    /** Una línea de 暦注 o de lecturas, si están encendidas. */
    val note: String?,
    val noPlaceHint: String,
    val weekStart: Youbi,
)

/**
 * Pinta un widget que enseña más cosas cuanto más grande es. Trabaja en dp, sobre el hueco que
 * deja el marco del tema.
 *
 * - Una fila de alto: columnas de izquierda a derecha, tantas como quepan (tiempo, fecha, máxima
 *   y mínima, próximo evento).
 * - Más alto y estrecho: una columna, de arriba abajo (tiempo, fecha, los tres días, eventos).
 * - Más alto y ancho: dos columnas, el tiempo a la izquierda y la fecha con los eventos a la derecha.
 */
internal class SummaryPainter(
    private val bitmap: Bitmap,
    private val look: WidgetLook,
    private val density: Float,
    private val data: SummaryData,
    private val sections: Set<Section>,
) {
    private val canvas = Canvas(bitmap)
    private val colors = look.colors
    private val skin = skinOf(look.skin)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = look.typeface }
    private val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sink = WeatherIcons.canvasSink(canvas)

    private val hasWeather = Section.WEATHER in sections && (data.place != null || sections.size == 1)

    fun paint() {
        val inset = look.frame(canvas, density) / density
        val left = inset
        val top = inset
        val width = bitmap.width / density - 2 * inset
        val height = bitmap.height / density - 2 * inset
        when {
            height < ROW_MODE_MAX_HEIGHT -> rowMode(left, top, width, height)
            width >= TWO_COLUMNS_MIN_WIDTH && hasWeather && sections.size > 1 -> twoColumns(left, top, width, height)
            else -> oneColumn(left, top, width, height)
        }
    }

    // ---------------------------------------------------------------- modos

    private fun rowMode(x: Float, y: Float, w: Float, h: Float) {
        class Column(val min: Float, val grow: Boolean = false, val draw: (x: Float, w: Float) -> Unit)

        val columns = buildList {
            if (hasWeather) add(Column(if (data.report == null) 100f else 112f) { cx, cw -> centred(y, h, heroHeight(compact = true, cw)) { top -> hero(cx, top, cw, compact = true) } })
            if (Section.DATE in sections) add(Column(88f) { cx, cw -> centred(y, h, DATE_BLOCK) { top -> dateBlock(cx, top, cw) } })
            if (hasWeather && data.report != null) add(Column(70f) { cx, cw -> centred(y, h, HILO_BLOCK) { top -> hiloBlock(cx, top, cw) } })
            if (Section.EVENTS in sections) add(Column(120f, grow = true) { cx, cw -> centred(y, h, NEXT_BLOCK) { top -> nextEvent(cx, top, cw) } })
        }
        // Tantas columnas como quepan, en el orden de importancia
        val chosen = mutableListOf<Column>()
        var used = 0f
        for (column in columns) {
            val needed = column.min + if (chosen.isEmpty()) 0f else COLUMN_GAP
            if (used + needed > w && chosen.isNotEmpty()) break
            chosen += column
            used += needed
        }
        // El sobrante se reparte entre las columnas, para que no queden pegadas a la izquierda
        val extra = ((w - used) / chosen.size).coerceAtLeast(0f)
        var cx = x
        chosen.forEachIndexed { index, column ->
            val cw = if (index == chosen.lastIndex) x + w - cx else column.min + extra
            column.draw(cx, cw)
            cx += cw
            if (index != chosen.lastIndex) {
                // Una raya fina entre columnas
                shape.color = colors.rule.toArgb()
                canvas.drawRect((cx + COLUMN_GAP / 2) * density, (y + 6) * density, (cx + COLUMN_GAP / 2) * density + density, (y + h - 6) * density, shape)
                cx += COLUMN_GAP
            }
        }
    }

    private fun oneColumn(x: Float, y: Float, w: Float, h: Float) {
        var top = y
        val bottom = y + h
        val big = h >= 150f
        if (hasWeather) top += hero(x, top, w, compact = !big) + GAP
        if (Section.DATE in sections && top + DATE_ROW <= bottom) top += dateRow(x, top, w) + GAP
        if (hasWeather && data.report != null) {
            // Los tres días si caben sin quitarle a los eventos sus dos primeras filas; si no, una
            // línea con la máxima y la mínima
            val room = bottom - top
            val keepForEvents = Section.EVENTS in sections && room < FORECAST_BLOCK + 2 * EVENT_ROW_STACKED
            if (room >= FORECAST_BLOCK && !keepForEvents) {
                top += forecast(x, top, w) + GAP
            } else if (room >= HILO_LINE) {
                top += hiloLine(x, top, w) + 2f
            }
        }
        if (data.note != null && Section.DATE in sections && top + NOTE_ROW <= bottom) top += noteLine(x, top, w)
        if (Section.EVENTS in sections) top += events(x, top, w, bottom - top) + GAP
        if (Section.DATE in sections && bottom - top >= MONTH_MIN) monthBlock(x, top, w, bottom - top)
    }

    private fun twoColumns(x: Float, y: Float, w: Float, h: Float) {
        val leftWidth = (w * 0.46f).coerceAtLeast(140f).coerceAtMost(w - 130f)
        val rightX = x + leftWidth + COLUMN_GAP
        val rightWidth = w - leftWidth - COLUMN_GAP
        val bottom = y + h

        var top = y
        top += hero(x, top, leftWidth, compact = h < 150f) + GAP
        if (data.report != null) {
            if (top + FORECAST_BLOCK <= bottom) top += forecast(x, top, leftWidth) + GAP else if (top + HILO_LINE <= bottom) top += hiloLine(x, top, leftWidth) + GAP
        }
        val monthOnLeft = Section.DATE in sections && bottom - top >= MONTH_MIN
        if (monthOnLeft) monthBlock(x, top, leftWidth, bottom - top)

        shape.color = colors.rule.toArgb()
        canvas.drawRect((rightX - COLUMN_GAP / 2) * density, (y + 4) * density, (rightX - COLUMN_GAP / 2) * density + density, (y + h - 4) * density, shape)

        var rightTop = y
        if (Section.DATE in sections) rightTop += dateRow(rightX, rightTop, rightWidth) + GAP
        if (data.note != null && Section.DATE in sections && rightTop + NOTE_ROW <= bottom) rightTop += noteLine(rightX, rightTop, rightWidth)
        if (Section.EVENTS in sections) rightTop += events(rightX, rightTop, rightWidth, bottom - rightTop) + GAP
        if (!monthOnLeft && Section.DATE in sections && bottom - rightTop >= MONTH_MIN) monthBlock(rightX, rightTop, rightWidth, bottom - rightTop)
    }

    // ---------------------------------------------------------------- bloques

    /** El tiempo: el icono, la temperatura, la palabra del parte y la ciudad. Devuelve el alto usado. */
    private fun hero(x: Float, y: Float, w: Float, compact: Boolean): Float {
        val report = data.report
        val place = data.place
        if (report == null) {
            write("天気", x, y + 14f, 12f, colors.inkSoft)
            val message = when {
                place == null -> data.noPlaceHint
                data.weatherFailed -> "取得できません"
                else -> "…"
            }
            write(message, x, y + 34f, 13f, colors.inkSoft, maxWidth = w)
            if (place != null) write(place.name, x, y + 52f, 11f, colors.inkSoft, maxWidth = w)
            return 56f
        }
        val icon = if (compact) 44f else 54f
        val temperatureSp = if (compact) 26f else 30f
        val wordSp = if (compact) 13f else 15f
        // Con poco ancho, el icono cede sitio a las letras
        val iconSize = if (w < 112f) 36f else icon
        WeatherIcons.paint(sink, skin, colors, Sky.of(report.code), !report.isDay, x * density, y * density, iconSize * density)

        val textX = x + iconSize + 8f
        val textW = w - iconSize - 8f
        var baseline = y + temperatureSp * 0.92f
        write(Tenki.temperature(report.temperature), textX, baseline, temperatureSp, colors.ink, maxWidth = textW)
        baseline += wordSp + 5f
        write(Tenki.of(report.code).kanji, textX, baseline, wordSp, colors.ink, maxWidth = textW)
        if (place != null && textW >= 56f) {
            baseline += 15f
            write(place.name, textX, baseline, 11f, colors.inkSoft, maxWidth = textW)
        }
        return maxOf(iconSize, baseline - y + 4f)
    }

    private fun heroHeight(compact: Boolean, w: Float): Float {
        if (data.report == null) return 56f
        val temperatureSp = if (compact) 26f else 30f
        val wordSp = if (compact) 13f else 15f
        val iconSize = if (w < 112f) 36f else if (compact) 44f else 54f
        val textW = w - iconSize - 8f
        val lines = temperatureSp * 0.92f + wordSp + 5f + (if (data.place != null && textW >= 56f) 15f else 0f) + 4f
        return maxOf(iconSize, lines)
    }

    /** El día en grande, con el día de la semana debajo y la era al lado. Para una fila baja. */
    private fun dateBlock(x: Float, y: Float, w: Float) {
        val date = data.date
        write(date.monthDay, x, y + 20f, 20f, colors.ink, maxWidth = w)
        val youbi = "（${date.youbi.short}）"
        val color = look.youbiColor(date.youbi, data.holiday != null)
        val shown = write(youbi, x, y + 40f, 14f, color)
        val aside = data.holiday ?: date.year.takeIf { data.dateStyle != DateStyle.SIMPLE }
        if (aside != null && w - shown > 40f) {
            write(aside, x + shown + 6f, y + 40f, 12f, if (data.holiday != null) colors.sunday else colors.inkSoft, maxWidth = w - shown - 6f)
        }
    }

    /** La fecha en una sola línea, con una raya debajo. Para columnas más altas. */
    private fun dateRow(x: Float, y: Float, w: Float): Float {
        val date = data.date
        val baseline = y + 20f
        val dateWidth = write(date.monthDay, x, baseline, 20f, colors.ink)
        val color = look.youbiColor(date.youbi, data.holiday != null)
        write("（${date.youbi.short}）", x + dateWidth, baseline, 14f, color)
        val aside = data.holiday ?: date.year.takeIf { data.dateStyle != DateStyle.SIMPLE }
        if (aside != null) {
            write(aside, x + w, baseline, 12f, if (data.holiday != null) colors.sunday else colors.inkSoft, maxWidth = w - dateWidth - 40f, alignRight = true)
        }
        shape.color = colors.rule.toArgb()
        canvas.drawRect(x * density, (y + 28f) * density, (x + w) * density, (y + 28f) * density + density, shape)
        return DATE_ROW
    }

    private fun hiloBlock(x: Float, y: Float, w: Float) {
        val today = data.report?.days?.getOrNull(0) ?: return
        write("最高 ${Tenki.temperature(today.max)}", x, y + 14f, 13f, colors.ink, maxWidth = w)
        write("最低 ${Tenki.temperature(today.min)}", x, y + 34f, 13f, colors.inkSoft, maxWidth = w)
    }

    private fun hiloLine(x: Float, y: Float, w: Float): Float {
        val today = data.report?.days?.getOrNull(0) ?: return 0f
        write("最高 ${Tenki.temperature(today.max)}　最低 ${Tenki.temperature(today.min)}", x, y + 14f, 13f, colors.ink, maxWidth = w)
        return HILO_LINE
    }

    /** Tres días, cada uno con su icono y su máxima sobre su mínima. */
    private fun forecast(x: Float, y: Float, w: Float): Float {
        val days = data.report?.days.orEmpty().take(FORECAST_LABELS.size)
        val column = w / FORECAST_LABELS.size
        days.forEachIndexed { index, day ->
            val cx = x + column * index + column / 2
            val label = FORECAST_LABELS[index]
            write(label, cx - measure(label, 11f) / 2, y + 11f, 11f, colors.inkSoft)
            WeatherIcons.paint(sink, skin, colors, Sky.of(day.code), false, (cx - 15f) * density, (y + 16f) * density, 30f * density)
            val max = Tenki.temperature(day.max)
            val min = Tenki.temperature(day.min)
            write(max, cx - measure(max, 12f) / 2, y + 60f, 12f, colors.ink)
            write(min, cx - measure(min, 11f) / 2, y + 73f, 11f, colors.inkSoft)
        }
        return FORECAST_BLOCK
    }

    private fun noteLine(x: Float, y: Float, w: Float): Float {
        write(data.note.orEmpty(), x, y + 12f, 12f, colors.inkSoft, maxWidth = w)
        return NOTE_ROW
    }

    /** El próximo evento, en dos líneas: la hora y el título. Para una fila baja. */
    private fun nextEvent(x: Float, y: Float, w: Float) {
        val event = data.rows.filterIsInstance<Row.Event>().firstOrNull()
        val note = data.rows.filterIsInstance<Row.Note>().firstOrNull()
        when {
            event != null -> {
                val dot = 6f
                shape.color = event.color.toArgb()
                dotAt(x, y + 4f, dot)
                write(event.time, x + dot + 6f, y + 12f, 12f, colors.inkSoft, maxWidth = w - dot - 6f)
                write(event.title, x, y + 32f, 14f, colors.ink, maxWidth = w)
            }
            note != null -> write(note.text, x, y + 22f, 13f, colors.inkSoft, maxWidth = w)
            else -> write("予定なし", x, y + 22f, 13f, colors.inkSoft)
        }
    }

    /**
     * Tantas filas de evento como quepan en [available]. En un ancho corto, la hora va encima del
     * título; si no, a su lado. Devuelve el alto que ocuparon.
     */
    private fun events(x: Float, y: Float, w: Float, available: Float): Float {
        val stacked = w < STACKED_BELOW
        fun heightOf(row: Row): Float = when (row) {
            is Row.Event -> if (stacked) EVENT_ROW_STACKED else EVENT_ROW
            is Row.Label -> LABEL_ROW
            is Row.Note -> EVENT_ROW
        }

        val rows = mutableListOf<Row>()
        var used = 0f
        for (row in data.rows) {
            if (used + heightOf(row) > available) break
            rows += row
            used += heightOf(row)
        }
        // Un «明日» al final, sin nada debajo, sobra
        while (rows.lastOrNull() is Row.Label) rows.removeAt(rows.lastIndex)
        if (rows.isEmpty()) return 0f

        val timeWidth = rows.filterIsInstance<Row.Event>().maxOfOrNull { measure(it.time, 12f) } ?: 0f
        val dot = 6f
        var top = y
        for (row in rows) {
            val height = heightOf(row)
            when (row) {
                is Row.Event -> {
                    shape.color = row.color.toArgb()
                    if (stacked) {
                        dotAt(x, top + (height - dot) / 2, dot)
                        val textX = x + dot + 8f
                        write(row.time, textX, top + 13f, 11f, colors.inkSoft, maxWidth = w - dot - 8f)
                        write(row.title, textX, top + 29f, 14f, colors.ink, maxWidth = w - dot - 8f)
                    } else {
                        val baseline = top + height * 0.5f + 5f
                        dotAt(x, top + (height - dot) / 2, dot)
                        val timeX = x + dot + 8f
                        write(row.time, timeX, baseline, 12f, colors.inkSoft)
                        val titleX = timeX + timeWidth + 8f
                        write(row.title, titleX, baseline, 14f, colors.ink, maxWidth = x + w - titleX)
                    }
                }
                is Row.Label -> {
                    val baseline = top + height * 0.5f + 5f
                    val width = write(row.text, x, baseline, 13f, colors.ink)
                    write(row.detail, x + width + 8f, baseline, 12f, row.detailColor)
                }
                is Row.Note -> write(row.text, x, top + height * 0.5f + 5f, 13f, colors.inkSoft, maxWidth = w)
            }
            top += height
        }
        return top - y
    }

    /** El mes de hoy en pequeño: los días en su semana, domingos y festivos en rojo, hoy resaltado. */
    private fun monthBlock(x: Float, y: Float, w: Float, available: Float): Float {
        val today = data.date.date
        val first = today.withDayOfMonth(1)
        val length = today.lengthOfMonth()
        val lead = (Youbi.of(first).ordinal - data.weekStart.ordinal + 7) % 7
        val weeks = (lead + length + 6) / 7
        val headerHeight = 15f
        val rowHeight = ((available - headerHeight) / weeks).coerceIn(14f, 19f)
        val cell = w / 7f

        MonthGrid.weekdays(data.weekStart).forEachIndexed { column, youbi ->
            val label = youbi.short
            write(label, x + cell * column + (cell - measure(label, 10f)) / 2, y + 11f, 10f, look.youbiColor(youbi, holiday = false))
        }
        for (day in 1..length) {
            val index = lead + day - 1
            val date = first.withDayOfMonth(day)
            val top = y + headerHeight + (index / 7) * rowHeight
            val left = x + cell * (index % 7)
            val isToday = day == today.dayOfMonth
            val color = when {
                isToday -> colors.surface
                Shukujitsu.of(date) != null || Youbi.of(date) == Youbi.NICHI -> colors.sunday
                Youbi.of(date) == Youbi.DO -> colors.saturday
                else -> colors.ink
            }
            if (isToday) {
                val size = minOf(cell, rowHeight) - 1f
                shape.color = colors.ink.toArgb()
                val cx = left + (cell - size) / 2
                val cy = top + (rowHeight - size) / 2
                if (look.squareDots) canvas.drawRect(cx * density, cy * density, (cx + size) * density, (cy + size) * density, shape)
                else canvas.drawOval(cx * density, cy * density, (cx + size) * density, (cy + size) * density, shape)
            }
            val label = day.toString()
            write(label, left + (cell - measure(label, 11f)) / 2, top + rowHeight / 2 + 4f, 11f, color)
        }
        return headerHeight + weeks * rowHeight
    }

    // ---------------------------------------------------------------- pincel

    private inline fun centred(y: Float, h: Float, blockHeight: Float, draw: (top: Float) -> Unit) = draw(y + ((h - blockHeight) / 2).coerceAtLeast(0f))

    private fun dotAt(x: Float, y: Float, size: Float) {
        if (look.squareDots) {
            canvas.drawRect(x * density, y * density, (x + size) * density, (y + size) * density, shape)
        } else {
            canvas.drawOval(x * density, y * density, (x + size) * density, (y + size) * density, shape)
        }
    }

    private fun measure(value: String, sp: Float): Float {
        text.textSize = sp * density
        return text.measureText(value) / density
    }

    /** Escribe con la línea base en [baseline] (dp). Devuelve el ancho escrito, en dp. */
    private fun write(value: String, x: Float, baseline: Float, sp: Float, color: Color, maxWidth: Float = Float.MAX_VALUE, alignRight: Boolean = false): Float {
        text.textSize = sp * density
        text.color = color.toArgb()
        val shown = if (maxWidth == Float.MAX_VALUE) value else TextUtils.ellipsize(value, text, (maxWidth * density).coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()
        val width = text.measureText(shown)
        canvas.drawText(shown, if (alignRight) x * density - width else x * density, baseline * density, text)
        return width / density
    }

    private companion object {
        // Por debajo de esta altura útil (dp) entra una sola fila de columnas
        const val ROW_MODE_MAX_HEIGHT = 100f
        const val TWO_COLUMNS_MIN_WIDTH = 250f
        const val COLUMN_GAP = 14f
        const val GAP = 8f
        const val DATE_ROW = 36f
        const val DATE_BLOCK = 46f
        const val HILO_BLOCK = 40f
        const val NEXT_BLOCK = 40f
        const val HILO_LINE = 20f
        const val NOTE_ROW = 18f
        const val FORECAST_BLOCK = 80f
        const val EVENT_ROW = 22f
        const val EVENT_ROW_STACKED = 36f
        const val LABEL_ROW = 26f
        // Por debajo de este ancho (dp) la hora no cabe al lado del título
        const val STACKED_BELOW = 230f
        // Alto mínimo para que el mes entre: su cabecera y cuatro semanas apretadas
        const val MONTH_MIN = 78f
        val FORECAST_LABELS = listOf("今日", "明日", "明後日")
    }
}
