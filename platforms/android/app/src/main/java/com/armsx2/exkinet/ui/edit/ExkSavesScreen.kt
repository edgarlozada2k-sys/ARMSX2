package com.armsx2.exkinet.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.GameInfo
import com.armsx2.SaveSlotLookup
import com.armsx2.exkinet.ui.ExkPanel
import com.armsx2.exkinet.ui.ExkRow
import com.armsx2.exkinet.ui.ExkSecondaryButton
import com.armsx2.exkinet.ui.LocalExk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Partidas: estados rápidos del juego (cargar uno arranca el juego en ese punto) y accesos a la
 *  gestión completa de ARMSX2 (estados y tarjetas de memoria, donde viven los guardados normales). */
@Composable
fun ExkSavesScreen(game: GameInfo, onLoadSlot: (Int) -> Unit, onManageStates: () -> Unit, onMemoryCards: () -> Unit, onClose: () -> Unit) {
    val c = LocalExk.current
    val context = LocalContext.current
    val slots = remember(game.serial) { SaveSlotLookup.slotsFor(context, game.serial).sortedByDescending { it.modified } }
    val fmt = remember { SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es")) }
    Box(Modifier.fillMaxSize().background(c.screenBrush)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 64.dp, vertical = 24.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PARTIDAS", color = c.accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text(game.title, color = c.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                ExkSecondaryButton("← Volver", onClose, navId = "exk.saves.back")
            }
            Spacer(Modifier.height(18.dp))
            ExkPanel(Modifier.widthIn(max = 860.dp)) {
                Column {
                    Text("Estados rápidos", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Guardados en cualquier momento desde el menú del juego. «Continuar» en Inicio carga el más reciente.", color = c.textMuted, fontSize = 12.sp)
                    if (slots.isEmpty()) Text("Este juego todavía no tiene estados rápidos.", color = c.textMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
                    slots.forEach { s ->
                        ExkRow("Ranura ${s.slot}", fmt.format(Date(s.modified))) {
                            ExkSecondaryButton("Cargar", { onLoadSlot(s.slot) }, compact = true, navId = "exk.saves.slot.${s.slot}")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Guardados normales", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Los que hace el propio juego van a la tarjeta de memoria virtual, como en la consola.", color = c.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExkSecondaryButton("Tarjetas de memoria", onMemoryCards, navId = "exk.saves.mc")
                        ExkSecondaryButton("Gestionar todos los estados", onManageStates, navId = "exk.saves.all")
                    }
                }
            }
        }
    }
}
