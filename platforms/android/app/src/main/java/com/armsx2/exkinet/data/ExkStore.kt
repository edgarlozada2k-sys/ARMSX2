package com.armsx2.exkinet.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.armsx2.GameInfo
import org.json.JSONObject
import java.io.File

/** Encuadre no destructivo: nunca deforma; elige qué parte queda visible. */
data class Framing(val x: Float = 0.5f, val y: Float = 0.5f, val zoom: Float = 1f) {
    fun toJson() = JSONObject().put("x", x.toDouble()).put("y", y.toDouble()).put("zoom", zoom.toDouble())
    companion object {
        fun from(o: JSONObject?) = if (o == null) Framing() else Framing(
            o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f), o.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
            o.optDouble("zoom", 1.0).toFloat().coerceIn(1f, 3f),
        )
    }
}

data class MediaRef(val file: String, val framing: Framing = Framing())
data class VideoRef(val uri: String, val name: String, val width: Int = 0, val height: Int = 0)

/** Lo que ExKinet añade a cada juego de la biblioteca de ARMSX2. */
data class GameMeta(
    val description: String? = null,
    val cover: MediaRef? = null,
    val background: MediaRef? = null,
    val video: VideoRef? = null,
    val favorite: Boolean = false,
)

enum class ExkThemeMode { DARK, LIGHT }

data class ExkPrefs(
    val reduceMotion: Boolean = false,
    val autoVideo: Boolean = true,
    val videoDelaySec: Int = 3,
    val theme: ExkThemeMode = ExkThemeMode.DARK,
    val classicUi: Boolean = false,
)

/**
 * Datos propios de ExKinet (fichas, portadas, fondos, vídeos, favoritos y preferencias de la
 * interfaz). Se guardan aparte de ARMSX2, en `files/exkinet/meta.json`, con escritura atómica y
 * copia `.bak`. La clave de cada juego es [GameInfo.settingsKey], la misma identidad que usan los
 * ajustes por juego de ARMSX2, así sobrevive a un nuevo escaneo de la biblioteca.
 */
object ExkStore {
    private var dir: File? = null
    private val metas = HashMap<String, GameMeta>()
    val prefs = mutableStateOf(ExkPrefs())
    /** Cambia con cada edición para que Compose recomponga. */
    val version = mutableIntStateOf(0)

    fun root(context: Context): File = File(context.filesDir, "exkinet").apply { mkdirs() }
    fun mediaDir(context: Context): File = File(root(context), "media").apply { mkdirs() }

    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        dir = root(context)
        val main = File(dir, "meta.json"); val bak = File(dir, "meta.json.bak")
        val parsed = read(main) ?: read(bak)
        if (parsed != null) apply(parsed)
    }

    private fun read(f: File): JSONObject? = runCatching { if (f.isFile) JSONObject(f.readText()) else null }.getOrNull()

    private fun apply(o: JSONObject) {
        metas.clear()
        o.optJSONObject("games")?.let { g ->
            g.keys().forEach { k ->
                val m = g.optJSONObject(k) ?: return@forEach
                metas[k] = GameMeta(
                    description = m.optString("description").takeIf { it.isNotBlank() },
                    cover = m.optJSONObject("cover")?.let { MediaRef(it.getString("file"), Framing.from(it.optJSONObject("framing"))) },
                    background = m.optJSONObject("background")?.let { MediaRef(it.getString("file"), Framing.from(it.optJSONObject("framing"))) },
                    video = m.optJSONObject("video")?.let { VideoRef(it.getString("uri"), it.optString("name"), it.optInt("w"), it.optInt("h")) },
                    favorite = m.optBoolean("favorite"),
                )
            }
        }
        o.optJSONObject("prefs")?.let { p ->
            prefs.value = ExkPrefs(
                reduceMotion = p.optBoolean("reduceMotion"),
                autoVideo = p.optBoolean("autoVideo", true),
                videoDelaySec = p.optInt("videoDelaySec", 3).takeIf { it in setOf(2, 3, 5) } ?: 3,
                theme = runCatching { ExkThemeMode.valueOf(p.optString("theme", "DARK")) }.getOrDefault(ExkThemeMode.DARK),
                classicUi = p.optBoolean("classicUi"),
            )
        }
        version.intValue++
    }

    fun toJson(): JSONObject {
        val g = JSONObject()
        for ((k, m) in metas) {
            g.put(k, JSONObject().apply {
                m.description?.let { put("description", it) }
                m.cover?.let { put("cover", JSONObject().put("file", it.file).put("framing", it.framing.toJson())) }
                m.background?.let { put("background", JSONObject().put("file", it.file).put("framing", it.framing.toJson())) }
                m.video?.let { put("video", JSONObject().put("uri", it.uri).put("name", it.name).put("w", it.width).put("h", it.height)) }
                put("favorite", m.favorite)
            })
        }
        val p = prefs.value
        return JSONObject().put("version", 1).put("games", g).put("prefs", JSONObject()
            .put("reduceMotion", p.reduceMotion).put("autoVideo", p.autoVideo).put("videoDelaySec", p.videoDelaySec)
            .put("theme", p.theme.name).put("classicUi", p.classicUi))
    }

    /** Restaura desde una copia de seguridad (sustituye todo). */
    fun restoreFrom(json: JSONObject) { apply(json); save() }

    @Synchronized
    private fun save() {
        val d = dir ?: return
        val main = File(d, "meta.json"); val tmp = File(d, "meta.json.tmp"); val bak = File(d, "meta.json.bak")
        tmp.writeText(toJson().toString())
        if (main.isFile && read(main) != null) main.copyTo(bak, overwrite = true)
        if (!tmp.renameTo(main)) { main.delete(); tmp.renameTo(main) }
        version.intValue++
    }

    fun key(game: GameInfo): String = game.settingsKey ?: game.uri.toString()

    fun meta(game: GameInfo): GameMeta {
        @Suppress("UNUSED_EXPRESSION") version.intValue
        return metas[key(game)] ?: GameMeta()
    }

    fun update(game: GameInfo, f: (GameMeta) -> GameMeta) {
        metas[key(game)] = f(meta(game))
        save()
    }

    fun setPrefs(f: (ExkPrefs) -> ExkPrefs) { prefs.value = f(prefs.value); save() }

    fun isFavorite(game: GameInfo) = meta(game).favorite
    fun toggleFavorite(game: GameInfo) = update(game) { it.copy(favorite = !it.favorite) }

    /** URIs de vídeo en uso (para no liberar un permiso que otro juego comparte). */
    fun videoUrisInUse(): Set<String> = metas.values.mapNotNull { it.video?.uri }.toSet()

    fun file(context: Context, rel: String): File = File(root(context), rel)
}
