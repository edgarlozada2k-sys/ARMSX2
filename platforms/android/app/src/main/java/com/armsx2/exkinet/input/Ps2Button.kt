package com.armsx2.exkinet.input

import com.armsx2.input.ControllerMappings

/**
 * Botones de PS2 que ExKinet muestra, enlazados a las acciones reales de ARMSX2
 * ([ControllerMappings.actions], por id) y a su código de destino en el núcleo.
 */
enum class Ps2Button(val actionId: String, val label: String, val group: Group) {
    DPAD_UP("dpad_up", "Cruceta arriba", Group.DPAD),
    DPAD_DOWN("dpad_down", "Cruceta abajo", Group.DPAD),
    DPAD_LEFT("dpad_left", "Cruceta izquierda", Group.DPAD),
    DPAD_RIGHT("dpad_right", "Cruceta derecha", Group.DPAD),
    CROSS("cross", "Cruz ✕", Group.FACE),
    CIRCLE("circle", "Círculo ○", Group.FACE),
    SQUARE("square", "Cuadrado □", Group.FACE),
    TRIANGLE("triangle", "Triángulo △", Group.FACE),
    L1("l1", "L1", Group.SHOULDER),
    L2("l2", "L2", Group.SHOULDER),
    R1("r1", "R1", Group.SHOULDER),
    R2("r2", "R2", Group.SHOULDER),
    L3("l3", "L3 (pulsar stick izquierdo)", Group.STICK_CLICK),
    R3("r3", "R3 (pulsar stick derecho)", Group.STICK_CLICK),
    SELECT("select", "Select", Group.MENU),
    START("start", "Start", Group.MENU),
    ANALOG("analog", "Analog (modo analógico)", Group.MENU),
    LS_UP("ls_up", "Stick izquierdo arriba", Group.LEFT_STICK),
    LS_DOWN("ls_down", "Stick izquierdo abajo", Group.LEFT_STICK),
    LS_LEFT("ls_left", "Stick izquierdo izquierda", Group.LEFT_STICK),
    LS_RIGHT("ls_right", "Stick izquierdo derecha", Group.LEFT_STICK),
    RS_UP("rs_up", "Stick derecho arriba", Group.RIGHT_STICK),
    RS_DOWN("rs_down", "Stick derecho abajo", Group.RIGHT_STICK),
    RS_LEFT("rs_left", "Stick derecho izquierda", Group.RIGHT_STICK),
    RS_RIGHT("rs_right", "Stick derecho derecha", Group.RIGHT_STICK);

    enum class Group(val title: String) {
        DPAD("Cruceta"), FACE("Botones frontales"), SHOULDER("Gatillos y bumpers"),
        STICK_CLICK("Clic de los sticks"), MENU("Start, Select y Analog"),
        LEFT_STICK("Stick izquierdo"), RIGHT_STICK("Stick derecho"),
    }

    val action: ControllerMappings.Action
        get() = ControllerMappings.actions.first { it.id == actionId }

    val target: Int get() = action.targetKeyCode

    companion object {
        fun forTarget(target: Int): Ps2Button? = entries.firstOrNull { it.target == target }
        fun forAction(id: String): Ps2Button? = entries.firstOrNull { it.actionId == id }
        /** Orden de «Configurar paso a paso». */
        val wizardOrder: List<Ps2Button> = listOf(
            DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
            LS_UP, LS_DOWN, LS_LEFT, LS_RIGHT,
            RS_UP, RS_DOWN, RS_LEFT, RS_RIGHT,
            CROSS, CIRCLE, SQUARE, TRIANGLE,
            L1, R1, L2, R2, L3, R3, SELECT, START,
        )
    }
}
