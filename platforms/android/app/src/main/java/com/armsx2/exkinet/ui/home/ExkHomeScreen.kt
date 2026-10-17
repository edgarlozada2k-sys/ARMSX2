package com.armsx2.exkinet.ui.home

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.armsx2.EmuState
import com.armsx2.GameInfo
import com.armsx2.PlayTime
import com.armsx2.SaveSlotLookup
import com.armsx2.exkinet.data.ExkDefaults
import com.armsx2.exkinet.data.ExkMedia
import com.armsx2.exkinet.data.ExkStore
import com.armsx2.exkinet.ui.ExkLogo
import com.armsx2.exkinet.ui.ExkPill
import com.armsx2.exkinet.ui.ExkPrimaryButton
import com.armsx2.exkinet.ui.ExkSecondaryButton
import com.armsx2.exkinet.ui.LocalExk
import com.armsx2.exkinet.ui.exkIsNavSelected
import com.armsx2.exkinet.ui.exkNav
import com.armsx2.runtime.MainActivityRuntime
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Selección del Inicio: se conserva al ir a Ajustes y volver. */
object ExkHomeState {
    val selectedKey = mutableStateOf<String?>(null)
}

enum class ExkTab { HOME, LIBRARY, FAVORITES, SETTINGS }

private val liftEasing = CubicBezierEasing(.22f, .75f, .18f, 1f)

@Composable
fun ExkTopBar(active: ExkTab, onTab: (ExkTab) -> Unit, onSearch: () -> Unit, transparent: Boolean) {
    val c = LocalExk.current
    val context = LocalContext.current
    var clock by remember { mutableStateOf("") }
    var battery by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            clock = fmt.format(Date())
            battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
                val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0) l * 100 / s else null
            }
            delay(30_000)
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .background(if (transparent) Brush.verticalGradient(listOf(Color(0x90020710), Color.Transparent)) else Brush.verticalGradient(listOf(c.bg, c.bg.copy(alpha = 0.9f))))
            .padding(horizontal = 48.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExkLogo(40.dp, onClick = { onTab(ExkTab.HOME) })
        Spacer(Modifier.width(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(ExkTab.HOME to "Inicio", ExkTab.LIBRARY to "Biblioteca", ExkTab.FAVORITES to "Favoritos", ExkTab.SETTINGS to "Ajustes").forEach { (t, l) ->
                ExkPill(l, active == t, { onTab(t) }, navId = "exk.nav.${t.name}")
            }
        }
        Spacer(Modifier.weight(1f))
        Text("⌕", color = c.text, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).exkNav("exk.nav.search", onSearch).clickable(onClick = onSearch).padding(8.dp))
        Spacer(Modifier.width(18.dp))
        Text(clock, color = c.textSecondary, fontSize = 14.sp)
        battery?.let { Spacer(Modifier.width(14.dp)); Text("▰ $it %", color = c.textSecondary, fontSize = 13.sp) }
    }
}

/**
 * Inicio de ExKinet: fondo cinematográfico del juego seleccionado, presentación a la izquierda
 * (nombre, descripción, última partida, tiempo jugado), botones Jugar / Continuar / Ajustes HD /
 * Texturas / Partidas y la colección en la parte inferior.
 */
@Composable
fun ExkHomeScreen(
    games: List<GameInfo>,
    onPlay: (GameInfo) -> Unit,
    onContinue: (GameInfo, Int) -> Unit,
    onTab: (ExkTab) -> Unit,
    onSearch: () -> Unit,
    onGraphics: (GameInfo) -> Unit,
    onTextures: (GameInfo) -> Unit,
    onSaves: (GameInfo) -> Unit,
    onEdit: (GameInfo) -> Unit,
    onAddFolder: () -> Unit,
) {
    val c = LocalExk.current
    val context = LocalContext.current
    val prefs = ExkStore.prefs.value
    @Suppress("UNUSED_EXPRESSION") ExkStore.version.intValue
    val selKey = ExkHomeState.selectedKey.value
    val index = games.indexOfFirst { ExkStore.key(it) == selKey }.takeIf { it >= 0 } ?: 0
    val game = games.getOrNull(index)
    LaunchedEffect(game) { if (game != null && selKey == null) ExkHomeState.selectedKey.value = ExkStore.key(game) }

    // Tareas visuales solo con la app en primer plano y sin un juego en marcha.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visualsActive = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && MainActivityRuntime.eState.value == EmuState.STOPPED

    Box(Modifier.fillMaxSize().background(c.bg)) {
        ExkBackdrop(game, prefs.reduceMotion || !visualsActive)
        val video = game?.let { ExkStore.meta(it).video }
        if (video != null && prefs.autoVideo && !prefs.reduceMotion && visualsActive) {
            androidx.compose.runtime.key(ExkStore.key(game), video.uri) {
                if (remember(video.uri) { ExkMedia.videoReadable(context, video) }) ExkBackgroundVideo(video, prefs.videoDelaySec)
            }
        }
        // Velo continuo: horizontal (detrás del texto) + inferior (detrás de la colección).
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Color(0xFF050A10), 0.15f to Color(0xF5050A10), 0.26f to Color(0xD9050A10), 0.39f to Color(0x99050A10),
            0.56f to Color(0x40050A10), 0.76f to Color(0x08050A10), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color(0x60050A10), 0.2f to Color.Transparent, 0.42f to Color.Transparent, 0.73f to Color(0x90050A10),
            0.91f to Color(0xEB050A10), 1f to Color(0xFF050A10))))

        Column(Modifier.fillMaxSize()) {
            ExkTopBar(ExkTab.HOME, onTab, onSearch, transparent = true)
            if (game == null) {
                EmptyLibrary(onAddFolder)
                return@Column
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val h = maxHeight
                Column(Modifier.padding(start = 64.dp, top = h * 0.10f).widthIn(max = 600.dp)) {
                    Hero(game, onPlay, onContinue, onGraphics, onTextures, onSaves, onEdit)
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 22.dp)) {
                    Carousel(games, index, prefs.reduceMotion, h, onSelect = { ExkHomeState.selectedKey.value = ExkStore.key(it) }, onPlay = onPlay)
                }
            }
        }
    }
}

@Composable
private fun Hero(
    game: GameInfo, onPlay: (GameInfo) -> Unit, onContinue: (GameInfo, Int) -> Unit,
    onGraphics: (GameInfo) -> Unit, onTextures: (GameInfo) -> Unit, onSaves: (GameInfo) -> Unit, onEdit: (GameInfo) -> Unit,
) {
    val c = LocalExk.current
    val context = LocalContext.current
    val meta = ExkStore.meta(game)
    val shadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 24f)
    val title = game.displayTitle(com.armsx2.EnglishTitles.enabled.value)
    val slots = remember(game.serial, MainActivityRuntime.eState.value) { SaveSlotLookup.slotsFor(context, game.serial) }
    val lastSlot = slots.maxByOrNull { it.modified }
    val last = PlayTime.lastPlayedMillis(game.serial)
    val secs = PlayTime.playedSeconds(game.serial)

    Text("PlayStation 2", color = c.textSecondary, fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    Text(title, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        lineHeight = 56.sp, style = TextStyle(shadow = shadow))
    Spacer(Modifier.height(12.dp))
    val desc = meta.description ?: ExkDefaults.description(game) ?: "Listo para jugar."
    Text(desc, color = Color(0xFFC4C8D1), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 380.dp), style = TextStyle(shadow = shadow))
    Spacer(Modifier.height(10.dp))
    Text(
        listOfNotNull(
            if (last > 0) "Última partida · ${relative(last)}" else "Sin partidas todavía",
            if (secs > 0) "Tiempo jugado · ${duration(secs)}" else null,
        ).joinToString("   ·   "),
        color = Color(0xFF8CB8D9), fontSize = 12.sp,
    )
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ExkPrimaryButton("Jugar", { onPlay(game) }, leading = "▶", navId = "exk.home.play")
        if (lastSlot != null) ExkSecondaryButton("Continuar", { onContinue(game, lastSlot.slot) }, navId = "exk.home.continue")
        val fav = meta.favorite
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Color(0x8007182A)).border(1.dp, Color(0x60486A85), CircleShape)
                .exkNav("exk.home.fav", { ExkStore.toggleFavorite(game) }, shape = RoundedCornerShape(23.dp))
                .clickable { ExkStore.toggleFavorite(game) },
            contentAlignment = Alignment.Center,
        ) { Text(if (fav) "♥" else "♡", color = c.accent, fontSize = 22.sp) }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        QuickLink("⚙  Ajustes HD", "exk.home.hd") { onGraphics(game) }
        QuickLink("▱  Texturas", "exk.home.tex") { onTextures(game) }
        QuickLink("▤  Partidas", "exk.home.saves") { onSaves(game) }
        QuickLink("✎  Editar ficha", "exk.home.edit") { onEdit(game) }
    }
}

@Composable
private fun QuickLink(text: String, navId: String, onClick: () -> Unit) {
    Text(text, color = Color(0xFFD6DCE4), fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).exkNav(navId, onClick, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp))
}

@Composable
private fun Carousel(games: List<GameInfo>, index: Int, reduceMotion: Boolean, areaHeight: androidx.compose.ui.unit.Dp,
                     onSelect: (GameInfo) -> Unit, onPlay: (GameInfo) -> Unit) {
    val c = LocalExk.current
    val state = rememberLazyListState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 20.dp
        val side = 52.dp
        // Cinco tarjetas a la vista como en el diseño; más juegos → el carrusel se desplaza.
        val cardW = ((maxWidth - side * 2 - gap * 4) / 5).coerceIn(140.dp, 340.dp)
        val cardH = (areaHeight * 0.36f).coerceIn(180.dp, 330.dp)
        // Desplazamiento suave solo cuando la tarjeta seleccionada queda fuera de la vista.
        LaunchedEffect(index) {
            val info = state.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            val fullyVisible = item != null && item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!fullyVisible) {
                val center = -((info.viewportEndOffset - info.viewportStartOffset) - with(density) { cardW.roundToPx() }) / 2
                if (reduceMotion) state.scrollToItem(index, center) else state.animateScrollToItem(index, center)
            }
        }
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(gap),
            // Margen holgado: el brillo de las tarjetas extremas nunca queda recortado.
            contentPadding = PaddingValues(start = side, end = side, top = 28.dp, bottom = 22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            itemsIndexed(games, key = { _, g -> ExkStore.key(g) }) { i, g ->
                GameCard(g, i == index, cardW, cardH, reduceMotion, onSelect = { onSelect(g) }, onPlay = { onPlay(g) })
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") c
}

@Composable
private fun GameCard(game: GameInfo, current: Boolean, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp,
                     reduceMotion: Boolean, onSelect: () -> Unit, onPlay: () -> Unit) {
    val c = LocalExk.current
    val density = LocalDensity.current
    val lift = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val navId = "exk.home.card.${ExkStore.key(game)}"
    // El elemento se conserva (misma clave): solo cambia su estado. La anterior baja y la nueva
    // sube, rebasa ligeramente y se asienta (≈650 ms, curva .22,.75,.18,1).
    LaunchedEffect(current, reduceMotion) {
        val settled = with(density) { (-6).dp.toPx() }
        if (current) {
            if (reduceMotion) { lift.snapTo(settled); scale.snapTo(1.035f) } else {
                coroutineScope {
                    launch {
                        lift.animateTo(settled, keyframes {
                            durationMillis = 650
                            with(density) { (-16).dp.toPx() } at 312 using liftEasing
                        })
                    }
                    launch {
                        scale.animateTo(1.035f, keyframes { durationMillis = 650; 1.04f at 312 using liftEasing })
                    }
                }
            }
        } else {
            if (reduceMotion) { lift.snapTo(0f); scale.snapTo(1f) } else {
                coroutineScope {
                    launch { lift.animateTo(0f, tween(650, easing = liftEasing)) }
                    launch { scale.animateTo(1f, tween(650, easing = liftEasing)) }
                }
            }
        }
    }
    // Con mando, mover la selección del registro selecciona el juego.
    val navSelected = exkIsNavSelected(navId)
    LaunchedEffect(navSelected) { if (navSelected && !current) onSelect() }
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .size(w, h)
            .graphicsLayer { translationY = lift.value; scaleX = scale.value; scaleY = scale.value; alpha = if (current) 1f else 0.88f }
            .drawBehind {
                if (current) {
                    // Brillo dibujado con trazos (sin sombras de color, que algunos drivers rellenan de negro).
                    val r = CornerRadius(13.dp.toPx())
                    for ((spread, a) in listOf(14f to 0.10f, 9f to 0.18f, 5f to 0.30f)) {
                        val s = spread.dp.toPx()
                        drawRoundRect(Color(0xFF0E9CFF).copy(alpha = a), Offset(-s / 2, -s / 2), Size(size.width + s, size.height + s), CornerRadius(r.x + s / 2), style = Stroke(s))
                    }
                }
            }
            .clip(shape)
            .border(2.dp, if (current) c.glow else Color(0x12FFFFFF), shape)
            .exkNav(navId, onPlay, shape = shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (current) onPlay() else onSelect() },
    ) {
        ExkCover(game, Modifier.fillMaxSize())
        if (ExkStore.isFavorite(game)) Text("♥", color = Color.White, fontSize = 20.sp, modifier = Modifier.align(Alignment.TopEnd).padding(9.dp))
    }
}

@Composable
private fun EmptyLibrary(onAddFolder: () -> Unit) {
    val c = LocalExk.current
    Column(Modifier.fillMaxSize().padding(64.dp), verticalArrangement = Arrangement.Center) {
        Text("Tu colección está vacía", color = c.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Elige la carpeta donde guardas tus juegos de PS2 (ISO, CHD, CSO…). ExKinet no incluye juegos ni BIOS: usa los tuyos.",
            color = c.textSecondary, fontSize = 15.sp, modifier = Modifier.widthIn(max = 560.dp))
        Spacer(Modifier.height(24.dp))
        ExkPrimaryButton("Añadir carpeta de juegos", onAddFolder, leading = "＋", navId = "exk.home.addfolder")
    }
}

fun relative(ms: Long): String {
    val d = (System.currentTimeMillis() - ms) / 86_400_000L
    return when {
        d <= 0 -> "Hoy"
        d == 1L -> "Ayer"
        d < 7 -> "Hace $d días"
        else -> SimpleDateFormat("d MMM yyyy", Locale("es")).format(Date(ms))
    }
}

fun duration(secs: Long): String {
    val h = secs / 3600; val m = (secs % 3600) / 60
    return if (h > 0) "$h h $m min" else "$m min"
}
