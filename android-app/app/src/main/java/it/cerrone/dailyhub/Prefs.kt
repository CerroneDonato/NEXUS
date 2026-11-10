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

    val isConfigured: Boolean get() = hubUrl.isNotBlank() && token.isNotBlank()

    fun client(): HubClient = HubClient(hubUrl, token)

    private companion object {
        const val KEY_URL = "hub_url"
        const val KEY_TOKEN = "token"
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_UPDATED = "updated"
        const val KEY_ERROR = "error"
    }
}
