package dev.spiowm.apptimechip

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.spiowm.apptimechip.ui.theme.AppTimeChipTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      AppTimeChipTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { SettingsScreen() } }
    }
  }
}

private class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap)

private data class Status(
  val usageAccess: Boolean,
  val notifications: Boolean,
  val promoted: Boolean,
  val promotable: Boolean,
  val battery: Boolean,
)

@Composable
private fun SettingsScreen() {
  val context = LocalContext.current
  val prefs = remember { Prefs(context) }
  val notifier = remember { Notifier(context) }

  fun readStatus() = Status(
    usageAccess = Permissions.hasUsageAccess(context),
    notifications = Permissions.hasNotifications(context),
    promoted = notifier.canPostPromoted(),
    promotable = notifier.liveNotification(context.packageName, System.currentTimeMillis(), null, "", false)
      .hasPromotableCharacteristics(),
    battery = Permissions.ignoresBatteryOptimizations(context),
  )

  var status by remember { mutableStateOf(readStatus()) }
  var enabled by remember { mutableStateOf(prefs.enabled) }
  var tracked by remember { mutableStateOf(prefs.trackedPackages) }
  var mode by remember { mutableStateOf(prefs.mode) }
  var grace by remember { mutableIntStateOf(prefs.graceSeconds) }
  var step by remember { mutableIntStateOf(prefs.stepMinutes) }
  var showBoth by remember { mutableStateOf(prefs.showBoth) }
  var reminderMinutes by remember { mutableIntStateOf(prefs.reminderMinutes) }
  var reminderVibrate by remember { mutableStateOf(prefs.reminderVibrate) }
  var reminderText by remember { mutableStateOf(prefs.reminderText) }
  val reminder = remember { Reminder(context) }
  var expanded by remember { mutableStateOf<String?>(null) }
  // Bumped when a per-app override changes, so the summaries under app names refresh.
  var overrides by remember { mutableIntStateOf(0) }
  var query by remember { mutableStateOf("") }
  var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

  // Settings screens are opened in between, so re-check everything whenever we come back.
  LifecycleResumeEffect(Unit) {
    status = readStatus()
    if (enabled && status.usageAccess) TrackerService.start(context)
    onPauseOrDispose {}
  }

  LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }

  val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    status = readStatus()
  }

  val shownApps = apps
    .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    .sortedWith(compareByDescending<AppEntry> { it.packageName in tracked }.thenBy { it.label.lowercase() })

  LazyColumn(
    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      Text("Час в апці", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
      Text(
        "Чип у статус-барі показує, скільки ви вже у вибраній апці.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    item {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
          Text("Дозволи", style = MaterialTheme.typography.titleMedium)
          PermissionRow("Доступ до використання", status.usageAccess) {
            context.startActivity(Permissions.usageAccessSettings(context))
          }
          PermissionRow("Сповіщення", status.notifications) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
          PermissionRow("Live Updates (чип)", status.promoted) {
            context.startActivity(Permissions.promotedSettings(context))
          }
          PermissionRow("Без оптимізації батареї", status.battery) {
            context.startActivity(Permissions.batterySettings(context))
          }
          Text(
            if (status.promotable) "Сповіщення відповідає вимогам Live Update" else "Сповіщення НЕ відповідає вимогам Live Update",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }

    item {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Відстежувати", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
              checked = enabled,
              enabled = status.usageAccess,
              onCheckedChange = {
                enabled = it
                prefs.enabled = it
                if (it) TrackerService.start(context) else TrackerService.stop(context)
              },
            )
          }
        }
      }
    }

    item {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
          Text("Що рахує чип", style = MaterialTheme.typography.titleMedium)
          Choices(Mode.entries, mode, ::modeLabel) {
            mode = it
            prefs.mode = it
          }
          Text(modeDescription(mode), style = MaterialTheme.typography.bodySmall)
          if (mode == Mode.SESSION) {
            Text(
              "Можна вийти й повернутися без скидання:",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(top = 8.dp),
            )
            Choices(Prefs.GRACE_OPTIONS, grace, ::graceLabel) {
              grace = it
              prefs.graceSeconds = it
            }
          }
          Text("Число в чипі змінюється:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
          Choices(Prefs.STEP_OPTIONS, step, ::stepLabel) {
            step = it
            prefs.stepMinutes = it
          }
          SwitchRow("У шторці — і за сьогодні, і поточна сесія", showBoth) {
            showBoth = it
            prefs.showBoth = it
          }
        }
      }
    }

    item {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
          Text("Нагадування", style = MaterialTheme.typography.titleMedium)
          Choices(Prefs.REMINDER_OPTIONS, reminderMinutes, ::reminderLabel) {
            reminderMinutes = it
            prefs.reminderMinutes = it
          }
          Text(
            "Щоразу, як лічильник доходить до наступних N хв. Значок у чипі стає пісочним годинником.",
            style = MaterialTheme.typography.bodySmall,
          )
          SwitchRow("Вібрація (з кожним разом довша)", reminderVibrate) {
            reminderVibrate = it
            prefs.reminderVibrate = it
          }
          SwitchRow("Текст на екрані", reminderText) {
            reminderText = it
            prefs.reminderText = it
          }
          TextButton(
            onClick = {
              if (reminderVibrate) reminder.buzz(level = 1)
              if (reminderText) reminder.say("15 хв у Reddit")
            },
          ) { Text("Спробувати") }
        }
      }
    }

    item {
      Text("Апки (${tracked.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Пошук") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    items(shownApps, key = { it.packageName }) { app ->
      val pkg = app.packageName
      val checked = pkg in tracked
      fun setTracked(on: Boolean) {
        tracked = if (on) tracked + pkg else tracked - pkg
        prefs.trackedPackages = tracked
        if (!on && expanded == pkg) expanded = null
      }
      Column(Modifier.fillMaxWidth()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { if (checked) expanded = if (expanded == pkg) null else pkg else setTracked(true) }
            .padding(vertical = 4.dp),
        ) {
          Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(app.label)
            if (checked) {
              val summary = remember(overrides, mode, reminderMinutes) { appSummary(prefs, pkg) }
              Text(summary, style = MaterialTheme.typography.bodySmall)
            }
          }
          Checkbox(checked = checked, onCheckedChange = ::setTracked)
        }
        if (checked && expanded == pkg) AppSettings(prefs, pkg) { overrides++ }
      }
    }

    item { Spacer(Modifier.padding(bottom = 16.dp)) }
  }
}

@Composable
private fun AppSettings(prefs: Prefs, packageName: String, onChange: () -> Unit) {
  var mode by remember { mutableStateOf(prefs.appMode(packageName)) }
  var reminder by remember { mutableStateOf(prefs.appReminder(packageName)) }
  Column(Modifier.padding(start = 48.dp, bottom = 8.dp)) {
    Text("Що рахує чип", style = MaterialTheme.typography.bodySmall)
    Choices(listOf(null) + Mode.entries, mode, { it?.let(::modeLabel) ?: "як загалом" }) {
      mode = it
      prefs.setAppMode(packageName, it)
      onChange()
    }
    Text("Нагадування", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    Choices(listOf(null) + Prefs.REMINDER_OPTIONS, reminder, { it?.let(::reminderLabel) ?: "як загалом" }) {
      reminder = it
      prefs.setAppReminder(packageName, it)
      onChange()
    }
  }
}

private fun appSummary(prefs: Prefs, packageName: String): String {
  val reminder = prefs.reminderFor(packageName)
  val text = modeLabel(prefs.modeFor(packageName)) + " · " +
    if (reminder == 0) "без нагадувань" else "нагадування кожні $reminder хв"
  val custom = prefs.appMode(packageName) != null || prefs.appReminder(packageName) != null
  return if (custom) text else "$text (загальні)"
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
    Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onGrant: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Text((if (granted) "✓ " else "✗ ") + title, modifier = Modifier.weight(1f))
    if (!granted) TextButton(onClick = onGrant) { Text("Дозволити") }
  }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    for (option in options) {
      FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
    }
  }
}

private fun modeLabel(mode: Mode) = when (mode) {
  Mode.DAY -> "За день"
  Mode.SESSION -> "Сесія"
  Mode.EACH_OPEN -> "Кожне відкриття"
}

private fun modeDescription(mode: Mode) = when (mode) {
  Mode.DAY -> "Скільки сьогодні загалом у цій апці. Обнуляється опівночі."
  Mode.SESSION -> "Безперервний час в апці. Коротко вийшли й повернулися — рахує далі, після довшої перерви — з нуля."
  Mode.EACH_OPEN -> "З нуля щоразу, як відкриваєте апку."
}

private fun graceLabel(seconds: Int) = if (seconds < 60) "$seconds с" else "${seconds / 60} хв"

private fun reminderLabel(minutes: Int) = if (minutes == 0) "вимк." else "$minutes хв"

private fun stepLabel(minutes: Int) = when (minutes) {
  0 -> "щосекунди"
  1 -> "щохвилини"
  else -> "кожні $minutes хв"
}

private fun loadApps(context: android.content.Context): List<AppEntry> {
  val pm = context.packageManager
  val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  return pm.queryIntentActivities(launcher, 0)
    .map { it.activityInfo.applicationInfo }
    .filter { it.packageName != context.packageName }
    .distinctBy { it.packageName }
    .map { info ->
      AppEntry(
        packageName = info.packageName,
        label = pm.getApplicationLabel(info).toString(),
        icon = pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap(),
      )
    }
}
