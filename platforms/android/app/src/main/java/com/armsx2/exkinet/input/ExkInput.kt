package com.armsx2.exkinet.input

import android.content.SharedPreferences
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.armsx2.EmuState
import com.armsx2.input.ControllerMappings
import com.armsx2.input.PadRouter
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp
import kotlin.math.abs

/**
 * Capa de entrada de ExKinet sobre el sistema de ARMSX2.
 *
 *  - Teclado: tabla propia de asignaciones (prefijo `kb.map.`), separada de la del mando, con
 *    nivel general y por juego (`game.<serial>.kb.map.`), igual que el resto de ARMSX2.
 *  - Captura: mientras la pantalla de Controles espera una tecla/botón/eje, se lo queda aquí
 *    antes que cualquier otra parte de la interfaz.
 *  - Prueba: con la zona «Probar controles» abierta y sin juego en marcha, cada entrada se
 *    traduce con las MISMAS funciones que usa el emulador (targetForPhysical, tabla de teclado,
 *    zona muerta e inversión de ejes) y se publica para iluminar el esquema.
 *  - Liberación: al desconectar un dispositivo o perder el foco se sueltan todos los botones y
 *    ejes del puerto, para que nada quede pulsado.
 */
object ExkInput {

    // ------------------------------------------------------------------ teclado

    private const val KB_PREFIX = "kb.map."
    const val KB_PLAYER_KEY = "kb.player"

    /** Valores por defecto del teclado (código de tecla Android) para cada acción de ARMSX2. */
    private val keyboardDefaults: Map<String, Int> = mapOf(
        "dpad_up" to KeyEvent.KEYCODE_DPAD_UP,
        "dpad_down" to KeyEvent.KEYCODE_DPAD_DOWN,
        "dpad_left" to KeyEvent.KEYCODE_DPAD_LEFT,
        "dpad_right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "cross" to KeyEvent.KEYCODE_K,
        "circle" to KeyEvent.KEYCODE_L,
        "square" to KeyEvent.KEYCODE_J,
        "triangle" to KeyEvent.KEYCODE_I,
        "l1" to KeyEvent.KEYCODE_Q,
        "r1" to KeyEvent.KEYCODE_E,
        "l2" to KeyEvent.KEYCODE_1,
        "r2" to KeyEvent.KEYCODE_3,
        "l3" to KeyEvent.KEYCODE_Z,
        "r3" to KeyEvent.KEYCODE_X,
        "select" to KeyEvent.KEYCODE_DEL,
        "start" to KeyEvent.KEYCODE_ENTER,
        "analog" to KeyEvent.KEYCODE_UNKNOWN,
        "ls_up" to KeyEvent.KEYCODE_W,
        "ls_down" to KeyEvent.KEYCODE_S,
        "ls_left" to KeyEvent.KEYCODE_A,
        "ls_right" to KeyEvent.KEYCODE_D,
        "rs_up" to KeyEvent.KEYCODE_NUMPAD_8,
        "rs_down" to KeyEvent.KEYCODE_NUMPAD_2,
        "rs_left" to KeyEvent.KEYCODE_NUMPAD_4,
        "rs_right" to KeyEvent.KEYCODE_NUMPAD_6,
    )

    fun keyboardDefault(action: ControllerMappings.Action): Int =
        keyboardDefaults[action.id] ?: KeyEvent.KEYCODE_UNKNOWN

    private fun prefs(): SharedPreferences = MainActivityRuntime.prefs

    private fun kbKey(actionId: String, serial: String?) =
        if (serial.isNullOrEmpty()) KB_PREFIX + actionId else "game.$serial.$KB_PREFIX$actionId"

    /** Asignación visible en la pantalla: la del juego si existe; si no, la general. */
    fun keyboardKeyFor(action: ControllerMappings.Action, serial: String?): Int {
        val p = prefs()
        if (!serial.isNullOrEmpty()) {
            val gk = kbKey(action.id, serial)
            if (p.contains(gk)) return p.getInt(gk, KeyEvent.KEYCODE_UNKNOWN)
        }
        return p.getInt(kbKey(action.id, null), keyboardDefault(action))
    }

    fun setKeyboardKey(action: ControllerMappings.Action, keyCode: Int, serial: String?) {
        prefs().edit().putInt(kbKey(action.id, serial), keyCode).apply()
        kbVersion.intValue++
    }

    fun hasKeyboardGameOverrides(serial: String?): Boolean {
        if (serial.isNullOrEmpty()) return false
        val prefix = "game.$serial.$KB_PREFIX"
        return prefs().all.keys.any { it.startsWith(prefix) }
    }

    fun clearKeyboardTier(serial: String?) {
        val prefix = if (serial.isNullOrEmpty()) KB_PREFIX else "game.$serial.$KB_PREFIX"
        val e = prefs().edit()
        prefs().all.keys.filter { it.startsWith(prefix) }.forEach { e.remove(it) }
        e.apply()
        kbVersion.intValue++
    }

    fun keyboardPlayer(): Int = prefs().getInt(KB_PLAYER_KEY, ControllerMappings.P1)
    fun setKeyboardPlayer(p: Int) { prefs().edit().putInt(KB_PLAYER_KEY, p).apply(); kbVersion.intValue++ }

    /** Cambia cada vez que se edita el teclado; invalida la caché usada durante el juego. */
    val kbVersion = mutableIntStateOf(0)
    @Volatile private var kbCache: Pair<String, Map<Int, Int>>? = null

    private fun runtimeSerial(): String? =
        MainActivityRuntime.currentGame.value?.serial?.takeIf { it.isNotEmpty() }

    /** Tabla tecla física → código PS2 para el juego en curso (o la general). */
    fun keyboardTargets(serial: String? = runtimeSerial()): Map<Int, Int> {
        val tag = "${serial.orEmpty()}#${kbVersion.intValue}"
        kbCache?.let { if (it.first == tag) return it.second }
        val map = HashMap<Int, Int>()
        for (a in ControllerMappings.actions) {
            val k = keyboardKeyFor(a, serial)
            // La primera acción gana, igual que en la tabla del mando de ARMSX2.
            if (k != KeyEvent.KEYCODE_UNKNOWN && k !in map) map[k] = a.targetKeyCode
        }
        kbCache = tag to map
        return map
    }

    /** True para teclados físicos (no mandos, que también anuncian SOURCE_KEYBOARD). */
    fun isKeyboardEvent(event: KeyEvent): Boolean {
        val dev = event.device ?: return false
        if (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        if (!event.isFromSource(InputDevice.SOURCE_KEYBOARD)) return false
        return dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }

    /** Usado por el despacho del juego: código PS2 para una tecla de teclado, o null. */
    fun keyboardTargetFor(keyCode: Int): Int? = keyboardTargets()[keyCode]

    // ------------------------------------------------------------------ captura

    sealed interface Captured {
        val label: String
        data class Key(val keyCode: Int, val fromKeyboard: Boolean, val device: String) : Captured {
            override val label: String get() = keyLabel(keyCode)
        }
        data class Axis(val left: Boolean, val dir: ControllerMappings.StickDir, val device: String) : Captured {
            override val label: String get() = "Stick ${if (left) "izquierdo" else "derecho"} ${dirLabel(dir)}"
        }
    }

    enum class CaptureKind { KEYBOARD, CONTROLLER_BUTTON, CONTROLLER_ANY }

    /** Captura pendiente. El callback recibe null si el usuario cancela. */
    private var captureKind: CaptureKind? = null
    private var captureCallback: ((Captured?) -> Unit)? = null
    val capturing = mutableStateOf(false)

    fun beginCapture(kind: CaptureKind, onResult: (Captured?) -> Unit) {
        captureKind = kind
        captureCallback = onResult
        capturing.value = true
    }

    fun cancelCapture() {
        val cb = captureCallback
        captureKind = null
        captureCallback = null
        capturing.value = false
        cb?.invoke(null)
    }

    private fun finishCapture(c: Captured) {
        val cb = captureCallback ?: return
        captureKind = null
        captureCallback = null
        capturing.value = false
        cb(c)
    }

    // ------------------------------------------------------------------ prueba

    /** Activo mientras la zona «Probar controles» está visible y no hay juego en marcha. */
    val testActive = mutableStateOf(false)
    /** Serial cuyo perfil se usa en la prueba (null = general). */
    @Volatile var testSerial: String? = null

    /** Códigos PS2 pulsados ahora mismo en la prueba, con la entrada que los produjo. */
    val testPressed = mutableStateMapOf<Int, String>()
    /** Posición de los sticks tras zona muerta/inversión: x, y en -1..1. */
    val testLeftStick = mutableStateOf(0f to 0f)
    val testRightStick = mutableStateOf(0f to 0f)
    val testLastInput = mutableStateOf("")

    fun clearTest() {
        testPressed.clear()
        testLeftStick.value = 0f to 0f
        testRightStick.value = 0f to 0f
        testLastInput.value = ""
    }

    private fun gameRunning(): Boolean = MainActivityRuntime.eState.value != EmuState.STOPPED

    /**
     * Llamado al principio de MainActivityRuntime.dispatchKeyEvent. Devuelve true si ExKinet
     * consume la tecla (captura o prueba); en ese caso no llega a ninguna otra función.
     */
    fun interceptKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_UNKNOWN) return false
        if (captureCallback != null) {
            val fromKb = isKeyboardEvent(event)
            val kind = captureKind
            // El botón Atrás del sistema nunca se captura: es la salida garantizada.
            if (code == KeyEvent.KEYCODE_BACK && !fromKb) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val accepted = when (kind) {
                    CaptureKind.KEYBOARD -> fromKb
                    CaptureKind.CONTROLLER_BUTTON, CaptureKind.CONTROLLER_ANY -> !fromKb
                    null -> false
                }
                if (accepted) finishCapture(Captured.Key(code, fromKb, event.device?.name ?: ""))
            }
            return true
        }
        if (testActive.value && !gameRunning()) {
            if (event.repeatCount > 0) return true
            val fromKb = isKeyboardEvent(event)
            val serial = testSerial
            val target = if (fromKb) keyboardTargets(serial)[code] else {
                val port = PadRouter.portForDevice(event.deviceId)
                // Misma resolución que el juego. El perfil por juego se lee con su serial.
                withSerial(serial) { ControllerMappings.targetForPhysical(code, port) }
            }
            val label = (if (fromKb) "Teclado · " else "Mando · ") + keyLabel(code)
            testLastInput.value = label + (target?.let { " → ${ps2Label(it)}" } ?: " → sin asignar")
            if (target != null) {
                if (event.action == KeyEvent.ACTION_DOWN) testPressed[target] = label
                else if (event.action == KeyEvent.ACTION_UP) testPressed.remove(target)
            }
            return code != KeyEvent.KEYCODE_BACK
        }
        return false
    }

    private val axisLatch = HashSet<String>()

    /** Llamado al principio de dispatchGenericMotionEvent. */
    fun interceptMotion(ev: MotionEvent): Boolean {
        val isPad = ev.isFromSource(InputDevice.SOURCE_JOYSTICK) || ev.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!isPad || ev.action != MotionEvent.ACTION_MOVE) return false
        if (captureCallback != null) {
            val name = ev.device?.name ?: ""
            // Muchos mandos envían la cruceta (HAT) y los gatillos como ejes, no como teclas:
            // se traducen a la tecla equivalente, que es como ARMSX2 los asigna.
            val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X); val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val lt = maxOf(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
            val rt = maxOf(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
            val asKey = when {
                hy < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
                hy > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
                hx < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
                hx > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
                lt > 0.6f -> KeyEvent.KEYCODE_BUTTON_L2
                rt > 0.6f -> KeyEvent.KEYCODE_BUTTON_R2
                else -> null
            }
            if (asKey != null && captureKind != CaptureKind.KEYBOARD) {
                finishCapture(Captured.Key(asKey, false, name)); return true
            }
            if (captureKind == CaptureKind.CONTROLLER_ANY) {
                stickDirection(ev)?.let { (left, dir) -> finishCapture(Captured.Axis(left, dir, name)) }
            }
            return true
        }
        if (testActive.value && !gameRunning()) {
            val serial = testSerial
            val l = processedStick(ev, left = true, serial)
            val r = processedStick(ev, left = false, serial)
            testLeftStick.value = l
            testRightStick.value = r
            // Cruceta tipo HAT: se traduce como hacen los mandos que la envían como eje.
            val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val port = PadRouter.portForDevice(ev.deviceId)
            fun hat(on: Boolean, phys: Int) {
                val t = withSerial(serial) { ControllerMappings.targetForPhysical(phys, port) } ?: return
                if (on) testPressed[t] = "Mando · ${keyLabel(phys)}" else testPressed.remove(t)
            }
            hat(hy < -0.5f, KeyEvent.KEYCODE_DPAD_UP); hat(hy > 0.5f, KeyEvent.KEYCODE_DPAD_DOWN)
            hat(hx < -0.5f, KeyEvent.KEYCODE_DPAD_LEFT); hat(hx > 0.5f, KeyEvent.KEYCODE_DPAD_RIGHT)
            for ((axis, phys) in listOf(MotionEvent.AXIS_LTRIGGER to KeyEvent.KEYCODE_BUTTON_L2, MotionEvent.AXIS_RTRIGGER to KeyEvent.KEYCODE_BUTTON_R2)) {
                val v = maxOf(ev.getAxisValue(axis), ev.getAxisValue(if (axis == MotionEvent.AXIS_LTRIGGER) MotionEvent.AXIS_BRAKE else MotionEvent.AXIS_GAS))
                val t = withSerial(serial) { ControllerMappings.targetForPhysical(phys, port) } ?: continue
                if (v > 0.3f) testPressed[t] = "Mando · gatillo" else if (v < 0.1f) testPressed.remove(t)
            }
            if (abs(l.first) + abs(l.second) + abs(r.first) + abs(r.second) > 0.05f)
                testLastInput.value = "Mando · sticks"
            return true
        }
        return false
    }

    private fun stickDirection(ev: MotionEvent): Pair<Boolean, ControllerMappings.StickDir>? {
        val cands = listOf(
            Triple(true, MotionEvent.AXIS_X, MotionEvent.AXIS_Y),
            Triple(false, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ),
        )
        for ((left, ax, ay) in cands) {
            val x = ev.getAxisValue(ax); val y = ev.getAxisValue(ay)
            val key = if (left) "L" else "R"
            if (abs(x) < 0.25f && abs(y) < 0.25f) { axisLatch.remove(key); continue }
            if (key in axisLatch) continue
            if (abs(x) < 0.7f && abs(y) < 0.7f) continue
            axisLatch.add(key)
            val dir = if (abs(x) > abs(y)) {
                if (x > 0) ControllerMappings.StickDir.RIGHT else ControllerMappings.StickDir.LEFT
            } else if (y > 0) ControllerMappings.StickDir.DOWN else ControllerMappings.StickDir.UP
            return left to dir
        }
        return null
    }

    /** Stick con intercambio, inversión y zona muerta tal y como los define ARMSX2. */
    fun processedStick(ev: MotionEvent, left: Boolean, serial: String?): Pair<Float, Float> {
        var x = ev.getAxisValue(if (left) MotionEvent.AXIS_X else MotionEvent.AXIS_Z)
        var y = ev.getAxisValue(if (left) MotionEvent.AXIS_Y else MotionEvent.AXIS_RZ)
        if (ControllerMappings.stickSwapXYScope(left, serial)) { val t = x; x = y; y = t }
        if (ControllerMappings.stickInvertXScope(left, serial)) x = -x
        if (ControllerMappings.stickInvertYScope(left, serial)) y = -y
        val dz = ControllerMappings.stickDeadzone(left)
        val mag = kotlin.math.sqrt(x * x + y * y)
        if (mag < dz) return 0f to 0f
        val scale = ((mag - dz) / (1f - dz).coerceAtLeast(0.01f)).coerceIn(0f, 1f) / mag
        return (x * scale) to (y * scale)
    }

    /**
     * ControllerMappings resuelve el nivel por juego con el juego en marcha. Para probar el
     * perfil de un juego desde el menú se fija ese serial solo durante la consulta.
     */
    private inline fun <T> withSerial(serial: String?, block: () -> T): T {
        if (serial.isNullOrEmpty() || gameRunning()) return block()
        val prev = MainActivityRuntime.currentGame.value
        val fake = prev?.takeIf { it.serial == serial } ?: com.armsx2.GameInfo(android.net.Uri.EMPTY, serial, serial)
        MainActivityRuntime.currentGame.value = fake
        ControllerMappings.invalidateRuntimeCaches()
        return try { block() } finally {
            MainActivityRuntime.currentGame.value = prev
            ControllerMappings.invalidateRuntimeCaches()
        }
    }

    // ------------------------------------------------------------------ liberación

    private val allPadCodes: IntArray by lazy {
        (ControllerMappings.actions.map { it.targetKeyCode } + (110..123) + listOf(19, 20, 21, 22))
            .distinct().filter { it > 0 }.toIntArray()
    }

    /** Suelta todos los botones y ejes de [port] en el núcleo. */
    fun releasePort(port: Int) {
        if (!MainActivityRuntime.nativeReady.value) return
        for (code in allPadCodes) runCatching { NativeApp.setPadButtonForPort(port, code, 0, false) }
    }

    /** Suelta todo en todos los puertos (pérdida de foco, pausa, cambio de ventana). */
    fun releaseAll() {
        for (p in 0 until PadRouter.MAX_PADS) releasePort(p)
        clearTest()
    }

    // ------------------------------------------------------------------ nombres

    fun dirLabel(d: ControllerMappings.StickDir) = when (d) {
        ControllerMappings.StickDir.UP -> "arriba"
        ControllerMappings.StickDir.DOWN -> "abajo"
        ControllerMappings.StickDir.LEFT -> "izquierda"
        ControllerMappings.StickDir.RIGHT -> "derecha"
    }

    /** Nombre comprensible para una tecla o botón. */
    fun keyLabel(code: Int): String = when (code) {
        KeyEvent.KEYCODE_UNKNOWN -> "Sin asignar"
        KeyEvent.KEYCODE_SPACE -> "Espacio"
        KeyEvent.KEYCODE_ENTER -> "Intro"
        KeyEvent.KEYCODE_NUMPAD_ENTER -> "Intro (numérico)"
        KeyEvent.KEYCODE_DEL -> "Retroceso"
        KeyEvent.KEYCODE_FORWARD_DEL -> "Suprimir"
        KeyEvent.KEYCODE_TAB -> "Tabulador"
        KeyEvent.KEYCODE_ESCAPE -> "Esc"
        KeyEvent.KEYCODE_SHIFT_LEFT -> "Mayús izquierda"
        KeyEvent.KEYCODE_SHIFT_RIGHT -> "Mayús derecha"
        KeyEvent.KEYCODE_CTRL_LEFT -> "Ctrl izquierdo"
        KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl derecho"
        KeyEvent.KEYCODE_ALT_LEFT -> "Alt"
        KeyEvent.KEYCODE_ALT_RIGHT -> "Alt Gr"
        KeyEvent.KEYCODE_CAPS_LOCK -> "Bloq Mayús"
        KeyEvent.KEYCODE_DPAD_UP -> "Flecha arriba"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Flecha abajo"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Flecha izquierda"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Flecha derecha"
        KeyEvent.KEYCODE_DPAD_CENTER -> "Centro"
        KeyEvent.KEYCODE_PAGE_UP -> "Re Pág"
        KeyEvent.KEYCODE_PAGE_DOWN -> "Av Pág"
        KeyEvent.KEYCODE_MOVE_HOME -> "Inicio"
        KeyEvent.KEYCODE_MOVE_END -> "Fin"
        KeyEvent.KEYCODE_INSERT -> "Insert"
        KeyEvent.KEYCODE_COMMA -> "Coma"
        KeyEvent.KEYCODE_PERIOD -> "Punto"
        KeyEvent.KEYCODE_MINUS -> "Guion"
        KeyEvent.KEYCODE_EQUALS -> "Igual"
        KeyEvent.KEYCODE_SEMICOLON -> "Punto y coma"
        KeyEvent.KEYCODE_SLASH -> "Barra"
        KeyEvent.KEYCODE_BACKSLASH -> "Barra invertida"
        KeyEvent.KEYCODE_GRAVE -> "Acento grave"
        KeyEvent.KEYCODE_APOSTROPHE -> "Apóstrofo"
        KeyEvent.KEYCODE_LEFT_BRACKET -> "Corchete ["
        KeyEvent.KEYCODE_RIGHT_BRACKET -> "Corchete ]"
        KeyEvent.KEYCODE_VOLUME_UP -> "Subir volumen"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Bajar volumen"
        KeyEvent.KEYCODE_BUTTON_A -> "Botón A"
        KeyEvent.KEYCODE_BUTTON_B -> "Botón B"
        KeyEvent.KEYCODE_BUTTON_X -> "Botón X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Botón Y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "LB / L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "RB / R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "LT / L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "RT / R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "Stick izquierdo (pulsar)"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "Stick derecho (pulsar)"
        KeyEvent.KEYCODE_BUTTON_START -> "Start / Menú"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select / Vista"
        KeyEvent.KEYCODE_BUTTON_MODE -> "Botón central"
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "F${code - KeyEvent.KEYCODE_F1 + 1}"
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> "${code - KeyEvent.KEYCODE_NUMPAD_0} (numérico)"
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> "${code - KeyEvent.KEYCODE_0}"
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
        in ControllerMappings.STICK_HOTKEY_KEY_BASE until ControllerMappings.STICK_HOTKEY_KEY_BASE + 8 -> {
            val i = code - ControllerMappings.STICK_HOTKEY_KEY_BASE
            "Stick ${if (i < 4) "izquierdo" else "derecho"} ${dirLabel(ControllerMappings.StickDir.values()[i % 4])}"
        }
        else -> ControllerMappings.labelForKey(code)
    }

    /** Nombre del botón de PS2 para un código de destino de ARMSX2. */
    fun ps2Label(target: Int): String = Ps2Button.forTarget(target)?.label ?: "Código $target"
}
