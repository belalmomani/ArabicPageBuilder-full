package com.arabicpagebuilder.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.arabicpagebuilder.AppContainer
import com.arabicpagebuilder.Screen
import com.arabicpagebuilder.editor.EditorScreen

@Composable
fun AppRoot(container: AppContainer) {
    // الواجهة عربية بالكامل: نفرض RTL بغض النظر عن لغة الجهاز.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                when (container.screen) {
                    Screen.Home -> HomeScreen(container)
                    Screen.Settings -> SettingsScreen(container)
                    Screen.Editor -> {
                        val editor = container.editor
                        if (editor != null) EditorScreen(container, editor) else Text("لا يوجد مشروع مفتوح")
                    }
                }
            }
        }
    }
}
