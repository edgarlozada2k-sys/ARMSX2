package com.armsx2.exkinet.ui.home

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.armsx2.CustomCovers
import com.armsx2.GameInfo
import com.armsx2.exkinet.data.ExkDefaults
import com.armsx2.exkinet.data.ExkStore
import com.armsx2.exkinet.data.Framing
import com.armsx2.exkinet.data.VideoRef
import com.armsx2.exkinet.ui.ExkToast
import java.io.File

fun Framing.alignment() = BiasAlignment(x * 2f - 1f, y * 2f - 1f)

/** Fuente de la portada: la de ExKinet, la personalizada de ARMSX2 o la descargada. */
fun coverModel(context: android.content.Context, game: GameInfo): Any? =
    ExkStore.meta(game).cover?.let { ExkStore.file(context, it.file) }
        ?: runCatching { CustomCovers.fileFor(context, game) }.getOrNull()
        ?: game.coverUrl

/** Fuente del fondo: SOLO un fondo independiente; nunca se amplía la portada. */
fun backgroundModel(context: android.content.Context, game: GameInfo): Any? =
    ExkStore.meta(game).background?.let { ExkStore.file(context, it.file) }
        ?: ExkDefaults.backgroundAsset(game)?.let { "file:///android_asset/$it" }

@Composable
fun ExkCover(game: GameInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val meta = ExkStore.meta(game)
    val model = remember(game.uri, meta.cover, CustomCovers.version.value) { coverModel(context, game) }
    var failed by remember(model) { mutableStateOf(model == null) }
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF1B3352), Color(0xFF0A1220))))) {
        if (!failed) {
            AsyncImage(
                model = model, contentDescription = "Portada de ${game.title}",
                contentScale = ContentScale.Crop, alignment = (meta.cover?.framing ?: Framing()).alignment(),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val z = meta.cover?.framing?.zoom ?: 1f
                    scaleX = z; scaleY = z
                },
                onError = { failed = true },
            )
        } else {
            Text(
                game.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
        }
    }
}

private data class Layer(val key: String, val bitmap: ImageBitmap?, val framing: Framing, val ambient: Color)

/** Color ambiental estable por juego para los juegos sin fondo propio. */
private fun ambientFor(key: String): Color {
    val h = (key.hashCode() and 0x7fffffff) % 360
    return Color.hsl(((200 + h % 50) % 360).toFloat(), 0.55f, 0.16f)
}

/**
 * Fondo a pantalla completa con fundido de ~800 ms. La imagen se precarga antes de mostrarse y
 * un contador descarta las cargas antiguas: si se eligen varios juegos rápido, solo aparece el
 * último. El borde izquierdo se funde con negro mediante una máscara (sin cortes visibles).
 */
@Composable
fun ExkBackdrop(game: GameInfo?, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var current by remember { mutableStateOf<Layer?>(null) }
    var previous by remember { mutableStateOf<Layer?>(null) }
    val alpha = remember { Animatable(1f) }
    val serial = remember { intArrayOf(0) }
    val key = game?.let { ExkStore.key(it) } ?: ""
    val meta = game?.let { ExkStore.meta(it) }

    LaunchedEffect(key, meta?.background) {
        val my = ++serial[0]
        val model = game?.let { backgroundModel(context, it) }
        val bmp: ImageBitmap? = if (model == null) null else runCatching {
            val req = ImageRequest.Builder(context).data(model).size(2560).allowHardware(false).build()
            (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap()?.asImageBitmap()
        }.getOrNull()
        if (my != serial[0]) return@LaunchedEffect // llegó otra selección mientras cargaba
        if (model != null && bmp == null) ExkToast.show("No se pudo cargar este fondo.")
        val next = Layer(key, bmp, meta?.background?.framing ?: Framing(0.7f, 0.57f), ambientFor(key))
        previous = current
        current = next
        if (reduceMotion || previous == null) { alpha.snapTo(1f); previous = null }
        else {
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(800))
            if (my == serial[0]) previous = null
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF050A10))) {
        previous?.let { LayerView(it, Modifier.fillMaxSize()) }
        current?.let { LayerView(it, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) }
    }
}

/** Máscara horizontal: transparente a la izquierda, opaca desde ~55 %. */
fun Modifier.leftFadeMask(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.55f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
    }

@Composable
private fun LayerView(layer: Layer, modifier: Modifier) {
    Box(modifier.background(Brush.radialGradient(listOf(layer.ambient, Color(0xFF050A10)), radius = 1800f,
        center = androidx.compose.ui.geometry.Offset(2400f, 200f)))) {
        layer.bitmap?.let {
            Image(
                it, contentDescription = null, contentScale = ContentScale.Crop, alignment = layer.framing.alignment(),
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = layer.framing.zoom; scaleY = layer.framing.zoom }.leftFadeMask(),
            )
        }
    }
}

/**
 * Vídeo de fondo: aparece tras [delaySec] con fundido, en bucle y sin sonido. Se libera al
 * cambiar de juego, salir de Inicio, abrir un juego o pasar a segundo plano (el llamador deja
 * de componerlo). Si falla, se elimina y queda la imagen.
 */
@Composable
fun ExkBackgroundVideo(video: VideoRef, delaySec: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var show by remember(video.uri) { mutableStateOf(false) }
    var failed by remember(video.uri) { mutableStateOf(false) }
    val alpha = remember(video.uri) { Animatable(0f) }
    var ready by remember(video.uri) { mutableStateOf(false) }
    LaunchedEffect(video.uri) { kotlinx.coroutines.delay(delaySec * 1000L); show = true }
    LaunchedEffect(ready) { if (ready) alpha.animateTo(1f, tween(1200)) }
    if (!show || failed) return

    val holder = remember(video.uri) { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(video.uri) {
        onDispose { holder[0]?.let { runCatching { it.stop() }; it.release() }; holder[0] = null }
    }
    AndroidView(
        modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.leftFadeMask(),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val mp = MediaPlayer()
                        holder[0] = mp
                        runCatching {
                            mp.setDataSource(ctx, Uri.parse(video.uri))
                            mp.setSurface(Surface(st))
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            mp.setOnPreparedListener { p -> centerCrop(this@apply, p.videoWidth, p.videoHeight); p.start() }
                            mp.setOnInfoListener { _, what, _ -> if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) ready = true; false }
                            mp.setOnErrorListener { _, _, _ -> failed = true; ExkToast.show("No se pudo reproducir el vídeo. Se conserva la imagen."); true }
                            mp.prepareAsync()
                        }.onFailure { failed = true; ExkToast.show("No se pudo abrir el vídeo. Se conserva la imagen.") }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        holder[0]?.let { centerCrop(this@apply, it.videoWidth, it.videoHeight) }
                    }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        holder[0]?.let { runCatching { it.stop() }; it.release() }; holder[0] = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}

private fun centerCrop(view: TextureView, vw: Int, vh: Int) {
    if (vw <= 0 || vh <= 0 || view.width == 0 || view.height == 0) return
    val sx = view.width.toFloat() / vw; val sy = view.height.toFloat() / vh
    val s = maxOf(sx, sy)
    val m = Matrix()
    m.setScale(vw * s / view.width, vh * s / view.height, view.width / 2f, view.height / 2f)
    view.setTransform(m)
}

@Suppress("unused") private fun touch(f: File) = f
