package com.armsx2.exkinet.input

import com.armsx2.input.ControllerMappings
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.touch.TouchControls

/**
 * «Guardar / Cancelar» sobre el sistema de controles de ARMSX2.
 *
 * Las asignaciones se aplican al instante en la configuración real (así «Probar controles»
 * usa exactamente lo que recibiría el juego). Al abrir la pantalla se toma una instantánea de
 * todas las claves de controles; «Cancelar» o «Descartar» la restaura tal cual y «Guardar»
 * la da por buena. Si la app se cierra a mitad, lo aplicado se conserva (no se pierde nada).
 */
object ControlsDraft {
    private var snapshot: Map<String, Any?>? = null
    @Volatile var edits = 0
        private set

    fun isControlKey(k: String): Boolean {
        val base = if (k.startsWith("game.")) k.substringAfter('.').substringAfter('.') else k
        return base.startsWith("pad.") || base.startsWith("p2.pad.") || base.startsWith("kb.") ||
            base.startsWith("touch.") || k.startsWith("touch.") || k == "pad_router_pinned" ||
            k == "pad_router_rumble"
    }

    fun begin() {
        snapshot = MainActivityRuntime.prefs.all.filterKeys(::isControlKey).toMap()
        edits = 0
    }

    fun markEdited() { edits++ }

    /** True si la configuración actual difiere de la instantánea. */
    fun hasChanges(): Boolean {
        val snap = snapshot ?: return false
        val now = MainActivityRuntime.prefs.all.filterKeys(::isControlKey)
        return now != snap
    }

    fun commit() {
        snapshot = MainActivityRuntime.prefs.all.filterKeys(::isControlKey).toMap()
        edits = 0
    }

    /** Vuelve exactamente al estado de la instantánea. */
    fun revert() {
        val snap = snapshot ?: return
        val prefs = MainActivityRuntime.prefs
        val e = prefs.edit()
        prefs.all.keys.filter(::isControlKey).filter { it !in snap }.forEach { e.remove(it) }
        for ((k, v) in snap) when (v) {
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is Boolean -> e.putBoolean(k, v)
            is String -> e.putString(k, v)
            is Set<*> -> @Suppress("UNCHECKED_CAST") e.putStringSet(k, v as Set<String>)
            null -> e.remove(k)
        }
        e.commit()
        refreshRuntime()
        edits = 0
    }

    fun end() { snapshot = null }

    /** Recarga las cachés del motor tras cambiar preferencias por fuera de sus setters. */
    fun refreshRuntime() {
        ControllerMappings.invalidateRuntimeCaches()
        ControllerMappings.exkDropTuningCaches()
        ExkInput.kbVersion.intValue++
        com.armsx2.input.PadRouter.loadPins()
        TouchControls.exkReload()
        ControllerMappings.stickBindTick.value++
        ControllerMappings.hotkeyBindTick.value++
    }
}
