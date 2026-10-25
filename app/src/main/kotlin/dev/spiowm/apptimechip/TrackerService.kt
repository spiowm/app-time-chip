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
  private lateinit var days: DayTracker
  private var shown: Shown? = null

  /** Everything the chip currently displays; it is reposted only when this changes. */
  private data class Shown(val active: Active, val minutes: Long?, val mode: Mode)

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
    val mode = prefs.mode
    val active = when (mode) {
      Mode.SESSION -> sessions.onTick(now, foreground, tracked, prefs.graceSeconds * 1000L)
      Mode.EACH_OPEN -> sessions.onTick(now, foreground, tracked, graceMs = 0)
      Mode.DAY -> days.onTick(now, foreground, tracked)
    }
    val step = prefs.stepMinutes
    val next = active?.let {
      val minutes = if (step == 0) null else (now - it.base) / 60_000 / step * step
      Shown(it, minutes, mode)
    }
    // The chronometer counts on its own, so with a live timer this reposts only when the
    // session changes; a chip the user swiped away stays away until then.
    if (next == shown) return
    if (next == null) notifier.cancelLive()
    else notifier.showLive(next.active.packageName, next.active.base, next.minutes, next.mode)
    shown = next
  }

  override fun onDestroy() {
    handler.removeCallbacks(tick)
    unregisterReceiver(screenReceiver)
    notifier.cancelLive()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    private const val TICK_MS = 1000L

    fun start(context: Context) = context.startForegroundService(Intent(context, TrackerService::class.java))

    fun stop(context: Context) = context.stopService(Intent(context, TrackerService::class.java))
  }
}
