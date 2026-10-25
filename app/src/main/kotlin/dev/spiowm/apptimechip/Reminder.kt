package dev.spiowm.apptimechip

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast

/**
 * Fires every time the counter passes another multiple of the interval. Opening an app
 * never fires for marks that were already behind, which matters for the daily mode.
 */
class Reminder(private val context: Context) {
  private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
  private var packageName: String? = null
  private var lastMark = 0L

  /** Returns the mark just reached (1 for the first interval, 2 for the second…), or null. */
  fun onTick(active: Active?, now: Long, intervalMinutes: Int): Long? {
    if (active == null || intervalMinutes == 0) {
      packageName = null
      return null
    }
    val mark = (now - active.base) / (intervalMinutes * 60_000L)
    if (active.packageName != packageName) {
      packageName = active.packageName
      lastMark = mark
      return null
    }
    if (mark <= lastMark) return null
    lastMark = mark
    return mark
  }

  /** A pattern that does not feel like a regular notification, and grows with each mark. */
  fun buzz(level: Int) {
    // Timings alternate off/on, starting with off: quick taps, plus a long buzz from level 3.
    val timings = mutableListOf(0L)
    repeat(level.coerceIn(1, 3) + 1) { timings += listOf(70L, 110L) }
    if (level >= 3) {
      timings[timings.lastIndex] = 250L
      timings += 450L
    }
    // Alarm usage so it still comes through with Do Not Disturb on.
    vibrator.vibrate(
      VibrationEffect.createWaveform(timings.toLongArray(), -1),
      VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
    )
  }

  /** A toast rather than a heads-up notification: Do Not Disturb does not hide it. */
  fun say(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/** Whether the counter is already past the first reminder mark. */
fun isOverdue(active: Active, now: Long, intervalMinutes: Int): Boolean =
  intervalMinutes > 0 && now - active.base >= intervalMinutes * 60_000L
