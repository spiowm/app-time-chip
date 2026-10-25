package dev.spiowm.apptimechip

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Buzzes every time the counter passes another multiple of the interval. Opening an app
 * never buzzes for marks that were already behind, which matters for the daily mode.
 */
class Reminder(context: Context) {
  private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
  private var packageName: String? = null
  private var lastMark = 0L

  fun onTick(active: Active?, now: Long, intervalMinutes: Int) {
    if (active == null || intervalMinutes == 0) {
      packageName = null
      return
    }
    val mark = (now - active.base) / (intervalMinutes * 60_000L)
    if (active.packageName != packageName) {
      packageName = active.packageName
      lastMark = mark
      return
    }
    if (mark > lastMark) {
      lastMark = mark
      buzz(level = mark.toInt())
    }
  }

  /** A pattern that does not sound like a regular notification, and grows with each mark. */
  fun buzz(level: Int) {
    // Timings alternate off/on, starting with off: quick taps, plus a long buzz from level 3.
    val timings = mutableListOf(0L)
    repeat(level.coerceIn(1, 3) + 1) { timings += listOf(70L, 110L) }
    if (level >= 3) {
      timings[timings.lastIndex] = 250L
      timings += 450L
    }
    vibrator.vibrate(
      VibrationEffect.createWaveform(timings.toLongArray(), -1),
      VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
    )
  }
}

/** Whether the counter is already past the first reminder mark. */
fun isOverdue(active: Active, now: Long, intervalMinutes: Int): Boolean =
  intervalMinutes > 0 && now - active.base >= intervalMinutes * 60_000L
