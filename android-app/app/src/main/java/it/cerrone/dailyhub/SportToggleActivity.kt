package it.cerrone.dailyhub

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Invisible trampoline for the widget's sport button. Starting the microphone service
 * from a visible activity is always allowed, unlike starting it from a broadcast.
 */
class SportToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when {
            SportState.active -> SportService.stop(this)
            hasMic -> SportService.start(this)
            // The microphone permission is granted from the app's sport card.
            else -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
