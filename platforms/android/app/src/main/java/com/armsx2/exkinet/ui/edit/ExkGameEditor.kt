package com.armsx2.exkinet.ui.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.armsx2.CustomNames
import com.armsx2.GameInfo
import com.armsx2.exkinet.data.ExkDefaults
import com.armsx2.exkinet.data.ExkMedia
import com.armsx2.exkinet.data.ExkStore
import com.armsx2.exkinet.data.Framing
import com.armsx2.exkinet.data.MediaRef
import com.armsx2.exkinet.ui.ExkPanel
import com.armsx2.exkinet.ui.ExkPrimaryButton
import com.armsx2.exkinet.ui.ExkSecondaryButton
import com.armsx2.exkinet.ui.ExkToast
import com.armsx2.exkinet.ui.LocalExk
import com.armsx2.exkinet.ui.home.alignment
import com.armsx2.exkinet.ui.home.coverModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editar ficha: nombre, descripción, portada y fondo por separado. Las imágenes se encuadran
 * arrastrando y pellizcando (o con los botones): se recortan sin deformarse nunca.
 */
@Composable
fun ExkGameEditor(game: GameInfo, onClose: () -> Unit) {
    val c = LocalExk.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta = ExkStore.meta(game)
    var name by remember { mutableStateOf(CustomNames.stored(game.settingsKey) ?: game.title) }
    var desc by remember { mutableStateOf(meta.description ?: ExkDefaults.description(game) ?: "") }
    var coverFraming by remember { mutableStateOf(meta.cover?.framing ?: Framing()) }
    var bgFraming by remember { mutableStateOf(meta.background?.framing ?: Framing(0.7f, 0.57f)) }
    var coverFile by remember { mutableStateOf(meta.cover?.file) }
    var bgFile by remember { mutableStateOf(meta.background?.file) }
    val key = ExkStore.key(game)

    fun importImage(uri: Uri, kind: String, done: (String) -> Unit) {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ExkMedia.importImage(context, uri, key, kind, if (kind == "cover") ExkMedia.COVER_MAX else ExkMedia.BACKGROUND_MAX) }
            }
            r.onSuccess(done).onFailure { ExkToast.show(it.message ?: "No se pudo importar la imagen") }
        }
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) importImage(u, "cover") { coverFile = it; coverFraming = Framing() }
    }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) importImage(u, "background") { bgFile = it; bgFraming = Framing() }
    }

    fun save() {
        CustomNames.setName(game.settingsKey, name.trim().takeIf { it.isNotEmpty() && it != game.title })
        val old = ExkStore.meta(game)
        if (old.cover?.file != coverFile) ExkMedia.delete(context, old.cover?.file)
        if (old.background?.file != bgFile) ExkMedia.delete(context, old.background?.file)
        ExkStore.update(game) {
            it.copy(
                description = desc.trim().takeIf { d -> d.isNotEmpty() },
                cover = coverFile?.let { f -> MediaRef(f, coverFraming) },
                background = bgFile?.let { f -> MediaRef(f, bgFraming) },
            )
        }
        ExkToast.show("Ficha guardada")
        onClose()
    }

    Box(Modifier.fillMaxSize().background(c.screenBrush)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 48.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EDITAR FICHA", color = c.accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text(game.title, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                ExkSecondaryButton("Cancelar", onClose, navId = "exk.edit.cancel")
                Spacer(Modifier.width(10.dp))
                ExkPrimaryButton("Guardar", { save() }, navId = "exk.edit.save")
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(0.42f).verticalScroll(rememberScrollState())) {
                    ExkPanel {
                        Column {
                            Field("Nombre", name, singleLine = true) { name = it }
                            Spacer(Modifier.height(14.dp))
                            Field("Descripción", desc, singleLine = false) { desc = it }
                            Spacer(Modifier.height(14.dp))
                            Text(listOfNotNull(game.serial, game.extension.ifBlank { null }).joinToString(" · "), color = c.textMuted, fontSize = 12.sp)
                            Text(game.uri.lastPathSegment?.substringAfterLast('/') ?: "", color = c.textMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ExkPanel {
                        Column {
                            Text("Portada", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Arrastra para encuadrar y pellizca para acercar. Nunca se deforma.", color = c.textMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.width(220.dp)) {
                                Framer(model = coverFile?.let { ExkStore.file(context, it) } ?: coverModel(context, game), ratio = 0.78f,
                                    framing = coverFraming, onChange = { coverFraming = it }, editable = coverFile != null)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExkSecondaryButton("Elegir imagen", { pickCover.launch(arrayOf("image/*")) }, compact = true, navId = "exk.edit.cover")
                                if (coverFile != null) ExkSecondaryButton("Quitar", { coverFile = null }, compact = true, danger = true)
                            }
                        }
                    }
                }
                Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState())) {
                    ExkPanel {
                        Column {
                            Text("Fondo", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Imagen independiente de la portada, en formato panorámico. La vista coincide con la pantalla de Inicio.", color = c.textMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            val bgModel = bgFile?.let { ExkStore.file(context, it) } ?: ExkDefaults.backgroundAsset(game)?.let { "file:///android_asset/$it" }
                            if (bgModel != null) Framer(bgModel, 16f / 10f, bgFraming, { bgFraming = it }, editable = true)
                            else Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0C1725)),
                                contentAlignment = Alignment.Center) { Text("Sin fondo: se usa un degradado ambiental", color = c.textMuted, fontSize = 13.sp) }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExkSecondaryButton("Elegir imagen", { pickBg.launch(arrayOf("image/*")) }, compact = true, navId = "exk.edit.bg")
                                ExkSecondaryButton("◀", { bgFraming = bgFraming.copy(x = (bgFraming.x - 0.05f).coerceIn(0f, 1f)) }, compact = true)
                                ExkSecondaryButton("▶", { bgFraming = bgFraming.copy(x = (bgFraming.x + 0.05f).coerceIn(0f, 1f)) }, compact = true)
                                ExkSecondaryButton("▲", { bgFraming = bgFraming.copy(y = (bgFraming.y - 0.05f).coerceIn(0f, 1f)) }, compact = true)
                                ExkSecondaryButton("▼", { bgFraming = bgFraming.copy(y = (bgFraming.y + 0.05f).coerceIn(0f, 1f)) }, compact = true)
                                if (bgFile != null) ExkSecondaryButton("Quitar", { bgFile = null }, compact = true, danger = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean, onChange: (String) -> Unit) {
    val c = LocalExk.current
    Text(label, color = c.textSecondary, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF20232F)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)).padding(12.dp)) {
        BasicTextField(value, onChange, singleLine = singleLine, minLines = if (singleLine) 1 else 4,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
    }
}

/** Vista de encuadre: recorta con ContentScale.Crop + alineación + zoom (sin deformar). */
@Composable
private fun Framer(model: Any?, ratio: Float, framing: Framing, onChange: (Framing) -> Unit, editable: Boolean) {
    // Estado vigente para el gesto (el detector no se reinicia con cada cambio).
    val cur = androidx.compose.runtime.rememberUpdatedState(framing)
    val change = androidx.compose.runtime.rememberUpdatedState(onChange)
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0C1725))
            .border(1.dp, Color(0x5584DDFF), RoundedCornerShape(12.dp))
            .pointerInput(editable) {
                if (editable) detectTransformGestures { _, pan, zoom, _ ->
                    val f = cur.value
                    val z = (f.zoom * zoom).coerceIn(1f, 3f)
                    change.value(Framing((f.x - pan.x / size.width).coerceIn(0f, 1f), (f.y - pan.y / size.height).coerceIn(0f, 1f), z))
                }
            },
    ) {
        AsyncImage(model, contentDescription = null, contentScale = ContentScale.Crop, alignment = framing.alignment(),
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = framing.zoom; scaleY = framing.zoom })
    }
}
