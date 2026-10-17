package com.armsx2.exkinet.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.armsx2.exkinet.input.Ps2Button
import com.armsx2.exkinet.ui.LocalExk
import kotlin.math.hypot

/**
 * Esquema de un mando de PS2. [selected] resalta el botón que se está asignando;
 * [pressed] ilumina lo que llega en «Probar controles»; los sticks muestran su posición.
 */
@Composable
fun Ps2Diagram(
    selected: Ps2Button?,
    pressed: Set<Ps2Button>,
    leftStick: Pair<Float, Float> = 0f to 0f,
    rightStick: Pair<Float, Float> = 0f to 0f,
    modifier: Modifier = Modifier,
    onTap: ((Ps2Button) -> Unit)? = null,
) {
    val c = LocalExk.current
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.75f)
            .semantics { contentDescription = "Esquema del mando de PS2" + (selected?.let { ", ${it.label} seleccionado" } ?: "") }
            .pointerInput(onTap) {
                if (onTap != null) detectTapGestures { p ->
                    val nx = p.x / size.width; val ny = p.y / size.height
                    hitRegions.minByOrNull { (_, r) -> hypot((r.x - nx) * 1.75f, r.y - ny) }
                        ?.takeIf { (_, r) -> hypot((r.x - nx) * 1.75f, r.y - ny) < r.radius * 1.6f }
                        ?.let { onTap(it.first) }
                }
            },
    ) {
        val w = size.width; val h = size.height
        val body = c.bgAlt
        val edge = c.text.copy(alpha = 0.22f)
        val idle = c.text.copy(alpha = 0.10f)
        val sel = c.accent
        val hot = c.glow

        fun fillFor(b: Ps2Button) = when {
            b in pressed -> hot
            b == selected -> sel.copy(alpha = 0.85f)
            else -> idle
        }
        fun strokeFor(b: Ps2Button) = when {
            b in pressed -> hot
            b == selected -> sel
            else -> edge
        }

        // Hombros (dibujados primero, quedan detrás del cuerpo).
        shoulder(Ps2Button.L2, 0.13f, 0.06f, fillFor(Ps2Button.L2), strokeFor(Ps2Button.L2), "L2", c.text)
        shoulder(Ps2Button.L1, 0.13f, 0.15f, fillFor(Ps2Button.L1), strokeFor(Ps2Button.L1), "L1", c.text)
        shoulder(Ps2Button.R2, 0.67f, 0.06f, fillFor(Ps2Button.R2), strokeFor(Ps2Button.R2), "R2", c.text)
        shoulder(Ps2Button.R1, 0.67f, 0.15f, fillFor(Ps2Button.R1), strokeFor(Ps2Button.R1), "R1", c.text)

        // Cuerpo con empuñaduras.
        val bodyPath = Path().apply {
            moveTo(w * 0.12f, h * 0.24f)
            cubicTo(w * 0.30f, h * 0.19f, w * 0.70f, h * 0.19f, w * 0.88f, h * 0.24f)
            cubicTo(w * 0.99f, h * 0.30f, w * 1.00f, h * 0.80f, w * 0.93f, h * 0.95f)
            cubicTo(w * 0.86f, h * 1.02f, w * 0.75f, h * 0.95f, w * 0.68f, h * 0.78f)
            lineTo(w * 0.32f, h * 0.78f)
            cubicTo(w * 0.25f, h * 0.95f, w * 0.14f, h * 1.02f, w * 0.07f, h * 0.95f)
            cubicTo(w * 0.00f, h * 0.80f, w * 0.01f, h * 0.30f, w * 0.12f, h * 0.24f)
            close()
        }
        drawPath(bodyPath, body)
        drawPath(bodyPath, edge, style = Stroke(2f))

        // Cruceta.
        val dx = w * 0.20f; val dy = h * 0.43f; val arm = w * 0.036f; val len = w * 0.05f
        dpadArm(Ps2Button.DPAD_UP, Offset(dx - arm / 2, dy - len - arm / 2), Size(arm, len), fillFor(Ps2Button.DPAD_UP), strokeFor(Ps2Button.DPAD_UP))
        dpadArm(Ps2Button.DPAD_DOWN, Offset(dx - arm / 2, dy + arm / 2), Size(arm, len), fillFor(Ps2Button.DPAD_DOWN), strokeFor(Ps2Button.DPAD_DOWN))
        dpadArm(Ps2Button.DPAD_LEFT, Offset(dx - len - arm / 2, dy - arm / 2), Size(len, arm), fillFor(Ps2Button.DPAD_LEFT), strokeFor(Ps2Button.DPAD_LEFT))
        dpadArm(Ps2Button.DPAD_RIGHT, Offset(dx + arm / 2, dy - arm / 2), Size(len, arm), fillFor(Ps2Button.DPAD_RIGHT), strokeFor(Ps2Button.DPAD_RIGHT))

        // Botones frontales con sus símbolos.
        val fx = w * 0.80f; val fy = h * 0.43f; val gap = w * 0.055f; val r = w * 0.027f
        face(Ps2Button.TRIANGLE, Offset(fx, fy - gap), r, fillFor(Ps2Button.TRIANGLE), strokeFor(Ps2Button.TRIANGLE), Color(0xFF5EE0A0))
        face(Ps2Button.CIRCLE, Offset(fx + gap, fy), r, fillFor(Ps2Button.CIRCLE), strokeFor(Ps2Button.CIRCLE), Color(0xFFFF6B7A))
        face(Ps2Button.CROSS, Offset(fx, fy + gap), r, fillFor(Ps2Button.CROSS), strokeFor(Ps2Button.CROSS), Color(0xFF7AB8FF))
        face(Ps2Button.SQUARE, Offset(fx - gap, fy), r, fillFor(Ps2Button.SQUARE), strokeFor(Ps2Button.SQUARE), Color(0xFFFF9EDC))

        // Select, Start y Analog.
        pill(Offset(w * 0.40f, h * 0.43f), fillFor(Ps2Button.SELECT), strokeFor(Ps2Button.SELECT))
        pill(Offset(w * 0.60f, h * 0.43f), fillFor(Ps2Button.START), strokeFor(Ps2Button.START))
        drawCircle(fillFor(Ps2Button.ANALOG), w * 0.014f, Offset(w * 0.5f, h * 0.55f))
        drawCircle(strokeFor(Ps2Button.ANALOG), w * 0.014f, Offset(w * 0.5f, h * 0.55f), style = Stroke(2f))

        // Sticks: cuatro sectores (direcciones), el centro es L3/R3, el punto marca la posición.
        stick(Offset(w * 0.34f, h * 0.66f), w * 0.07f, leftStick,
            listOf(Ps2Button.LS_UP, Ps2Button.LS_RIGHT, Ps2Button.LS_DOWN, Ps2Button.LS_LEFT), Ps2Button.L3, ::fillFor, ::strokeFor, edge, hot)
        stick(Offset(w * 0.66f, h * 0.66f), w * 0.07f, rightStick,
            listOf(Ps2Button.RS_UP, Ps2Button.RS_RIGHT, Ps2Button.RS_DOWN, Ps2Button.RS_LEFT), Ps2Button.R3, ::fillFor, ::strokeFor, edge, hot)
    }
}

/** Centros normalizados para tocar el esquema y elegir un botón. */
private data class Region(val x: Float, val y: Float, val radius: Float)

private val hitRegions: List<Pair<Ps2Button, Region>> = listOf(
    Ps2Button.L2 to Region(0.23f, 0.08f, 0.04f), Ps2Button.L1 to Region(0.23f, 0.17f, 0.04f),
    Ps2Button.R2 to Region(0.77f, 0.08f, 0.04f), Ps2Button.R1 to Region(0.77f, 0.17f, 0.04f),
    Ps2Button.DPAD_UP to Region(0.20f, 0.33f, 0.035f), Ps2Button.DPAD_DOWN to Region(0.20f, 0.53f, 0.035f),
    Ps2Button.DPAD_LEFT to Region(0.15f, 0.43f, 0.035f), Ps2Button.DPAD_RIGHT to Region(0.25f, 0.43f, 0.035f),
    Ps2Button.TRIANGLE to Region(0.80f, 0.33f, 0.035f), Ps2Button.CIRCLE to Region(0.855f, 0.43f, 0.035f),
    Ps2Button.CROSS to Region(0.80f, 0.53f, 0.035f), Ps2Button.SQUARE to Region(0.745f, 0.43f, 0.035f),
    Ps2Button.SELECT to Region(0.40f, 0.43f, 0.03f), Ps2Button.START to Region(0.60f, 0.43f, 0.03f),
    Ps2Button.ANALOG to Region(0.50f, 0.55f, 0.025f),
    Ps2Button.L3 to Region(0.34f, 0.66f, 0.02f), Ps2Button.R3 to Region(0.66f, 0.66f, 0.02f),
    Ps2Button.LS_UP to Region(0.34f, 0.55f, 0.03f), Ps2Button.LS_DOWN to Region(0.34f, 0.77f, 0.03f),
    Ps2Button.LS_LEFT to Region(0.30f, 0.66f, 0.03f), Ps2Button.LS_RIGHT to Region(0.38f, 0.66f, 0.03f),
    Ps2Button.RS_UP to Region(0.66f, 0.55f, 0.03f), Ps2Button.RS_DOWN to Region(0.66f, 0.77f, 0.03f),
    Ps2Button.RS_LEFT to Region(0.62f, 0.66f, 0.03f), Ps2Button.RS_RIGHT to Region(0.70f, 0.66f, 0.03f),
)

private fun DrawScope.shoulder(@Suppress("UNUSED_PARAMETER") b: Ps2Button, x: Float, y: Float, fill: Color, stroke: Color, @Suppress("UNUSED_PARAMETER") label: String, @Suppress("UNUSED_PARAMETER") text: Color) {
    val tl = Offset(size.width * x, size.height * y)
    val s = Size(size.width * 0.20f, size.height * 0.075f)
    drawRoundRect(fill, tl, s, CornerRadius(s.height / 2))
    drawRoundRect(stroke, tl, s, CornerRadius(s.height / 2), style = Stroke(2f))
}

private fun DrawScope.dpadArm(@Suppress("UNUSED_PARAMETER") b: Ps2Button, tl: Offset, s: Size, fill: Color, stroke: Color) {
    drawRoundRect(fill, tl, s, CornerRadius(4f))
    drawRoundRect(stroke, tl, s, CornerRadius(4f), style = Stroke(2f))
}

private fun DrawScope.face(b: Ps2Button, center: Offset, r: Float, fill: Color, stroke: Color, symbol: Color) {
    drawCircle(fill, r, center)
    drawCircle(stroke, r, center, style = Stroke(2f))
    val s = r * 0.5f
    val st = Stroke(r * 0.16f)
    when (b) {
        Ps2Button.CROSS -> {
            drawLine(symbol, center + Offset(-s, -s), center + Offset(s, s), st.width)
            drawLine(symbol, center + Offset(-s, s), center + Offset(s, -s), st.width)
        }
        Ps2Button.CIRCLE -> drawCircle(symbol, s, center, style = st)
        Ps2Button.SQUARE -> drawRect(symbol, center - Offset(s, s), Size(2 * s, 2 * s), style = st)
        Ps2Button.TRIANGLE -> drawPath(Path().apply {
            moveTo(center.x, center.y - s); lineTo(center.x + s, center.y + s * 0.8f); lineTo(center.x - s, center.y + s * 0.8f); close()
        }, symbol, style = st)
        else -> Unit
    }
}

private fun DrawScope.pill(center: Offset, fill: Color, stroke: Color) {
    val s = Size(size.width * 0.05f, size.height * 0.04f)
    val tl = center - Offset(s.width / 2, s.height / 2)
    drawRoundRect(fill, tl, s, CornerRadius(s.height / 2))
    drawRoundRect(stroke, tl, s, CornerRadius(s.height / 2), style = Stroke(2f))
}

private fun DrawScope.stick(
    center: Offset, radius: Float, pos: Pair<Float, Float>, dirs: List<Ps2Button>, click: Ps2Button,
    fillFor: (Ps2Button) -> Color, strokeFor: (Ps2Button) -> Color, edge: Color, hot: Color,
) {
    // Arriba, derecha, abajo, izquierda → ángulos de inicio en grados (0 = derecha).
    val starts = listOf(225f, 315f, 45f, 135f)
    dirs.forEachIndexed { i, b ->
        drawArc(fillFor(b), starts[i], 90f, true, center - Offset(radius, radius), Size(radius * 2, radius * 2))
    }
    drawCircle(edge, radius, center, style = Stroke(2f))
    dirs.forEachIndexed { i, b ->
        if (strokeFor(b) != edge) drawArc(strokeFor(b), starts[i], 90f, false, center - Offset(radius, radius), Size(radius * 2, radius * 2), style = Stroke(4f))
    }
    val knob = center + Offset(pos.first * radius * 0.6f, pos.second * radius * 0.6f)
    drawCircle(fillFor(click).let { if (it == hot) it else Color(0xFF1B2A3B) }, radius * 0.42f, knob)
    drawCircle(strokeFor(click), radius * 0.42f, knob, style = Stroke(2f))
}
