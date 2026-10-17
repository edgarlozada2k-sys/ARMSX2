package com.armsx2.exkinet.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armsx2.GameInfo
import com.armsx2.TexturePackInstaller
import com.armsx2.config.SettingsScope
import com.armsx2.exkinet.data.ExkBackup
import com.armsx2.exkinet.data.ExkMedia
import com.armsx2.exkinet.data.ExkStore
import com.armsx2.exkinet.data.ExkThemeMode
import com.armsx2.exkinet.ui.ExkBadge
import com.armsx2.exkinet.ui.ExkChoice
import com.armsx2.exkinet.ui.ExkPanel
import com.armsx2.exkinet.ui.ExkPrimaryButton
import com.armsx2.exkinet.ui.ExkRow
import com.armsx2.exkinet.ui.ExkSecondaryButton
import com.armsx2.exkinet.ui.ExkSwitch
import com.armsx2.exkinet.ui.ExkToast
import com.armsx2.exkinet.ui.LocalExk
import com.armsx2.exkinet.ui.controls.ExkDialog
import com.armsx2.exkinet.ui.exkNav
import com.armsx2.exkinet.ui.home.ExkTab
import com.armsx2.exkinet.ui.home.ExkTopBar
import com.armsx2.navigation.SettingsCategory
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.InGameOverlay
import com.armsx2.ui.settingshub.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ExkSettingsPage(val title: String, val icon: String) {
    GENERAL("General", "◉"), APPEARANCE("Apariencia y movimiento", "✧"), GRAPHICS("Gráficos por juego", "⚙"),
    MEDIA("Fondos y vídeos", "▷"), TEXTURES("Texturas HD", "▱"), CONTROLS("Controles", "⊕"), BACKUP("Copias y restauración", "↓"),
}

data class ExkSettingsActions(
    val onTab: (ExkTab) -> Unit,
    val onOpenControls: (GameInfo?) -> Unit,
    val onOpenArmsx2Settings: (GameInfo?) -> Unit,
    val onOpenBios: () -> Unit,
    val onEditGame: (GameInfo) -> Unit,
    val onAddFolder: () -> Unit,
    val onRefreshLibrary: () -> Unit,
)

@Composable
fun ExkSettingsScreen(start: ExkSettingsPage, initialGame: GameInfo?, games: List<GameInfo>, actions: ExkSettingsActions) {
    val c = LocalExk.current
    var page by remember { mutableStateOf(start) }
    var game by remember { mutableStateOf(initialGame ?: games.firstOrNull()) }
    var pickGame by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.screenBrush)) {
        Column(Modifier.fillMaxSize()) {
            ExkTopBar(ExkTab.SETTINGS, actions.onTab, onSearch = { actions.onTab(ExkTab.LIBRARY) }, transparent = false)
            Row(Modifier.padding(horizontal = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EXKINET / CONFIGURACIÓN", color = c.accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("Ajustes", color = c.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("Tu consola, a tu manera.", color = c.textMuted, fontSize = 14.sp)
                }
                ExkSecondaryButton("← Volver al inicio", { actions.onTab(ExkTab.HOME) }, navId = "exk.set.home")
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.weight(1f).padding(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.width(270.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExkSettingsPage.entries.forEach { p -> NavItem(p, p == page) { if (p == ExkSettingsPage.CONTROLS) actions.onOpenControls(game) else page = p } }
                    Spacer(Modifier.height(12.dp))
                    NavItemPlain("☰  Ajustes avanzados de ARMSX2", "exk.set.advanced") { actions.onOpenArmsx2Settings(null) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
                    ExkPanel(Modifier.widthIn(max = 900.dp)) {
                        Column {
                            if (page in setOf(ExkSettingsPage.GRAPHICS, ExkSettingsPage.TEXTURES)) {
                                GameHeader(game) { pickGame = true }
                            }
                            when (page) {
                                ExkSettingsPage.GENERAL -> GeneralPanel(game, actions)
                                ExkSettingsPage.APPEARANCE -> AppearancePanel()
                                ExkSettingsPage.GRAPHICS -> game?.let { GraphicsPanel(it) } ?: NoGame()
                                ExkSettingsPage.MEDIA -> MediaPanel(games, actions)
                                ExkSettingsPage.TEXTURES -> game?.let { TexturesPanel(it) } ?: NoGame()
                                ExkSettingsPage.CONTROLS -> Unit
                                ExkSettingsPage.BACKUP -> BackupPanel(games)
                            }
                        }
                    }
                }
            }
        }
        if (pickGame) GamePicker(games, onPick = { game = it; pickGame = false }, onDismiss = { pickGame = false })
    }
}

@Composable
private fun NavItem(p: ExkSettingsPage, active: Boolean, onClick: () -> Unit) {
    val c = LocalExk.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (active) Color(0x350D67D7) else Color.Transparent)
            .exkNav("exk.set.page.${p.name}", onClick, shape = shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) Box(Modifier.size(width = 3.dp, height = 18.dp).background(Color(0xFF3CAAFF)))
        Spacer(Modifier.width(10.dp))
        Text("${p.icon}   ${p.title}", color = if (active) Color(0xFFE7F4FF) else c.textMuted, fontSize = 14.sp)
    }
}

@Composable
private fun NavItemPlain(text: String, navId: String, onClick: () -> Unit) {
    val c = LocalExk.current
    Text(text, color = c.textSecondary, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).exkNav(navId, onClick).clickable(onClick = onClick).padding(18.dp))
}

@Composable
private fun GameHeader(game: GameInfo?, onChange: () -> Unit) {
    val c = LocalExk.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(game?.title ?: "Ningún juego", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(game?.serial, regionOf(game?.serial)).joinToString(" · ").ifEmpty { "Sin serial reconocido" }, color = c.textMuted, fontSize = 12.sp)
        }
        ExkSecondaryButton("Cambiar juego", onChange, compact = true, navId = "exk.set.pickgame")
    }
    Spacer(Modifier.height(10.dp))
}

fun regionOf(serial: String?): String? {
    val p = serial?.take(4)?.uppercase() ?: return null
    return when (p) {
        "SCUS", "SLUS" -> "NTSC-U"; "SCES", "SLES", "SCED" -> "PAL"
        "SCPS", "SLPS", "SLPM", "SCAJ", "SLAJ" -> "NTSC-J"; "SCKA", "SLKA" -> "NTSC-K"
        else -> null
    }
}

@Composable private fun NoGame() {
    Text("Añade un juego a la biblioteca para configurarlo.", color = LocalExk.current.textMuted, fontSize = 14.sp)
}

@Composable
private fun GamePicker(games: List<GameInfo>, onPick: (GameInfo) -> Unit, onDismiss: () -> Unit) {
    val c = LocalExk.current
    com.armsx2.ui.common.PadModal(key = "exk.gamepicker", onDismiss = onDismiss, initialFocusId = "exk.pick.0") {
        ExkPanel(Modifier.widthIn(max = 520.dp).height(520.dp)) {
            Column {
                Text("Elige un juego", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                    items(games.size) { i ->
                        val g = games[i]
                        Text(g.title + (g.serial?.let { "  ·  $it" } ?: ""), color = c.text, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .exkNav("exk.pick.$i", { onPick(g) }).clickable { onPick(g) }.padding(14.dp))
                    }
                }
                ExkSecondaryButton("Cerrar", onDismiss, navId = "exk.pick.close")
            }
        }
    }
}

// ------------------------------------------------------------------- General

@Composable
private fun GeneralPanel(game: GameInfo?, a: ExkSettingsActions) {
    val c = LocalExk.current
    val prefs = ExkStore.prefs.value
    Text("Todo en su sitio", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("ExKinet funciona sobre el motor ARMSX2 (GPL-3.0), integrado en esta misma aplicación.", color = c.textMuted, fontSize = 13.sp)
    ExkRow("Dispositivo", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
    ExkRow("Motor", "ARMSX2 ${runCatching { kr.co.iefriends.pcsx2.NativeApp.getBuildVersion() }.getOrNull().orEmpty()} · listo: ${if (MainActivityRuntime.nativeReady.value) "sí" else "iniciando"}")
    ExkRow("Juego seleccionado", game?.title ?: "Ninguno")
    val dirs = MainActivityRuntime.romsDirs.value
    ExkRow("Carpetas de juegos", if (dirs.isEmpty()) "Ninguna todavía" else "${dirs.size} carpeta(s). Los juegos se buscan también en subcarpetas.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExkSecondaryButton("＋ Añadir", a.onAddFolder, compact = true, navId = "exk.set.addfolder")
            ExkSecondaryButton("Actualizar", a.onRefreshLibrary, compact = true, navId = "exk.set.rescan")
        }
    }
    ExkRow("BIOS", "Necesaria para jugar. Usa el volcado de tu propia consola.") {
        ExkSecondaryButton("Gestionar BIOS", a.onOpenBios, compact = true, navId = "exk.set.bios")
    }
    ExkRow("Interfaz clásica de ARMSX2", "Vuelve a la pantalla original de ARMSX2. Se recupera desde su menú.") {
        ExkSwitch(prefs.classicUi, { v -> ExkStore.setPrefs { it.copy(classicUi = v) } }, navId = "exk.set.classic")
    }
}

// ------------------------------------------------------------------- Apariencia

@Composable
private fun AppearancePanel() {
    val c = LocalExk.current
    val p = ExkStore.prefs.value
    Text("Apariencia y movimiento", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Degradado cinematográfico, selección luminosa y transiciones suaves.", color = c.textMuted, fontSize = 13.sp)
    ExkRow("Reducir animaciones", "Desactiva el movimiento de las tarjetas, los fundidos y los vídeos automáticos.") {
        ExkSwitch(p.reduceMotion, { v -> ExkStore.setPrefs { it.copy(reduceMotion = v) } }, navId = "exk.set.reduce")
    }
    ExkRow("Tema", "El Inicio mantiene siempre el fondo oscuro para que el texto se lea sobre las imágenes.") {
        ExkChoice(listOf(ExkThemeMode.DARK to "Oscuro", ExkThemeMode.LIGHT to "Claro"), p.theme, { v -> ExkStore.setPrefs { it.copy(theme = v) } }, navBase = "exk.set.theme")
    }
}

// ------------------------------------------------------------------- Gráficos

private enum class Profile(val label: String, val scale: Float, val detail: String) {
    PERFORMANCE("Rendimiento", 1f, "Resolución original (1×). La opción más fluida."),
    BALANCED("Equilibrado", 2f, "2× la resolución interna. Buen punto de partida en la Yoga Tab 13."),
    QUALITY("Calidad", 3f, "3×. Más nítido y más exigente: comprueba que el juego siga fluido."),
}

@Composable
private fun GraphicsPanel(game: GameInfo) {
    val c = LocalExk.current
    val vm: SettingsViewModel = viewModel(key = "exk-graphics")
    var perGame by remember(game.uri) { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(game.uri, perGame) { vm.load(SettingsCategory.Graphics, if (perGame) game else null); tick++ }
    @Suppress("UNUSED_EXPRESSION") tick
    val s = vm.settings.value
    fun save(f: (com.armsx2.config.Settings) -> com.armsx2.config.Settings) { InGameOverlay.saveSettings(f(vm.settings.value)); tick++ }

    Text("Tu imagen, a tu manera", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Aplicar a:", color = c.textMuted, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        ExkChoice(listOf(true to "Solo este juego", false to "Todos los juegos"), perGame, { perGame = it }, navBase = "exk.gfx.scope")
    }
    Text(if (InGameOverlay.settingsScope.value == SettingsScope.Game) "Cambios guardados solo para este juego (el resto hereda la configuración general)." else "Cambios guardados en la configuración general.",
        color = c.textMuted, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    val currentProfile = Profile.entries.firstOrNull { kotlin.math.abs(it.scale - s.output.upscaleFloat) < 0.01f }
    ExkRow("Perfil", currentProfile?.detail ?: "Personalizado (${s.output.upscaleFloat}×)") {
        ExkChoice(Profile.entries.map { it to it.label }, currentProfile ?: Profile.BALANCED,
            { p -> save { it.copy(output = it.output.copy(upscaleFloat = p.scale)) } }, navBase = "exk.gfx.profile")
    }
    ExkRow("Resolución interna 3D", "Aumenta la definición de los polígonos y bordes. No cambia las texturas originales ni los vídeos.") {
        ExkChoice(listOf(1f to "1×", 2f to "2×", 3f to "3×", 4f to "4×"), listOf(1f, 2f, 3f, 4f).minByOrNull { kotlin.math.abs(it - s.output.upscaleFloat) } ?: 1f,
            { v -> save { it.copy(output = it.output.copy(upscaleFloat = v)) } }, navBase = "exk.gfx.res")
    }
    ExkRow("Renderizador", "Vulkan suele rendir mejor en Adreno 650. «Automático» deja elegir a ARMSX2.") {
        ExkChoice(listOf("auto" to "Auto", "vulkan" to "Vulkan", "opengl" to "OpenGL"), s.output.renderer,
            { v -> save { it.copy(output = it.output.copy(renderer = v)) } }, navBase = "exk.gfx.renderer")
    }
    ExkRow("Filtrado de texturas", "Suaviza las texturas al ampliarlas. «PS2» respeta lo que pide el juego.") {
        ExkChoice(listOf(0 to "Nítido", 2 to "PS2", 1 to "Suave"), s.graphics.textureFiltering,
            { v -> save { it.copy(graphics = it.graphics.copy(textureFiltering = v)) } }, navBase = "exk.gfx.filter")
    }
    ExkRow("Antialiasing FXAA", "Reduce los dientes de sierra con un coste bajo.") {
        ExkSwitch(s.graphics.fxaa, { v -> save { it.copy(graphics = it.graphics.copy(fxaa = v)) } }, navId = "exk.gfx.fxaa")
    }
    ExkRow("Vídeos pregrabados por software", "Corrige vídeos con errores en algunos juegos. NO los convierte en HD: los vídeos pregrabados conservan su resolución original.") {
        ExkSwitch(s.emuCore.gamefixSoftwareRendererFmv, { v -> save { it.copy(emuCore = it.emuCore.copy(gamefixSoftwareRendererFmv = v)) } }, navId = "exk.gfx.fmv")
    }
    Spacer(Modifier.height(12.dp))
    Text("Resolución 3D, texturas HD y vídeos son cosas distintas: subir la resolución afila el 3D; las texturas HD dependen de un paquete para tu versión del juego; los vídeos pregrabados no mejoran con ninguna de las dos.",
        color = c.textMuted, fontSize = 12.sp)
    if (game.serial?.uppercase() in setOf("SCUS-97490", "SCES-54552", "SCPS-15102", "SCAJ-20162")) {
        Spacer(Modifier.height(8.dp))
        Text("Rogue Galaxy: ARMSX2 aplica automáticamente sus correcciones (mezcla nivel 4, escalado nativo de efectos). Empieza en 2× y comprueba la fluidez; no se han medido FPS en tu tablet.",
            color = c.warning, fontSize = 12.sp)
    }
    Spacer(Modifier.height(14.dp))
    ExkSecondaryButton("Restaurar valores predeterminados", { vm.resetCurrentScope(SettingsCategory.Graphics); vm.resetCurrentScope(SettingsCategory.Performance); tick++
        ExkToast.show("Valores predeterminados restaurados") }, navId = "exk.gfx.reset")
}

// ------------------------------------------------------------------- Fondos y vídeos

@Composable
private fun MediaPanel(games: List<GameInfo>, a: ExkSettingsActions) {
    val c = LocalExk.current
    val p = ExkStore.prefs.value
    val context = LocalContext.current
    var target by remember { mutableStateOf<GameInfo?>(null) }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val g = target ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        ExkMedia.probeVideo(context, uri).onSuccess { v ->
            val old = ExkStore.meta(g).video
            ExkStore.update(g) { it.copy(video = v) }
            if (old != null && old.uri != v.uri) ExkMedia.releaseVideo(context, old)
            ExkToast.show("Vídeo listo. Selecciona ${g.title} en Inicio.")
        }.onFailure { ExkToast.show(it.message ?: "Vídeo no compatible") }
    }
    Text("Primero imagen. Después vídeo.", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Al elegir un juego se muestra su fondo. Tras la espera, el vídeo aparece con un fundido, en bucle y sin sonido.", color = c.textMuted, fontSize = 13.sp)
    ExkRow("Vídeos automáticos", if (p.reduceMotion) "Desactivados mientras «Reducir animaciones» esté activo." else null) {
        ExkSwitch(p.autoVideo, { v -> ExkStore.setPrefs { it.copy(autoVideo = v) } }, navId = "exk.media.auto")
    }
    ExkRow("Espera antes del vídeo") {
        ExkChoice(listOf(2 to "2 s", 3 to "3 s", 5 to "5 s"), p.videoDelaySec, { v -> ExkStore.setPrefs { it.copy(videoDelaySec = v) } }, navBase = "exk.media.delay")
    }
    Text("Los vídeos se guardan con permiso permanente de Android (siguen tras reiniciar). Formatos: MP4 o WebM que el dispositivo pueda decodificar; se comprueban al añadirlos.",
        color = c.textMuted, fontSize = 11.sp)
    games.forEach { g ->
        val v = ExkStore.meta(g).video
        ExkRow(g.title, v?.let { "${it.name} · ${it.width}×${it.height}" } ?: "Sin vídeo · se mostrará la imagen") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExkSecondaryButton("Fondo", { a.onEditGame(g) }, compact = true, navId = "exk.media.bg.${ExkStore.key(g)}")
                ExkSecondaryButton(if (v == null) "＋ Vídeo" else "Cambiar", { target = g; pickVideo.launch(arrayOf("video/mp4", "video/webm", "video/*")) }, compact = true, navId = "exk.media.vid.${ExkStore.key(g)}")
                if (v != null) ExkSecondaryButton("Quitar", { ExkStore.update(g) { it.copy(video = null) }; ExkMedia.releaseVideo(context, v) }, compact = true, danger = true)
            }
        }
    }
}

// ------------------------------------------------------------------- Texturas

private fun humanSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format("%.1f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "${b / 1024} KB"
    else -> "$b B"
}

/** Serial que declara un ZIP de texturas por sus carpetas (p. ej. textures/SCES-54552/…), si lo hay. */
private fun serialInZip(context: Context, uri: Uri): String? = runCatching {
    java.util.zip.ZipInputStream(context.contentResolver.openInputStream(uri)!!.buffered()).use { z ->
        var n = 0
        while (n++ < 4000) {
            val e = z.nextEntry ?: break
            Regex("""(?i)\b([A-Z]{4}-\d{5})\b""").find(e.name)?.let { return it.groupValues[1].uppercase() }
        }
        null
    }
}.getOrNull()

@Composable
private fun TexturesPanel(game: GameInfo) {
    val c = LocalExk.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serial = game.serial?.takeIf { it.isNotBlank() }?.uppercase()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var cancel by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var mismatch by remember { mutableStateOf<String?>(null) }
    val vm: SettingsViewModel = viewModel(key = "exk-textures")
    LaunchedEffect(game.uri) { vm.load(SettingsCategory.Graphics, game); tick++ }
    val root = File(MainActivityRuntime.assetCopyRoot(context))
    val packDir = serial?.let { File(root, "textures/$it/replacements") }
    val size = remember(tick, serial) { packDir?.let { ExkBackup.dirSize(it) } ?: 0L }
    val free = remember(tick) { runCatching { StatFs(root.path).availableBytes }.getOrDefault(0L) }
    @Suppress("UNUSED_EXPRESSION") tick
    val enabled = vm.settings.value.graphics.loadTextureReplacements

    fun install(uri: Uri) {
        val s = serial ?: return
        busy = "Preparando…"; cancel = false
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                TexturePackInstaller.installLocal(context, uri, s, onProgress = { p ->
                    busy = when (p) {
                        is TexturePackInstaller.Progress.Extracting -> "Extrayendo ${p.done}/${p.total}…"
                        TexturePackInstaller.Progress.Installing -> "Instalando…"
                        TexturePackInstaller.Progress.Verifying -> "Comprobando…"
                        is TexturePackInstaller.Progress.Downloading -> "Leyendo…"
                    }
                }, isCancelled = { cancel })
            }
            busy = null
            if (out.ok) {
                InGameOverlay.saveSettings(vm.settings.value.let { it.copy(graphics = it.graphics.copy(loadTextureReplacements = true)) })
                ExkToast.show("Paquete instalado: ${out.textures} texturas")
            } else ExkToast.show(out.error?.let { "No se pudo instalar: $it" } ?: "Instalación cancelada")
            tick++
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || serial == null) return@rememberLauncherForActivityResult
        val declared = serialInZip(context, uri)
        if (declared != null && declared != serial) { pendingUri = uri; mismatch = declared } else install(uri)
    }

    Text("Texturas HD", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (serial == null) {
        Text("Este juego no tiene serial: no se puede asociar un paquete de texturas.", color = c.warning, fontSize = 13.sp); return
    }
    Text("Los paquetes sustituyen texturas concretas cuando el juego las usa. Deben estar hechos para tu versión ($serial). Las texturas originales siempre siguen disponibles.",
        color = c.textMuted, fontSize = 13.sp)
    ExkRow("Paquete instalado", if (size > 0) "${humanSize(size)} en textures/$serial" else "Ninguno", badge = if (size > 0 && enabled) "ACTIVO" else null)
    ExkRow("Usar texturas HD en este juego", "Desactívalo para volver a las originales sin borrar el paquete.") {
        ExkSwitch(enabled, { v -> InGameOverlay.saveSettings(vm.settings.value.let { it.copy(graphics = it.graphics.copy(loadTextureReplacements = v)) }); tick++ },
            enabled = size > 0, navId = "exk.tex.enable")
    }
    ExkRow("Espacio libre", humanSize(free))
    Spacer(Modifier.height(10.dp))
    if (busy != null) {
        Text(busy!!, color = c.glow, fontSize = 14.sp)
        ExkSecondaryButton("Cancelar", { cancel = true }, compact = true, navId = "exk.tex.cancel")
    } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExkPrimaryButton("＋ Importar paquete (ZIP)", { pick.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) }, navId = "exk.tex.import")
        if (size > 0) ExkSecondaryButton("Eliminar paquete", {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { TexturePackInstaller.removePack(File(root, "textures/$serial")) }
                ExkToast.show(if (ok) "Paquete eliminado" else "No se pudo eliminar por completo"); tick++
            }
        }, danger = true, navId = "exk.tex.delete")
    }
    Spacer(Modifier.height(10.dp))
    Text("La extracción es segura: rechaza rutas peligrosas, no toca archivos fuera de la carpeta del juego y conserva el paquete anterior hasta que el nuevo termina. ExKinet no incluye ni descarga paquetes: usa los que tengas autorización para usar.",
        color = c.textMuted, fontSize = 11.sp)

    mismatch?.let { other ->
        ExkDialog("El paquete parece de otra versión",
            "El ZIP contiene carpetas de $other, pero tu juego es $serial. Si lo instalas igualmente, puede que ninguna textura coincida.",
            listOf("Instalar igualmente" to { mismatch = null; pendingUri?.let { install(it) } }, "Cancelar" to { mismatch = null; pendingUri = null }))
    }
}

// ------------------------------------------------------------------- Copias

@Composable
private fun BackupPanel(games: List<GameInfo>) {
    val c = LocalExk.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var withTextures by remember { mutableStateOf(false) }
    var withGames by remember { mutableStateOf(false) }
    var est by remember { mutableStateOf<ExkBackup.Estimate?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(Unit) { est = withContext(Dispatchers.IO) { ExkBackup.estimate(context, games) } }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Creando copia…"
            val r = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { ExkBackup.export(context, it, games, ExkBackup.Options(withTextures, withGames)) { m -> busy = m } } }
            }
            busy = null
            ExkToast.show(r.fold({ "Copia guardada" }, { "Error: ${it.message}" }))
        }
    }
    fun doRestore(src: Uri, folder: Uri?) {
        scope.launch {
            busy = "Restaurando…"
            val r = withContext(Dispatchers.IO) {
                runCatching { ExkBackup.restore(context, { context.contentResolver.openInputStream(src)!! }, folder) { m -> busy = m } }
                    .getOrElse { ExkBackup.RestoreResult(false, "Error: ${it.message}", false) }
            }
            busy = null
            if (r.gamesPending) { restoreUri = src; ExkToast.show(r.message) } else { restoreUri = null; ExkToast.show(r.message) }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val src = restoreUri ?: return@rememberLauncherForActivityResult
        if (tree != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            doRestore(src, tree)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) doRestore(uri, null) }

    Text("Tus partidas, contigo", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Un solo archivo con partidas guardadas, estados rápidos, tarjetas de memoria, ajustes, perfiles de controles y tu biblioteca de ExKinet con sus imágenes. Nunca incluye la BIOS ni claves de firma.",
        color = c.textMuted, fontSize = 13.sp)
    val e = est
    ExkRow("Contenido básico", e?.let { "≈ ${humanSize(it.base)}" } ?: "Calculando…")
    ExkRow("Incluir texturas HD", e?.let { "≈ ${humanSize(it.textures)}" }) { ExkSwitch(withTextures, { withTextures = it }, navId = "exk.bk.tex") }
    ExkRow("Incluir juegos", e?.let { "≈ ${humanSize(it.games)} · puede tardar mucho" }) { ExkSwitch(withGames, { withGames = it }, navId = "exk.bk.games") }
    e?.let { ExkRow("Tamaño estimado", humanSize(it.base + (if (withTextures) it.textures else 0) + (if (withGames) it.games else 0))) }
    Spacer(Modifier.height(10.dp))
    if (busy != null) Text(busy!!, color = c.glow, fontSize = 14.sp) else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExkPrimaryButton("↓ Exportar copia", { create.launch("ExKinet-copia-${System.currentTimeMillis() / 1000}.zip") }, navId = "exk.bk.export")
        ExkSecondaryButton("↑ Restaurar copia", { open.launch(arrayOf("application/zip", "*/*")) }, navId = "exk.bk.import")
    }
    if (restoreUri != null) {
        Spacer(Modifier.height(8.dp))
        ExkSecondaryButton("Elegir carpeta para los juegos de la copia", { pickFolder.launch(null) }, navId = "exk.bk.folder")
    }
    Spacer(Modifier.height(10.dp))
    Text("Al exportar eliges dónde guardar el archivo (por ejemplo, Descargas). Lo verás con la app Archivos de la tablet y puedes copiarlo a tu ordenador o a la nube.",
        color = c.textMuted, fontSize = 11.sp)
}

@Suppress("unused") private val keepImports = listOf(SolidColor(Color.White), TextStyle())
@Suppress("unused") @Composable private fun keepFlow() { FlowRow {}; BasicTextField("", {}) }
@Suppress("unused") private val keepBadge: @Composable () -> Unit = { ExkBadge("") }
