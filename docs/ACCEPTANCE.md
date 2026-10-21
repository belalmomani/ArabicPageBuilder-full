# معايير القبول: ما اختُبر آليًا وما يحتاج فحصًا يدويًا

الاختبارات الآلية تعمل في GitHub Actions عند كل بناء (`./gradlew test`) وكلها في وحدة `core`.
لا توجد اختبارات آلية لواجهة `app` (Compose وWebView وSAF)؛ تُفحص يدويًا بالقائمة في آخر الملف.

## البنود الخمسة عشر

| # | البند | الاختبارات الآلية |
|---|-------|-------------------|
| 1 | إنشاء مشروع جديد | `DocumentEditorTest.newDocumentHasEmptyRootAndRtlDefault`، `ProjectStoreTest.createThenLoadAndReopenWithNewStoreInstance` |
| 2 | إضافة عنوان وتغيير نصه | `DocumentEditorTest.addHeadingThenChangeItsText`، `EditorSessionTest.setPropertyChangesSelectedAndBumpsRevisionOnlyOnRealChange` |
| 3 | إضافة صورة وتغيير خصائصها | `ProjectStoreTest.assetNamesAreSanitizedAndKeptInsideAssetsFolder`، `ProjectStoreTest.imageContentMustMatchItsExtension`، `HtmlGeneratorTest.imageRendersEscapedAltAndWarnsOnMissingAlt`، `PropertySpecsTest.imageSourceValidation` |
| 4 | رابط وتوليد href الصحيح | `HtmlGeneratorTest.linkGetsCorrectHrefAndExternalGetsRel`، `...dangerousLinksAreDroppedWithWarning`، `UrlSanitizerTest.*` |
| 5 | حاوية وعناصر داخلها | `DocumentEditorTest.nestedStructureIsSupported`، `HtmlGeneratorTest.nestedElementsKeepOrderAndBalance` |
| 6 | إعادة ترتيب العناصر | `DocumentEditorTest.reorderWithMoveUpAndMoveDown`، `...moveToAnotherContainerAtGivenIndex`، `DropPlannerTest.*` |
| 7 | التراجع والإعادة | `HistoryTest.*`، `EditorSessionTest.undoRedoRestoreDocumentAndFixSelection` |
| 8 | حفظ وإغلاق وإعادة فتح | `ProjectStoreTest.saveCloseAndReopenKeepsDocument`، `...restoreBackupReturnsPreviousSavedVersion` |
| 9 | توليد HTML وCSS صالحين | `HtmlGeneratorTest.documentShell...`، `NewElementsGeneratorTest.everyElementTypeGeneratesBalancedHtmlWithoutCrashing`، `...cssInjectionAttemptsAreRejected` (الصلاحية = توازن الوسوم وبنية صحيحة وقيم CSS مفحوصة، وليست فحص W3C كاملًا) |
| 10 | تصدير ZIP والتحقق من الملفات والمسارات | `SiteExporterTest.containsExpectedEntries...`، `...exportToFileSucceedsAndResultVerifiesClean`، `...verifierFlags*` |
| 11 | فتح المشروع المصدَّر بعد فك الضغط | `SiteExporterTest.exportedZipExtractsToDiskAndEveryReferencedFileExists` |
| 12 | نصوص عربية وخاصة بلا تلف | `ProjectFormatTest.roundTripPreservesEverythingIncludingSpecialText`، `HtmlGeneratorTest.specialCharactersInTextAreEscapedAndArabicIsPreserved`، `SiteExporterTest.arabicFileNamesAndTextSurviveRoundTrip` |
| 13 | صور مفقودة وروابط غير صالحة | `SiteExporterTest.missingImageGivesWarning...`، `HtmlGeneratorTest.badOrMissingImageSourcesAreOmittedWithWarnings`، `NewElementsGeneratorTest.badMediaSourcesAreOmittedWithWarnings` |
| 14 | أخطاء التصدير والاستيراد | `SiteExporterTest` (حدود الحجم، فشل الكتابة، بقاء الملف القديم)، `ProjectArchiveTest` (Zip Slip، قنبلة الضغط، أرشيف تالف، مدخلات غير متوقعة، تطابق محتوى الصور) |
| 15 | بناء APK عبر GitHub Actions | `.github/workflows/build.yml` (يرفع APK ويطبع بصمة التوقيع) |

## ما لا يغطيه أي اختبار آلي (فحص يدوي على الجهاز)
- [ ] إنشاء مشروع من الشاشة الرئيسية، وإعادة تسميته ونسخه وحذفه (مع التأكيد).
- [ ] اختيار صورة وفيديو وملف تنزيل من الجهاز، وظهورها في المعاينة.
- [ ] إضافة عنصر بالضغط، وبالسحب من المكتبة، وسحب عنصر موجود إلى حاوية؛ ظهور مؤشر الإدراج؛ والتمرير التلقائي.
- [ ] رسالة واضحة عند محاولة إفلات عنصر داخل نفسه.
- [ ] المعاينة بأوضاع الهاتف واللوحي والحاسوب، وتحديثها مع كل تعديل مع بقاء موضع التمرير.
- [ ] الصور الخارجية لا تظهر في المعاينة حتى يُفعَّل مفتاح الموارد الخارجية.
- [ ] تصدير ZIP عبر «حفظ» وعبر «مشاركة»، وفتح `index.html` من الملف المفكوك في متصفح.
- [ ] حفظ نسخة `.apbproj` ثم استيرادها من الشاشة الرئيسية.
- [ ] إغلاق التطبيق وإعادة فتحه: المشاريع باقية.
- [ ] تثبيت نسخة جديدة فوق القديمة بلا حذف (بصمة التوقيع متطابقة في ملخص التشغيل).

## قيود معروفة (غير مُنفَّذة عمدًا في هذا الإصدار)
- لا تضمين لخطوط عربية؛ يُستخدم خط النظام (أو سيريف/أحادي).
- لا تصغير للصور الكبيرة عند الاستيراد (يوجد حد 20 ميغابايت للملف).
- يُفحص محتوى الصور (PNG وJPEG وGIF وWebP) فقط؛ الفيديو والصوت والملفات تُفحص امتدادها وحجمها دون محتواها.
- النماذج لا ترسل بيانات دون رابط استقبال خارجي (https)؛ يُنبَّه إلى ذلك في المعاينة وفي README المُصدَّر.
- المفتاح الموقِّع للإصدارات تجريبي (debug) ومحفوظ في المستودع؛ النشر الرسمي يحتاج مفتاحًا سريًا في GitHub Secrets (مرحلة مستقلة).
- لا Firebase ولا ذكاء اصطناعي ولا حسابات مستخدمين (مؤجَّلة بقرار).
