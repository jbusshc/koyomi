package app.koyomi.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
import app.koyomi.settings.FrameWeight
import app.koyomi.settings.SkinId
import app.koyomi.ui.skin.PixelFrame
import app.koyomi.ui.skin.AiSkin
import app.koyomi.ui.skin.YakeiSkin
import java.time.LocalDate
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
    /** Cómo se dice la fecha, en kana, si el modo aprendizaje está encendido. */
    val reading: String?,
    val noPlaceHint: String,
    val weekStart: Youbi,
    /** Los días del mes con algún evento, para el calendario grande. Vacío si no se pidió. */
    val busyDays: Set<LocalDate> = emptySet(),
    /** «11月3日　文化の日（あと23日）», o nulo. */
    val nextHoliday: String? = null,
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
    private val config: WidgetConfig = WidgetConfig(),
) {
    private val canvas = Canvas(bitmap)
    private val colors = look.colors
    private val skin = skinOf(look.skin)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = look.typeface
        // Sin fondo propio, una sombra suave deja leer sobre cualquier fondo de pantalla
        if (look.background == WidgetBackground.CLEAR) setShadowLayer(3f * density, 0f, density, 0xAA000000.toInt())
    }
    private val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sink = WeatherIcons.canvasSink(canvas)

    private val hasWeather = Section.WEATHER in sections && (data.place != null || sections.size == 1)

    fun paint() {
        val inset = look.frame(canvas, density) / density
        val left = inset
        val top = inset
        val width = bitmap.width / density - 2 * inset
        val height = bitmap.height / density - 2 * inset
        paintBody(left, top, width, height)
        if (config.showRefresh && config.layout != WidgetLayout.DATE) refreshGlyph(bitmap.width / density, inset)
    }

    private fun paintBody(left: Float, top: Float, width: Float, height: Float) {
        when {
            config.layout == WidgetLayout.DATE -> dateLayout(left, top, width, height)
            config.layout == WidgetLayout.MONTH -> if (height >= MONTH_LAYOUT_MIN_HEIGHT) monthLayout(left, top, width, height) else weekStrip(left, top, width, height)
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
            if (Section.DATE in sections) add(Column(DATE_COLUMN_MIN) { cx, cw -> centred(y, h, DATE_BLOCK) { top -> dateBlock(cx, top, cw) } })
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
            if (room >= FORECAST_BLOCK && !keepForEvents && config.showForecast) {
                top += forecast(x, top, w) + GAP
            } else if (room >= HILO_LINE) {
                top += hiloLine(x, top, w) + 2f
            }
        }
        top += notes(x, top, w, bottom - top)
        if (Section.EVENTS in sections) top += events(x, top, w, bottom - top) + GAP
        if (Section.DATE in sections && config.showMonth && bottom - top >= MONTH_MIN) monthBlock(x, top, w, bottom - top)
    }

    private fun twoColumns(x: Float, y: Float, w: Float, h: Float) {
        val leftWidth = (w * 0.46f).coerceAtLeast(140f).coerceAtMost(w - 130f)
        val rightX = x + leftWidth + COLUMN_GAP
        val rightWidth = w - leftWidth - COLUMN_GAP
        val bottom = y + h

        var top = y
        top += hero(x, top, leftWidth, compact = h < 150f) + GAP
        if (data.report != null) {
            if (top + FORECAST_BLOCK <= bottom && config.showForecast) top += forecast(x, top, leftWidth) + GAP else if (top + HILO_LINE <= bottom) top += hiloLine(x, top, leftWidth) + GAP
        }
        val monthOnLeft = Section.DATE in sections && config.showMonth && bottom - top >= MONTH_MIN
        if (monthOnLeft) monthBlock(x, top, leftWidth, bottom - top)

        shape.color = colors.rule.toArgb()
        canvas.drawRect((rightX - COLUMN_GAP / 2) * density, (y + 4) * density, (rightX - COLUMN_GAP / 2) * density + density, (y + h - 4) * density, shape)

        var rightTop = y
        if (Section.DATE in sections) rightTop += dateRow(rightX, rightTop, rightWidth) + GAP
        rightTop += notes(rightX, rightTop, rightWidth, bottom - rightTop)
        if (Section.EVENTS in sections) rightTop += events(rightX, rightTop, rightWidth, bottom - rightTop) + GAP
        if (!monthOnLeft && Section.DATE in sections && config.showMonth && bottom - rightTop >= MONTH_MIN) monthBlock(rightX, rightTop, rightWidth, bottom - rightTop)
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

    /**
     * ↻ en la esquina de arriba a la derecha, dentro del margen del marco: pequeño y discreto. El
     * toque es de un área mayor que pone el widget (R.id.widget_refresh).
     */
    private fun refreshGlyph(widthDp: Float, inset: Float) {
        val radius = 4.5f
        // Dentro del margen que deja el marco, entre su borde y el contenido
        val offset = maxOf(inset - 5.5f, radius + 2f)
        val cx = widthDp - offset
        val cy = offset
        shape.style = Paint.Style.STROKE
        shape.strokeWidth = 1.5f * density
        shape.strokeCap = Paint.Cap.ROUND
        shape.color = colors.inkSoft.copy(alpha = 0.9f).toArgb()
        val oval = android.graphics.RectF((cx - radius) * density, (cy - radius) * density, (cx + radius) * density, (cy + radius) * density)
        canvas.drawArc(oval, 20f, 290f, false, shape)
        // La punta de la flecha, en el extremo del arco
        val tipX = cx + radius * kotlin.math.cos(Math.toRadians(20.0)).toFloat()
        val tipY = cy + radius * kotlin.math.sin(Math.toRadians(20.0)).toFloat()
        val arrow = 4.2f
        canvas.drawLine(tipX * density, tipY * density, (tipX + arrow) * density, (tipY - arrow * 0.3f) * density, shape)
        canvas.drawLine(tipX * density, tipY * density, (tipX - arrow * 0.2f) * density, (tipY - arrow) * density, shape)
        shape.style = Paint.Style.FILL
        shape.strokeCap = Paint.Cap.BUTT
    }

    /** El día de la semana, en un sello a la izquierda y la fecha al lado. Para una fila baja. */
    private fun dateBlock(x: Float, y: Float, w: Float) {
        val date = data.date
        val size = BADGE_SMALL
        weekdayBadge(x, y + (DATE_BLOCK - size) / 2, size)
        val textX = x + size + 9f
        val textW = w - size - 9f
        write(date.monthDay, textX, y + 19f, fitSp(date.monthDay, 20f, 14f, textW), colors.ink, maxWidth = textW)
        val aside = data.holiday ?: date.year.takeIf { data.dateStyle != DateStyle.SIMPLE }
        if (aside != null) write(aside, textX, y + 37f, 12f, if (data.holiday != null) colors.sunday else colors.inkSoft, maxWidth = textW)
    }

    /** La fecha en una línea con su sello de día de la semana, y una raya debajo. Para columnas más altas. */
    private fun dateRow(x: Float, y: Float, w: Float): Float {
        val date = data.date
        val size = BADGE_ROW
        weekdayBadge(x, y, size)
        val baseline = y + size / 2 + 7f
        val textX = x + size + 9f
        val dateWidth = write(date.monthDay, textX, baseline, fitSp(date.monthDay, 20f, 14f, w - size - 9f), colors.ink, maxWidth = w - size - 9f)
        val aside = data.holiday ?: date.year.takeIf { data.dateStyle != DateStyle.SIMPLE }
        if (aside != null && w - size - 9f - dateWidth > 44f) {
            write(aside, x + w, baseline, 12f, if (data.holiday != null) colors.sunday else colors.inkSoft, maxWidth = w - size - dateWidth - 20f, alignRight = true)
        }
        shape.color = colors.rule.toArgb()
        canvas.drawRect(x * density, (y + size + 6f) * density, (x + w) * density, (y + size + 6f) * density + density, shape)
        return DATE_ROW
    }

    /**
     * El día de la semana como protagonista: su kanji en un sello con la forma del tema. Los
     * domingos y festivos van en rojo; los sábados, en azul.
     */
    private fun weekdayBadge(x: Float, y: Float, size: Float) {
        val youbi = data.date.youbi
        val special = data.holiday != null || youbi == Youbi.NICHI || youbi == Youbi.DO
        val tone = when {
            data.holiday != null || youbi == Youbi.NICHI -> colors.sunday
            youbi == Youbi.DO -> colors.saturday
            else -> when (look.skin) {
                SkinId.SAKURA -> colors.accent
                SkinId.YAKEI -> YakeiSkin.neon(colors).second
                else -> colors.ink
            }
        }
        val l = x * density
        val t = y * density
        val r = (x + size) * density
        val b = (y + size) * density
        var ink = tone

        fun roundRect(radius: Float, fill: Int, line: Int?, lineWidth: Float = 1f) {
            shape.style = Paint.Style.FILL
            shape.color = fill
            canvas.drawRoundRect(l, t, r, b, radius * density, radius * density, shape)
            if (line != null) {
                val half = lineWidth * density / 2
                shape.style = Paint.Style.STROKE
                shape.strokeWidth = lineWidth * density
                shape.color = line
                canvas.drawRoundRect(l + half, t + half, r - half, b - half, radius * density, radius * density, shape)
                shape.style = Paint.Style.FILL
            }
        }

        when (look.skin) {
            SkinId.JRPG -> {
                // Una ventana pequeña, como la del resto del tema, con el kanji dentro: un bloque
                // lleno de color desentonaba con ellas
                val line = PixelFrame.border(FrameWeight.THIN, density)
                for (rect in PixelFrame.rects(r - l, b - t, line)) {
                    shape.color = when (rect.role) {
                        PixelFrame.Role.FRAME -> colors.frame
                        PixelFrame.Role.SURFACE -> colors.surface
                        PixelFrame.Role.HAIRLINE -> colors.frame.copy(alpha = 0.3f)
                    }.toArgb()
                    canvas.drawRect(l + rect.x, t + rect.y, l + rect.x + rect.w, t + rect.y + rect.h, shape)
                }
            }
            SkinId.TRADITIONAL -> {
                // El sello bermellón; en sábado y domingo, con el color del día
                roundRect(2f, (if (special) tone else colors.accent).toArgb(), null)
                ink = colors.background
            }
            SkinId.SAKURA -> {
                shape.color = tone.copy(alpha = 0.14f).toArgb()
                canvas.drawOval(l, t, r, b, shape)
                shape.style = Paint.Style.STROKE
                shape.strokeWidth = density
                shape.color = tone.copy(alpha = 0.35f).toArgb()
                canvas.drawOval(l + density / 2, t + density / 2, r - density / 2, b - density / 2, shape)
                shape.style = Paint.Style.FILL
            }
            SkinId.YAKEI -> {
                val (pink, cyan) = YakeiSkin.neon(colors)
                val end = if (look.dark) Color(0xFF7A3CFF) else cyan
                shape.shader = LinearGradient(l, t, r, b, pink.toArgb(), end.toArgb(), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(l, t, r, b, size * 0.28f * density, size * 0.28f * density, shape)
                shape.shader = null
                ink = Color.White
            }
            SkinId.AI -> {
                val (banner, _) = AiSkin.banner(colors)
                val onBanner = when {
                    data.holiday != null || youbi == Youbi.NICHI -> Color(0xFFF08C7A)
                    youbi == Youbi.DO -> Color(0xFF8DB7F0)
                    else -> Color(0xFFE9D8A6)
                }
                roundRect(6f, banner.toArgb(), onBanner.toArgb(), 1.5f)
                ink = onBanner
            }
            SkinId.STANDARD -> roundRect(size * 0.28f, tone.copy(alpha = 0.14f).toArgb(), tone.copy(alpha = 0.35f).toArgb())
        }
        val label = youbi.short
        text.textSize = size * 0.56f * density
        text.color = ink.toArgb()
        val metrics = text.fontMetrics
        val baseline = (t + b) / 2 - (metrics.ascent + metrics.descent) / 2
        canvas.drawText(label, (l + r) / 2 - text.measureText(label) / 2, baseline, text)
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

    /** Las líneas que acompañan a la fecha (la lectura y el 暦注), las que quepan. */
    private fun notes(x: Float, y: Float, w: Float, available: Float): Float {
        if (Section.DATE !in sections || !config.showNotes) return 0f
        var top = y
        for (line in listOfNotNull(data.reading, data.note)) {
            if (top + NOTE_ROW - y > available) break
            write(line, x, top + 12f, 12f, colors.inkSoft, maxWidth = w)
            top += NOTE_ROW
        }
        return top - y
    }

    /** El próximo evento, en dos líneas: la hora y el título. Para una fila baja. */
    private fun nextEvent(x: Float, y: Float, w: Float) {
        val first = data.rows.firstOrNull()
        // Con lo de hoy vacío, el primer evento es de otro día: el día va delante de la hora
        val dayLabel = (data.rows.firstOrNull { it is Row.Label } as? Row.Label)?.takeIf { first is Row.Note }
        val event = data.rows.filterIsInstance<Row.Event>().firstOrNull()
        when {
            event != null -> {
                val dot = 6f
                shape.color = event.color.toArgb()
                dotAt(x, y + 4f, dot)
                val head = if (dayLabel != null) "${dayLabel.text}　${event.time}" else event.time
                write(head, x + dot + 6f, y + 12f, 12f, colors.inkSoft, maxWidth = w - dot - 6f)
                write(event.title, x, y + 32f, 14f, colors.ink, maxWidth = w)
            }
            first is Row.Note -> write(first.text, x, y + 22f, 13f, colors.inkSoft, maxWidth = w)
            else -> write("今日は予定なし", x, y + 22f, 13f, colors.inkSoft, maxWidth = w)
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

    /** El mes de hoy: los días en su semana, domingos y festivos en rojo, hoy resaltado y, si hay sitio, un punto bajo los días con eventos. */
    private fun monthBlock(x: Float, y: Float, w: Float, available: Float, large: Boolean = false): Float {
        val today = data.date.date
        val first = today.withDayOfMonth(1)
        val length = today.lengthOfMonth()
        val lead = (Youbi.of(first).ordinal - data.weekStart.ordinal + 7) % 7
        val weeks = (lead + length + 6) / 7
        val headerHeight = if (large) 20f else 15f
        val rowHeight = ((available - headerHeight) / weeks).coerceIn(14f, if (large) 36f else 19f)
        val numberSp = if (rowHeight >= 28f) 14f else if (rowHeight >= 22f) 12f else 11f
        val dots = rowHeight >= 26f
        val cell = w / 7f

        MonthGrid.weekdays(data.weekStart).forEachIndexed { column, youbi ->
            val label = youbi.short
            val labelSp = if (large) 12f else 10f
            write(label, x + cell * column + (cell - measure(label, labelSp)) / 2, y + if (large) 14f else 11f, labelSp, look.youbiColor(youbi, holiday = false))
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
            val lift = if (dots) 2f else 0f
            write(label, left + (cell - measure(label, numberSp)) / 2, top + rowHeight / 2 + numberSp * 0.35f - lift, numberSp, color)
            if (dots && date in data.busyDays) {
                shape.color = (if (isToday) colors.surface else colors.accent).toArgb()
                dotAt(left + cell / 2 - 2f, top + rowHeight - 7f, 4f)
            }
        }
        return headerHeight + weeks * rowHeight
    }

    // ---------------------------------------------------------------- disposiciones propias

    /** Solo la fecha: el día de la semana en grande, con el día y la era. Ancho: sello a la izquierda; alto: centrado. */
    private fun dateLayout(x: Float, y: Float, w: Float, h: Float) {
        val date = data.date
        val holiday = data.holiday
        val year = if (data.dateStyle != DateStyle.SIMPLE) date.year else null
        val lines = if (config.showNotes) listOfNotNull(data.reading, data.note, data.nextHoliday) else emptyList()
        if (w >= h * 1.5f) {
            val size = minOf(h - 4f, w * 0.27f, 120f)
            weekdayBadge(x, y + (h - size) / 2, size)
            val textX = x + size + 14f
            val textW = w - size - 14f
            var daySp = (size * 0.42f).coerceIn(20f, 38f)
            // Con poco ancho, la fecha se encoge antes que cortarse
            val needed = measure(date.monthDay, daySp)
            if (needed > textW) daySp = (daySp * textW / needed).coerceAtLeast(13f)
            // El bloque de texto, centrado en vertical junto al sello
            val rows = listOfNotNull(year?.let { 18f }, daySp + 4f, holiday?.let { 20f }) + lines.map { 16f }
            var baseline = y + ((h - rows.sum()) / 2).coerceAtLeast(0f)
            if (year != null) {
                baseline += 14f
                write(year, textX, baseline, 13f, colors.inkSoft, maxWidth = textW)
                baseline += 4f
            }
            baseline += daySp * 0.9f
            write(date.monthDay, textX, baseline, daySp, colors.ink, maxWidth = textW)
            baseline += 4f
            if (holiday != null) {
                baseline += 16f
                write(holiday, textX, baseline, 14f, colors.sunday, maxWidth = textW)
            }
            for (line in lines) {
                baseline += 16f
                if (baseline > y + h) break
                write(line, textX, baseline, 12f, colors.inkSoft, maxWidth = textW)
            }
        } else {
            val size = minOf(w * 0.5f, h * 0.42f, 130f)
            val daySp = (size * 0.3f).coerceIn(20f, 34f)
            val total = size + 12f + daySp + (if (year != null) 18f else 0f) + (if (holiday != null) 20f else 0f) + lines.size * 17f
            var top = y + ((h - total) / 2).coerceAtLeast(0f)
            weekdayBadge(x + (w - size) / 2, top, size)
            top += size + 12f + daySp * 0.9f
            writeCentred(date.monthDay, x, w, top, daySp, colors.ink)
            if (year != null) {
                top += 18f
                writeCentred(year, x, w, top, 13f, colors.inkSoft)
            }
            if (holiday != null) {
                top += 20f
                writeCentred(holiday, x, w, top, 14f, colors.sunday)
            }
            for (line in lines) {
                top += 17f
                if (top > y + h) break
                writeCentred(line, x, w, top, 12f, colors.inkSoft)
            }
        }
    }

    /** El mes entero y grande, con su nombre arriba y un punto bajo los días con eventos. */
    private fun monthLayout(x: Float, y: Float, w: Float, h: Float) {
        val date = data.date
        val header = 28f
        write(date.month, x + 2f, y + 20f, 20f, colors.ink)
        if (data.dateStyle != DateStyle.SIMPLE) write(date.year, x + w - 2f, y + 19f, 13f, colors.inkSoft, alignRight = true)
        shape.color = colors.rule.toArgb()
        canvas.drawRect(x * density, (y + header - 2f) * density, (x + w) * density, (y + header - 2f) * density + density, shape)
        monthBlock(x, y + header + 4f, w, h - header - 4f, large = true)
    }

    /** La semana de hoy en una fila, para cuando el mes no cabe: cada día con su letra y su número. */
    private fun weekStrip(x: Float, y: Float, w: Float, h: Float) {
        val today = data.date.date
        val start = today.minusDays(((Youbi.of(today).ordinal - data.weekStart.ordinal + 7) % 7).toLong())
        val cell = w / 7f
        val numberSp = minOf(if (h >= 56f) 18f else 15f, cell * 0.6f)
        val circle = minOf(cell - 2f, numberSp + 10f)
        val blockHeight = 20f + circle
        val top = y + ((h - blockHeight) / 2).coerceAtLeast(0f)
        for (index in 0 until 7) {
            val day = start.plusDays(index.toLong())
            val youbi = Youbi.of(day)
            val holiday = Shukujitsu.of(day) != null
            val cx = x + cell * index + cell / 2
            val label = youbi.short
            write(label, cx - measure(label, 11f) / 2, top + 11f, 11f, look.youbiColor(youbi, holiday = false))
            val isToday = day == today
            val cy = top + 20f + circle / 2
            if (isToday) {
                shape.color = colors.ink.toArgb()
                val l = (cx - circle / 2) * density
                val t = (cy - circle / 2) * density
                val r = (cx + circle / 2) * density
                val b = (cy + circle / 2) * density
                if (look.squareDots) canvas.drawRect(l, t, r, b, shape) else canvas.drawOval(l, t, r, b, shape)
            }
            val color = when {
                isToday -> colors.surface
                holiday || youbi == Youbi.NICHI -> colors.sunday
                youbi == Youbi.DO -> colors.saturday
                else -> colors.ink
            }
            val number = day.dayOfMonth.toString()
            write(number, cx - measure(number, numberSp) / 2, cy + numberSp * 0.35f, numberSp, color)
        }
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

    /** El mayor tamaño, de [max] a [min], con el que [value] cabe en [width] dp. */
    private fun fitSp(value: String, max: Float, min: Float, width: Float): Float {
        val needed = measure(value, max)
        return if (needed <= width) max else (max * width / needed).coerceAtLeast(min)
    }

    /** Escribe [value] centrado en [x]..[x]+[w], sin pasarse de los bordes. */
    private fun writeCentred(value: String, x: Float, w: Float, baseline: Float, sp: Float, color: Color) {
        val shown = minOf(measure(value, sp), w)
        write(value, x + (w - shown) / 2, baseline, sp, color, maxWidth = w)
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
        const val DATE_ROW = 44f
        const val DATE_BLOCK = 42f
        const val BADGE_SMALL = 38f
        const val BADGE_ROW = 38f
        const val DATE_COLUMN_MIN = 128f
        // Por debajo de este alto (dp) el mes no cabe: se enseña solo la semana
        const val MONTH_LAYOUT_MIN_HEIGHT = 150f
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
