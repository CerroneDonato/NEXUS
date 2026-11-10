package it.cerrone.dailyhub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * NEXT_ALARM_CLOCK_CHANGED is exempt from Android's implicit-broadcast limits,
 * so this fires whenever the user sets, changes or dismisses an alarm.
 */
class AlarmChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }
}
