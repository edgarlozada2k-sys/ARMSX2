package com.armsx2.exkinet.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armsx2.ui.settings.SettingsControllerNav
import com.armsx2.ui.settings.controllerFocusable

/**
 * Navegación con mando: ARMSX2 no usa el foco de Compose (la vista del juego es un SurfaceView)
 * sino un registro propio de controles navegables. Cada control de ExKinet se registra aquí con
 * un id estable; la cruceta/stick mueve la selección, A confirma, B vuelve, Izq/Der ajusta.
 */
@Composable
fun Modifier.exkNav(
    id: String?,
    onConfirm: (() -> Unit)?,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
): Modifier {
    if (id == null) return this
    // controllerFocusable ya dibuja el anillo azul de selección (compatible con todos los drivers).
    return this.controllerFocusable(controllerId = id, shape = shape, onConfirm = onConfirm, onLeft = onLeft, onRight = onRight)
}

fun exkIsNavSelected(id: String): Boolean = SettingsControllerNav.isSelected(id)

/** Intercepta «Atrás» (botón B, gesto o tecla) para pantallas con cambios sin guardar. */
object ExkBack {
    private val stack = ArrayList<() -> Boolean>()
    /** Compatibilidad: asigna/quita el manejador de la pantalla principal. */
    var handler: (() -> Boolean)?
        get() = stack.lastOrNull()
        set(v) { if (v == null) stack.clear() else { stack.clear(); stack.add(v) } }
    fun push(h: () -> Boolean) { stack.add(h) }
    fun pop(h: () -> Boolean) { stack.remove(h) }
    fun intercept(): Boolean = stack.lastOrNull()?.invoke() == true
}
