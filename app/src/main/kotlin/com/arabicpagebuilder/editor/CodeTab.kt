package com.arabicpagebuilder.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicpagebuilder.core.generator.HtmlGenerator

/** عرض الكود الناتج فعلًا (index.html وcss/style.css) كما سيُصدَّر. */
@Composable
fun CodeTab(state: EditorState) {
    val site = remember(state.doc) { HtmlGenerator.generate(state.doc) }
    var showCss by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!showCss) Button(onClick = {}) { Text("index.html") } else OutlinedButton(onClick = { showCss = false }) { Text("index.html") }
            if (showCss) Button(onClick = {}) { Text("css/style.css") } else OutlinedButton(onClick = { showCss = true }) { Text("css/style.css") }
        }
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                text = if (showCss) site.styleCss else site.indexHtml,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(8.dp),
            )
        }
    }
}
