package dev.spiowm.apptimechip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Builds the two notifications: the silent service one and the Live Update chip. */
class Notifier(private val context: Context) {
  private val nm = context.getSystemService(NotificationManager::class.java)

  init {
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL_SERVICE, "Фонова робота", NotificationManager.IMPORTANCE_MIN)
    )
    // A promoted notification must not sit in an IMPORTANCE_MIN channel, so the chip gets
    // its own default-importance channel with sound and vibration turned off.
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL_LIVE, "Таймер апки", NotificationManager.IMPORTANCE_DEFAULT).apply {
        setSound(null, null)
        enableVibration(false)
        setShowBadge(false)
      }
    )
  }

  fun serviceNotification(): Notification =
    Notification.Builder(context, CHANNEL_SERVICE)
      .setSmallIcon(R.drawable.ic_timer)
      .setContentTitle("Стежу за часом в апках")
      .setContentIntent(openAppIntent())
      .setOngoing(true)
      .build()

  /**
   * The chip. With [minutes] == null the system chronometer counts seconds on its own;
   * otherwise the chip shows [minutes] as text and the caller reposts when it changes.
   * Pixel ignores the notification color in the chip, so [overdue] swaps the icon instead.
   */
  fun liveNotification(packageName: String, base: Long, minutes: Long?, mode: Mode, overdue: Boolean): Notification {
    val label = if (mode == Mode.DAY) "Сьогодні в цій апці" else "Час у цій апці"
    val builder = Notification.Builder(context, CHANNEL_LIVE)
      .setSmallIcon(if (overdue) R.drawable.ic_hourglass else R.drawable.ic_timer)
      .setContentTitle(appLabel(packageName))
      .setContentIntent(openAppIntent())
      .setCategory(Notification.CATEGORY_STOPWATCH)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setRequestPromotedOngoing(true)
      .setWhen(base)
    if (minutes == null) {
      builder.setContentText(label).setShowWhen(true).setUsesChronometer(true)
    } else {
      builder.setContentText("$label: ${longMinutes(minutes)}")
        .setShowWhen(false)
        .setShortCriticalText(shortMinutes(minutes))
    }
    return builder.build()
  }

  fun showLive(packageName: String, base: Long, minutes: Long?, mode: Mode, overdue: Boolean) =
    nm.notify(ID_LIVE, liveNotification(packageName, base, minutes, mode, overdue))

  fun cancelLive() = nm.cancel(ID_LIVE)

  fun canPostPromoted(): Boolean = nm.canPostPromotedNotifications()

  private fun appLabel(packageName: String): String =
    try {
      val pm = context.packageManager
      pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))).toString()
    } catch (_: PackageManager.NameNotFoundException) {
      packageName
    }

  private fun openAppIntent(): PendingIntent =
    PendingIntent.getActivity(
      context, 0,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE,
    )

  companion object {
    /** Chip text, kept under 7 characters so the status bar never truncates it. */
    fun shortMinutes(minutes: Long): String =
      if (minutes < 60) "$minutes хв" else "${minutes / 60} г %02d".format(minutes % 60)

    fun longMinutes(minutes: Long): String =
      if (minutes < 60) "$minutes хв" else "${minutes / 60} год ${minutes % 60} хв"

    const val CHANNEL_SERVICE = "service"
    const val CHANNEL_LIVE = "live"
    const val ID_SERVICE = 1
    const val ID_LIVE = 2
  }
}
