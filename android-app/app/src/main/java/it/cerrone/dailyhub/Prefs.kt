package it.cerrone.dailyhub

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("daily_hub", Context.MODE_PRIVATE)

    var hubUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_URL, value.trim()) }

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit { putString(KEY_TOKEN, value.trim()) }

    var lastSnapshotJson: String?
        get() = prefs.getString(KEY_SNAPSHOT, null)
        set(value) = prefs.edit { putString(KEY_SNAPSHOT, value) }

    var lastUpdateMillis: Long
        get() = prefs.getLong(KEY_UPDATED, 0L)
        set(value) = prefs.edit { putLong(KEY_UPDATED, value) }

    var lastError: String?
        get() = prefs.getString(KEY_ERROR, null)
        set(value) = prefs.edit { putString(KEY_ERROR, value) }

    /** Request codes of the event reminders currently scheduled with AlarmManager. */
    var reminderCodes: Set<Int>
        get() = prefs.getStringSet(KEY_REMINDERS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        set(value) = prefs.edit { putStringSet(KEY_REMINDERS, value.map { it.toString() }.toSet()) }

    /** ISO date of the last morning notification, so it appears once a day. */
    var lastBriefingDate: String?
        get() = prefs.getString(KEY_BRIEFING, null)
        set(value) = prefs.edit { putString(KEY_BRIEFING, value) }

    var askedNotifications: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIFY, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED_NOTIFY, value) }

    /** "HH:mm" of alarms the user hid from NEXUS (e.g. a ghost alarm that is not theirs). */
    var ignoredAlarmTimes: Set<String>
        get() = prefs.getStringSet(KEY_IGNORED_ALARMS, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEY_IGNORED_ALARMS, value) }

    /** Sport mode with music: listen through the phone microphone so the earbuds keep playing music. */
    var sportPhoneMic: Boolean
        get() = prefs.getBoolean(KEY_SPORT_PHONE_MIC, false)
        set(value) = prefs.edit { putBoolean(KEY_SPORT_PHONE_MIC, value) }

    var city: String
        get() = prefs.getString(KEY_CITY, "Milano") ?: "Milano"
        set(value) = prefs.edit { putString(KEY_CITY, value) }

    var latitude: String
        get() = prefs.getString(KEY_LAT, "45.4642") ?: "45.4642"
        set(value) = prefs.edit { putString(KEY_LAT, value) }

    var longitude: String
        get() = prefs.getString(KEY_LON, "9.19") ?: "9.19"
        set(value) = prefs.edit { putString(KEY_LON, value) }

    var assistantName: String
        get() = prefs.getString(KEY_NAME, "Lilith") ?: "Lilith"
        set(value) = prefs.edit { putString(KEY_NAME, value) }

    /** Gemini model, shared with the PC through the sync (the PC's .env value is the first default). */
    var geminiModel: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit { putString(KEY_MODEL, value) }

    /** "auto" (follow the phone), "it" or "en". Per device: it is not synced. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, Lang.AUTO) ?: Lang.AUTO
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }

    /** ISO time of the last edit we accepted for each shared setting. */
    fun settingAt(key: String): String = prefs.getString("setting_at_$key", "") ?: ""

    fun setSettingAt(key: String, iso: String) = prefs.edit { putString("setting_at_$key", iso) }

    /** Gemini key received from the PC. Stays in these private preferences and is never shown. */
    var geminiKey: String
        get() = prefs.getString(KEY_GEMINI, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GEMINI, value) }

    var geminiKeyAt: String
        get() = prefs.getString(KEY_GEMINI_AT, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GEMINI_AT, value) }

    /** Highest PC change-counter already applied. */
    var pcRevision: Long
        get() = prefs.getLong(KEY_REVISION, 0L)
        set(value) = prefs.edit { putLong(KEY_REVISION, value) }

    var lastSyncAt: String?
        get() = prefs.getString(KEY_SYNC_AT, null)
        set(value) = prefs.edit { putString(KEY_SYNC_AT, value) }

    /** JSON array of the last sync attempts, newest first (see [SyncHistory]). */
    var syncLogJson: String
        get() = prefs.getString(KEY_SYNC_LOG, "[]") ?: "[]"
        set(value) = prefs.edit { putString(KEY_SYNC_LOG, value) }

    /** NEXUS version the PC reported on the last sync. */
    var pcVersion: String
        get() = prefs.getString(KEY_PC_VERSION, "") ?: ""
        set(value) = prefs.edit { putString(KEY_PC_VERSION, value) }

    var reminderLeadMinutes: Long
        get() = prefs.getLong(KEY_REMINDER_LEAD, REMINDER_LEAD_MINUTES)
        set(value) = prefs.edit { putLong(KEY_REMINDER_LEAD, value) }

    var morningBriefing: Boolean
        get() = prefs.getBoolean(KEY_MORNING_BRIEFING, true)
        set(value) = prefs.edit { putBoolean(KEY_MORNING_BRIEFING, value) }

    val isConfigured: Boolean get() = hubUrl.isNotBlank() && token.isNotBlank()

    /** Sync needs the token. The address can be discovered on the home network. */
    val canSync: Boolean get() = token.isNotBlank()

    fun client(): HubClient = HubClient(hubUrl, token)

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_URL = "hub_url"
        const val KEY_TOKEN = "token"
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_UPDATED = "updated"
        const val KEY_ERROR = "error"
        const val KEY_REMINDERS = "reminder_codes"
        const val KEY_BRIEFING = "briefing_date"
        const val KEY_ASKED_NOTIFY = "asked_notifications"
        const val KEY_IGNORED_ALARMS = "ignored_alarm_times"
        const val KEY_SPORT_PHONE_MIC = "sport_phone_mic"
        const val KEY_CITY = "city"
        const val KEY_LAT = "latitude"
        const val KEY_LON = "longitude"
        const val KEY_NAME = "assistant_name"
        const val KEY_GEMINI = "gemini_key"
        const val KEY_GEMINI_AT = "gemini_key_at"
        const val KEY_REVISION = "pc_revision"
        const val KEY_SYNC_AT = "last_sync_at"
        const val KEY_SYNC_LOG = "sync_log"
        const val KEY_PC_VERSION = "pc_version"
        const val KEY_REMINDER_LEAD = "reminder_lead_minutes"
        const val KEY_MORNING_BRIEFING = "morning_briefing"
    }
}
