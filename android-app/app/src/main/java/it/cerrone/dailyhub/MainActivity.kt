package it.cerrone.dailyhub

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cerrone.dailyhub.widget.HubWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    /** Grows each time the widget microphone opens the app, so the chat starts listening. */
    private val voiceRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        RefreshWorker.schedulePeriodic(this)
        if (savedInstanceState == null && wantsVoice(intent)) voiceRequests.intValue++
        val prefs = Prefs(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val request = voiceRequests.intValue
                var chatOpen by remember { mutableStateOf(request > 0) }
                var settingsOpen by remember { mutableStateOf(false) }
                var listenOnOpen by remember { mutableIntStateOf(request) }
                LaunchedEffect(request) {
                    if (request > 0) {
                        chatOpen = true
                        listenOnOpen = request
                    }
                }
                when {
                    chatOpen -> ChatScreen(prefs, listenRequest = listenOnOpen, onBack = {
                        chatOpen = false
                        listenOnOpen = 0
                    })
                    settingsOpen -> SettingsScreen(prefs, onBack = { settingsOpen = false })
                    else -> HubScreen(
                        prefs,
                        onOpenChat = { voice -> if (voice) voiceRequests.intValue++ else chatOpen = true },
                        onOpenSettings = { settingsOpen = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (wantsVoice(intent)) voiceRequests.intValue++
    }

    companion object {
        const val ACTION_VOICE = "it.cerrone.dailyhub.VOICE"

        private fun wantsVoice(intent: Intent?): Boolean = intent?.action == ACTION_VOICE
    }
}

@Composable
private fun HubScreen(prefs: Prefs, onOpenChat: (voice: Boolean) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var alarmVersion by remember { mutableIntStateOf(0) }
    var newTitle by remember { mutableStateOf("") }
    var newDue by remember { mutableStateOf<LocalDate?>(null) }
    var snapshot by remember { mutableStateOf(prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }) }
    val tasks = remember { mutableStateListOf<TaskInfo>().apply { snapshot?.let { addAll(it.tasks) } } }

    val now = LocalTime.now()
    val sky = skyOf(snapshot, now.hour)
    val accent = skyAccent(sky)
    val today = snapshot?.date ?: LocalDate.now()

    fun showSnapshot(json: String) {
        snapshot = parseToday(json)
        tasks.clear()
        tasks.addAll(snapshot?.tasks.orEmpty())
    }

    var editing by remember { mutableStateOf<TaskInfo?>(null) }

    /** Local copy first: the list updates at once, then the PC hears about it a second later. */
    fun afterTaskChange() {
        scope.launch {
            LocalToday.refreshTasks(context)?.let { showSnapshot(it) }
            HubWidget.refresh(context)
            SyncTrigger.nudge(context)
        }
    }

    fun reload() {
        scope.launch {
            try {
                showSnapshot(LocalToday.refresh(context))
                if (prefs.canSync) {
                    status = SyncClient(context).exchange()
                    showSnapshot(LocalToday.refresh(context))
                }
                HubWidget.refresh(context)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: tr("Aggiornamento non riuscito", "Update failed")
            }
        }
    }

    val askSportPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Notification and Bluetooth are optional; the microphone is what sport mode needs.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            SportService.start(context)
        } else {
            status = tr("Per la modalità sport serve il permesso del microfono", "Sport mode needs the microphone permission")
        }
    }

    fun toggleSport(on: Boolean) {
        if (!on) {
            SportService.stop(context)
            return
        }
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = wanted.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) SportService.start(context) else askSportPermissions.launch(missing.toTypedArray())
    }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        reload()
    }

    // Changes the PC sent (picked up by the sync below) show up here without a full reload.
    val applied by SyncSignal.applied.collectAsState()
    LaunchedEffect(applied) {
        if (applied > 0) LocalToday.refreshTasks(context)?.let { showSnapshot(it) }
    }

    // While the app is on screen, ask the PC for news every 15 seconds.
    LaunchedEffect(Unit) {
        context.findActivity()?.repeatOnLifecycle(Lifecycle.State.STARTED) { SyncTrigger.watch(context) }
    }

    LaunchedEffect(Unit) {
        reload()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            askCalendar.launch(Manifest.permission.READ_CALENDAR)
        }
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk && !prefs.askedNotifications) {
            prefs.askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SkyBackground(sky) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(8.dp))
                    Text("NEXUS", color = Nexus.Text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
                    Spacer(Modifier.weight(1f))
                    Pill("Sync", accent) { reload() }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = tr("Impostazioni", "Settings"), tint = Nexus.Text2)
                    }
                }
            }

            item {
                Column {
                    Text(greetingFor(now.hour), color = Nexus.Text, fontSize = 26.sp, fontWeight = FontWeight.Light)
                    Text(italianDate(today), color = Nexus.Text3, fontSize = 15.sp)
                }
            }

            item { WeatherHero(snapshot, accent) }

            if (!prefs.canSync) {
                item {
                    GlassCard {
                        SectionLabel(tr("Collega il PC", "Connect the PC"), accent)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr("Meteo e agenda funzionano già. Per task, chat e Lilith condivisi scrivi il token del PC in Impostazioni → Sync.", "Weather and agenda already work. For shared tasks, chat and Lilith, enter the PC token in Settings → Sync."),
                            color = Nexus.Text2, fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Pill(tr("Apri Sync", "Open Sync"), accent, filled = true, onClick = onOpenSettings)
                    }
                }
            }

            item { AgendaCard(snapshot, accent) }

            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(if (tasks.isEmpty()) tr("Task", "Tasks") else tr("Task · ${tasks.size}", "Tasks · ${tasks.size}"), accent, Modifier.weight(1f))
                        val overdue = tasks.count { it.overdue }
                        if (overdue > 0) Text(tr("$overdue scaduti", "$overdue overdue"), color = Nexus.Warn, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTitle, onValueChange = { newTitle = it.take(200) },
                            placeholder = { Text(tr("Aggiungi un task...", "Add a task...")) }, singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = glassFieldColors(accent),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (newTitle.isNotBlank()) accent else Nexus.Glass)
                                .clickable(enabled = newTitle.isNotBlank()) {
                                    val title = newTitle.trim()
                                    val due = newDue
                                    scope.launch {
                                        withContext(Dispatchers.IO) { LocalDb.get(context).addTask(title, due) }
                                        newTitle = ""
                                        newDue = null
                                        status = tr("Task aggiunto", "Task added")
                                        afterTaskChange()
                                    }
                                },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = tr("Aggiungi", "Add"), tint = if (newTitle.isNotBlank()) Nexus.Ink else Nexus.Text3)
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill(newDue?.let { tr("Entro il ${it.dayOfMonth}/${it.monthValue}", "Due ${it.dayOfMonth}/${it.monthValue}") } ?: tr("Scadenza", "Due date"), accent) {
                            val start = newDue ?: LocalDate.now()
                            DatePickerDialog(context, { _, y, m, d -> newDue = LocalDate.of(y, m + 1, d) },
                                start.year, start.monthValue - 1, start.dayOfMonth).show()
                        }
                        if (newDue != null) {
                            Spacer(Modifier.width(6.dp))
                            Pill(tr("Rimuovi", "Remove"), accent) { newDue = null }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (tasks.isEmpty()) Text(tr("Tutto fatto", "All done"), color = Nexus.Text3, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task, today, accent,
                    onDone = {
                        scope.launch {
                            withContext(Dispatchers.IO) { LocalDb.get(context).completeTask(task.id) }
                            afterTaskChange()
                        }
                    },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { LocalDb.get(context).deleteTask(task.id) }
                            afterTaskChange()
                        }
                    },
                    onEdit = { editing = task },
                )
            }

            snapshot?.weather?.takeIf { it.daily.isNotEmpty() }?.let { weather ->
                item { WeekCard(weather, today, accent) }
            }

            snapshot?.takeIf { it.calendarError == null && it.week.isNotEmpty() }?.let { snap ->
                item { WeekAgendaCard(snap, today, accent) }
            }

            item { SportCard(accent, prefs, onToggle = ::toggleSport) }

            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_alarm), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Prossima sveglia", "Next alarm"), color = Nexus.Text3, fontSize = 12.sp)
                            // alarmVersion makes the card re-read the phone after "Ignora" / "Ripristina".
                            val version = alarmVersion
                            val alarm = remember(version) { AlarmSync.nextAlarmIso(context) }
                            val shownKey = remember(version) { AlarmSync.shownKey(context) }
                            val ignored = remember(version) { AlarmSync.ignoredAlarm(context) }
                            val hiddenTimes = remember(version) { prefs.ignoredAlarmTimes.sorted() }
                            Text(alarm?.let(::formatTime) ?: tr("Nessuna", "None"), color = Nexus.Text, fontSize = 20.sp, fontWeight = FontWeight.Light)
                            ignored?.let {
                                val who = it.appName?.let { app -> tr("di $app", "by $app") } ?: tr("nascosta da te", "hidden by you")
                                Text(tr("Ignorata: ${formatTime(it.iso)} $who", "Ignored: ${formatTime(it.iso)} $who"), color = Nexus.Text3, fontSize = 12.sp, maxLines = 2)
                            }
                            if (hiddenTimes.isNotEmpty()) {
                                Text(tr("Orari ignorati: ${hiddenTimes.joinToString()}", "Ignored times: ${hiddenTimes.joinToString()}"), color = Nexus.Text3, fontSize = 12.sp, maxLines = 2)
                            }
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (shownKey != null) {
                                    Pill(tr("Ignora", "Ignore"), accent) {
                                        AlarmSync.ignore(context, shownKey)
                                        alarmVersion++
                                        HubWidget.refresh(context)
                                        reload()
                                        status = tr("Sveglia delle $shownKey ignorata", "Alarm at $shownKey ignored")
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                if (hiddenTimes.isNotEmpty()) {
                                    Pill(tr("Ripristina", "Restore"), accent) {
                                        AlarmSync.restoreAll(context)
                                        alarmVersion++
                                        HubWidget.refresh(context)
                                        reload()
                                        status = tr("Sveglie ripristinate", "Alarms restored")
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                Pill(tr("Invia al PC", "Send to PC"), accent) { reload() }
                            }
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                item { Text(status, color = Nexus.Text3, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
            }

            item { Spacer(Modifier.navigationBarsPadding().height(96.dp)) }
        }

        LilithLauncher(accent, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp), onOpenChat)
    }

    editing?.let { task ->
        EditTaskDialog(
            task, accent,
            onDismiss = { editing = null },
            onSave = { title, due ->
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { LocalDb.get(context).updateTask(task.id, title, due) }
                    status = tr("Task modificato", "Task updated")
                    afterTaskChange()
                }
            },
        )
    }
}

/** Title and due date of a task, edited in place. Saving updates the list right away and syncs. */
@Composable
private fun EditTaskDialog(task: TaskInfo, accent: Color, onDismiss: () -> Unit, onSave: (String, LocalDate?) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(task.title) }
    var due by remember { mutableStateOf(task.due) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101826),
        title = { Text(tr("Modifica task", "Edit task"), color = Nexus.Text) },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(200) },
                    singleLine = true, colors = glassFieldColors(accent), modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(due?.let { tr("Entro il ${it.dayOfMonth}/${it.monthValue}", "Due ${it.dayOfMonth}/${it.monthValue}") } ?: tr("Scadenza", "Due date"), accent) {
                        val start = due ?: LocalDate.now()
                        DatePickerDialog(context, { _, y, m, d -> due = LocalDate.of(y, m + 1, d) },
                            start.year, start.monthValue - 1, start.dayOfMonth).show()
                    }
                    if (due != null) {
                        Spacer(Modifier.width(6.dp))
                        Pill(tr("Rimuovi", "Remove"), accent) { due = null }
                    }
                }
            }
        },
        confirmButton = { Pill(tr("Salva", "Save"), accent, filled = true) { if (title.isNotBlank()) onSave(title.trim(), due) } },
        dismissButton = { Pill(tr("Annulla", "Cancel"), accent, onClick = onDismiss) },
    )
}

/** The activity behind a Compose context, which can be wrapped. */
private fun android.content.Context.findActivity(): ComponentActivity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/** Switch for sport mode: while on, "Lilith, ..." works from the earbuds. */
@Composable
private fun SportCard(accent: Color, prefs: Prefs, onToggle: (Boolean) -> Unit) {
    var withMusic by remember { mutableStateOf(prefs.sportPhoneMic) }
    @Composable
    fun switchColors() = nexusSwitchColors(accent)
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(tr("Modalità sport", "Sport mode"), accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (SportState.active) tr("Ti ascolto: di' «Lilith» e la domanda", "I'm listening: say \"Lilith\" and your question") else tr("Chiamala per nome dagli auricolari", "Call her by name from the earbuds"),
                    color = Nexus.Text2, fontSize = 14.sp,
                )
            }
            Switch(checked = SportState.active, onCheckedChange = onToggle, colors = switchColors())
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Con la musica", "With music"), color = Nexus.Text, fontSize = 15.sp)
                Text(
                    tr("Ascolta dal microfono del telefono: gli auricolari restano per la musica", "Listens through the phone microphone: the earbuds stay for the music"),
                    color = Nexus.Text3, fontSize = 12.sp,
                )
            }
            Switch(
                checked = withMusic,
                onCheckedChange = { withMusic = it; prefs.sportPhoneMic = it },
                colors = switchColors(),
            )
        }
    }
}

@Composable
private fun WeatherHero(snapshot: TodaySnapshot?, accent: Color) {
    val weather = snapshot?.weather
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (weather != null) {
            Image(
                painterResource(weatherIcon(weather.icon, weather.isDay)),
                contentDescription = weather.description,
                modifier = Modifier.size(92.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                weather?.temperature?.let { "${it.roundToInt()}°" } ?: "--°",
                color = Nexus.Text, fontSize = 72.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 72.sp,
            )
            Text(weather?.description ?: snapshot?.weatherError ?: tr("In attesa dell'hub", "Waiting for the hub"), color = Nexus.Text2, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text((weather?.city ?: "NEXUS").uppercase(), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        }
        if (weather != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${weather.min?.roundToInt() ?: "--"}° / ${weather.max?.roundToInt() ?: "--"}°", color = Nexus.Text2, fontSize = 15.sp)
                Text(tr("Pioggia ${weather.precipitationProbability ?: "--"}%", "Rain ${weather.precipitationProbability ?: "--"}%"), color = Nexus.Text3, fontSize = 13.sp)
            }
        }
    }
}

/** Seven days, one row each: day, icon with rain chance, low, range bar on the week's scale, high. */
@Composable
private fun WeekCard(weather: WeatherInfo, today: LocalDate, accent: Color) {
    val days = weather.daily
    val low = days.minOf { it.min }
    val high = days.maxOf { it.max }
    val span = (high - low).coerceAtLeast(1.0)
    GlassCard {
        SectionLabel(tr("Prossimi giorni", "Coming days"), accent)
        Spacer(Modifier.height(6.dp))
        days.forEachIndexed { index, day ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Nexus.GlassBorder))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(day.date, today), color = Nexus.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(weatherIcon(day.icon, true)), contentDescription = day.description, modifier = Modifier.size(24.dp))
                    rainLabel(day.precipitationProbability)?.let {
                        Text(it, color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 11.sp)
                    }
                }
                Text("${day.min.roundToInt()}°", color = Nexus.Text3, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                Spacer(Modifier.width(10.dp))
                val start = ((day.min - low) / span).toFloat()
                val width = ((day.max - day.min) / span).toFloat().coerceAtLeast(0.04f)
                val now = if (index == 0) weather.temperature?.let { ((it - low) / span).toFloat().coerceIn(0f, 1f) } else null
                RangeBar(start, width, tempColor(day.min), tempColor(day.max), now, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text("${day.max.roundToInt()}°", color = Nexus.Text, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
            }
        }
    }
}

@Composable
private fun RangeBar(start: Float, width: Float, from: Color, to: Color, now: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier.height(10.dp)) {
        val barHeight = 5.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(Nexus.Glass, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
        val left = size.width * start
        val right = (left + size.width * width).coerceAtMost(size.width)
        drawRoundRect(
            Brush.horizontalGradient(listOf(from, to), startX = left, endX = right),
            topLeft = Offset(left, top), size = Size(right - left, barHeight), cornerRadius = radius,
        )
        if (now != null) {
            val center = Offset(size.width * now, size.height / 2)
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 4.5.dp.toPx(), center = center)
            drawCircle(Color.White, radius = 3.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun AgendaCard(snapshot: TodaySnapshot?, accent: Color) {
    val events = snapshot?.events.orEmpty()
    val focus = focusEvent(events, OffsetDateTime.now())
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Agenda", accent, Modifier.weight(1f))
            if (events.isNotEmpty()) Text(tr("${events.size} oggi", "${events.size} today"), color = Nexus.Text3, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        when {
            snapshot?.calendarError != null -> Text(tr("Calendario non collegato", "Calendar not connected"), color = Nexus.Text3, fontSize = 14.sp)
            focus != null -> {
                val event = focus.event
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Nexus.Glass)
                        .border(1.dp, Nexus.GlassBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(if (focus.ongoing) tr("In corso", "Now") else tr("Prossimo", "Next"), accent, Modifier.weight(1f))
                        if (!focus.ongoing) {
                            countdownLabel(event.start, OffsetDateTime.now())?.let { label ->
                                Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Text(event.title, color = Nexus.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val range = buildString {
                        append(event.timeLabel)
                        event.end?.let { append(" – ").append(formatTime(it)) }
                        event.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    }
                    Text(range, color = Nexus.Text2, fontSize = 14.sp, maxLines = 1)
                }
            }
            else -> Text(if (events.isEmpty()) tr("Agenda libera oggi", "Nothing on the agenda today") else tr("Impegni di oggi completati", "Today's events are over"), color = Nexus.Text2, fontSize = 14.sp)
        }
        events.filter { it != focus?.event }.forEach { event ->
            val whenLabel = if (event.allDay) tr("Tutto il giorno", "All day") else buildString {
                append(event.timeLabel)
                event.end?.let { append(" – ").append(formatTime(it)) }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Text(
                    whenLabel,
                    color = Nexus.Text2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp,
                    modifier = Modifier.width(112.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(event.title, color = Nexus.Text, fontSize = 14.sp, maxLines = 2)
                    event.location?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Nexus.Text3, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Next seven days, empty ones left out. Same facts as the dashboard week agenda. */
@Composable
private fun WeekAgendaCard(snapshot: TodaySnapshot, today: LocalDate, accent: Color) {
    val days = snapshot.week.filter { it.events.isNotEmpty() }
    GlassCard {
        SectionLabel(tr("Agenda della settimana", "This week's agenda"), accent)
        Spacer(Modifier.height(8.dp))
        if (days.isEmpty()) {
            Text(tr("Settimana libera", "Free week"), color = Nexus.Text3, fontSize = 14.sp)
            return@GlassCard
        }
        days.forEachIndexed { index, day ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.width(52.dp)) {
                    Text(dayLabel(day.date, today), color = Nexus.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(dayMonthLabel(day.date), color = Nexus.Text3, fontSize = 11.sp)
                }
                Column(Modifier.weight(1f)) {
                    day.events.forEach { event ->
                        Text(
                            buildString {
                                append(if (event.allDay) tr("Tutto il giorno", "All day") else event.timeLabel)
                                append("  ")
                                append(event.title)
                                event.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                            },
                            color = Nexus.Text2,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskInfo, today: LocalDate, accent: Color, onDone: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val due = dueLabel(task, today)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Nexus.Glass)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (task.overdue) Nexus.Warn else accent, CircleShape)
                .clickable(onClick = onDone),
        )
        Spacer(Modifier.width(12.dp))
        Text(task.title, color = Nexus.Text, fontSize = 15.sp, maxLines = 2, modifier = Modifier.weight(1f).clickable(onClick = onEdit))
        if (due != null) {
            Text(
                due,
                color = if (task.overdue) Nexus.Warn else Nexus.Text2,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (task.overdue) Nexus.Warn.copy(alpha = 0.2f) else Nexus.Glass)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = tr("Modifica", "Edit"), tint = Nexus.Text3, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = tr("Elimina", "Delete"), tint = Nexus.Text3, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun Pill(text: String, accent: Color, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (filled) Nexus.Ink else Nexus.Text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) accent else Nexus.Glass)
            .border(1.dp, if (filled) Color.Transparent else Nexus.GlassBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Same pair as on the dashboard: the Lilith pill opens the chat, the round button talks straight away. */
@Composable
private fun LilithLauncher(accent: Color, modifier: Modifier, onOpenChat: (voice: Boolean) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC101826))
                .border(1.dp, Nexus.GlassBorder, RoundedCornerShape(50))
                .clickable { onOpenChat(false) }
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Orb(36.dp, accent)
            Spacer(Modifier.width(8.dp))
            Text("Lilith", color = Nexus.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable { onOpenChat(true) },
        ) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = tr("Parla con Lilith", "Talk to Lilith"), tint = Nexus.Ink, modifier = Modifier.size(24.dp))
        }
    }
}
