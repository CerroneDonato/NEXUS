package it.cerrone.dailyhub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Where [entryName] of a zip lands under [root], or null when it would escape the folder ("../x").
 * Pure so it can be tested without Android.
 */
fun zipTarget(root: File, entryName: String): File? {
    val target = File(root, entryName)
    val base = root.canonicalPath + File.separator
    return target.takeIf { it.canonicalPath.startsWith(base) || it.canonicalPath == root.canonicalPath }
}

/**
 * The small Italian model of Vosk (about 50 MB), the offline engine that listens for "Lilith" in sport mode.
 * It is downloaded once, from the Vosk project's own site, and then lives in the app's private storage.
 */
object VoskModel {
    private const val NAME = "vosk-model-small-it-0.22"
    private const val URL = "https://alphacephei.com/vosk/models/$NAME.zip"
    private const val READY_MARKER = ".ready"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun root(context: Context) = File(context.filesDir, "vosk")

    fun dir(context: Context) = File(root(context), NAME)

    fun isReady(context: Context): Boolean = File(dir(context), READY_MARKER).exists()

    /** Downloads and unpacks the model. [onProgress] gets 0..100. Throws [IOException] on any failure. */
    suspend fun ensure(context: Context, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady(context)) return@withContext
        val root = root(context).apply { mkdirs() }
        val zip = File(context.cacheDir, "$NAME.zip")
        try {
            download(zip, onProgress)
            val target = dir(context)
            target.deleteRecursively()
            unzip(zip, root)
            if (!File(target, "am").isDirectory) throw IOException(tr("Modello vocale incompleto.", "Voice model incomplete."))
            File(target, READY_MARKER).writeText("ok")
        } catch (e: IOException) {
            dir(context).deleteRecursively()
            throw e
        } finally {
            zip.delete()
        }
    }

    private fun download(into: File, onProgress: (Int) -> Unit) {
        http.newCall(Request.Builder().url(URL).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException(tr("Download del modello non riuscito (${response.code}).", "Model download failed (${response.code})."))
            val body = response.body ?: throw IOException(tr("Download del modello vuoto.", "Model download is empty."))
            val total = body.contentLength()
            var done = 0L
            var last = -1
            body.byteStream().use { input ->
                into.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != last) { last = percent; onProgress(percent) }
                        }
                    }
                }
            }
        }
    }

    private fun unzip(zip: File, root: File) {
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val target = zipTarget(root, entry.name) ?: throw IOException(tr("Archivio non valido.", "Invalid archive."))
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
}
