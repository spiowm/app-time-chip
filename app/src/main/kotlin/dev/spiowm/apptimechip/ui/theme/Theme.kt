package dev.spiowm.apptimechip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material You colors taken from the wallpaper; minSdk is well above Android 12. */
@Composable
fun AppTimeChipTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  MaterialTheme(colorScheme = colors, content = content)
}
