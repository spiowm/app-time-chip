package dev.spiowm.apptimechip

import android.content.Context
import androidx.core.content.edit

/** What the chip counts. */
enum class Mode {
  /** Total time in the app today; resets at midnight. */
  DAY,

  /** Continuous time in the app; a short step out does not restart it. */
  SESSION,

  /** Starts from zero every time the app is opened. */
  EACH_OPEN,
}

/** User settings, stored in plain SharedPreferences. */
class Prefs(context: Context) {
  private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

  var enabled: Boolean
    get() = sp.getBoolean(KEY_ENABLED, false)
    set(value) = sp.edit { putBoolean(KEY_ENABLED, value) }

  var trackedPackages: Set<String>
    get() = sp.getStringSet(KEY_TRACKED, null) ?: DEFAULT_TRACKED
    set(value) = sp.edit { putStringSet(KEY_TRACKED, value) }

  var mode: Mode
    get() = Mode.entries.firstOrNull { it.name == sp.getString(KEY_MODE, null) } ?: Mode.DAY
    set(value) = sp.edit { putString(KEY_MODE, value.name) }

  /** In [Mode.SESSION]: how long you can step out of an app and come back without a restart. */
  var graceSeconds: Int
    get() = sp.getInt(KEY_GRACE, 60)
    set(value) = sp.edit { putInt(KEY_GRACE, value) }

  /** How often the number in the chip changes; 0 means a live seconds timer. */
  var stepMinutes: Int
    get() = sp.getInt(KEY_STEP, 0)
    set(value) = sp.edit { putInt(KEY_STEP, value) }

  /** Buzz each time the counter passes another multiple of this; 0 turns reminders off. */
  var reminderMinutes: Int
    get() = sp.getInt(KEY_REMINDER, 15)
    set(value) = sp.edit { putInt(KEY_REMINDER, value) }

  /** Show both today's total and the current session in the notification shade. */
  var showBoth: Boolean
    get() = sp.getBoolean(KEY_SHOW_BOTH, true)
    set(value) = sp.edit { putBoolean(KEY_SHOW_BOTH, value) }

  var reminderVibrate: Boolean
    get() = sp.getBoolean(KEY_REMINDER_VIBRATE, true)
    set(value) = sp.edit { putBoolean(KEY_REMINDER_VIBRATE, value) }

  var reminderText: Boolean
    get() = sp.getBoolean(KEY_REMINDER_TEXT, true)
    set(value) = sp.edit { putBoolean(KEY_REMINDER_TEXT, value) }

  /** Per-app override of [mode]; null means the app follows the global setting. */
  fun appMode(packageName: String): Mode? =
    Mode.entries.firstOrNull { it.name == sp.getString(KEY_APP_MODE + packageName, null) }

  fun setAppMode(packageName: String, value: Mode?) =
    sp.edit { if (value == null) remove(KEY_APP_MODE + packageName) else putString(KEY_APP_MODE + packageName, value.name) }

  /** Per-app override of [reminderMinutes]; null means the app follows the global setting. */
  fun appReminder(packageName: String): Int? =
    if (sp.contains(KEY_APP_REMINDER + packageName)) sp.getInt(KEY_APP_REMINDER + packageName, 0) else null

  fun setAppReminder(packageName: String, value: Int?) =
    sp.edit { if (value == null) remove(KEY_APP_REMINDER + packageName) else putInt(KEY_APP_REMINDER + packageName, value) }

  fun modeFor(packageName: String): Mode = appMode(packageName) ?: mode

  fun reminderFor(packageName: String): Int = appReminder(packageName) ?: reminderMinutes

  companion object {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TRACKED = "tracked"
    private const val KEY_MODE = "mode"
    private const val KEY_GRACE = "grace_seconds"
    private const val KEY_STEP = "step_minutes"
    private const val KEY_REMINDER = "reminder_minutes"
    private const val KEY_SHOW_BOTH = "show_both"
    private const val KEY_REMINDER_VIBRATE = "reminder_vibrate"
    private const val KEY_REMINDER_TEXT = "reminder_text"
    private const val KEY_APP_MODE = "app_mode:"
    private const val KEY_APP_REMINDER = "app_reminder:"

    val DEFAULT_TRACKED = setOf("com.reddit.frontpage")
    val GRACE_OPTIONS = listOf(30, 60, 120, 300)
    val STEP_OPTIONS = listOf(0, 1, 5, 10)
    val REMINDER_OPTIONS = listOf(0, 5, 10, 15, 30)
  }
}
