package com.arabicpagebuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arabicpagebuilder.ui.AppRoot
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PageBuilderApp).container
        setContent { AppRoot(container) }
    }

    /**
     * عند ذهاب التطبيق إلى الخلفية (أو إغلاقه) نحفظ فورًا أي تعديل لم يُحفظ بعد،
     * فلا تبقى نافذة ضياع بين آخر تعديل والحفظ التلقائي المؤجَّل. الكتابة صغيرة وسريعة.
     */
    override fun onStop() {
        super.onStop()
        val editor = (application as PageBuilderApp).container.editor
        if (editor != null) runBlocking { editor.saveNow() }
    }
}
