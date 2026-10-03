package com.pipo.robot.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The offline speech model behind the "Pipo" wake word (Vosk small English, Apache 2.0).
 *
 * Downloaded once, only when you switch Pipo Voice on, over HTTPS from the model's publisher, and
 * accepted only if its SHA-256 matches the one pinned here, so a tampered or truncated file is
 * thrown away. It is unpacked into Pipo's private storage. Nothing is ever uploaded: the model
 * runs entirely on the phone.
 */
object WakeModel {
    const val URL_ZIP = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    const val SHA256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
    /** The real file is ~41 MB; anything far bigger is not it. */
    private const val MAX_ZIP_BYTES = 60L * 1024 * 1024
    /** Unpacked it is ~71 MB; cap what a zip may expand to. */
    private const val MAX_UNPACKED_BYTES = 120L * 1024 * 1024
    private const val READY = ".ready"
    private const val TAG = "PipoWake"

    /** Download progress for the Settings screen: null = idle, 0..100 = downloading, -1 = failed. */
    val progress = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /**
     * Fetch the model app-wide (leaving the Settings screen doesn't cancel it), then run [then] on
     * success. Only one download at a time.
     */
    fun installInBackground(ctx: Context, then: () -> Unit) {
        val app = ctx.applicationContext
        if (installed(app)) { then(); return }
        if ((progress.value ?: -1) >= 0) return
        progress.value = 0
        scope.launch {
            val ok = install(app) { progress.value = it.coerceIn(0, 99) }
            progress.value = if (ok) null else -1
            if (ok) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { then() }
        }
    }

    fun dir(ctx: Context) = File(ctx.filesDir, "wake-model")
    fun installed(ctx: Context) = File(dir(ctx), READY).exists()

    /**
     * Download, verify and unpack. Blocking: call off the main thread. [progress] gets 0..100.
     * Returns true when the model is ready to use.
     */
    fun install(ctx: Context, progress: (Int) -> Unit = {}): Boolean {
        if (installed(ctx)) return true
        val tmp = File(ctx.cacheDir, "wake-model.zip")
        try {
            val c = (URL(URL_ZIP).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
            try {
                if (c.responseCode != 200) { Log.w(TAG, "download failed: HTTP ${c.responseCode}"); return false }
                val total = c.contentLengthLong.takeIf { it in 1..MAX_ZIP_BYTES } ?: MAX_ZIP_BYTES
                c.inputStream.use { input -> tmp.outputStream().use { out -> copyCapped(input, out, MAX_ZIP_BYTES) { progress((it * 90 / total).toInt()) } } }
            } finally { c.disconnect() }
            if (sha256(tmp) != SHA256) { Log.w(TAG, "download rejected: checksum mismatch"); return false }
            val target = dir(ctx)
            target.deleteRecursively()
            target.mkdirs()
            tmp.inputStream().use { unzip(it, target) }
            File(target, READY).writeText("1")
            progress(100)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "install failed: ${e.javaClass.simpleName}")
            dir(ctx).deleteRecursively()
            return false
        } finally {
            tmp.delete()
        }
    }

    fun remove(ctx: Context) { dir(ctx).deleteRecursively() }

    /* -------------------------------------------------------------- pure helpers (unit-tested) */

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Where a zip entry may be written, or null if it must be refused. The archive's single top
     * folder ("vosk-model-small-en-us-0.15/") is dropped; any entry that would land outside
     * [root] ("../", absolute paths) is refused.
     */
    fun entryTarget(root: File, entryName: String): File? {
        val name = entryName.replace('\\', '/').substringAfter('/', "")
        if (name.isEmpty() || name.startsWith("/")) return null
        val out = File(root, name)
        val rootPath = root.canonicalPath + File.separator
        return out.takeIf { it.canonicalPath.startsWith(rootPath) }
    }

    fun unzip(input: InputStream, root: File) {
        var written = 0L
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = entryTarget(root, e.name)
                if (out == null) { if (e.name.trimEnd('/').contains('/')) throw SecurityException("bad entry"); continue }
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    copyCapped(z, o, MAX_UNPACKED_BYTES - written) { }.also { written += it }
                }
            }
        }
    }

    private fun copyCapped(input: InputStream, out: java.io.OutputStream, cap: Long, onBytes: (Long) -> Unit): Long {
        val b = ByteArray(64 * 1024)
        var n = 0L
        while (true) {
            val r = input.read(b)
            if (r < 0) break
            n += r
            if (n > cap) throw SecurityException("too large")
            out.write(b, 0, r)
            onBytes(n)
        }
        return n
    }
}
