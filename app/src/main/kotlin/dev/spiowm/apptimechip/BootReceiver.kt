package dev.spiowm.apptimechip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts tracking after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (Prefs(context).enabled && Permissions.hasUsageAccess(context)) TrackerService.start(context)
  }
}
