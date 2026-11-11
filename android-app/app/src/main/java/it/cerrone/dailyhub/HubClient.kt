package it.cerrone.dailyhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HubException(message: String) : IOException(message)

class HubClient(baseUrl: String, private val token: String) {
    private val base = baseUrl.trim().trimEnd('/')
    private val json = "application/json".toMediaType()

    suspend fun today(): String = call("GET", "/api/today")

    suspend fun tasks(): List<TaskInfo> = parseTasks(call("GET", "/api/tasks"))

    suspend fun addTask(title: String, due: LocalDate?) {
        val body = JSONObject().put("title", title).put("due", due?.toString() ?: JSONObject.NULL)
        call("POST", "/api/tasks", body)
    }

    suspend fun completeTask(id: Long) {
        call("PATCH", "/api/tasks/$id", JSONObject().put("done", true))
    }

    suspend fun deleteTask(id: Long) {
        call("DELETE", "/api/tasks/$id")
    }

    /** [isoAlarm] null means "no alarm set" and cancels the wake task on the PC. */
    suspend fun putAlarm(isoAlarm: String?) {
        call("PUT", "/api/alarm", JSONObject().put("nextAlarm", isoAlarm ?: JSONObject.NULL))
    }

    suspend fun assistantInfo(): AssistantInfo = parseAssistantInfo(call("GET", "/api/assistant"))

    suspend fun chat(conversationId: String, message: String): ChatResult {
        val body = JSONObject().put("conversationId", conversationId).put("message", message)
        return parseChatResult(call("POST", "/api/assistant/chat", body, chatHttp))
    }

    suspend fun resetChat(conversationId: String) {
        call("DELETE", "/api/assistant/chat/$conversationId")
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null, client: OkHttpClient = http): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(base + path)
                .header("Authorization", "Bearer $token")
                .method(method, body?.toString()?.toRequestBody(json))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> throw HubException("Token non valido")
                    !response.isSuccessful -> throw HubException(errorDetail(text) ?: "Errore HTTP ${response.code}")
                    else -> text
                }
            }
        }

    companion object {
        private val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        // Assistant replies may chain several model and tool calls.
        private val chatHttp = http.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
