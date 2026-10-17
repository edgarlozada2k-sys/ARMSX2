package com.armsx2.exkinet.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.armsx2.BackupManager
import com.armsx2.GameInfo
import com.armsx2.runtime.MainActivityRuntime
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia completa de ExKinet en un único .zip:
 *  - `armsx2-backup.zip`: la copia oficial de ARMSX2 (partidas guardadas, estados rápidos,
 *    tarjetas de memoria, ajustes por juego y TODAS las preferencias, incluidos los perfiles de
 *    controles de teclado, mando y táctiles).
 *  - `exkinet/`: biblioteca de ExKinet (fichas, favoritos, encuadres) y sus imágenes.
 *  - opcional `textures/<SERIAL>/…` y `games/<archivo>`.
 * Nunca incluye claves de firma ni la BIOS.
 */
object ExkBackup {
    data class Options(val textures: Boolean, val games: Boolean)
    data class Estimate(val base: Long, val textures: Long, val games: Long)

    private val armsx2Dirs = listOf("memcards", "sstates", "savestates", "gamesettings", "inputprofiles", "cheats", "patches", "covers", "memcard-backups")

    private fun dataRoot(context: Context) = File(MainActivityRuntime.assetCopyRoot(context))

    fun dirSize(f: File): Long = if (!f.exists()) 0 else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun estimate(context: Context, games: List<GameInfo>): Estimate {
        val root = dataRoot(context)
        val base = armsx2Dirs.sumOf { dirSize(File(root, it)) } + dirSize(ExkStore.root(context))
        val tex = dirSize(File(root, "textures"))
        val g = games.sumOf { gameSize(context, it.uri) }
        return Estimate(base, tex, g)
    }

    private fun gameSize(context: Context, uri: Uri): Long = runCatching {
        if (uri.scheme == "file") File(uri.path!!).length() else DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
    }.getOrDefault(0L)

    fun export(context: Context, out: java.io.OutputStream, games: List<GameInfo>, opt: Options, progress: (String) -> Unit): String {
        val tmp = File(context.cacheDir, "armsx2-backup.zip")
        ZipOutputStream(out.buffered()).use { zip ->
            progress("Partidas y ajustes de ARMSX2…")
            val r = tmp.outputStream().use { BackupManager.export(context, it) }
            if (!r.ok) error("ARMSX2: ${r.detail}")
            putFile(zip, "armsx2-backup.zip", tmp)
            tmp.delete()
            progress("Biblioteca e imágenes de ExKinet…")
            zip.putNextEntry(ZipEntry("exkinet/meta.json")); zip.write(ExkStore.toJson().toString(2).toByteArray()); zip.closeEntry()
            val media = ExkStore.mediaDir(context)
            media.walkTopDown().filter { it.isFile }.forEach { putFile(zip, "exkinet/" + it.relativeTo(ExkStore.root(context)).path, it) }
            if (opt.textures) {
                progress("Texturas HD…")
                val t = File(dataRoot(context), "textures")
                t.walkTopDown().filter { it.isFile && !it.path.contains("/.") }.forEach { putFile(zip, "textures/" + it.relativeTo(t).path, it) }
            }
            if (opt.games) {
                for (g in games) {
                    progress("Juego: ${g.title}…")
                    val name = (g.uri.lastPathSegment ?: g.title).substringAfterLast('/').substringAfterLast(':').ifBlank { g.title }
                    runCatching {
                        context.contentResolver.openInputStream(g.uri)?.use { input ->
                            zip.putNextEntry(ZipEntry("games/$name"))
                            input.copyTo(zip, 1 shl 16); zip.closeEntry()
                        }
                    }
                }
            }
            zip.putNextEntry(ZipEntry("exkinet-backup.json"))
            zip.write(JSONObject().put("app", "exkinet").put("version", 1).put("created", System.currentTimeMillis())
                .put("textures", opt.textures).put("games", opt.games).toString().toByteArray())
            zip.closeEntry()
        }
        return "Copia creada"
    }

    private fun putFile(zip: ZipOutputStream, name: String, f: File) {
        zip.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zip, 1 shl 16) }; zip.closeEntry()
    }

    data class RestoreResult(val ok: Boolean, val message: String, val gamesPending: Boolean)

    /**
     * Restaura una copia. Las rutas de destino se validan (sin «..» ni rutas absolutas) y los
     * juegos incluidos se escriben en [gamesFolder] (carpeta elegida por el usuario), sin
     * sobrescribir archivos existentes.
     */
    fun restore(context: Context, input: () -> InputStream, gamesFolder: Uri?, progress: (String) -> Unit): RestoreResult {
        // 1) Validar que es una copia de ExKinet.
        var valid = false; var hasGames = false
        ZipInputStream(input().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "exkinet-backup.json") {
                    val o = JSONObject(z.readBytes().decodeToString())
                    valid = o.optString("app") == "exkinet"; hasGames = o.optBoolean("games")
                }
            }
        }
        if (!valid) return RestoreResult(false, "El archivo no es una copia válida de ExKinet.", false)
        if (hasGames && gamesFolder == null) return RestoreResult(false, "Elige una carpeta para los juegos incluidos.", true)

        val texRoot = File(dataRoot(context), "textures")
        val exkRoot = ExkStore.root(context)
        val tree = gamesFolder?.let { DocumentFile.fromTreeUri(context, it) }
        ZipInputStream(input().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                if (e.isDirectory || n.contains("..") || n.startsWith("/")) continue
                when {
                    n == "armsx2-backup.zip" -> {
                        progress("Partidas y ajustes de ARMSX2…")
                        val tmp = File(context.cacheDir, "restore-armsx2.zip")
                        tmp.outputStream().use { z.copyTo(it) }
                        val r = tmp.inputStream().use { BackupManager.restore(context, it) }
                        tmp.delete()
                        if (!r.ok) return RestoreResult(false, "ARMSX2: ${r.detail}", false)
                    }
                    n == "exkinet/meta.json" -> ExkStore.restoreFrom(JSONObject(z.readBytes().decodeToString()))
                    n.startsWith("exkinet/media/") -> safeWrite(exkRoot, n.removePrefix("exkinet/"), z)
                    n.startsWith("textures/") -> { progress("Texturas HD…"); safeWrite(texRoot, n.removePrefix("textures/"), z) }
                    n.startsWith("games/") && tree != null -> {
                        val name = n.removePrefix("games/")
                        progress("Juego: $name…")
                        if (tree.findFile(name) == null) tree.createFile("application/octet-stream", name)?.let { doc ->
                            context.contentResolver.openOutputStream(doc.uri)?.use { z.copyTo(it, 1 shl 16) }
                        }
                    }
                }
            }
        }
        gamesFolder?.let {
            val dirs = MainActivityRuntime.romsDirs.value
            if (it.toString() !in dirs) MainActivityRuntime.setRomsDirs(dirs + it.toString())
        }
        return RestoreResult(true, "Copia restaurada. Reinicia ExKinet para recargar todos los ajustes.", false)
    }

    private fun safeWrite(root: File, rel: String, input: InputStream) {
        val dest = File(root, rel)
        if (!dest.canonicalPath.startsWith(root.canonicalPath + File.separator)) return
        dest.parentFile?.mkdirs()
        dest.outputStream().use { input.copyTo(it, 1 shl 16) }
    }
}
