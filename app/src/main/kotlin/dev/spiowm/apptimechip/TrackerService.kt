package dev.spiowm.apptimechip

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Polls the foreground app once a second while the screen is on and keeps the Live Update
 * chip in sync. Nothing runs while the screen is off.
 */
class TrackerService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var prefs: Prefs
  private lateinit var notifier: Notifier
  private lateinit var detector: ForegroundDetector
  private val sessions = SessionTracker()
  private val opens = SessionTracker()
  private lateinit var days: DayTracker
  private lateinit var reminder: Reminder
  private var shown: Shown? = null

  /** Everything the chip currently displays; it is reposted only when this changes. */
  private data class Shown(val packageName: String, val base: Long, val chipMinutes: Long?, val text: String, val overdue: Boolean)

  private val tick = object : Runnable {
    override fun run() {
      update(screenOn = true)
      handler.postDelayed(this, TICK_MS)
    }
  }

  private val screenReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      handler.removeCallbacks(tick)
      when (intent.action) {
        Intent.ACTION_SCREEN_ON -> handler.post(tick)
        Intent.ACTION_SCREEN_OFF -> {
          detector.reset()
          update(screenOn = false)
        }
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    prefs = Prefs(this)
    notifier = Notifier(this)
    detector = ForegroundDetector(getSystemService(UsageStatsManager::class.java))
    days = DayTracker(detector)
    reminder = Reminder(this)
    startForeground(Notifier.ID_SERVICE, notifier.serviceNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    registerReceiver(
      screenReceiver,
      IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
      },
    )
    if (getSystemService(PowerManager::class.java).isInteractive) handler.post(tick)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!prefs.enabled || !Permissions.hasUsageAccess(this)) stopSelf()
    return START_STICKY
  }

  private fun update(screenOn: Boolean) {
    val now = System.currentTimeMillis()
    val foreground = if (screenOn) detector.update(now) else null
    val tracked = prefs.trackedPackages
    // All counters keep ticking, so switching modes or showing two numbers never starts
    // one of them from scratch.
    val session = sessions.onTick(now, foreground, tracked, prefs.graceSeconds * 1000L)
    val open = opens.onTick(now, foreground, tracked, graceMs = 0)
    val day = days.onTick(now, foreground, tracked)
    if (session == null || open == null || day == null) {
      reminder.onTick(null, now, 0)
      show(null)
      return
    }

    val packageName = session.packageName
    val mode = prefs.modeFor(packageName)
    val primary = when (mode) {
      Mode.DAY -> day
      Mode.SESSION -> session
      Mode.EACH_OPEN -> open
    }
    val minutes = (now - primary.base) / 60_000
    val interval = prefs.reminderFor(packageName)
    reminder.onTick(primary, now, interval)?.let { mark -> remind(packageName, mode, mark, mark * interval) }

    val step = prefs.stepMinutes
    val chipMinutes = if (step == 0) null else minutes / step * step
    val text = when {
      prefs.showBoth -> {
        val (otherMode, other) = if (mode == Mode.DAY) Mode.SESSION to session else Mode.DAY to day
        getString(
          R.string.counter_pair,
          counterValue(mode, minutes),
          counterValue(otherMode, (now - other.base) / 60_000),
        )
      }
      chipMinutes == null -> counterName(mode)
      else -> counterValue(mode, chipMinutes)
    }
    show(Shown(packageName, primary.base, chipMinutes, text, isOverdue(primary, now, interval)))
  }

  private fun show(next: Shown?) {
    // With a live timer the chronometer counts on its own, so this reposts only when the
    // session or the shade text changes; a chip the user swiped away stays away until then.
    if (next == shown) return
    if (next == null) notifier.cancelLive()
    else notifier.showLive(next.packageName, next.base, next.chipMinutes, next.text, next.overdue)
    shown = next
  }

  private fun remind(packageName: String, mode: Mode, mark: Long, minutes: Long) {
    if (prefs.reminderVibrate) reminder.buzz(level = mark.toInt())
    if (prefs.reminderText) {
      val app = notifier.appLabel(packageName)
      val time = notifier.longMinutes(minutes)
      val message = if (mode == Mode.DAY) R.string.reminder_today else R.string.reminder_elapsed
      reminder.say(getString(message, time, app))
    }
  }

  override fun onDestroy() {
    handler.removeCallbacks(tick)
    unregisterReceiver(screenReceiver)
    notifier.cancelLive()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun counterName(mode: Mode) = getString(when (mode) {
    Mode.DAY -> R.string.counter_today
    Mode.SESSION -> R.string.counter_session
    Mode.EACH_OPEN -> R.string.counter_visit
  })

  private fun counterValue(mode: Mode, minutes: Long) =
    getString(R.string.counter_value, counterName(mode), notifier.longMinutes(minutes))

  companion object {
    private const val TICK_MS = 1000L

    fun start(context: Context) = context.startForegroundService(Intent(context, TrackerService::class.java))

    fun stop(context: Context) = context.stopService(Intent(context, TrackerService::class.java))
  }
}
