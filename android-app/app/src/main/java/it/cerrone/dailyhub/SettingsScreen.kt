package it.cerrone.dailyhub

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import it.cerrone.dailyhub.widget.HubWidget
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

private enum class Page(private val italian: String, private val english: String, val icon: ImageVector?) {
    MENU("Impostazioni", "Settings", null),
    SYNC("Sync", "Sync", Icons.Filled.Refresh),
    LILITH("Lilith", "Lilith", Icons.Filled.Star),
    PLACE("Luogo e meteo", "Place and weather", Icons.Filled.LocationOn),
    CALENDAR("Calendario", "Calendar", Icons.Filled.DateRange),
    REMINDERS("Promemoria e sveglia", "Reminders and alarm", Icons.Filled.Notifications),
    SPORT("Modalità sport", "Sport mode", Icons.Filled.Favorite),
    LANGUAGE("Lingua", "Language", Icons.Filled.Settings),
    INFO("Info", "Info", Icons.Filled.Info);

    val title: String get() = tr(italian, english)
}

/** Full-page settings: a menu, and one page per topic. Features stay on the home page. */
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    val snapshot = remember { prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() } }
    val sky = skyOf(snapshot, LocalTime.now().hour)
    val accent = skyAccent(sky)
    var page by remember { mutableStateOf(Page.MENU) }

    fun back() {
        if (page == Page.MENU) onBack() else page = Page.MENU
    }
    BackHandler { back() }

    SkyBackground(sky) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Indietro", "Back"), tint = Nexus.Text)
                }
                Text(page.title, color = Nexus.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (page) {
                    Page.MENU -> MenuPage(prefs, accent) { page = it }
                    Page.SYNC -> SyncPage(prefs, accent)
                    Page.LILITH -> LilithPage(prefs, accent)
                    Page.PLACE -> PlacePage(prefs, accent)
                    Page.CALENDAR -> CalendarPage(accent)
                    Page.REMINDERS -> RemindersPage(prefs, accent)
                    Page.SPORT -> SportPage(prefs, accent)
                    Page.LANGUAGE -> LanguagePage(prefs, accent)
                    Page.INFO -> InfoPage(prefs, accent)
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}

@Composable
private fun MenuPage(prefs: Prefs, accent: Color, onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    val last = remember { SyncHistory.all(prefs).firstOrNull() }
    fun subtitle(page: Page): String = when (page) {
        Page.SYNC -> when {
            !prefs.canSync -> tr("Da collegare", "To connect")
            last == null -> tr("Mai provata", "Never tried")
            last.ok -> tr("Ultima sync ${whenLabel(last.at)}", "Last sync ${whenLabel(last.at)}")
            else -> tr("Errore: ${last.message}", "Error: ${last.message}")
        }
        Page.LILITH -> prefs.assistantName + " · " + prefs.geminiModel + if (prefs.geminiKey.isBlank()) tr(" · manca la chiave", " · key missing") else ""
        Page.PLACE -> prefs.city
        Page.CALENDAR -> if (hasCalendar(context)) tr("Accesso consentito", "Access granted") else tr("Permesso mancante", "Permission missing")
        Page.REMINDERS -> tr(
            "${prefs.reminderLeadMinutes} min prima · Buongiorno ${if (prefs.morningBriefing) "attivo" else "spento"}",
            "${prefs.reminderLeadMinutes} min before · Good morning ${if (prefs.morningBriefing) "on" else "off"}",
        )
        Page.SPORT -> if (prefs.sportPhoneMic) tr("Con la musica", "With music") else tr("Microfono degli auricolari", "Earbuds microphone")
        Page.INFO -> tr("Versione ${appVersion(context)}", "Version ${appVersion(context)}")
        Page.LANGUAGE -> when (prefs.language) {
            Lang.IT -> "Italiano"
            Lang.EN -> "English"
            else -> tr("Come il telefono", "Same as the phone")
        }
        Page.MENU -> ""
    }
    GlassCard {
        Page.entries.filter { it != Page.MENU }.forEachIndexed { index, page ->
            if (index > 0) Divider()
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(page) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                page.icon?.let { Icon(it, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(page.title, color = Nexus.Text, fontSize = 16.sp)
                    val warn = page == Page.SYNC && (last?.ok == false || !prefs.canSync)
                    Text(subtitle(page), color = if (warn) Nexus.Warn else Nexus.Text3, fontSize = 12.sp, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Nexus.Text3)
            }
        }
    }
}

@Composable
private fun SyncPage(prefs: Prefs, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(prefs.hubUrl) }
    var token by remember { mutableStateOf(prefs.token) }
    var busy by remember { mutableStateOf<String?>(null) }
    var probe by remember { mutableStateOf<ProbeResult?>(null) }
    var note by remember { mutableStateOf("") }
    val log = remember { mutableStateListOf<SyncReport>().apply { addAll(SyncHistory.all(prefs)) } }

    fun saveFields() {
        prefs.hubUrl = hubAddress(url)
        url = prefs.hubUrl
        prefs.token = token
        token = prefs.token
    }

    fun run(label: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        note = ""
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note = e.message ?: tr("Operazione non riuscita", "Operation failed")
            } finally {
                busy = null
                log.clear()
                log.addAll(SyncHistory.all(prefs))
            }
        }
    }

    val last = log.firstOrNull()
    GlassCard {
        SectionLabel(tr("Stato", "Status"), accent)
        Spacer(Modifier.height(6.dp))
        val (title, color) = when {
            last == null -> tr("Mai sincronizzato", "Never synced") to Nexus.Text
            last.ok -> tr("Sincronizzato", "Synced") to Nexus.Ok
            else -> last.message to Nexus.Warn
        }
        Text(title, color = color, fontSize = 24.sp, fontWeight = FontWeight.Light)
        Text(
            when {
                last == null -> tr("Scrivi il token qui sotto e premi «Sincronizza ora».", "Enter the token below and press 'Sync now'.")
                last.ok -> tr("${whenLabel(last.at)} · inviati: ${last.sent} · ricevuti: ${last.received}", "${whenLabel(last.at)} · sent: ${last.sent} · received: ${last.received}")
                else -> explain(last)
            },
            color = Nexus.Text2, fontSize = 13.sp,
        )
        if (last != null && !last.ok) {
            prefs.lastSyncAt?.let { Text(tr("Ultima sync riuscita: ${whenLabel(it)}", "Last successful sync: ${whenLabel(it)}"), color = Nexus.Text3, fontSize = 12.sp) }
        }
        busy?.let { Text("$it...", color = accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(tr("Sincronizza ora", "Sync now"), accent, filled = true) {
                run(tr("Sincronizzo", "Syncing")) {
                    saveFields()
                    note = SyncClient(context).exchange()
                    LocalToday.refresh(context)
                    HubWidget.refresh(context)
                }
            }
            Spacer(Modifier.width(8.dp))
            Pill(tr("Prova connessione", "Test connection"), accent) {
                run(tr("Provo il collegamento", "Testing the connection")) {
                    saveFields()
                    probe = SyncClient(context).probe()
                }
            }
        }
    }

    probe?.let { result ->
        GlassCard {
            SectionLabel(tr("Prova connessione", "Test connection"), accent)
            Spacer(Modifier.height(6.dp))
            Step(tr("PC raggiungibile", "PC reachable") + if (result.address.isNotBlank()) " · ${result.address}" else "", result.reachable)
            Step(tr("Token accettato", "Token accepted"), result.tokenOk)
            result.pcVersion?.takeIf { it.isNotBlank() }?.let { Step(tr("Server NEXUS $it", "NEXUS server $it"), true) }
            Spacer(Modifier.height(6.dp))
            Text(result.message, color = Nexus.Text2, fontSize = 13.sp)
        }
    }

    GlassCard {
        SectionLabel(tr("Collegamento", "Connection"), accent)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text(tr("Indirizzo del PC", "PC address")) },
            placeholder = { Text(tr("es. 192.168.1.20 (vuoto: lo cerca da solo)", "e.g. 192.168.1.20 (empty: finds it by itself)")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = glassFieldColors(accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text(tr("Token API", "API token")) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = glassFieldColors(accent),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Indirizzo e token sono sulla dashboard del PC: rotellina → Sync. La porta 8765 si aggiunge da sola.", "Address and token are on the PC dashboard: gear → Sync. Port 8765 is added automatically."),
            color = Nexus.Text3, fontSize = 12.sp,
        )
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Salva", accent) {
                saveFields()
                note = "Salvato"
            }
            Spacer(Modifier.width(8.dp))
            Pill(tr("Cerca PC in rete", "Find PC on the network"), accent) {
                run(tr("Cerco il PC", "Looking for the PC")) {
                    val found = PcFinder.find(context)
                    if (found.isNullOrBlank()) {
                        note = tr("Nessun PC NEXUS trovato: scrivi l'indirizzo a mano", "No NEXUS PC found: type the address by hand")
                    } else {
                        prefs.hubUrl = found
                        url = found
                        note = tr("Trovato: $found", "Found: $found")
                    }
                }
            }
        }
        if (note.isNotBlank()) Text(note, color = Nexus.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }

    GlassCard {
        SectionLabel(tr("Ultimi tentativi", "Latest attempts"), accent)
        Spacer(Modifier.height(4.dp))
        if (log.isEmpty()) Text(tr("Ancora nessun tentativo", "No attempts yet"), color = Nexus.Text3, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        log.forEachIndexed { index, report ->
            if (index > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Box(
                    Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape)
                        .background(if (report.ok) Nexus.Ok else Nexus.Warn),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    val code = if (!report.ok && report.httpCode > 0) " · HTTP ${report.httpCode}" else ""
                    Text(report.message + code, color = Nexus.Text, fontSize = 14.sp)
                    val facts = listOf(
                        report.address,
                        report.sent.takeIf { it.isNotBlank() }?.let { tr("inviati: $it", "sent: $it") },
                        report.received.takeIf { it.isNotBlank() }?.let { tr("ricevuti: $it", "received: $it") },
                        "${report.durationMs} ms",
                    ).filter { !it.isNullOrBlank() }.joinToString(" · ")
                    Text(facts, color = Nexus.Text3, fontSize = 12.sp)
                    if (!report.ok && report.detail.isNotBlank()) {
                        Text(report.detail, color = Nexus.Text3, fontSize = 12.sp, maxLines = 2)
                    }
                }
                Text(whenLabel(report.at), color = Nexus.Text3, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(tr("Mostra gli ultimi 10 tentativi con novità o errori. Con l'app aperta la sync controlla il PC ogni 15 secondi; ad app chiusa parte ogni 15 minuti.", "Shows the last 10 attempts with news or errors. While the app is open it checks the PC every 15 seconds; when closed, every 15 minutes."), color = Nexus.Text3, fontSize = 12.sp)
    }
}

@Composable
private fun LilithPage(prefs: Prefs, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(prefs.assistantName) }
    var note by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(prefs.geminiModel) }
    GlassCard {
        SectionLabel(tr("Nome", "Name"), accent)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = glassFieldColors(accent),
        )
        Spacer(Modifier.height(8.dp))
        Text(tr("Il nome arriva anche al PC con la prossima sync.", "The name also reaches the PC with the next sync."), color = Nexus.Text3, fontSize = 12.sp)
        Row(Modifier.padding(top = 12.dp)) {
            Pill("Salva", accent, filled = true) {
                val clean = name.trim()
                if (clean.isEmpty()) {
                    note = tr("Scrivi un nome", "Type a name")
                } else {
                    prefs.assistantName = clean
                    prefs.setSettingAt("assistant_name", LocalDb.nowIso())
                    name = clean
                    note = "Salvato"
                }
            }
        }
    }
    GlassCard {
        SectionLabel(tr("Modello", "Model"), accent)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = model, onValueChange = { model = it.filter { c -> !c.isWhitespace() }.take(80) },
            label = { Text(tr("Nome del modello Gemini", "Gemini model name")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = glassFieldColors(accent),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Vale per telefono e PC (arriva con la sync): per cambiare modello basta scriverlo qui, per esempio gemini-3.5-flash-lite.",
                "Shared by phone and PC (it travels with the sync): to switch model just type it here, for example gemini-3.5-flash-lite."),
            color = Nexus.Text3, fontSize = 12.sp,
        )
        Row(Modifier.padding(top = 12.dp)) {
            Pill(tr("Salva", "Save"), accent, filled = true) {
                val clean = model.trim()
                if (clean.isEmpty()) {
                    note = tr("Scrivi il nome del modello", "Type the model name")
                } else {
                    prefs.geminiModel = clean
                    prefs.setSettingAt("gemini_model", LocalDb.nowIso())
                    model = clean
                    note = tr("Modello salvato", "Model saved")
                }
            }
            Spacer(Modifier.width(8.dp))
            Pill(tr("Predefinito", "Default"), accent) {
                prefs.geminiModel = Prefs.DEFAULT_MODEL
                prefs.setSettingAt("gemini_model", LocalDb.nowIso())
                model = Prefs.DEFAULT_MODEL
                note = tr("Modello ripristinato", "Model reset")
            }
        }
    }
    GlassCard {
        SectionLabel("Gemini", accent)
        Spacer(Modifier.height(6.dp))
        val ready = prefs.geminiKey.isNotBlank()
        Text(
            if (ready) tr("Chiave ricevuta dal PC", "Key received from the PC") else tr("Chiave non ancora ricevuta", "Key not received yet"),
            color = if (ready) Nexus.Ok else Nexus.Warn, fontSize = 18.sp, fontWeight = FontWeight.Light,
        )
        Text(
            tr("La chiave arriva dal PC con la sync e resta solo nell'app. Meteo, agenda e task rispondono anche senza.", "The key comes from the PC with the sync and stays only in the app. Weather, agenda and tasks answer without it too."),
            color = Nexus.Text3, fontSize = 12.sp,
        )
        Row(Modifier.padding(top = 12.dp)) {
            Pill(if (testing) tr("Provo...", "Testing...") else tr("Prova ${prefs.assistantName}", "Test ${prefs.assistantName}"), accent) {
                if (testing) return@Pill
                testing = true
                scope.launch {
                    note = try {
                        tr("Risposta: ", "Answer: ") + PhoneAssistant(context).test()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.message ?: tr("Prova non riuscita", "Test failed")
                    } finally {
                        testing = false
                    }
                }
            }
        }
    }
    if (note.isNotBlank()) Text(note, color = Nexus.Text2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun PlacePage(prefs: Prefs, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var city by remember { mutableStateOf(prefs.city) }
    var lat by remember { mutableStateOf(prefs.latitude) }
    var lon by remember { mutableStateOf(prefs.longitude) }
    var note by remember { mutableStateOf("") }
    GlassCard {
        SectionLabel(tr("Luogo del meteo", "Weather location"), accent)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = city, onValueChange = { city = it.take(60) }, label = { Text(tr("Città", "City")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = glassFieldColors(accent),
        )
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedTextField(
                value = lat, onValueChange = { lat = it }, label = { Text(tr("Latitudine", "Latitude")) },
                singleLine = true, modifier = Modifier.weight(1f), colors = glassFieldColors(accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = lon, onValueChange = { lon = it }, label = { Text(tr("Longitudine", "Longitude")) },
                singleLine = true, modifier = Modifier.weight(1f), colors = glassFieldColors(accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Vale per l'app, i widget e, con la sync, anche per il PC.", "Used by the app, the widgets and, with the sync, the PC."), color = Nexus.Text3, fontSize = 12.sp)
        Row(Modifier.padding(top = 12.dp)) {
            Pill(tr("Trova coordinate", "Find coordinates"), accent) {
                val wanted = city.trim()
                if (wanted.isEmpty()) return@Pill
                scope.launch {
                    note = try {
                        val place = WeatherClient.geocode(wanted)
                        if (place == null) {
                            tr("Città non trovata", "City not found")
                        } else {
                            city = place.name
                            lat = String.format(Locale.ROOT, "%.4f", place.latitude)
                            lon = String.format(Locale.ROOT, "%.4f", place.longitude)
                            tr("Trovata: ", "Found: ") + place.name + (if (place.region.isNotBlank()) ", ${place.region}" else "") + tr(". Premi Salva.", ". Press Save.")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.message ?: tr("Ricerca non riuscita", "Search failed")
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Pill("Salva", accent, filled = true) {
                val latitude = lat.replace(',', '.').toDoubleOrNull()
                val longitude = lon.replace(',', '.').toDoubleOrNull()
                note = when {
                    city.isBlank() -> tr("Scrivi la città", "Type the city")
                    latitude == null || latitude !in -90.0..90.0 -> tr("Latitudine non valida", "Invalid latitude")
                    longitude == null || longitude !in -180.0..180.0 -> tr("Longitudine non valida", "Invalid longitude")
                    else -> {
                        val now = LocalDb.nowIso()
                        prefs.city = city.trim()
                        prefs.latitude = latitude.toString()
                        prefs.longitude = longitude.toString()
                        listOf("city", "latitude", "longitude").forEach { prefs.setSettingAt(it, now) }
                        scope.launch {
                            try {
                                LocalToday.refresh(context)
                                HubWidget.refresh(context)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                note = tr("Salvato, ma il meteo non si è aggiornato: riprovo più tardi", "Saved, but the weather did not update: I will retry later")
                            }
                        }
                        "Salvato"
                    }
                }
            }
        }
    }
    if (note.isNotBlank()) Text(note, color = Nexus.Text2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun CalendarPage(accent: Color) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCalendar(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    GlassCard {
        SectionLabel(tr("Calendario del telefono", "Phone calendar"), accent)
        Spacer(Modifier.height(6.dp))
        Text(
            if (granted) tr("Accesso consentito", "Access granted") else tr("Permesso mancante", "Permission missing"),
            color = if (granted) Nexus.Ok else Nexus.Warn, fontSize = 20.sp, fontWeight = FontWeight.Light,
        )
        Text(
            tr("L'app legge gli impegni dai calendari di Android (anche Google) senza passare dal PC.", "The app reads events from the Android calendars (Google too) without going through the PC."),
            color = Nexus.Text3, fontSize = 12.sp,
        )
        Row(Modifier.padding(top = 12.dp)) {
            if (!granted) {
                Pill(tr("Consenti accesso", "Allow access"), accent, filled = true) { ask.launch(Manifest.permission.READ_CALENDAR) }
                Spacer(Modifier.width(8.dp))
            }
            Pill(tr("Permessi dell'app", "App permissions"), accent) {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
    GlassCard {
        SectionLabel(tr("Calendario del PC", "PC calendar"), accent)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("La dashboard usa Google Calendar con un login sul PC: lo stato e le istruzioni sono nella sua pagina Impostazioni → Calendario.", "The dashboard uses Google Calendar with a login on the PC: status and instructions are on its Settings → Calendar page."),
            color = Nexus.Text2, fontSize = 14.sp,
        )
    }
}

@Composable
private fun RemindersPage(prefs: Prefs, accent: Color) {
    val context = LocalContext.current
    var lead by remember { mutableLongStateOf(prefs.reminderLeadMinutes) }
    var briefing by remember { mutableStateOf(prefs.morningBriefing) }

    fun replan() {
        prefs.lastSnapshotJson?.let { runCatching { parseToday(it) }.getOrNull() }?.let { Reminders.schedule(context, it) }
    }

    GlassCard {
        SectionLabel(tr("Promemoria impegni", "Event reminders"), accent)
        Spacer(Modifier.height(6.dp))
        Text(tr("Un avviso silenzioso prima di ogni impegno", "A silent notice before every event"), color = Nexus.Text2, fontSize = 14.sp)
        Row(Modifier.padding(top = 12.dp)) {
            listOf(5L, 10L, 15L, 30L).forEach { minutes ->
                Pill(tr("$minutes min", "$minutes min"), accent, filled = lead == minutes) {
                    lead = minutes
                    prefs.reminderLeadMinutes = minutes
                    replan()
                }
                Spacer(Modifier.width(6.dp))
            }
        }
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(tr("Buongiorno", "Good morning"), accent)
                Spacer(Modifier.height(4.dp))
                Text(tr("Riepilogo silenzioso dopo la sveglia: meteo, impegni e task", "Silent summary after the alarm: weather, events and tasks"), color = Nexus.Text2, fontSize = 14.sp)
            }
            Switch(
                checked = briefing,
                onCheckedChange = {
                    briefing = it
                    prefs.morningBriefing = it
                    replan()
                },
                colors = nexusSwitchColors(accent),
            )
        }
    }
    GlassCard {
        SectionLabel(tr("Sveglia", "Alarm"), accent)
        Spacer(Modifier.height(6.dp))
        val alarm = remember { AlarmSync.nextAlarmIso(context) }
        Text(alarm?.let { tr("Prossima alle ${formatTime(it)}", "Next at ${formatTime(it)}") } ?: tr("Nessuna sveglia", "No alarm"), color = Nexus.Text, fontSize = 18.sp, fontWeight = FontWeight.Light)
        Text(
            tr("La sveglia del telefono arriva al PC con la sync, così la dashboard si accende in tempo. «Ignora» e «Ripristina» restano nella pagina principale.", "The phone alarm reaches the PC with the sync, so the dashboard wakes up on time. 'Ignore' and 'Restore' stay on the home page."),
            color = Nexus.Text3, fontSize = 12.sp,
        )
    }
}

@Composable
private fun LanguagePage(prefs: Prefs, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(prefs.language) }

    fun choose(next: String) {
        if (next == mode) return
        mode = next
        prefs.language = next
        Lang.apply(next)
        RefreshWorker.refreshNow(context) // weather descriptions are stored in the language they were fetched in
        scope.launch {
            try {
                HubWidget.refresh(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The widgets pick the language up at their next refresh.
            }
            var current: Context? = context
            while (current is android.content.ContextWrapper) {
                if (current is Activity) { current.recreate(); break }
                current = current.baseContext
            }
        }
    }

    GlassCard {
        SectionLabel(tr("Lingua dell'app", "App language"), accent)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Vale per schermate, widget, notifiche e Lilith su questo telefono. In inglese Lilith risponde sempre con Gemini.",
                "Applies to screens, widgets, notifications and Lilith on this phone. In English Lilith always answers with Gemini."),
            color = Nexus.Text2, fontSize = 14.sp,
        )
        Row(Modifier.padding(top = 12.dp)) {
            Pill("Italiano", accent, filled = mode == Lang.IT) { choose(Lang.IT) }
            Spacer(Modifier.width(6.dp))
            Pill("English", accent, filled = mode == Lang.EN) { choose(Lang.EN) }
            Spacer(Modifier.width(6.dp))
            Pill(tr("Come il telefono", "Same as phone"), accent, filled = mode == Lang.AUTO) { choose(Lang.AUTO) }
        }
    }
    GlassCard {
        Text(
            tr("La riattivazione vocale «Lilith» resta in italiano; il microfono capisce l'inglese quando premi il pulsante.",
                "The 'Lilith' wake word stays Italian; the microphone understands English when you press the button."),
            color = Nexus.Text3, fontSize = 12.sp,
        )
    }
}

@Composable
private fun SportPage(prefs: Prefs, accent: Color) {
    var withMusic by remember { mutableStateOf(prefs.sportPhoneMic) }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(tr("Con la musica", "With music"), accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("Ascolta dal microfono del telefono: gli auricolari restano per la musica", "Listens through the phone microphone: the earbuds stay for the music"),
                    color = Nexus.Text2, fontSize = 14.sp,
                )
            }
            Switch(
                checked = withMusic,
                onCheckedChange = { withMusic = it; prefs.sportPhoneMic = it },
                colors = nexusSwitchColors(accent),
            )
        }
    }
    GlassCard {
        SectionLabel(tr("Come si usa", "How it works"), accent)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Accendi la modalità sport dalla pagina principale, poi chiama ${prefs.assistantName} per nome dagli auricolari.", "Turn on sport mode from the home page, then call ${prefs.assistantName} by name from the earbuds."),
            color = Nexus.Text2, fontSize = 14.sp,
        )
    }
}

@Composable
private fun InfoPage(prefs: Prefs, accent: Color) {
    val context = LocalContext.current
    val app = remember { appVersion(context) }
    GlassCard {
        SectionLabel("NEXUS", accent)
        Spacer(Modifier.height(6.dp))
        Text(tr("Versione $app", "Version $app"), color = Nexus.Text, fontSize = 26.sp, fontWeight = FontWeight.Light)
        Text(
            tr("Hub personale: l'app e i widget sul telefono, la dashboard sul PC, l'assistente ${prefs.assistantName}. Tutto resta in casa tra PC e telefono.", "Personal hub: the app and widgets on the phone, the dashboard on the PC, the assistant ${prefs.assistantName}. Everything stays at home between PC and phone."),
            color = Nexus.Text2, fontSize = 14.sp,
        )
    }
    GlassCard {
        SectionLabel(tr("Dettagli", "Details"), accent)
        Spacer(Modifier.height(4.dp))
        InfoRow("App", app)
        Divider()
        InfoRow(tr("Server del PC", "PC server"), prefs.pcVersion.ifBlank { tr("non ancora visto", "not seen yet") })
        Divider()
        InfoRow(tr("Ultima sync", "Last sync"), prefs.lastSyncAt?.let(::whenLabel) ?: tr("mai", "never"))
        Divider()
        InfoRow(tr("Indirizzo del PC", "PC address"), prefs.hubUrl.ifBlank { tr("automatico", "automatic") })
        Divider()
        InfoRow("Android", Build.VERSION.RELEASE)
        if (prefs.pcVersion.isNotBlank() && prefs.pcVersion != app) {
            Spacer(Modifier.height(8.dp))
            Text(tr("App e PC hanno versioni diverse: aggiorna quello più vecchio.", "App and PC have different versions: update the older one."), color = Nexus.Warn, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Nexus.Text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Nexus.Text, fontSize = 14.sp)
    }
}

@Composable
private fun Step(label: String, ok: Boolean?) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val (mark, color) = when (ok) {
            true -> "✓" to Nexus.Ok
            false -> "✕" to Nexus.Warn
            null -> "–" to Nexus.Text3
        }
        Text(mark, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(label, color = Nexus.Text, fontSize = 14.sp)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Nexus.GlassBorder))
}

private fun explain(report: SyncReport): String = when (report.code) {
    "http_401" -> tr(
        "Il PC risponde ma rifiuta il token: ricopialo da HUB_API_TOKEN nel file .env del PC.",
        "The PC answers but rejects the token: copy it again from HUB_API_TOKEN in the PC .env file.",
    )
    "unreachable" -> tr(
        "Nessuna risposta da ${report.address}. Telefono e PC sono sullo stesso Wi‑Fi? La porta 8765 è aperta nel firewall del PC? (${report.detail})",
        "No answer from ${report.address}. Are phone and PC on the same Wi‑Fi? Is port 8765 open in the PC firewall? (${report.detail})",
    )
    "no_pc" -> tr(
        "Scrivi l'indirizzo del PC (lo trovi nella dashboard: rotellina → Sync) o usa «Cerca PC in rete».",
        "Type the PC address (it is on the dashboard: gear → Sync) or use 'Find PC on the network'.",
    )
    "no_token" -> tr("Scrivi il token qui sotto.", "Enter the token below.")
    else -> listOf(report.detail, report.address).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun whenLabel(iso: String): String {
    val at = runCatching { OffsetDateTime.parse(iso) }.getOrNull()?.atZoneSameInstant(ZoneId.systemDefault()) ?: return iso
    return if (at.toLocalDate() == LocalDate.now()) {
        tr("oggi ", "today ") + at.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    } else {
        at.format(DateTimeFormatter.ofPattern("d/M HH:mm"))
    }
}

private fun hasCalendar(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}
