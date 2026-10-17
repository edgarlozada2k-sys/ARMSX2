package com.armsx2.exkinet.ui.controls

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.exkinet.ui.ExkChoice
import com.armsx2.exkinet.ui.ExkPanel
import com.armsx2.exkinet.ui.ExkPrimaryButton
import com.armsx2.exkinet.ui.ExkRow
import com.armsx2.exkinet.ui.ExkSecondaryButton
import com.armsx2.exkinet.ui.ExkSlider
import com.armsx2.exkinet.ui.ExkSwitch
import com.armsx2.exkinet.ui.ExkToast
import com.armsx2.exkinet.ui.LocalExk
import com.armsx2.ui.touch.TouchButtonCfg
import com.armsx2.ui.touch.TouchButtonId
import com.armsx2.ui.touch.TouchControls
import com.armsx2.ui.touch.TouchLayout
import kotlin.math.roundToInt

private fun spanishLabel(id: TouchButtonId): String = when (id) {
    TouchButtonId.DPAD -> "Cruceta"
    TouchButtonId.L_STICK -> "Stick izquierdo"
    TouchButtonId.R_STICK -> "Stick derecho"
    TouchButtonId.PAUSE -> "Pausa"
    TouchButtonId.FAST_FORWARD -> "Avance rápido"
    TouchButtonId.SAVE_STATE -> "Guardado rápido"
    TouchButtonId.LOAD_STATE -> "Carga rápida"
    TouchButtonId.SCREENSHOT -> "Captura"
    TouchButtonId.PRESSURE -> "Presión ½"
    TouchButtonId.ANALOG_EXTRA -> "Extra analógico"
    TouchButtonId.CROSS -> "Cruz ✕"
    TouchButtonId.CIRCLE -> "Círculo ○"
    TouchButtonId.SQUARE -> "Cuadrado □"
    TouchButtonId.TRIANGLE -> "Triángulo △"
    else -> id.label
}

private val editable: List<TouchButtonId> = TouchButtonId.entries.filter { it.kind != TouchButtonId.Kind.GUN }

/**
 * «Editar distribución»: vista de juego de ejemplo donde se arrastran los controles, se cambia
 * tamaño, visibilidad y transparencia, y se prueba antes de guardar. Horizontal y vertical se
 * editan y guardan por separado. Ningún control puede quedar fuera de la zona segura.
 */
@Composable
fun ExkTouchEditor(serial: String?, gameTitle: String?, onClose: () -> Unit) {
    val c = LocalExk.current
    val deviceLandscape = LocalConfiguration.current.screenWidthDp >= LocalConfiguration.current.screenHeightDp
    var portrait by remember { mutableStateOf(!deviceLandscape) }
    // Una copia de trabajo por orientación: nada se guarda hasta pulsar «Guardar».
    val work = remember {
        mutableStateMapOf(
            false to TouchControls.exkLayoutFor(serial, false),
            true to TouchControls.exkLayoutFor(serial, true),
        )
    }
    val original = remember { mapOf(false to work[false]!!, true to work[true]!!) }
    var selected by remember { mutableStateOf<TouchButtonId?>(null) }
    var testMode by remember { mutableStateOf(false) }
    var opacity by remember { mutableStateOf(TouchControls.opacity.floatValue) }
    var showHidden by remember { mutableStateOf(true) }
    val pressed = remember { mutableStateMapOf<TouchButtonId, Boolean>() }
    var askExit by remember { mutableStateOf(false) }
    val layout = work[portrait]!!
    val dirty = work[false] != original[false] || work[true] != original[true] || opacity != TouchControls.opacity.floatValue

    fun update(id: TouchButtonId, f: (TouchButtonCfg) -> TouchButtonCfg) {
        // Se lee la copia vigente (no la de la última composición) para que el arrastre no salte.
        val cur = work[portrait] ?: return
        work[portrait] = TouchLayout(cur.buttons.map { if (it.id == id) f(it) else it })
    }

    fun save() {
        TouchControls.exkSaveLayout(serial, false, work[false]!!)
        TouchControls.exkSaveLayout(serial, true, work[true]!!)
        if (opacity != TouchControls.opacity.floatValue) TouchControls.setOpacity(opacity)
        ExkToast.show("Distribución guardada" + (if (serial != null) " para «$gameTitle»" else " (general)"))
        onClose()
    }

    BackHandler { if (dirty) askExit = true else onClose() }
    // Botón B del mando: llega por UiNavigator.back → ExkBack (la pila da prioridad a este editor).
    val dirtyNow = androidx.compose.runtime.rememberUpdatedState(dirty)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val h: () -> Boolean = { if (askExit) askExit = false else if (dirtyNow.value) askExit = true else onClose(); true }
        com.armsx2.exkinet.ui.ExkBack.push(h)
        onDispose { com.armsx2.exkinet.ui.ExkBack.pop(h) }
    }

    Row(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Vista de juego de ejemplo
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Editar distribución" + (gameTitle?.let { " · $it" } ?: " · general"), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                ExkChoice(listOf(false to "Horizontal", true to "Vertical"), portrait, { portrait = it; selected = null })
            }
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val ratio = if (portrait) 10f / 16f else 16f / 10f
                Box(
                    Modifier.aspectRatio(ratio).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF14324F), Color(0xFF070D15), Color(0xFF2B1A2E))))
                        .border(1.dp, c.panelBorder, RoundedCornerShape(14.dp)),
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
                        val density = LocalDensity.current
                        // Margen de seguridad: barra de gestos y esquinas redondeadas del sistema.
                        val marginX = wPx * 0.025f; val marginTop = hPx * 0.03f; val marginBottom = hPx * 0.06f
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(Color(0x22FFFFFF), topLeft = Offset(marginX, marginTop), size = androidx.compose.ui.geometry.Size(size.width - 2 * marginX, size.height - marginTop - marginBottom),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
                        }
                        Text("VISTA DE JUEGO DE EJEMPLO", color = Color(0x55FFFFFF), fontSize = 11.sp, letterSpacing = 3.sp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
                        layout.buttons.filter { it.id in editable && (it.enabled || showHidden) }.forEach { b ->
                            val sizePx = with(density) { b.sizeDp.dp.toPx() }
                            fun clampX(x: Float) = x.coerceIn((marginX + sizePx / 2) / wPx, 1f - (marginX + sizePx / 2) / wPx)
                            fun clampY(y: Float) = y.coerceIn((marginTop + sizePx / 2) / hPx, 1f - (marginBottom + sizePx / 2) / hPx)
                            val cx = clampX(b.xFrac) * wPx; val cy = clampY(b.yFrac) * hPx
                            val isSel = selected == b.id
                            val isPressed = pressed[b.id] == true
                            Box(
                                Modifier
                                    .offset { IntOffset((cx - sizePx / 2).roundToInt(), (cy - sizePx / 2).roundToInt()) }
                                    .size(b.sizeDp.dp)
                                    .alpha(if (!b.enabled) 0.25f else opacity.coerceAtLeast(0.25f))
                                    .clip(if (b.id.kind == TouchButtonId.Kind.SHOULDER || b.id.kind == TouchButtonId.Kind.MENU) RoundedCornerShape(12.dp) else CircleShape)
                                    .background(if (isPressed) c.glow else Color(0x33FFFFFF))
                                    .border(if (isSel) 3.dp else 1.5.dp, if (isSel) c.accent else Color(0x88FFFFFF),
                                        if (b.id.kind == TouchButtonId.Kind.SHOULDER || b.id.kind == TouchButtonId.Kind.MENU) RoundedCornerShape(12.dp) else CircleShape)
                                    .pointerInput(b.id, testMode, portrait, wPx, hPx) {
                                        if (testMode) {
                                            detectTapGestures(onPress = {
                                                pressed[b.id] = true
                                                tryAwaitRelease()
                                                pressed.remove(b.id)
                                            })
                                        } else {
                                            detectDragGestures(
                                                onDragStart = { selected = b.id },
                                                onDrag = { change, drag ->
                                                    change.consume()
                                                    update(b.id) { cur ->
                                                        cur.copy(xFrac = clampX(cur.xFrac + drag.x / wPx), yFrac = clampY(cur.yFrac + drag.y / hPx))
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    .pointerInput(b.id, testMode) { if (!testMode) detectTapGestures { selected = b.id } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(b.id.label, color = Color.White, fontSize = (b.sizeDp / 4.2f).coerceIn(9f, 22f).sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val pressedNames = pressed.keys.joinToString(" · ") { spanishLabel(it) }
            Text(
                if (testMode) (if (pressedNames.isEmpty()) "Modo prueba: toca los controles. Varios a la vez se iluminan juntos." else "Pulsado: $pressedNames")
                else "Arrastra un control para moverlo; tócalo para seleccionarlo. La línea discontinua es la zona segura.",
                color = if (testMode) c.glow else c.textMuted, fontSize = 12.sp,
            )
        }

        // Panel de ajustes
        Column(Modifier.width(360.dp).fillMaxHeight()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ExkPanel(padding = 18.dp) {
                    Column {
                        ExkRow("Probar distribución", detail = "Toca los controles para ver cómo responden, sin moverlos.") {
                            ExkSwitch(testMode, { testMode = it; pressed.clear() })
                        }
                        ExkRow("Transparencia · ${(opacity * 100).toInt()} %") {
                            ExkSlider(opacity, { opacity = it }, 0.1f..1f, Modifier.width(150.dp))
                        }
                        ExkRow("Mostrar controles ocultos", detail = "Los ocultos se ven atenuados para poder recuperarlos.") {
                            ExkSwitch(showHidden, { showHidden = it })
                        }
                        val sel = layout.buttons.firstOrNull { it.id == selected }
                        if (sel != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(spanishLabel(sel.id), color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            ExkRow("Tamaño · ${sel.sizeDp.toInt()} dp") {
                                ExkSlider(sel.sizeDp, { v -> update(sel.id) { it.copy(sizeDp = v) } }, 36f..220f, Modifier.width(150.dp))
                            }
                            ExkRow("Visible") { ExkSwitch(sel.enabled, { on -> update(sel.id) { it.copy(enabled = on) } }) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExkSecondaryButton("◀", { update(sel.id) { it.copy(xFrac = (it.xFrac - 0.01f).coerceIn(0f, 1f)) } }, compact = true)
                                ExkSecondaryButton("▲", { update(sel.id) { it.copy(yFrac = (it.yFrac - 0.01f).coerceIn(0f, 1f)) } }, compact = true)
                                ExkSecondaryButton("▼", { update(sel.id) { it.copy(yFrac = (it.yFrac + 0.01f).coerceIn(0f, 1f)) } }, compact = true)
                                ExkSecondaryButton("▶", { update(sel.id) { it.copy(xFrac = (it.xFrac + 0.01f).coerceIn(0f, 1f)) } }, compact = true)
                            }
                            Text("Las flechas mueven el control con precisión (útil con teclado o mando).", color = c.textMuted, fontSize = 11.sp)
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text("Selecciona un control para cambiar su tamaño o visibilidad.", color = c.textMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("SEPARACIÓN Y POSICIÓN DE GRUPOS", color = c.accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExkSecondaryButton("Juntar botones", { spread(work, portrait, 0.94f) }, compact = true)
                            ExkSecondaryButton("Separar", { spread(work, portrait, 1.06f) }, compact = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("CONTROLES", color = c.accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        layout.buttons.filter { it.id in editable }.forEach { b ->
                            ExkRow(spanishLabel(b.id)) { ExkSwitch(b.enabled, { on -> update(b.id) { it.copy(enabled = on) } }) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExkSecondaryButton("Restaurar", {
                    work[portrait] = TouchLayout.defaultFor(portrait); selected = null
                    ExkToast.show("Distribución ${if (portrait) "vertical" else "horizontal"} restaurada (guarda para aplicarla)")
                }, compact = true)
                ExkSecondaryButton("Cancelar", onClose, compact = true)
                ExkPrimaryButton("Guardar", { save() }, minWidth = 110.dp, enabled = dirty)
            }
        }
    }

    if (askExit) ExkDialog(
        title = "Distribución sin guardar",
        body = "¿Guardar los cambios de la distribución táctil?",
        actions = listOf("Guardar" to { askExit = false; save() }, "Descartar" to { askExit = false; onClose() }, "Seguir editando" to { askExit = false }),
    )
}

/** Acerca o separa cada lado de la pantalla respecto a su ancla (izquierda o derecha). */
private fun spread(work: MutableMap<Boolean, TouchLayout>, portrait: Boolean, factor: Float) {
    val l = work[portrait] ?: return
    work[portrait] = TouchLayout(l.buttons.map { b ->
        val anchorX = if (b.xFrac < 0.5f) 0.18f else 0.82f
        val anchorY = 0.62f
        b.copy(
            xFrac = (anchorX + (b.xFrac - anchorX) * factor).coerceIn(0.03f, 0.97f),
            yFrac = (anchorY + (b.yFrac - anchorY) * factor).coerceIn(0.04f, 0.93f),
        )
    })
}
