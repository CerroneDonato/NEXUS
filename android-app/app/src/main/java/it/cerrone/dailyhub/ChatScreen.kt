package it.cerrone.dailyhub

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalTime
import java.time.OffsetDateTime

private data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val actions: List<String> = emptyList(),
    val error: Boolean = false,
)

private val SUGGESTIONS: List<String>
    get() = listOf(
        tr("Cosa ho in programma oggi?", "What is on my agenda today?"),
        tr("Mi serve l'ombrello?", "Do I need an umbrella?"),
        tr("Che task ho in scadenza?", "Which tasks are due soon?"),
    )

private fun newConversationId(): String = "android-" + java.util.UUID.randomUUID().toString().take(18)

/** [listenRequest] grows each time the widget microphone opens the chat; every new value starts listening. */
@Composable
fun ChatScreen(prefs: Prefs, listenRequest: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var name by remember { mutableStateOf("Lilith") }
    var conversationId by remember { mutableStateOf(newConversationId()) }
    var showHistory by remember { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<ChatSummary>?>(null) }
    var historyError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val sky = remember { skyOf(prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }, LocalTime.now().hour) }
    val accent = skyAccent(sky)

    val speaker = remember { Speaker(context) { speaking = it } }

    fun send(text: String, spoken: Boolean = false) {
        val message = text.trim()
        if (message.isEmpty() || busy) return
        input = ""
        messages.add(ChatMessage(true, message))
        busy = true
        scope.launch {
            try {
                val result = PhoneAssistant(context).chat(conversationId, message)
                messages.add(ChatMessage(false, result.reply, result.actions))
                if (spoken) speaker.speak(result.reply)
                if (result.actions.isNotEmpty()) RefreshWorker.refreshNow(context)
                SyncTrigger.nudge(context)
            } catch (e: Exception) {
                messages.add(ChatMessage(false, e.message ?: tr("Non riesco a rispondere adesso", "I can't answer right now"), error = true))
            } finally {
                busy = false
            }
        }
    }

    val voice = remember {
        VoiceInput(
            context,
            onPartial = { input = it },
            onLevel = { level = it },
            onFinal = { text ->
                listening = false
                level = 0f
                send(text, spoken = true)
            },
            onError = { message ->
                listening = false
                level = 0f
                message?.let { messages.add(ChatMessage(false, it, error = true)) }
            },
        )
    }

    fun beginListening() {
        speaker.stop()
        input = ""
        listening = true
        voice.start()
    }

    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening()
        else messages.add(ChatMessage(false, tr("Per parlare con $name serve il permesso del microfono.", "To talk to $name the microphone permission is needed."), error = true))
    }

    fun startMic() {
        when {
            busy || listening -> Unit
            !voice.isAvailable -> messages.add(ChatMessage(false, tr("Riconoscimento vocale non disponibile su questo telefono.", "Speech recognition is not available on this phone."), error = true))
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> beginListening()
            else -> askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleMic() {
        if (listening) voice.stop() else startMic()
    }

    fun loadHistory() {
        historyError = null
        scope.launch {
            try {
                historyItems = PhoneAssistant(context).history()
            } catch (e: Exception) {
                historyError = e.message ?: tr("Storico non disponibile", "History not available")
            }
        }
    }

    fun toggleHistory() {
        if (showHistory) {
            showHistory = false
        } else {
            voice.destroy()
            speaker.stop()
            listening = false
            showHistory = true
            loadHistory()
        }
    }

    fun openConversation(item: ChatSummary) {
        scope.launch {
            try {
                val stored = PhoneAssistant(context).messages(item.id)
                messages.clear()
                stored.forEach { messages.add(ChatMessage(it.fromUser, it.text, it.actions)) }
                conversationId = item.id
                showHistory = false
            } catch (e: Exception) {
                historyError = e.message ?: tr("Conversazione non disponibile", "Conversation not available")
            }
        }
    }

    fun removeConversation(item: ChatSummary) {
        scope.launch {
            try {
                PhoneAssistant(context).deleteHistory(item.id)
                historyItems = historyItems?.filterNot { it.id == item.id }
                if (item.id == conversationId) {
                    conversationId = newConversationId()
                    messages.clear()
                }
            } catch (e: Exception) {
                historyError = e.message ?: tr("Non riesco a eliminare la conversazione", "I can't delete the conversation")
            }
        }
    }

    fun reset() {
        voice.destroy()
        speaker.stop()
        listening = false
        showHistory = false
        conversationId = newConversationId()
        messages.clear()
    }

    fun leave() {
        voice.destroy()
        speaker.stop()
        onBack()
    }

    BackHandler { if (showHistory) showHistory = false else leave() }

    DisposableEffect(Unit) {
        onDispose {
            voice.destroy()
            speaker.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        name = prefs.assistantName.ifBlank { "Lilith" }
        if (prefs.geminiKey.isBlank()) {
            messages.add(ChatMessage(false, tr("Meteo, agenda e task funzionano già. Per il resto sincronizza con il PC: da lì arriva la chiave Gemini.", "Weather, agenda and tasks already work. For the rest, sync with the PC: the Gemini key comes from there.")))
        }
    }

    LaunchedEffect(listenRequest) { if (listenRequest > 0) startMic() }

    LaunchedEffect(messages.size, busy) {
        val count = messages.size + if (busy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val status = when {
        listening -> tr("Ti ascolto...", "Listening...")
        busy -> tr("Sta pensando...", "Thinking...")
        speaking -> tr("Sta parlando...", "Speaking...")
        else -> tr("Assistente personale", "Personal assistant")
    }

    SkyBackground(sky) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { leave() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Nexus.Text)
                }
                Orb(40.dp, accent, active = busy || listening || speaking, level = level)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Nexus.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(status, color = if (listening) Nexus.Warn else Nexus.Text3, fontSize = 13.sp)
                }
                IconButton(onClick = { toggleHistory() }) {
                    Icon(
                        painterResource(R.drawable.ic_history),
                        contentDescription = tr("Storico conversazioni", "Conversation history"),
                        tint = if (showHistory) accent else Nexus.Text2,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = { reset() }, enabled = !busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = tr("Nuova conversazione", "New conversation"), tint = Nexus.Text2)
                }
            }

            if (showHistory) {
                HistoryList(
                    items = historyItems,
                    error = historyError,
                    accent = accent,
                    onOpen = { openConversation(it) },
                    onDelete = { removeConversation(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (messages.isEmpty()) {
                        item { EmptyChat(name, accent, listening || speaking, level) { send(it) } }
                    }
                    items(messages) { message -> Bubble(message, accent) }
                    if (busy) item { Bubble(ChatMessage(false, "..."), accent) }
                }

                Composer(
                    input = input,
                    onInput = { input = it.take(2000) },
                    placeholder = if (listening) tr("Parla pure...", "Go ahead...") else tr("Scrivi o tocca il microfono", "Type or tap the microphone"),
                    listening = listening,
                    level = level,
                    busy = busy,
                    accent = accent,
                    onMic = { toggleMic() },
                    onSend = { send(input) },
                )
            }
        }
    }
}

@Composable
private fun HistoryList(
    items: List<ChatSummary>?,
    error: String?,
    accent: Color,
    onOpen: (ChatSummary) -> Unit,
    onDelete: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember { OffsetDateTime.now() }
    when {
        items == null -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(error ?: tr("Carico lo storico...", "Loading history..."), color = if (error != null) Nexus.Warn else Nexus.Text3, fontSize = 14.sp)
        }
        items.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(tr("Nessuna conversazione salvata.\nLe prossime resteranno qui.", "No saved conversations.\nThe next ones will stay here."), color = Nexus.Text3, fontSize = 14.sp)
        }
        else -> LazyColumn(modifier.padding(horizontal = 14.dp)) {
            if (error != null) item { Text(error, color = Nexus.Warn, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            items(items, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Nexus.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${sourceLabel(item.source)} · " + tr("${item.messages} messaggi", "${item.messages} messages"), color = Nexus.Text3, fontSize = 13.sp)
                    }
                    Text(historyWhenLabel(item.lastAt, now), color = Nexus.Text3, fontSize = 13.sp)
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(
                            painterResource(R.drawable.ic_close_small),
                            contentDescription = tr("Elimina conversazione", "Delete conversation"),
                            tint = Nexus.Text3,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Nexus.GlassBorder))
            }
        }
    }
}

@Composable
private fun EmptyChat(name: String, accent: Color, active: Boolean, level: Float, onSuggestion: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Orb(132.dp, accent, active = active, level = level)
        Spacer(Modifier.height(6.dp))
        Text(tr("Ciao, sono $name", "Hi, I'm $name"), color = Nexus.Text, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Text(tr("Tocca il microfono e parla, oppure scrivi.", "Tap the microphone and speak, or type."), color = Nexus.Text3, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        SUGGESTIONS.forEach { suggestion ->
            Text(
                suggestion,
                color = Nexus.Text2,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Nexus.Glass)
                    .border(1.dp, Nexus.GlassBorder, RoundedCornerShape(50))
                    .clickable { onSuggestion(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInput: (String) -> Unit,
    placeholder: String,
    listening: Boolean,
    level: Float,
    busy: Boolean,
    accent: Color,
    onMic: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            placeholder = { Text(placeholder) },
            maxLines = 4,
            shape = RoundedCornerShape(26.dp),
            colors = glassFieldColors(if (listening) Nexus.Warn else accent),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (input.isNotBlank() && !listening) {
            RoundButton(accent, enabled = !busy, onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia", tint = Nexus.Ink)
            }
        } else {
            val ring = if (listening) 6.dp + (level * 10).dp else 0.dp
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp + ring * 2)) {
                if (listening) {
                    Box(Modifier.size(56.dp + ring * 2).clip(CircleShape).background(Nexus.Warn.copy(alpha = 0.25f)))
                }
                RoundButton(if (listening) Nexus.Warn else accent, enabled = !busy, onClick = onMic) {
                    Icon(
                        painterResource(if (listening) R.drawable.ic_stop else R.drawable.ic_mic),
                        contentDescription = if (listening) tr("Smetti di ascoltare", "Stop listening") else tr("Parla", "Speak"),
                        tint = Nexus.Ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundButton(color: Color, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
    ) { content() }
}

@Composable
private fun Bubble(message: ChatMessage, accent: Color) {
    val user = message.fromUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
            val shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (user) 20.dp else 6.dp,
                bottomEnd = if (user) 6.dp else 20.dp,
            )
            Text(
                message.text,
                color = when {
                    user -> Nexus.Ink
                    message.error -> Nexus.Warn
                    else -> Nexus.Text
                },
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = if (user) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(if (user) accent else Nexus.Glass)
                    .then(if (user) Modifier else Modifier.border(1.dp, Nexus.GlassBorder, shape))
                    .padding(horizontal = 15.dp, vertical = 11.dp),
            )
            message.actions.forEach { action ->
                Text(
                    "✓  $action",
                    color = Nexus.Ok,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Nexus.Ok.copy(alpha = 0.14f))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
        }
    }
}
