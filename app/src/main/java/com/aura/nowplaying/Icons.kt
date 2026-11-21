package com.aura.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/* Íconos dibujados a mano para no depender de librerías extra. */

private fun DrawScope.triangle(color: Color, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
    val p = Path().apply {
        moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); close()
    }
    drawPath(p, color)
}

@Composable
fun PlayIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    triangle(color, w * 0.28f, h * 0.16f, w * 0.28f, h * 0.84f, w * 0.86f, h * 0.5f)
}

@Composable
fun PauseIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val bw = w * 0.2f
    val r = CornerRadius(bw / 3f, bw / 3f)
    drawRoundRect(color, Offset(w * 0.24f, h * 0.18f), Size(bw, h * 0.64f), r)
    drawRoundRect(color, Offset(w * 0.56f, h * 0.18f), Size(bw, h * 0.64f), r)
}

@Composable
fun SkipIcon(forward: Boolean, color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    scale(if (forward) 1f else -1f, 1f) {
        triangle(color, w * 0.08f, h * 0.2f, w * 0.08f, h * 0.8f, w * 0.48f, h * 0.5f)
        triangle(color, w * 0.46f, h * 0.2f, w * 0.46f, h * 0.8f, w * 0.86f, h * 0.5f)
        drawRoundRect(color, Offset(w * 0.84f, h * 0.2f), Size(w * 0.09f, h * 0.6f), CornerRadius(w * 0.03f, w * 0.03f))
    }
}

@Composable
fun SpeakerIcon(loud: Boolean, color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.1f, h * 0.38f); lineTo(w * 0.28f, h * 0.38f); lineTo(w * 0.5f, h * 0.18f)
        lineTo(w * 0.5f, h * 0.82f); lineTo(w * 0.28f, h * 0.62f); lineTo(w * 0.1f, h * 0.62f); close()
    }
    drawPath(p, color)
    val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
    drawArc(color, -50f, 100f, false, Offset(w * 0.38f, h * 0.32f), Size(w * 0.3f, h * 0.36f), style = stroke)
    if (loud) {
        drawArc(color, -50f, 100f, false, Offset(w * 0.3f, h * 0.16f), Size(w * 0.58f, h * 0.68f), style = stroke)
    }
}

@Composable
fun NoteIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    drawRect(color, Offset(w * 0.36f, h * 0.2f), Size(w * 0.06f, h * 0.52f))
    drawRect(color, Offset(w * 0.72f, h * 0.12f), Size(w * 0.06f, h * 0.52f))
    val beam = Path().apply {
        moveTo(w * 0.36f, h * 0.2f); lineTo(w * 0.78f, h * 0.12f); lineTo(w * 0.78f, h * 0.22f); lineTo(w * 0.36f, h * 0.3f); close()
    }
    drawPath(beam, color)
    drawOval(color, Offset(w * 0.2f, h * 0.66f), Size(w * 0.22f, h * 0.17f))
    drawOval(color, Offset(w * 0.56f, h * 0.58f), Size(w * 0.22f, h * 0.17f))
}
