package com.armsx2.exkinet

import com.armsx2.exkinet.data.Framing
import com.armsx2.exkinet.input.ControlsDraft
import com.armsx2.exkinet.input.Ps2Button
import com.armsx2.exkinet.ui.controls.Conflict
import com.armsx2.input.ControllerMappings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de lógica pura de ExKinet (JVM, sin dispositivo). */
class ExkLogicTest {

    @Test fun controlKeysCoverGeneralAndPerGameTiers() {
        assertTrue(ControlsDraft.isControlKey("pad.map.cross"))
        assertTrue(ControlsDraft.isControlKey("p2.pad.map.cross"))
        assertTrue(ControlsDraft.isControlKey("kb.map.start"))
        assertTrue(ControlsDraft.isControlKey("game.SCES-54552.kb.map.start"))
        assertTrue(ControlsDraft.isControlKey("game.SCES-54552.pad.map.l1"))
        assertTrue(ControlsDraft.isControlKey("touch.layout.game.SCES-54552.portrait"))
        assertTrue(ControlsDraft.isControlKey("pad_router_pinned"))
        // Ajustes que NO son controles no deben tocarse al cancelar.
        assertFalse(ControlsDraft.isControlKey("config.global"))
        assertFalse(ControlsDraft.isControlKey("library.customName.SCES-54552"))
        assertFalse(ControlsDraft.isControlKey("game.SCES-54552.renderer"))
    }

    @Test fun framingIsClampedAndNeverDistorts() {
        val f = Framing.from(JSONObject().put("x", 1.7).put("y", -2.0).put("zoom", 9.0))
        assertEquals(1f, f.x, 0f)
        assertEquals(0f, f.y, 0f)
        assertEquals(3f, f.zoom, 0f)
        assertEquals(Framing(), Framing.from(null))
    }

    @Test fun everyPs2ButtonMapsToARealArmsx2Action() {
        val ids = ControllerMappings.actions.map { it.id }.toSet()
        for (b in Ps2Button.entries) assertTrue("${b.actionId} no existe en ARMSX2", b.actionId in ids)
        // El asistente pide todos los botones salvo Analog (sin tecla estándar).
        assertEquals(Ps2Button.entries.size - 1, Ps2Button.wizardOrder.size)
        assertEquals(Ps2Button.CROSS, Ps2Button.forTarget(Ps2Button.CROSS.target))
    }

    @Test fun onlyHotkeyModifiersCanKeepBothBindings() {
        assertFalse(Conflict.OtherButton(Ps2Button.CROSS).canKeepBoth)
        assertFalse(Conflict.Hotkey(ControllerMappings.SysHotkey.MENU, asModifier = false).canKeepBoth)
        assertTrue(Conflict.Hotkey(ControllerMappings.SysHotkey.MENU, asModifier = true).canKeepBoth)
    }
}
