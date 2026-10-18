# منشئ الصفحات العربي (Arabic Page Builder)

الحالة: المراحل 1–4 (هيكل المشروع وسير GitHub Actions، نموذج الشجرة والتراجع/الإعادة، مولّد HTML/CSS، مصدّر ZIP) داخل وحدة `core`.
واجهة التطبيق (`app`) ما زالت هيكلًا فقط. لم يُبنَ المشروع بعد؛ أول تحقق فعلي هو GitHub Actions.

## التشغيل الأول (مرة واحدة)
1. ارفع محتويات هذا المجلد إلى مستودع GitHub (الفرع `main`).
2. من تبويب Actions شغّل **Bootstrap Gradle Wrapper** يدويًا.
3. نزّل الـ Artifact `gradle-wrapper-files`، وضع `gradlew` و`gradlew.bat` في جذر المستودع، ومجلد `gradle/wrapper/` داخل `gradle/` (بجانب `libs.versions.toml`)، ثم ارفعها. 
4. سيعمل **Build Debug APK** تلقائيًا (أو يدويًا). نزّل `app-debug-apk` من نتائج التشغيل.

## الإصدارات المثبّتة ومصادرها
- AGP 9.1.1 → Gradle 9.3.1، JDK 17، Build Tools 36.0.0، API 37: developer.android.com/build/releases/agp-9-1-0-release-notes
- Kotlin 2.4.21: kotlinlang.org/docs/releases.html
- Compose BOM 2026.08.00 (يتطلب compileSdk 37 وAGP 9.1.1 فأعلى): android-developers.googleblog.com (Compose August '26)
- activity-compose 1.13.0: developer.android.com/jetpack/androidx/releases/activity

لم تُبنَ هذه المجموعة بعد؛ أول تحقق فعلي هو تشغيل GitHub Actions.
