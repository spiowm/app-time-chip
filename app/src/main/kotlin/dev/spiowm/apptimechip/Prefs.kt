package dev.spiowm.apptimechip

import android.content.Context
import androidx.core.content.edit

/** What the chip counts. */
enum class Mode {
  /** Continuous time in the app; a short step out does not restart it. */
  SESSION,

  /** Starts from zero every time the app is opened. */
  EACH_OPEN,

  /** Total time in the app today; resets at midnight. */
  DAY,
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
    get() = Mode.entries.firstOrNull { it.name == sp.getString(KEY_MODE, null) } ?: Mode.SESSION
    set(value) = sp.edit { putString(KEY_MODE, value.name) }

  /** In [Mode.SESSION]: how long you can step out of an app and come back without a restart. */
  var graceSeconds: Int
    get() = sp.getInt(KEY_GRACE, 60)
    set(value) = sp.edit { putInt(KEY_GRACE, value) }

  /** How often the number in the chip changes; 0 means a live seconds timer. */
  var stepMinutes: Int
    get() = sp.getInt(KEY_STEP, 1)
    set(value) = sp.edit { putInt(KEY_STEP, value) }

  companion object {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TRACKED = "tracked"
    private const val KEY_MODE = "mode"
    private const val KEY_GRACE = "grace_seconds"
    private const val KEY_STEP = "step_minutes"

    val DEFAULT_TRACKED = setOf("com.reddit.frontpage")
    val GRACE_OPTIONS = listOf(30, 60, 120, 300)
    val STEP_OPTIONS = listOf(0, 1, 5, 10)
  }
}
