package it.cerrone.dailyhub

import android.content.Context
import it.cerrone.dailyhub.widget.NexusWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** One sync attempt, as the Sync settings page lists it. [code] is a stable id, shown through [message]. */
data class SyncReport(
    val at: String,
    val ok: Boolean,
    val code: String,
    val detail: String,
    val address: String,
    val httpCode: Int,
    val sent: String,
    val received: String,
    val durationMs: Long,
) {
    /** The code turned into text in the current language (entries saved by older versions already hold text). */
    val message: String get() = syncMessage(code, httpCode)
}

fun syncMessage(code: String, httpCode: Int = 0): String = when (code) {
    "ok" -> tr("Sincronizzato", "Synced")
    "no_token" -> tr("Manca il token del PC", "The PC token is missing")
    "no_pc" -> tr("PC non trovato in rete", "PC not found on the network")
    "unreachable" -> tr("PC non raggiungibile", "PC unreachable")
    "bad_response" -> tr("Risposta del PC non valida", "Invalid answer from the PC")
    "http_401" -> tr("Token non valido", "Invalid token")
    "http_404" -> tr("Il server del PC è vecchio: aggiornalo", "The PC server is outdated: update it")
    "http_422" -> tr("Il PC ha rifiutato i dati", "The PC rejected the data")
    "http_5xx" -> tr("Errore del server del PC", "PC server error")
    "http_other" -> tr("Errore HTTP $httpCode", "HTTP error $httpCode")
    else -> code
}

/** The last attempts, newest first, kept in [Prefs.syncLogJson]. */
object SyncHistory {
    const val LIMIT = 10

    @Synchronized
    fun add(prefs: Prefs, report: SyncReport) {
        val items = JSONArray().put(
            JSONObject()
                .put("at", report.at)
                .put("ok", report.ok)
                .put("message", report.code)
                .put("detail", report.detail)
                .put("address", report.address)
                .put("httpCode", report.httpCode)
                .put("sent", report.sent)
                .put("received", report.received)
                .put("durationMs", report.durationMs),
        )
        val old = stored(prefs)
        for (index in 0 until minOf(old.length(), LIMIT - 1)) items.put(old.get(index))
        prefs.syncLogJson = items.toString()
    }

    fun all(prefs: Prefs): List<SyncReport> {
        val items = stored(prefs)
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            SyncReport(
                at = item.optString("at"),
                ok = item.optBoolean("ok"),
                code = item.optString("message"),
                detail = item.optString("detail"),
                address = item.optString("address"),
                httpCode = item.optInt("httpCode"),
                sent = item.optString("sent"),
                received = item.optString("received"),
                durationMs = item.optLong("durationMs"),
            )
        }.take(LIMIT)
    }

    private fun stored(prefs: Prefs): JSONArray =
        try { JSONArray(prefs.syncLogJson) } catch (e: Exception) { JSONArray() }
}

/** Raised whenever a sync applied something received from the PC, so the open screen can reload from the local copy. */
object SyncSignal {
    val applied = MutableStateFlow(0)
}

/** What "Prova connessione" found: first whether the PC answers, then whether it accepts the token. */
data class ProbeResult(val address: String, val reachable: Boolean, val tokenOk: Boolean?, val pcVersion: String?, val message: String)

/** Outcome of one exchange: whether it worked and whether the PC sent anything new. */
class SyncResult(val ok: Boolean, val changed: Boolean, val message: String)

internal class SyncHttpError(val code: Int, reason: String, val detail: String) : IOException(reason)

internal data class Counts(val tasks: Int, val messages: Int, val deletions: Int) {
    fun isEmpty(): Boolean = tasks + messages + deletions == 0

    fun label(): String = listOfNotNull(
        tasks.takeIf { it > 0 }?.let { "$it task" },
        messages.takeIf { it > 0 }?.let { tr("$it messaggi", "$it messages") },
        deletions.takeIf { it > 0 }?.let { tr("$it chat eliminate", "$it chats deleted") },
    ).joinToString(", ").ifEmpty { tr("niente di nuovo", "nothing new") }
}

/** One exchange with the PC: send what changed here, keep what the PC answers with. */
class SyncClient(private val context: Context) {
    private val prefs = Prefs(context)
    private val db = LocalDb.get(context)

    private class Outcome(
        val ok: Boolean,
        val code: String,
        val detail: String = "",
        val httpCode: Int = 0,
        val sent: Counts? = null,
        val received: Counts? = null,
    )

    suspend fun exchange(): String = sync().message

    /**
     * One exchange at a time (the open app, a task edit and the background worker can all ask at once).
     * Checks with nothing to send and nothing received are not logged: with the app open they run every
     * few seconds and would bury the real attempts. A success that follows a failure is always logged.
     */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        gate.withLock {
            val started = System.currentTimeMillis()
            val outcome = attempt()
            val idle = outcome.ok && outcome.sent?.isEmpty() != false && outcome.received?.isEmpty() != false
            val skipLog = idle && SyncHistory.all(prefs).firstOrNull()?.ok != false
            if (!skipLog) {
                SyncHistory.add(
                    prefs,
                    SyncReport(
                        at = LocalDb.nowIso(),
                        ok = outcome.ok,
                        code = outcome.code,
                        detail = outcome.detail,
                        address = prefs.hubUrl,
                        httpCode = outcome.httpCode,
                        sent = outcome.sent?.label().orEmpty(),
                        received = outcome.received?.label().orEmpty(),
                        durationMs = System.currentTimeMillis() - started,
                    ),
                )
            }
            lastFinished = System.currentTimeMillis()
            val changed = outcome.received?.isEmpty() == false
            if (changed) SyncSignal.applied.update { it + 1 }
            // Whoever synced (app, widget, background), the widgets show the same tasks and sync time as the app.
            if (changed || (outcome.ok && lastFinished - lastWidgetUpdate > WIDGET_REFRESH_MS)) {
                lastWidgetUpdate = lastFinished
                runCatching {
                    if (changed) LocalToday.refreshTasks(context)
                    NexusWidgets.updateAll(context)
                }
            }
            SyncResult(outcome.ok, changed, syncMessage(outcome.code, outcome.httpCode))
        }
    }

    private suspend fun attempt(): Outcome {
        if (!prefs.canSync) return Outcome(false, "no_token", tr("Scrivilo in Impostazioni → Sync", "Enter it in Settings → Sync"))
        val address = hubAddress(prefs.hubUrl)
        if (address != prefs.hubUrl) prefs.hubUrl = address
        if (prefs.hubUrl.isBlank()) {
            val found = PcFinder.find(context)
            if (!found.isNullOrBlank()) prefs.hubUrl = found
        }
        if (prefs.hubUrl.isBlank()) {
            return Outcome(
                false, "no_pc",
                tr("Nessun indirizzo salvato e la ricerca automatica non ha risposto", "No saved address and the automatic search got no answer"),
            )
        }
        val body = body()
        val sent = Counts(
            body.getJSONArray("tasks").length(),
            body.getJSONArray("messages").length(),
            body.getJSONArray("deletedConversations").length(),
        )
        return try {
            val response = try {
                post(body)
            } catch (e: SyncHttpError) {
                throw e
            } catch (e: IOException) {
                // The saved address may be stale (the router gave the PC a new one): look again once.
                val found = PcFinder.find(context)
                if (found.isNullOrBlank() || found == prefs.hubUrl) throw e
                prefs.hubUrl = found
                post(body)
            }
            apply(response)
            val received = Counts(
                response.optJSONArray("tasks")?.length() ?: 0,
                response.optJSONArray("messages")?.length() ?: 0,
                response.optJSONArray("deletedConversations")?.length() ?: 0,
            )
            Outcome(true, "ok", httpCode = 200, sent = sent, received = received)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncHttpError) {
            Outcome(false, e.message ?: "http_other", e.detail, e.code, sent)
        } catch (e: IOException) {
            Outcome(false, "unreachable", e.message ?: e.javaClass.simpleName, sent = sent)
        } catch (e: Exception) {
            Outcome(false, "bad_response", e.message ?: e.javaClass.simpleName, sent = sent)
        }
    }

    /** Health first (is the PC there?), then an authenticated call (is the token right?). */
    suspend fun probe(): ProbeResult = withContext(Dispatchers.IO) {
        val address = hubAddress(prefs.hubUrl).ifBlank { PcFinder.find(context).orEmpty() }
        if (address.isBlank()) {
            return@withContext ProbeResult(
                "", false, null, null,
                tr("Nessun indirizzo: scrivilo o usa «Cerca PC in rete»", "No address: type it or use \"Find PC on the network\""),
            )
        }
        try {
            http.newCall(Request.Builder().url("$address/api/health").build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ProbeResult(
                        address, false, null, null,
                        tr("Il PC risponde con errore ${response.code}", "The PC answers with error ${response.code}"),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            val why = e.message ?: e.javaClass.simpleName
            return@withContext ProbeResult(
                address, false, null, null,
                tr(
                    "Nessuna risposta da $address ($why). Controlla Wi‑Fi, indirizzo e firewall del PC.",
                    "No answer from $address ($why). Check Wi‑Fi, address and the PC firewall.",
                ),
            )
        }
        if (prefs.token.isBlank()) {
            return@withContext ProbeResult(address, true, false, null, tr("Il PC risponde, ma manca il token", "The PC answers, but the token is missing"))
        }
        val info = Request.Builder().url("$address/api/info").header("Authorization", "Bearer ${prefs.token}").build()
        try {
            http.newCall(info).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> ProbeResult(address, true, false, null, tr("Il PC risponde, ma il token è sbagliato", "The PC answers, but the token is wrong"))
                    response.code == 404 -> ProbeResult(
                        address, true, null, null,
                        tr("Il PC risponde, ma il server è una versione vecchia: aggiornalo", "The PC answers, but the server is an old version: update it"),
                    )
                    !response.isSuccessful -> ProbeResult(address, true, null, null, tr("Il PC risponde con errore ${response.code}", "The PC answers with error ${response.code}"))
                    else -> {
                        val version = JSONObject(text).optString("version")
                        if (version.isNotBlank()) prefs.pcVersion = version
                        ProbeResult(address, true, true, version, tr("Collegamento riuscito", "Connection successful"))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProbeResult(address, true, null, null, tr("Il PC risponde, ma non riesco a verificare il token", "The PC answers, but I can't verify the token"))
        }
    }

    private fun body(): JSONObject {
        val settings = JSONArray()
        for (key in SETTING_KEYS) {
            val at = prefs.settingAt(key)
            if (at.isBlank()) continue
            val value = when (key) {
                "city" -> prefs.city
                "latitude" -> prefs.latitude
                "longitude" -> prefs.longitude
                "gemini_model" -> prefs.geminiModel
                else -> prefs.assistantName
            }
            settings.put(JSONObject().put("key", key).put("value", value).put("updatedAt", at))
        }
        return JSONObject()
            .put("since", prefs.pcRevision)
            .put("tasks", db.dirtyTasks())
            .put("messages", db.dirtyMessages())
            .put("deletedConversations", db.dirtyTombstones())
            .put("settings", settings)
            .put("alarm", AlarmSync.nextAlarmIso(context) ?: JSONObject.NULL)
    }

    private fun apply(response: JSONObject) {
        response.optJSONArray("deletedConversations")?.let { items ->
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                db.applyTombstone(item.getString("id"), item.getString("updatedAt"))
            }
        }
        response.optJSONArray("tasks")?.let { items ->
            for (index in 0 until items.length()) db.applyTask(items.getJSONObject(index))
        }
        response.optJSONArray("messages")?.let { items ->
            for (index in 0 until items.length()) db.applyMessage(items.getJSONObject(index))
        }
        response.optJSONArray("settings")?.let { items ->
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val key = item.getString("key")
                val at = item.getString("updatedAt")
                val local = prefs.settingAt(key)
                if (local.isNotBlank() && !preferIncoming(local, at, incomingFromPc = true)) continue
                when (key) {
                    "city" -> prefs.city = item.getString("value")
                    "latitude" -> prefs.latitude = item.getString("value")
                    "longitude" -> prefs.longitude = item.getString("value")
                    "assistant_name" -> prefs.assistantName = item.getString("value")
                    "gemini_model" -> prefs.geminiModel = item.getString("value")
                }
                prefs.setSettingAt(key, at)
            }
        }
        if (!response.isNull("geminiKeyUpdatedAt")) {
            val at = response.getString("geminiKeyUpdatedAt")
            if (prefs.geminiKeyAt.isBlank() || preferIncoming(prefs.geminiKeyAt.ifBlank { at }, at, incomingFromPc = true)) {
                prefs.geminiKey = if (response.isNull("geminiKey")) "" else response.optString("geminiKey")
                prefs.geminiKeyAt = at
            }
        }
        prefs.pcRevision = response.optLong("revision", prefs.pcRevision)
        if (!response.isNull("syncedAt")) prefs.lastSyncAt = response.getString("syncedAt")
        response.optString("version").takeIf { it.isNotBlank() }?.let { prefs.pcVersion = it }
    }

    private fun post(body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(prefs.hubUrl.trim().trimEnd('/') + "/api/sync")
            .header("Authorization", "Bearer ${prefs.token}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = errorDetail(text).orEmpty()
                val reason = when (response.code) {
                    401 -> "http_401"
                    404 -> "http_404"
                    422 -> "http_422"
                    in 500..599 -> "http_5xx"
                    else -> "http_other"
                }
                throw SyncHttpError(response.code, reason, detail)
            }
            JSONObject(text)
        }
    }

    companion object {
        /** Settings shared with the PC, in the order they are sent. */
        private val SETTING_KEYS = listOf("city", "latitude", "longitude", "assistant_name", "gemini_model")

        private val gate = Mutex()

        /** When the last exchange ended; the open-app loop uses it to avoid syncing twice in a row. */
        @Volatile
        var lastFinished: Long = 0L
            private set

        /** Idle syncs redraw the widgets at most once a minute, just to keep the shown sync time current. */
        private const val WIDGET_REFRESH_MS = 60_000L
        private var lastWidgetUpdate: Long = 0L

        private val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
