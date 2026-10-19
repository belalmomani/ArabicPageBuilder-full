package com.arabicpagebuilder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arabicpagebuilder.AppContainer
import com.arabicpagebuilder.Screen

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    BackHandler { container.screen = Screen.Home }
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (e: Exception) {
            "—"
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { container.screen = Screen.Home }) { Text("رجوع") }
            Text("الإعدادات", style = MaterialTheme.typography.headlineSmall)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("السماح بالموارد الخارجية في المعاينة")
                Text(
                    "عند التعطيل (الافتراضي) لا تُحمَّل أي صورة أو مورد من الإنترنت داخل المعاينة. الصور المحلية تظهر دائمًا.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = container.prefs.allowExternalPreview, onCheckedChange = { container.prefs.setAllowExternalPreview(it) })
        }
        Text("إصدار التطبيق: $versionName", style = MaterialTheme.typography.bodySmall)
        Text("المشاريع تُحفظ على جهازك فقط. لا توجد حسابات ولا خدمات سحابية.", style = MaterialTheme.typography.bodySmall)
    }
}
