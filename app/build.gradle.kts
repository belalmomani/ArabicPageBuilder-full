plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// versionCode = دقائق منذ 1970: يزيد مع كل بناء ولو أُعيد إنشاء المستودع، فيُقبل التحديث فوق النسخة المثبّتة.
// (الحد الأقصى لـ versionCode نحو 2.1 مليار، والقيمة الحالية قرابة 30 مليونًا.)
val buildMinutes: Int = (System.currentTimeMillis() / 60_000L).toInt()
val ciRun: String = System.getenv("GITHUB_RUN_NUMBER") ?: "local"

android {
    namespace = "com.arabicpagebuilder"
    compileSdk { version = release(37) }

    defaultConfig {
        // applicationId ثابت: تغييره يجعل أندرويد يعامل التطبيق كتطبيق جديد لا كتحديث.
        applicationId = "com.arabicpagebuilder"
        minSdk = 26
        targetSdk = 36
        versionCode = buildMinutes
        versionName = "0.5.0 (build $ciRun)"
    }

    // مفتاح توقيع ثابت داخل المستودع (مفتاح debug غير سري). لولاه يولّد كل تشغيل على GitHub مفتاحًا عشوائيًا
    // فيرفض أندرويد التحديث ويضطرك لحذف التطبيق. للإصدار الرسمي لاحقًا نستخدم مفتاحًا سريًا في GitHub Secrets.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
}
