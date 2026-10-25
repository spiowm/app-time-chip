package dev.spiowm.apptimechip

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.time.LocalDate
import java.time.ZoneId

/** Works out which app is in the foreground from the usage events stream. */
class ForegroundDetector(private val usm: UsageStatsManager) {
  var current: String? = null
    private set
  private var lastQuery = 0L

  fun update(now: Long): String? {
    // On the first call look back far enough to catch the app that is already open.
    val begin = if (lastQuery == 0L) now - LOOKBACK_MS else lastQuery
    val events = usm.queryEvents(begin, now)
    val e = UsageEvents.Event()
    // Only resumes matter: leaving an app always resumes something else (the launcher at
    // least), while reacting to pauses would flicker when an app switches its own screens.
    while (events.getNextEvent(e)) {
      if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) current = e.packageName
    }
    lastQuery = now
    return current
  }

  /** Screen went off: whatever was open is no longer being used. */
  fun reset() {
    current = null
  }

  /** Time [packageName] has spent in the foreground since local midnight. */
  fun foregroundToday(packageName: String, now: Long): Long {
    val midnight = startOfDay(now)
    val events = usm.queryEvents(midnight, now)
    val e = UsageEvents.Event()
    var total = 0L
    var since: Long? = null
    while (events.getNextEvent(e)) {
      when (e.eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED ->
          if (e.packageName == packageName) since = since ?: e.timeStamp
          else since?.let { total += e.timeStamp - it; since = null }
        UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
          since?.let { total += e.timeStamp - it; since = null }
      }
    }
    since?.let { total += now - it }
    return total
  }

  private companion object {
    const val LOOKBACK_MS = 30 * 60_000L
  }
}

fun startOfDay(now: Long): Long {
  val zone = ZoneId.systemDefault()
  return LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone).atStartOfDay(zone).toInstant().toEpochMilli()
}

/** What the chip should show: [base] is the chronometer start, so elapsed = now - base. */
data class Active(val packageName: String, val base: Long)

/**
 * Per-app sessions. Time spent outside the app is not counted, and stepping out for less
 * than the grace period continues the same session instead of starting a new one.
 */
class SessionTracker {
  private class Session(var accumulatedMs: Long, var activeSince: Long?, var lastSeen: Long)

  private val sessions = mutableMapOf<String, Session>()

  fun onTick(now: Long, foreground: String?, tracked: Set<String>, graceMs: Long): Active? {
    for ((pkg, s) in sessions) {
      val since = s.activeSince
      if (pkg != foreground && since != null) {
        s.accumulatedMs += s.lastSeen - since
        s.activeSince = null
      }
    }
    sessions.entries.removeAll { (_, s) -> s.activeSince == null && now - s.lastSeen > graceMs }

    if (foreground == null || foreground !in tracked) return null
    val s = sessions.getOrPut(foreground) { Session(0, now, now) }
    if (s.activeSince == null) s.activeSince = now
    s.lastSeen = now
    return Active(foreground, s.activeSince!! - s.accumulatedMs)
  }
}

/** Today's total per app, read from the usage history once each time the app is entered. */
class DayTracker(private val detector: ForegroundDetector) {
  private var current: Active? = null
  private var currentDay = 0L

  fun onTick(now: Long, foreground: String?, tracked: Set<String>): Active? {
    if (foreground == null || foreground !in tracked) {
      current = null
      return null
    }
    val day = startOfDay(now)
    if (current?.packageName != foreground || day != currentDay) {
      current = Active(foreground, now - detector.foregroundToday(foreground, now))
      currentDay = day
    }
    return current
  }
}
