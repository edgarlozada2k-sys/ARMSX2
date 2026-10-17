package com.armsx2.exkinet.ui.controls

import android.content.Context
import android.hardware.input.InputManager
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.GameInfo
import com.armsx2.exkinet.input.ControlsDraft
import com.armsx2.exkinet.input.ExkInput
import com.armsx2.exkinet.input.Ps2Button
import com.armsx2.exkinet.ui.ExkBadge
import com.armsx2.exkinet.ui.ExkChoice
import com.armsx2.exkinet.ui.ExkPanel
import com.armsx2.exkinet.ui.ExkPill
import com.armsx2.exkinet.ui.ExkPrimaryButton
import com.armsx2.exkinet.ui.ExkRow
import com.armsx2.exkinet.ui.ExkSecondaryButton
import com.armsx2.exkinet.ui.ExkSlider
import com.armsx2.exkinet.ui.ExkSwitch
import com.armsx2.exkinet.ui.ExkToast
import com.armsx2.exkinet.ui.LocalExk
import com.armsx2.exkinet.ui.exkFocusRing
import com.armsx2.exkinet.ui.exkNav
import com.armsx2.exkinet.ui.ExkBack
import com.armsx2.input.ControllerMappings
import com.armsx2.input.PadRouter

enum class ControlsTab(val title: String) { KEYBOARD("Teclado"), CONTROLLER("Mando"), TOUCH("Pantalla táctil") }

/** Petición de captura en curso, mostrada como «Pulsa la nueva tecla». */
private data class CaptureRequest(
    val table: InputTable,
    val button: Ps2Button?,
    val hotkey: ControllerMappings.SysHotkey? = null,
    val wizardIndex: Int = -1,
)

private data class PendingConflict(
    val table: InputTable, val button: Ps2Button, val code: Int, val conflicts: List<Conflict>, val wizardIndex: Int,
)

@Composable
fun ExkControlsScreen(game: GameInfo?, onBack: () -> Unit) {
    val c = LocalExk.current
    val context = LocalContext.current
    var tab by remember { mutableStateOf(ControlsTab.KEYBOARD) }
    var perGame by remember { mutableStateOf(false) }
    val serial = if (perGame) game?.serial?.takeIf { it.isNotBlank() } else null
    var player by remember { mutableIntStateOf(ControllerMappings.P1) }
    var selected by remember { mutableStateOf<Ps2Button?>(null) }
    var capture by remember { mutableStateOf<CaptureRequest?>(null) }
    var conflict by remember { mutableStateOf<PendingConflict?>(null) }
    var askExit by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    var askUseGeneral by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    // El editor táctil se abre encima (sin salir de la pantalla) para no perder la instantánea de Cancelar.
    var editingTouch by remember { mutableStateOf<String?>(null) }
    var editingTouchOpen by remember { mutableStateOf(false) }
    // Se incrementa con cada cambio para recomponer las listas (las asignaciones viven en prefs).
    var tick by remember { mutableIntStateOf(0) }
    val bump = { tick++; ControlsDraft.markEdited() }

    DisposableEffect(Unit) {
        ControlsDraft.begin()
        // Botón B / Atrás: cancela la captura en curso o pregunta por los cambios sin guardar.
        ExkBack.handler = {
            when {
                capture != null -> { ExkInput.cancelCapture(); capture = null; true }
                conflict != null -> { conflict = null; true }
                askExit || askReset || askUseGeneral -> { askExit = false; askReset = false; askUseGeneral = false; true }
                ControlsDraft.hasChanges() -> { askExit = true; true }
                else -> false
            }
        }
        onDispose {
            ExkBack.handler = null
            ExkInput.cancelCapture()
            ExkInput.testActive.value = false
            ExkInput.clearTest()
            ControlsDraft.end()
        }
    }
    LaunchedEffect(testing, serial) {
        ExkInput.testSerial = serial
        ExkInput.testActive.value = testing
        if (!testing) ExkInput.clearTest()
    }

    fun requestExit() { if (ControlsDraft.hasChanges()) askExit = true else onBack() }
    BackHandler(enabled = capture == null) { requestExit() }

    // Inicia la captura real en la capa de entrada.
    fun startCapture(req: CaptureRequest) {
        capture = req
        selected = req.button
        val kind = when {
            req.table == InputTable.KEYBOARD -> ExkInput.CaptureKind.KEYBOARD
            req.button?.group == Ps2Button.Group.LEFT_STICK || req.button?.group == Ps2Button.Group.RIGHT_STICK -> ExkInput.CaptureKind.CONTROLLER_ANY
            else -> ExkInput.CaptureKind.CONTROLLER_BUTTON
        }
        ExkInput.beginCapture(kind) { got ->
            val r = capture
            capture = null
            if (got == null || r == null) return@beginCapture
            if (r.hotkey != null && got is ExkInput.Captured.Key) {
                ControllerMappings.bindHotkey(r.hotkey, got.keyCode)
                ExkToast.show("«${hotkeyLabel(r.hotkey)}» → ${got.label}")
                bump(); return@beginCapture
            }
            val b = r.button ?: return@beginCapture
            when (got) {
                is ExkInput.Captured.Axis -> {
                    // Mover el mismo stick en la dirección pedida = dejarlo analógico (lo normal).
                    val wantsLeft = b.group == Ps2Button.Group.LEFT_STICK
                    val want = b.actionId.substringAfter('_')
                    if (got.left == wantsLeft && got.dir.id == want) {
                        ControlsLogic.clear(r.table, b, player, serial)
                        ExkToast.show("${b.label}: stick analógico (asignación estándar)")
                        bump()
                        if (r.wizardIndex >= 0) nextWizard(r.wizardIndex, r.table) { startCapture(it) }
                    } else {
                        ExkToast.show("No compatible: ARMSX2 lee cada stick físico como su stick de PS2. Mueve el stick ${if (wantsLeft) "izquierdo" else "derecho"} o pulsa un botón.")
                        startCapture(r)
                    }
                }
                is ExkInput.Captured.Key -> {
                    val conflicts = ControlsLogic.conflictsFor(r.table, b, got.keyCode, player, serial)
                    if (conflicts.isEmpty()) {
                        ControlsLogic.assign(r.table, b, got.keyCode, player, serial)
                        ExkToast.show("${b.label} → ${got.label}")
                        bump()
                        if (r.wizardIndex >= 0) nextWizard(r.wizardIndex, r.table) { startCapture(it) }
                    } else conflict = PendingConflict(r.table, b, got.keyCode, conflicts, r.wizardIndex)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.screenBrush)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 36.dp, vertical = 20.dp)) {
            // Cabecera
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EXKINET / AJUSTES / CONTROLES", color = c.accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("Controles", color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                ExkSecondaryButton("← Volver", onClick = { requestExit() }, navId = "exk.ctl.back")
            }
            Spacer(Modifier.height(14.dp))
            // Pestañas y ámbito
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlsTab.entries.forEach { t -> ExkPill(t.title, tab == t, { tab = t; selected = null }, navId = "exk.ctl.tab.${t.name}") }
                Spacer(Modifier.weight(1f))
                ScopeSelector(game, perGame, onChange = { perGame = it; selected = null; tick++ })
            }
            if (perGame && serial != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val own = remember(tick, serial) { ControlsLogic.gameHasProfile(serial) }
                    Text(
                        if (own) "Este juego tiene su propio perfil. Lo que no cambies aquí se hereda de la configuración general."
                        else "Este juego usa la configuración general. Cualquier cambio creará su perfil propio.",
                        color = c.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    if (own) ExkSecondaryButton("Volver a usar la configuración general", { askUseGeneral = true }, compact = true)
                }
            } else if (perGame) {
                Text("Este juego no tiene serial reconocido: solo puede usar la configuración general.", color = c.warning, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Columna izquierda: esquema + prueba
                Column(Modifier.weight(0.46f).verticalScroll(rememberScrollState())) {
                    val pressedButtons = ExkInput.testPressed.keys.mapNotNull { Ps2Button.forTarget(it) }.toSet()
                    ExkPanel(padding = 18.dp) {
                        Column {
                            Ps2Diagram(
                                selected = selected, pressed = pressedButtons,
                                leftStick = ExkInput.testLeftStick.value, rightStick = ExkInput.testRightStick.value,
                                onTap = { b -> if (tab != ControlsTab.TOUCH) { selected = b } },
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                selected?.let { "Seleccionado: ${it.label}. Toca su fila para cambiarlo." } ?: "Toca un botón del esquema o de la lista.",
                                color = c.textMuted, fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TestPanel(testing, onToggle = { testing = it }, tab = tab)
                }
                // Columna derecha: asignaciones de la pestaña
                Column(Modifier.weight(0.54f).verticalScroll(rememberScrollState())) {
                    when (tab) {
                        ControlsTab.KEYBOARD -> KeyboardTab(serial, tick, selected,
                            onSelect = { selected = it },
                            onCapture = { b -> startCapture(CaptureRequest(InputTable.KEYBOARD, b)) },
                            onCaptureHotkey = { h -> startCapture(CaptureRequest(InputTable.KEYBOARD, null, h)) },
                            onClear = { b -> ControlsLogic.clear(InputTable.KEYBOARD, b, player, serial); bump() },
                            onClearHotkey = { h -> ControllerMappings.clearHotkey(h); bump() },
                            onWizard = { startCapture(CaptureRequest(InputTable.KEYBOARD, Ps2Button.wizardOrder[0], wizardIndex = 0)) },
                            onChanged = bump,
                        )
                        ControlsTab.CONTROLLER -> ControllerTab(context, serial, player, tick, selected,
                            onPlayer = { player = it; tick++ },
                            onSelect = { selected = it },
                            onCapture = { b -> startCapture(CaptureRequest(InputTable.CONTROLLER, b)) },
                            onCaptureHotkey = { h -> startCapture(CaptureRequest(InputTable.CONTROLLER, null, h)) },
                            onClear = { b -> ControlsLogic.clear(InputTable.CONTROLLER, b, player, serial); bump() },
                            onClearHotkey = { h -> ControllerMappings.clearHotkey(h); bump() },
                            onWizard = { startCapture(CaptureRequest(InputTable.CONTROLLER, Ps2Button.wizardOrder[0], wizardIndex = 0)) },
                            onChanged = bump,
                        )
                        ControlsTab.TOUCH -> TouchTab(serial, tick, onEdit = { editingTouch = serial; editingTouchOpen = true }, onChanged = bump)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Barra inferior
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val dirty = remember(tick) { ControlsDraft.hasChanges() }
                Text(
                    if (dirty) "Cambios sin guardar · ya se aplican en la prueba" else "Sin cambios pendientes",
                    color = if (dirty) c.warning else c.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                ExkSecondaryButton("Restablecer", { askReset = true }, navId = "exk.ctl.reset")
                ExkSecondaryButton("Cancelar", { ControlsDraft.revert(); tick++; ExkToast.show("Cambios descartados") }, enabled = dirty, navId = "exk.ctl.cancel")
                ExkPrimaryButton("Guardar cambios", { ControlsDraft.commit(); tick++; ExkToast.show("Controles guardados") }, enabled = dirty, navId = "exk.ctl.save")
            }
        }

        capture?.let { req -> CaptureOverlay(req, onCancel = { ExkInput.cancelCapture(); capture = null },
            onClear = req.button?.let { b -> { ExkInput.cancelCapture(); capture = null; ControlsLogic.clear(req.table, b, player, serial); bump() } },
            onSkip = if (req.wizardIndex >= 0) ({ ExkInput.cancelCapture(); capture = null; nextWizard(req.wizardIndex, req.table) { startCapture(it) } }) else null) }

        if (editingTouchOpen) {
            ExkTouchEditor(editingTouch, if (editingTouch != null) game?.title else null, onClose = { editingTouchOpen = false; bump() })
        }

        conflict?.let { pc ->
            ConflictDialog(pc,
                onReplace = {
                    ControlsLogic.resolve(pc.table, pc.button, pc.code, player, serial, pc.conflicts, keepBoth = false)
                    conflict = null; bump(); ExkToast.show("Asignación reemplazada")
                    if (pc.wizardIndex >= 0) nextWizard(pc.wizardIndex, pc.table) { startCapture(it) }
                },
                onKeepBoth = if (pc.conflicts.all { it.canKeepBoth }) ({
                    ControlsLogic.resolve(pc.table, pc.button, pc.code, player, serial, pc.conflicts, keepBoth = true)
                    conflict = null; bump(); ExkToast.show("Se conservan ambas asignaciones")
                    if (pc.wizardIndex >= 0) nextWizard(pc.wizardIndex, pc.table) { startCapture(it) }
                }) else null,
                onCancel = { conflict = null },
            )
        }

        if (askExit) ExkDialog(
            title = "Tienes cambios sin guardar",
            body = "¿Quieres guardarlos antes de salir?",
            actions = listOf(
                "Guardar" to { ControlsDraft.commit(); askExit = false; onBack() },
                "Descartar" to { ControlsDraft.revert(); askExit = false; onBack() },
                "Seguir editando" to { askExit = false },
            ),
        )
        if (askReset) ExkDialog(
            title = "Restablecer controles",
            body = "Elige qué quieres devolver a los valores predeterminados" +
                (if (serial != null) " para «${game?.title}»." else " de la configuración general.") +
                " Podrás deshacerlo con «Cancelar» mientras no guardes.",
            actions = listOf(
                "Solo «${tab.title}»" to {
                    when (tab) {
                        ControlsTab.KEYBOARD -> ControlsLogic.resetKeyboard(serial)
                        ControlsTab.CONTROLLER -> ControlsLogic.resetController(player, serial)
                        ControlsTab.TOUCH -> ControlsLogic.resetTouch(serial)
                    }
                    askReset = false; bump()
                },
                "Perfil completo" to {
                    ControlsLogic.resetKeyboard(serial)
                    ControlsLogic.resetController(ControllerMappings.P1, serial)
                    ControlsLogic.resetController(ControllerMappings.P2, serial)
                    ControlsLogic.resetTouch(serial)
                    askReset = false; bump()
                },
                "Cancelar" to { askReset = false },
            ),
        )
        if (askUseGeneral && serial != null) ExkDialog(
            title = "Volver a la configuración general",
            body = "Se borrará el perfil propio de «${game?.title}» (teclado, mando y táctil) y el juego heredará la configuración general.",
            actions = listOf(
                "Usar la general" to { ControlsLogic.clearGameProfile(serial); askUseGeneral = false; bump(); ExkToast.show("El juego vuelve a usar la configuración general") },
                "Cancelar" to { askUseGeneral = false },
            ),
        )
    }
}

private fun nextWizard(index: Int, table: InputTable, start: (CaptureRequest) -> Unit) {
    val next = index + 1
    if (next < Ps2Button.wizardOrder.size) start(CaptureRequest(table, Ps2Button.wizardOrder[next], wizardIndex = next))
    else ExkToast.show("Configuración paso a paso terminada")
}

@Composable
private fun ScopeSelector(game: GameInfo?, perGame: Boolean, onChange: (Boolean) -> Unit) {
    val options = buildList {
        add(false to "Configuración general")
        if (game != null) add(true to "Solo para este juego · ${game.title.take(28)}")
    }
    ExkChoice(options, perGame, onChange, navBase = "exk.ctl.scope")
}

@Composable
private fun TestPanel(testing: Boolean, onToggle: (Boolean) -> Unit, tab: ControlsTab) {
    val c = LocalExk.current
    ExkPanel(padding = 20.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Probar controles", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (tab == ControlsTab.TOUCH) "Para la pantalla táctil, prueba los botones dentro de «Editar distribución»."
                        else "Pulsa teclas, botones o mueve los sticks: se traducen con las mismas asignaciones que recibe el emulador.",
                        color = c.textMuted, fontSize = 12.sp,
                    )
                }
                ExkSwitch(testing, onToggle, enabled = tab != ControlsTab.TOUCH, navId = "exk.ctl.test")
            }
            if (testing) {
                Spacer(Modifier.height(10.dp))
                val last = ExkInput.testLastInput.value
                Text(if (last.isEmpty()) "Esperando entrada…" else last, color = c.glow, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val pressed = ExkInput.testPressed.entries.toList()
                if (pressed.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Pulsados a la vez: " + pressed.joinToString(" · ") { ExkInput.ps2Label(it.key) }, color = c.textSecondary, fontSize = 12.sp)
                }
                Text("Mientras la prueba está activa, las teclas no mueven la interfaz. Pulsa Atrás para salir.", color = c.textMuted, fontSize = 11.sp)
            }
        }
    }
}

// --------------------------------------------------------------------------- Teclado

@Composable
private fun KeyboardTab(
    serial: String?, tick: Int, selected: Ps2Button?,
    onSelect: (Ps2Button) -> Unit, onCapture: (Ps2Button) -> Unit, onCaptureHotkey: (ControllerMappings.SysHotkey) -> Unit,
    onClear: (Ps2Button) -> Unit, onClearHotkey: (ControllerMappings.SysHotkey) -> Unit, onWizard: () -> Unit, onChanged: () -> Unit,
) {
    val c = LocalExk.current
    @Suppress("UNUSED_EXPRESSION") tick
    ExkPanel {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Botones de PS2 en el teclado", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Un teclado físico tiene su propia tabla, independiente del mando.", color = c.textMuted, fontSize = 12.sp)
                }
                ExkSecondaryButton("Configurar paso a paso", onWizard, compact = true, navId = "exk.ctl.kb.wizard")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("El teclado juega como", color = c.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.width(12.dp))
                ExkChoice(listOf(ControllerMappings.P1 to "Jugador 1", ControllerMappings.P2 to "Jugador 2"), ExkInput.keyboardPlayer(),
                    { ExkInput.setKeyboardPlayer(it); onChanged() })
            }
            BindingList(InputTable.KEYBOARD, serial, ControllerMappings.P1, selected, onSelect, onCapture, onClear)
            HotkeySection(onCaptureHotkey, onClearHotkey)
        }
    }
}

// --------------------------------------------------------------------------- Mando

@Composable
private fun ControllerTab(
    context: Context, serial: String?, player: Int, tick: Int, selected: Ps2Button?,
    onPlayer: (Int) -> Unit, onSelect: (Ps2Button) -> Unit, onCapture: (Ps2Button) -> Unit,
    onCaptureHotkey: (ControllerMappings.SysHotkey) -> Unit, onClear: (Ps2Button) -> Unit,
    onClearHotkey: (ControllerMappings.SysHotkey) -> Unit, onWizard: () -> Unit, onChanged: () -> Unit,
) {
    val c = LocalExk.current
    var pads by remember { mutableStateOf(PadRouter.connectedPads()) }
    // Lista de mandos en vivo: conectar o desconectar la refresca sin salir de la pantalla.
    DisposableEffect(Unit) {
        val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val l = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(id: Int) { pads = PadRouter.connectedPads() }
            override fun onInputDeviceRemoved(id: Int) { pads = PadRouter.connectedPads() }
            override fun onInputDeviceChanged(id: Int) { pads = PadRouter.connectedPads() }
        }
        im.registerInputDeviceListener(l, null)
        onDispose { im.unregisterInputDeviceListener(l) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExkPanel {
            Column {
                Text("Mandos conectados", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (pads.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("No hay ningún mando conectado. Conéctalo por Bluetooth o USB; tu perfil se conserva aunque lo desconectes.",
                        color = c.warning, fontSize = 13.sp)
                }
                val pins = remember(tick, pads) { PadRouter.pins() }
                pads.forEach { pad ->
                    ExkRow(pad.name, detail = "Se reconoce automáticamente; los botones estándar de Android se asignan solos.") {
                        val current = pins[pad.descriptor]
                        ExkChoice<Int?>(listOf(null to "Auto", 0 to "J1", 1 to "J2"), current,
                            { PadRouter.setPin(pad.descriptor, it); onChanged() }, navBase = "exk.ctl.pin.${pad.descriptor.hashCode()}")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Al desconectar un mando, ExKinet suelta todos sus botones y sticks para que nada quede pulsado.", color = c.textMuted, fontSize = 11.sp)
            }
        }
        ExkPanel {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Asignación del mando", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Toca una fila y pulsa el botón o mueve el eje que quieras usar.", color = c.textMuted, fontSize = 12.sp)
                    }
                    ExkChoice(listOf(ControllerMappings.P1 to "Jugador 1", ControllerMappings.P2 to "Jugador 2"), player, onPlayer)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExkSecondaryButton("Configurar paso a paso", onWizard, compact = true, navId = "exk.ctl.pad.wizard")
                    ExkSecondaryButton("Asignación automática", {
                        ControlsLogic.resetController(player, serial); onChanged()
                        ExkToast.show("Asignación estándar de Android restaurada para el jugador ${player + 1}")
                    }, compact = true, navId = "exk.ctl.pad.auto")
                }
                BindingList(InputTable.CONTROLLER, serial, player, selected, onSelect, onCapture, onClear)
            }
        }
        StickPanel(serial, tick, onChanged)
        ExkPanel { HotkeySection(onCaptureHotkey, onClearHotkey) }
    }
}

@Composable
private fun StickPanel(serial: String?, tick: Int, onChanged: () -> Unit) {
    val c = LocalExk.current
    @Suppress("UNUSED_EXPRESSION") tick
    ExkPanel {
        Column {
            Text("Sticks", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            for (left in listOf(true, false)) {
                val name = if (left) "Stick izquierdo" else "Stick derecho"
                var dz by remember(serial, left, tick) { mutableStateOf(ControllerMappings.stickDeadzoneScope(left, serial)) }
                ExkRow("$name · zona muerta ${(dz * 100).toInt()} %", detail = "Ignora movimientos pequeños para evitar desplazamientos solos del stick.") {
                    ExkSlider(dz, { dz = it }, 0f..ControllerMappings.STICK_DZ_MAX, Modifier.width(220.dp),
                        onDone = { ControllerMappings.setStickDeadzoneScope(left, dz, serial); onChanged() })
                }
                ExkRow("$name · invertir horizontal") {
                    ExkSwitch(ControllerMappings.stickInvertXScope(left, serial), { ControllerMappings.setStickInvertX(left, it, serial); onChanged() })
                }
                ExkRow("$name · invertir vertical") {
                    ExkSwitch(ControllerMappings.stickInvertYScope(left, serial), { ControllerMappings.setStickInvertY(left, it, serial); onChanged() })
                }
                ExkRow("$name · intercambiar ejes", detail = "Para mandos que informan el stick girado 90°.") {
                    ExkSwitch(ControllerMappings.stickSwapXYScope(left, serial), { ControllerMappings.setStickSwapXY(left, it, serial); onChanged() })
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- Táctil

@Composable
private fun TouchTab(serial: String?, tick: Int, onEdit: () -> Unit, onChanged: () -> Unit) {
    val c = LocalExk.current
    @Suppress("UNUSED_EXPRESSION") tick
    val t = com.armsx2.ui.touch.TouchControls
    ExkPanel {
        Column {
            Text("Controles táctiles", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (serial != null && t.exkHasGameLayout(serial)) "Este juego tiene su propia distribución." else
                    if (serial != null) "Este juego usa la distribución general." else "Distribución general (la heredan todos los juegos sin perfil propio).",
                color = c.textMuted, fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            ExkPrimaryButton("Editar distribución", onEdit, leading = "✥", navId = "exk.ctl.touch.edit")
            Spacer(Modifier.height(10.dp))
            ExkRow("Transparencia general · ${(t.opacity.floatValue * 100).toInt()} %", detail = "Afecta a todos los botones táctiles.") {
                var o by remember(tick) { mutableStateOf(t.opacity.floatValue) }
                ExkSlider(o, { o = it }, 0.1f..1f, Modifier.width(220.dp), onDone = { t.setOpacity(o); onChanged() })
            }
            ExkRow("Vibración al tocar") { ExkSwitch(t.touchHaptics.value, { t.setTouchHaptics(it); onChanged() }) }
            ExkRow("Separación de la cruceta · ${(t.dpadSpacing.floatValue * 100).toInt()} %") {
                var s by remember(tick) { mutableStateOf(t.dpadSpacing.floatValue) }
                ExkSlider(s, { s = it }, 0f..1f, Modifier.width(220.dp), onDone = { t.setDpadSpacing(s); onChanged() })
            }
            Text("La horizontal y la vertical se guardan por separado.", color = c.textMuted, fontSize = 11.sp)
        }
    }
}

// --------------------------------------------------------------------------- Comunes

@Composable
private fun BindingList(
    table: InputTable, serial: String?, player: Int, selected: Ps2Button?,
    onSelect: (Ps2Button) -> Unit, onCapture: (Ps2Button) -> Unit, onClear: (Ps2Button) -> Unit,
) {
    val c = LocalExk.current
    Ps2Button.Group.entries.forEach { g ->
        Spacer(Modifier.height(14.dp))
        Text(g.title.uppercase(), color = c.accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Ps2Button.entries.filter { it.group == g }.forEach { b ->
            val code = ControlsLogic.currentKey(table, b, player, serial)
            val isStick = g == Ps2Button.Group.LEFT_STICK || g == Ps2Button.Group.RIGHT_STICK
            val label = when {
                code != KeyEvent.KEYCODE_UNKNOWN -> ExkInput.keyLabel(code)
                isStick && table == InputTable.CONTROLLER -> "Stick analógico"
                else -> "Sin asignar"
            }
            BindingRow(b.label, label, active = b == selected, unset = code == KeyEvent.KEYCODE_UNKNOWN && !(isStick && table == InputTable.CONTROLLER),
                onClick = { onSelect(b); onCapture(b) }, onClear = if (code != KeyEvent.KEYCODE_UNKNOWN) ({ onClear(b) }) else null,
                navId = "exk.ctl.${table.name}.${b.name}")
        }
    }
}

@Composable
private fun BindingRow(name: String, value: String, active: Boolean, unset: Boolean, onClick: () -> Unit, onClear: (() -> Unit)?, navId: String? = null) {
    val c = LocalExk.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .exkNav(navId, onClick, shape = shape)
            .exkFocusRing(focused, shape, c.glow)
            .clip(shape)
            .background(if (active) c.accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (active) c.accent.copy(alpha = 0.6f) else Color.Transparent, shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.widthIn(min = 120.dp).clip(RoundedCornerShape(8.dp)).background(c.chip).padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) { Text(value, color = if (unset) c.textMuted else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        if (onClear != null) {
            Spacer(Modifier.width(8.dp))
            ExkSecondaryButton("Borrar", onClear, compact = true)
        }
    }
}

@Composable
private fun HotkeySection(onCapture: (ControllerMappings.SysHotkey) -> Unit, onClear: (ControllerMappings.SysHotkey) -> Unit) {
    val c = LocalExk.current
    Column {
        Spacer(Modifier.height(18.dp))
        Text("ATAJOS DEL EMULADOR", color = c.accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(
            "Separados de los botones de PS2. Se activan una sola vez por pulsación: mantenerlos no repite la acción. " +
                "Son comunes a teclado y mando y no tienen versión por juego. «Pantalla completa» no aplica en Android: el juego ya ocupa toda la pantalla.",
            color = c.textMuted, fontSize = 11.sp,
        )
        ControllerMappings.hotkeysInDisplayOrder.filter { it.name in setOf("MENU", "SAVE_STATE", "LOAD_STATE", "CYCLE_SLOT", "FAST_FORWARD", "SCREENSHOT", "TOGGLE_OSD") }
            .forEach { h ->
                val lbl = ControllerMappings.hotkeyLabel(h)
                BindingRow(hotkeyLabel(h), if (lbl.isEmpty()) "Sin asignar" else lbl, active = false, unset = lbl.isEmpty(),
                    onClick = { onCapture(h) }, onClear = if (lbl.isNotEmpty()) ({ onClear(h) }) else null, navId = "exk.ctl.hk.${h.name}")
            }
    }
}

@Composable
private fun CaptureOverlay(req: CaptureRequest, onCancel: () -> Unit, onClear: (() -> Unit)?, onSkip: (() -> Unit)?) {
    val c = LocalExk.current
    Box(Modifier.fillMaxSize().background(Color(0xCC02050A)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        ExkPanel(Modifier.widthIn(max = 560.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (req.wizardIndex >= 0) Text("Paso ${req.wizardIndex + 1} de ${Ps2Button.wizardOrder.size}", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(req.hotkey?.let { hotkeyLabel(it) } ?: req.button?.label.orEmpty(), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val what = when {
                    req.table == InputTable.KEYBOARD -> "Pulsa la nueva tecla"
                    req.button?.group == Ps2Button.Group.LEFT_STICK || req.button?.group == Ps2Button.Group.RIGHT_STICK ->
                        "Pulsa un botón o mueve el stick en esa dirección"
                    else -> "Pulsa el nuevo botón del mando"
                }
                Text(what, color = c.glow, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("La pulsación solo se usa para esta asignación; no activa nada más de la interfaz.", color = c.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExkSecondaryButton("Cancelar", onCancel)
                    if (onClear != null) ExkSecondaryButton("Borrar asignación", onClear, danger = true)
                    if (onSkip != null) ExkSecondaryButton("Omitir este botón", onSkip)
                }
            }
        }
    }
}

@Composable
private fun ConflictDialog(pc: PendingConflict, onReplace: () -> Unit, onKeepBoth: (() -> Unit)?, onCancel: () -> Unit) {
    ExkDialog(
        title = "${ExkInput.keyLabel(pc.code)} ya está en uso",
        body = "Esta ${if (pc.table == InputTable.KEYBOARD) "tecla" else "entrada"} " +
            pc.conflicts.joinToString("; ") { it.description } + ".\n\n" +
            (if (onKeepBoth == null) "El motor no puede mantener ambas: cada entrada controla una sola acción de PS2."
            else "Puedes conservar ambas: el atajo solo se activa con la combinación completa.") +
            " Nada se cambia sin tu confirmación.",
        actions = buildList {
            add("Reemplazar" to onReplace)
            if (onKeepBoth != null) add("Conservar ambas" to onKeepBoth)
            add("Cancelar" to onCancel)
        },
    )
}

@Composable
fun ExkDialog(title: String, body: String, actions: List<Pair<String, () -> Unit>>) {
    val c = LocalExk.current
    // PadModal de ARMSX2 (no una ventana de diálogo): así el mando también puede usarlo.
    com.armsx2.ui.common.PadModal(key = "exk.dialog.$title", onDismiss = { actions.last().second() }, initialFocusId = "exk.dlg.0") {
        ExkPanel(Modifier.widthIn(max = 560.dp).padding(24.dp)) {
            Column {
                Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(body, color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(20.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    actions.forEachIndexed { i, (label, act) ->
                        if (i == 0) ExkPrimaryButton(label, act, minWidth = 140.dp, navId = "exk.dlg.$i")
                        else ExkSecondaryButton(label, act, navId = "exk.dlg.$i")
                    }
                }
            }
        }
    }
}

@Suppress("unused") private val unusedSize = Modifier.size(1.dp)
