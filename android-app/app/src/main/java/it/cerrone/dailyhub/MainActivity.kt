package it.cerrone.dailyhub

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshWorker.schedulePeriodic(this)
        val prefs = Prefs(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var chatOpen by remember { mutableStateOf(false) }
                if (chatOpen) {
                    ChatScreen(prefs, onBack = { chatOpen = false })
                } else {
                    HubScreen(prefs, onOpenChat = { chatOpen = true })
                }
            }
        }
    }
}

@Composable
private fun HubScreen(prefs: Prefs, onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(prefs.hubUrl.ifBlank { "http://" }) }
    var token by remember { mutableStateOf(prefs.token) }
    var status by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(!prefs.isConfigured) }
    var newTitle by remember { mutableStateOf("") }
    var newDue by remember { mutableStateOf<LocalDate?>(null) }
    val tasks = remember { mutableStateListOf<TaskInfo>() }

    fun withHub(action: suspend (HubClient) -> Unit) {
        if (!prefs.isConfigured) {
            status = "Configura indirizzo e token"
            return
        }
        scope.launch {
            try {
                action(prefs.client())
            } catch (e: IOException) {
                status = "Errore: ${e.message ?: "hub non raggiungibile"}"
            }
        }
    }

    fun reloadTasks() = withHub { client ->
        val fresh = client.tasks()
        tasks.clear()
        tasks.addAll(fresh)
        RefreshWorker.refreshNow(context)
    }

    LaunchedEffect(Unit) { if (prefs.isConfigured) reloadTasks() }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("NEXUS", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    if (prefs.isConfigured) Button(onClick = onOpenChat) { Text("Lilith") }
                    TextButton(onClick = { showSettings = !showSettings }) { Text("Impostazioni") }
                }
            }

            if (showSettings) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = url, onValueChange = { url = it },
                                label = { Text("Indirizzo hub (es. http://100.64.0.5:8765)") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            )
                            OutlinedTextField(
                                value = token, onValueChange = { token = it },
                                label = { Text("Token API") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(onClick = {
                                prefs.hubUrl = url
                                prefs.token = token
                                status = "Verifica connessione..."
                                withHub { client ->
                                    client.today()
                                    AlarmSync.push(context, client)
                                    status = "Connesso"
                                    showSettings = false
                                    reloadTasks()
                                }
                            }) { Text("Salva e verifica") }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val alarm = AlarmSync.nextAlarmIso(context)
                        Text("Prossima sveglia: ${alarm?.let(::formatTime) ?: "nessuna"}")
                        OutlinedButton(onClick = {
                            withHub { client ->
                                AlarmSync.push(context, client)
                                status = "Sveglia inviata al PC"
                            }
                        }) { Text("Sincronizza sveglia ora") }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nuovo task", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = newTitle, onValueChange = { newTitle = it.take(200) },
                            label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                val start = newDue ?: LocalDate.now()
                                DatePickerDialog(context, { _, y, m, d -> newDue = LocalDate.of(y, m + 1, d) },
                                    start.year, start.monthValue - 1, start.dayOfMonth).show()
                            }) { Text(newDue?.let { "Scadenza ${it.dayOfMonth}/${it.monthValue}" } ?: "Scadenza") }
                            if (newDue != null) TextButton(onClick = { newDue = null }) { Text("Rimuovi") }
                            Spacer(Modifier.weight(1f))
                            Button(enabled = newTitle.isNotBlank(), onClick = {
                                val title = newTitle.trim()
                                val due = newDue
                                withHub { client ->
                                    client.addTask(title, due)
                                    newTitle = ""
                                    newDue = null
                                    status = "Task aggiunto"
                                    reloadTasks()
                                }
                            }) { Text("Aggiungi") }
                        }
                    }
                }
            }

            item { Text("Task aperti", style = MaterialTheme.typography.titleMedium) }
            if (tasks.isEmpty()) item { Text("Nessun task aperto") }
            items(tasks, key = { it.id }) { task ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = {
                        withHub { client -> client.completeTask(task.id); reloadTasks() }
                    })
                    Column(Modifier.weight(1f)) {
                        Text(task.title)
                        task.due?.let {
                            Text(
                                if (task.overdue) "Scaduto il ${it.dayOfMonth}/${it.monthValue}" else "Entro il ${it.dayOfMonth}/${it.monthValue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (task.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { withHub { client -> client.deleteTask(task.id); reloadTasks() } }) { Text("Elimina") }
                }
            }

            if (status.isNotBlank()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
