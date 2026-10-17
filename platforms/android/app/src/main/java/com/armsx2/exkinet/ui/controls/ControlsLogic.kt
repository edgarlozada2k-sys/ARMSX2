package com.armsx2.exkinet.ui.controls

import android.view.KeyEvent
import com.armsx2.exkinet.input.ExkInput
import com.armsx2.exkinet.input.Ps2Button
import com.armsx2.input.ControllerMappings
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.touch.TouchControls

/** Qué tabla se edita. */
enum class InputTable { KEYBOARD, CONTROLLER }

/** Un uso ya existente de una tecla/botón que choca con la nueva asignación. */
sealed interface Conflict {
    val description: String
    /** True si el motor puede mantener las dos asignaciones a la vez. */
    val canKeepBoth: Boolean

    data class OtherButton(val button: Ps2Button) : Conflict {
        override val description get() = "ya controla «${button.label}»"
        // ARMSX2 resuelve cada tecla física a UNA sola acción de PS2 (la primera de la lista):
        // con dos, la segunda nunca recibiría la pulsación.
        override val canKeepBoth get() = false
    }

    data class Hotkey(val hotkey: ControllerMappings.SysHotkey, val asModifier: Boolean) : Conflict {
        override val description get() =
            if (asModifier) "se usa como modificador del atajo «${hotkeyLabel(hotkey)}»"
            else "abre el atajo «${hotkeyLabel(hotkey)}»"
        // Un modificador de combinación (p. ej. Select en Select+R1) convive con su función de
        // botón: el atajo solo salta con la combinación completa. Un atajo de una sola tecla no.
        override val canKeepBoth get() = asModifier
    }
}

object ControlsLogic {

    fun currentKey(table: InputTable, b: Ps2Button, player: Int, serial: String?): Int = when (table) {
        InputTable.KEYBOARD -> ExkInput.keyboardKeyFor(b.action, serial)
        InputTable.CONTROLLER -> ControllerMappings.physicalForScope(b.action, player, serial)
    }

    fun assign(table: InputTable, b: Ps2Button, code: Int, player: Int, serial: String?) {
        when (table) {
            InputTable.KEYBOARD -> ExkInput.setKeyboardKey(b.action, code, serial)
            InputTable.CONTROLLER -> ControllerMappings.bind(b.action, code, player, serial)
        }
    }

    fun clear(table: InputTable, b: Ps2Button, player: Int, serial: String?) {
        when (table) {
            InputTable.KEYBOARD -> ExkInput.setKeyboardKey(b.action, KeyEvent.KEYCODE_UNKNOWN, serial)
            InputTable.CONTROLLER -> ControllerMappings.clearAction(b.action, player, serial)
        }
    }

    /** Conflictos de [code] con otras acciones de la MISMA tabla y con los atajos del emulador. */
    fun conflictsFor(table: InputTable, target: Ps2Button, code: Int, player: Int, serial: String?): List<Conflict> {
        if (code == KeyEvent.KEYCODE_UNKNOWN) return emptyList()
        val out = mutableListOf<Conflict>()
        for (b in Ps2Button.entries) {
            if (b == target) continue
            if (currentKey(table, b, player, serial) == code) out += Conflict.OtherButton(b)
        }
        for (h in ControllerMappings.SysHotkey.entries) {
            if (ControllerMappings.hotkeyCode(h) == code) out += Conflict.Hotkey(h, asModifier = false)
            else if (ControllerMappings.hotkeyModCode(h) == code) out += Conflict.Hotkey(h, asModifier = true)
        }
        return out
    }

    /** Aplica la asignación resolviendo conflictos según la opción elegida. */
    fun resolve(table: InputTable, target: Ps2Button, code: Int, player: Int, serial: String?, conflicts: List<Conflict>, keepBoth: Boolean) {
        if (!keepBoth) {
            for (c in conflicts) when (c) {
                is Conflict.OtherButton -> clear(table, c.button, player, serial)
                is Conflict.Hotkey -> ControllerMappings.clearHotkey(c.hotkey)
            }
        }
        assign(table, target, code, player, serial)
    }

    /** True si el juego tiene algo propio (teclado, mando o táctil). */
    fun gameHasProfile(serial: String?): Boolean {
        if (serial.isNullOrEmpty()) return false
        return ExkInput.hasKeyboardGameOverrides(serial) ||
            ControllerMappings.hasGameOverrides(serial, ControllerMappings.P1) ||
            ControllerMappings.hasGameOverrides(serial, ControllerMappings.P2) ||
            TouchControls.exkHasGameLayout(serial) ||
            MainActivityRuntime.prefs.all.keys.any { it.startsWith("game.$serial.pad.stick.") }
    }

    /** «Volver a usar la configuración general»: elimina todo lo propio del juego. */
    fun clearGameProfile(serial: String) {
        ExkInput.clearKeyboardTier(serial)
        ControllerMappings.clearGameOverrides(serial, ControllerMappings.P1)
        ControllerMappings.clearGameOverrides(serial, ControllerMappings.P2)
        val prefs = MainActivityRuntime.prefs
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("game.$serial.pad.") || it.startsWith("game.$serial.p2.pad.") || it == "touch.active.game.$serial" }
            .forEach { e.remove(it) }
        e.apply()
        TouchControls.clearGameLayout(serial)
        ControllerMappings.exkDropTuningCaches()
    }

    fun resetKeyboard(serial: String?) = ExkInput.clearKeyboardTier(serial)

    fun resetController(player: Int, serial: String?) {
        if (serial.isNullOrEmpty()) ControllerMappings.reset(player, null)
        else ControllerMappings.clearGameOverrides(serial, player)
    }

    fun resetTouch(serial: String?) {
        if (!serial.isNullOrEmpty()) TouchControls.clearGameLayout(serial)
        else for (portrait in listOf(false, true))
            TouchControls.exkSaveLayout(null, portrait, com.armsx2.ui.touch.TouchLayout.defaultFor(portrait))
    }
}

fun hotkeyLabel(h: ControllerMappings.SysHotkey): String = when (h.name) {
    "MENU" -> "Menú / Pausa"
    "SAVE_STATE" -> "Guardado rápido"
    "LOAD_STATE" -> "Carga rápida"
    "CYCLE_SLOT" -> "Cambiar ranura de guardado"
    "TEXTURE_DUMP" -> "Volcado de texturas"
    "SCREENSHOT" -> "Captura de pantalla"
    "TOGGLE_OSD" -> "Estadísticas en pantalla"
    "FAST_FORWARD" -> "Avance rápido (mantener)"
    else -> h.label
}
