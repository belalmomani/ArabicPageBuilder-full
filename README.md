# منشئ الصفحات العربي (Arabic Page Builder)

الحالة: المراحل 1–5 (هيكل المشروع وسير GitHub Actions، نموذج الشجرة والتراجع/الإعادة، مولّد HTML/CSS، مصدّر ZIP، الحفظ المحلي، تصدير/استيراد أرشيف المشروع) داخل وحدة `core`.
واجهة التطبيق (`app`) ما زالت هيكلًا فقط. لم يُبنَ المشروع بعد؛ أول تحقق فعلي هو GitHub Actions.

## التشغيل
1. ارفع محتويات هذا المجلد إلى مستودع GitHub (الفرع `main`).
2. سير **Build Debug APK** يعمل تلقائيًا. إن لم يكن Gradle Wrapper في المستودع يولّده بنفسه لهذا التشغيل، ويرفع ملفاته كـ Artifact باسم `gradle-wrapper-files` لتضيفها إلى المستودع (اختياري لكنه مستحسن).
3. نزّل `app-debug-apk` من نتائج التشغيل.

## الإصدارات المثبّتة ومصادرها
- AGP 9.1.1 → Gradle 9.3.1، JDK 17، Build Tools 36.0.0، API 37: developer.android.com/build/releases/agp-9-1-0-release-notes
- Kotlin 2.4.21: kotlinlang.org/docs/releases.html
- Compose BOM 2026.08.00 (يتطلب compileSdk 37 وAGP 9.1.1 فأعلى): android-developers.googleblog.com (Compose August '26)
- activity-compose 1.13.0: developer.android.com/jetpack/androidx/releases/activity

لم تُبنَ هذه المجموعة بعد؛ أول تحقق فعلي هو تشغيل GitHub Actions.
