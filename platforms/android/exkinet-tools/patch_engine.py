#!/usr/bin/env python3
"""Cambios mínimos de ExKinet en el motor de ARMSX2 (Kotlin). Idempotente."""
import sys, re
ROOT = sys.argv[1] if len(sys.argv) > 1 else '/root/armsx2-src/platforms/android/app/src/main/java/com/armsx2'

def patch(path, old, new, count=1, marker=None):
    p = f'{ROOT}/{path}'
    s = open(p, encoding='utf-8').read()
    if marker and marker in s:
        print('ya aplicado:', path, marker); return
    n = s.count(old)
    if n < count:
        sys.exit(f'NO ENCONTRADO en {path}: {old[:80]!r} (veces={n})')
    s = s.replace(old, new, count)
    open(p, 'w', encoding='utf-8').write(s)
    print('ok:', path, (marker or old[:40]).strip())

RT = 'runtime/MainActivityRuntime.kt'

# 1) Captura / prueba de ExKinet antes que ninguna otra parte de la interfaz.
patch(RT,
"""        val kc = event.keyCode
        if (kc != KeyEvent.KEYCODE_UNKNOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> heldKeys.add(kc)""",
"""        // ExKinet: captura de teclas y «Probar controles» (ver exkinet/input/ExkInput.kt).
        if (com.armsx2.exkinet.input.ExkInput.interceptKey(event)) return true
        val kc = event.keyCode
        if (kc != KeyEvent.KEYCODE_UNKNOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> heldKeys.add(kc)""", marker='ExkInput.interceptKey')

patch(RT,
"""    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (com.armsx2.ui.home.LibraryScreensaver.onMotion(ev)) return true
""",
"""    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (com.armsx2.ui.home.LibraryScreensaver.onMotion(ev)) return true
        // ExKinet: captura de ejes y prueba de sticks.
        if (com.armsx2.exkinet.input.ExkInput.interceptMotion(ev)) return true
""", marker='ExkInput.interceptMotion')

# 2) Teclado con tabla propia, separada del mando.
patch(RT,
"""        val target = ControllerMappings.targetForPhysical(physicalCode, port) ?: return false
        // Pads that report a trigger BOTH ways""",
"""        // ExKinet: un teclado físico usa su propia tabla (kb.map.*) y su propio jugador.
        val fromKeyboard = com.armsx2.exkinet.input.ExkInput.isKeyboardEvent(event)
        val target = (if (fromKeyboard) com.armsx2.exkinet.input.ExkInput.keyboardTargetFor(physicalCode)
            else ControllerMappings.targetForPhysical(physicalCode, port)) ?: return false
        // Pads that report a trigger BOTH ways""", marker='ExkInput.keyboardTargetFor')

patch(RT,
"""        val port = com.armsx2.input.PadRouter.portForDevice(event.deviceId)
        val fromController = !isVolumeKey(physicalCode)
        com.armsx2.ui.touch.TouchControls.macroForPhysicalCode(physicalCode)?.let { macro ->""",
"""        val port = if (com.armsx2.exkinet.input.ExkInput.isKeyboardEvent(event))
            com.armsx2.exkinet.input.ExkInput.keyboardPlayer()
        else com.armsx2.input.PadRouter.portForDevice(event.deviceId)
        val fromController = !isVolumeKey(physicalCode)
        com.armsx2.ui.touch.TouchControls.macroForPhysicalCode(physicalCode)?.let { macro ->""", marker='ExkInput.keyboardPlayer()')

# 3) Al desconectar un mando: soltar todo lo que tenía pulsado su puerto.
patch(RT,
"""        override fun onInputDeviceRemoved(deviceId: Int) {
            com.armsx2.input.PadRouter.forgetDevice(deviceId)
        }""",
"""        override fun onInputDeviceRemoved(deviceId: Int) {
            // ExKinet: liberar botones y ejes del puerto antes de olvidar el dispositivo, para que
            // ninguna dirección quede atascada al desconectar el mando.
            val port = com.armsx2.input.PadRouter.claimedPortOf(deviceId)
            synchronized(padHeldKeys) { padHeldKeys.removeAll { (it ushr 32).toInt() == deviceId } }
            if (port != null) {
                if (port in analogKeyHeld.indices) analogKeyHeld[port].clear()
                com.armsx2.exkinet.input.ExkInput.releasePort(port)
            }
            com.armsx2.input.PadRouter.forgetDevice(deviceId)
        }""", marker='PadRouter.claimedPortOf')

# 4) Pérdida de foco o pausa: soltar todo.
patch(RT,
"""    override fun onPause() {
        // Take the second-display panel down with the app.""",
"""    /** ExKinet: suelta toda la entrada del pad (ningún botón queda pulsado al cambiar de ventana). */
    private fun exkReleaseAllInput() {
        padHeldKeys.clear()
        analogKeyHeld.forEach { it.clear() }
        com.armsx2.exkinet.input.ExkInput.releaseAll()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) exkReleaseAllInput()
    }

    override fun onPause() {
        exkReleaseAllInput()
        // Take the second-display panel down with the app.""", marker='exkReleaseAllInput')

# 5) PadRouter: puerto reclamado sin reclamar uno nuevo.
patch('input/PadRouter.kt',
"""    fun forgetDevice(deviceId: Int) {""",
"""    /** ExKinet: puerto que tiene reclamado [deviceId], o null (no reclama ninguno). */
    fun claimedPortOf(deviceId: Int): Int? = slots.indexOf(deviceId).takeIf { it >= 0 }

    fun forgetDevice(deviceId: Int) {""", marker='claimedPortOf')

# 6) ControllerMappings: zona muerta por ámbito explícito y vaciado de cachés.
patch('input/ControllerMappings.kt',
"""		fun reset(edit: android.content.SharedPreferences.Editor) {""",
"""		/** ExKinet: valor en el nivel indicado (null = general), sin depender del juego en marcha. */
		fun scoped(left: Boolean, serial: String?): Float {
			val p = MainActivityRuntime.prefs
			return (when {
				serial != null && p.contains(gameKey(serial, key(left))) -> p.getFloat(gameKey(serial, key(left)), def)
				p.contains(key(left)) -> p.getFloat(key(left), def)
				else -> p.getFloat(baseKey, def)
			}).coerceIn(lo, hi)
		}
		fun setScoped(left: Boolean, v: Float, serial: String?) {
			MainActivityRuntime.prefs.edit {
				putFloat(if (serial != null) gameKey(serial, key(left)) else key(left), v.coerceIn(lo, hi))
			}
			drop()
		}
		fun drop() { cacheL = Float.NaN; cacheR = Float.NaN; cachedFor = "\\u0000unset" }
		fun reset(edit: android.content.SharedPreferences.Editor) {""", marker='fun setScoped(left: Boolean')

patch('input/ControllerMappings.kt',
"""    fun stickDeadzone(left: Boolean): Float = prefStickDz.get(left)""",
"""    fun stickDeadzone(left: Boolean): Float = prefStickDz.get(left)
    // ExKinet: zona muerta por ámbito explícito (general / juego) para la pantalla de Controles.
    fun stickDeadzoneScope(left: Boolean, serial: String?): Float = prefStickDz.scoped(left, serial)
    fun setStickDeadzoneScope(left: Boolean, v: Float, serial: String?) = prefStickDz.setScoped(left, v, serial)
    fun exkDropTuningCaches() {
        prefStickSens.drop(); prefStickAccel.drop(); prefStickCurve.drop()
        prefStickDz.drop(); prefStickOuter.drop(); prefStickAntiDz.drop()
    }""", marker='stickDeadzoneScope')

# 7) TouchControls: lectura y guardado explícitos por ámbito y orientación.
patch('ui/touch/TouchControls.kt',
"""    fun clearGameLayoutIfRunning() {""",
"""    // ---- ExKinet: editor de distribución fuera del juego -------------------------------
    private fun exkKey(serial: String, portrait: Boolean) =
        KEY_LAYOUT_GAME_PREFIX + serial + if (portrait) ".portrait" else ""

    fun exkHasGameLayout(serial: String?): Boolean = !serial.isNullOrEmpty() &&
        (MainActivityRuntime.prefs.contains(exkKey(serial, false)) || MainActivityRuntime.prefs.contains(exkKey(serial, true)))

    /** Distribución efectiva para [serial] (null = general) en la orientación pedida. */
    fun exkLayoutFor(serial: String?, portrait: Boolean): TouchLayout {
        ensureLoaded()
        if (!serial.isNullOrEmpty()) {
            MainActivityRuntime.prefs.getString(exkKey(serial, portrait), null)?.let { raw ->
                runCatching { TouchLayout.fromJson(JSONObject(raw)) }.getOrNull()?.let { return it }
            }
        }
        val prof = profiles.firstOrNull { it.name == activeProfileName.value } ?: profiles.firstOrNull()
        return prof?.layoutFor(portrait)?.copy() ?: TouchLayout.defaultFor(portrait)
    }

    fun exkSaveLayout(serial: String?, portrait: Boolean, layout: TouchLayout) {
        ensureLoaded()
        if (!serial.isNullOrEmpty()) {
            MainActivityRuntime.prefs.edit { putString(exkKey(serial, portrait), layout.toJson().toString()) }
        } else {
            val idx = profiles.indexOfFirst { it.name == activeProfileName.value }.takeIf { it >= 0 } ?: 0
            if (profiles.isNotEmpty()) {
                profiles[idx] = profiles[idx].withLayoutFor(portrait, layout.copy())
                persist()
            }
        }
        if (!gameIsRunning() && portrait == this.portrait.value) activeLayout.value = layout.copy()
    }

    /** Vuelve a leer perfiles y ajustes tras restaurar preferencias (Cancelar en ExKinet). */
    fun exkReload() {
        loaded = false
        ensureLoaded()
        if (!gameIsRunning()) applyActiveProfileForOrientation()
    }

    fun clearGameLayoutIfRunning() {""", marker='exkLayoutFor')
print('listo')
