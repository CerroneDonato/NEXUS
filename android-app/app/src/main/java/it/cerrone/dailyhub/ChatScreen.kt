package it.cerrone.dailyhub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.IOException

private data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val actions: List<String> = emptyList(),
    val error: Boolean = false,
)

private val SUGGESTIONS = listOf("Cosa ho in programma oggi?", "Mi serve l'ombrello?", "Che task ho in scadenza?")
private val ActionGreen = Color(0xFF7FE0A8)

private fun newConversationId(): String = "android-" + java.util.UUID.randomUUID().toString().take(18)

@Composable
fun ChatScreen(prefs: Prefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Lilith") }
    var conversationId by remember { mutableStateOf(newConversationId()) }
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        try {
            val info = prefs.client().assistantInfo()
            name = info.name
            if (!info.enabled) {
                messages.add(ChatMessage(false, "Non sono ancora configurata: sul PC hub aggiungi GEMINI_API_KEY nel file .env.", error = true))
            }
        } catch (e: IOException) {
            messages.add(ChatMessage(false, e.message ?: "Hub non raggiungibile", error = true))
        }
    }

    LaunchedEffect(messages.size, busy) {
        val count = messages.size + if (busy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || busy) return
        input = ""
        messages.add(ChatMessage(true, message))
        busy = true
        scope.launch {
            try {
                val result = prefs.client().chat(conversationId, message)
                messages.add(ChatMessage(false, result.reply, result.actions))
                if (result.actions.isNotEmpty()) RefreshWorker.refreshNow(context)
            } catch (e: IOException) {
                messages.add(ChatMessage(false, e.message ?: "Hub non raggiungibile", error = true))
            } finally {
                busy = false
            }
        }
    }

    fun reset() {
        val old = conversationId
        conversationId = newConversationId()
        messages.clear()
        scope.launch { runCatching { prefs.client().resetChat(old) } }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Indietro") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (busy) "sta pensando..." else "Assistente personale",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { reset() }, enabled = !busy) { Text("Nuova") }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Come posso aiutarti?", style = MaterialTheme.typography.titleMedium)
                            SUGGESTIONS.forEach { suggestion ->
                                OutlinedButton(onClick = { send(suggestion) }) { Text(suggestion) }
                            }
                        }
                    }
                }
                items(messages) { message -> Bubble(message) }
                if (busy) item { Bubble(ChatMessage(false, "...")) }
            }

            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(2000) },
                    placeholder = { Text("Chiedi a $name...") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { send(input) },
                    enabled = !busy && input.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Invia") }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (message.fromUser) 16.dp else 4.dp,
                    bottomEnd = if (message.fromUser) 4.dp else 16.dp,
                ),
                color = if (message.fromUser) colors.primary else colors.surfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    message.text,
                    color = when {
                        message.fromUser -> colors.onPrimary
                        message.error -> colors.error
                        else -> colors.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            message.actions.forEach { action ->
                Text("✓ $action", color = ActionGreen, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, start = 6.dp))
            }
        }
    }
}
