package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.model.PdfColorMode
import com.example.data.model.ReadingStatus
import com.example.ui.theme.ReaderCanvasDark
import com.example.ui.theme.ReaderCanvasLight
import com.example.ui.theme.ReaderCanvasSepia
import com.example.ui.theme.StatusAbandonedColor
import com.example.ui.theme.StatusCompletedColor
import com.example.ui.theme.StatusOnHoldColor
import com.example.ui.theme.StatusReadingColor
import com.example.ui.theme.StatusToReadColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun pdfColorFilterFor(mode: PdfColorMode): ColorFilter? = when (mode) {
    PdfColorMode.NORMAL -> null
    PdfColorMode.DARK_INVERT -> {
        val matrix = ColorMatrix(
            floatArrayOf(
                -0.88f, 0f, 0f, 0f, 238f,
                0f, -0.88f, 0f, 0f, 238f,
                0f, 0f, -0.88f, 0f, 238f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ColorFilter.colorMatrix(matrix)
    }
    PdfColorMode.SEPIA -> {
        val matrix = ColorMatrix(
            floatArrayOf(
                0.96f, 0f, 0f, 0f, 0f,
                0f, 0.91f, 0f, 0f, 0f,
                0f, 0f, 0.80f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ColorFilter.colorMatrix(matrix)
    }
    PdfColorMode.HIGH_CONTRAST -> {
        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ColorFilter.colorMatrix(matrix)
    }
}

fun readerCanvasColorFor(mode: PdfColorMode, isDarkTheme: Boolean): Color = when (mode) {
    PdfColorMode.DARK_INVERT -> ReaderCanvasDark
    PdfColorMode.SEPIA -> ReaderCanvasSepia
    PdfColorMode.NORMAL, PdfColorMode.HIGH_CONTRAST ->
        if (isDarkTheme) ReaderCanvasDark else ReaderCanvasLight
}

fun statusAccentColor(status: ReadingStatus): Color = when (status) {
    ReadingStatus.READING -> StatusReadingColor
    ReadingStatus.TO_READ -> StatusToReadColor
    ReadingStatus.ON_HOLD -> StatusOnHoldColor
    ReadingStatus.COMPLETED -> StatusCompletedColor
    ReadingStatus.ABANDONED -> StatusAbandonedColor
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFFFACC15)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

@Composable
fun ReadingStatusBadge(
    status: ReadingStatus,
    modifier: Modifier = Modifier
) {
    val accent = statusAccentColor(status)
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

fun serializeVectorStrokes(strokes: List<List<Offset>>): String {
    return strokes.filter { it.isNotEmpty() }.joinToString("|") { stroke ->
        stroke.joinToString(";") { pt ->
            String.format(Locale.US, "%.4f,%.4f", pt.x.coerceIn(0f, 1f), pt.y.coerceIn(0f, 1f))
        }
    }
}

fun deserializeVectorStrokes(serialized: String): List<List<Offset>> {
    if (serialized.isBlank()) return emptyList()
    return serialized.split("|").mapNotNull { strokeStr ->
        val pts = strokeStr.split(";").mapNotNull { pair ->
            val coords = pair.split(",")
            if (coords.size == 2) {
                val x = coords[0].toFloatOrNull()
                val y = coords[1].toFloatOrNull()
                if (x != null && y != null) Offset(x, y) else null
            } else {
                null
            }
        }
        pts.takeIf { it.isNotEmpty() }
    }
}

@Composable
fun VectorSignaturePreview(
    signatureVectorData: String,
    strokeColor: Color,
    strokeWidthDp: Float = 2.6f,
    modifier: Modifier = Modifier
) {
    val strokes = deserializeVectorStrokes(signatureVectorData)
    Canvas(modifier = modifier) {
        val strokePx = strokeWidthDp.dp.toPx()
        strokes.forEach { stroke ->
            if (stroke.size == 1) {
                val pt = stroke.first()
                drawCircle(
                    color = strokeColor,
                    radius = strokePx / 2f,
                    center = Offset(pt.x * size.width, pt.y * size.height)
                )
            } else if (stroke.size > 1) {
                val path = Path().apply {
                    val first = stroke.first()
                    moveTo(first.x * size.width, first.y * size.height)
                    for (i in 1 until stroke.size) {
                        val pt = stroke[i]
                        lineTo(pt.x * size.width, pt.y * size.height)
                    }
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Local PDF"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatRelativeTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val diff = abs(System.currentTimeMillis() - timestampMs)
    val minutes = diff / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
    }
}
