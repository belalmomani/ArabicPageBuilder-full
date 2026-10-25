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

## أجزاء الصفحة والقوائم والتأثيرات (الإصدار 0.5.0)
- **شريط علوي** (`NAVBAR`) بخصائصه: تثبيت عند التمرير، وتوزيع العناصر ومحاذاتها، وكل خصائص الحاوية.
- **قائمة تنقل** (`NAV_MENU`): العناصر (النص|الرابط) وقوائم فرعية منسدلة، والعنصر النشط، وشكل القائمة (بسيط/أقراص/خط سفلي/مربعات)، وسلوكها على الهاتف (زر ☰ بلا JavaScript أو عمودية)، واتجاه العناصر وتوزيعها والمسافة بينها، وألوان العناصر وأحجامها وزواياها، وتأثير المرور (خط سفلي/رفع/تكبير/شفافية)، وألوان العنصر النشط.
- **تأثيرات وحركات لكل عنصر** (CSS فقط): حركة الظهور (8 أنواع) بمدة وتأخير وتكرار، وتأثير عند المرور (6 أنواع) مع ألوان وانتقال، والشفافية. وتُعطَّل الحركات تلقائيًا لمن فعّل «تقليل الحركة» في جهازه.
- **أجزاء جاهزة:** شريط علوي مع قائمة، قسم رئيسي، شبكة مزايا، نموذج تواصل، تذييل.
- **محرر عناصر القائمة المرئي** (إضافة/حذف/ترتيب/رئيسي-فرعي/فحص الرابط): منطقه في `NavItemsCodec` واختباراته في `NavItemsCodecTest`؛ وواجهته تُفحص يدويًا.
- لوحة الخصائص مقسمة بعناوين: المحتوى، نوع القائمة، شكل عناصر القائمة، الشكل والتنسيق، التأثيرات والحركة، الاستجابة للشاشات.
- الاختبارات: `NavigationEffectsTest` و`PropertySpecsTest` (المجموعات والتحقق) و`NewElementsGeneratorTest`.

## البرمجة المرئية (الإصدار 0.6.0)
- **تفاعل = ثلاث خطوات متصلة:** «عند» (ضغط، تغيّر حقل، إرسال نموذج، فتح الصفحة) ← «إذا» (اختيارية: حقل فارغ/غير فارغ/يساوي/يحتوي، مربع محدد، عنصر ظاهر/مخفي، بـ«كل الشروط» أو «أي شرط») ← «افعل» (إظهار/إخفاء/تبديل، تمرير، تغيير نص، عرض رسالة) مع «وإلا».
- **لا كود من المستخدم أبدًا:** المولّد يبني `js/main.js` من نص ثابت مراجَع (`InteractionScript.RUNTIME`) وبيانات مُهرَّبة. بلا eval ولا innerHTML ولا مستمعات في HTML، وتغيير النص عبر `textContent`. **لا يُولَّد أي JavaScript إن لم توجد تفاعلات صالحة.**
- **تفاعل غير صالح** (عنصر محذوف، شرط ناقص...) لا يدخل الموقع المُصدَّر، ويظهر في المحرر بمشكلاته بالعربية وفي تنبيهات المعاينة والتصدير.
- **عنصر «مخفي عند فتح الصفحة»** (خاصية لكل عنصر) يُظهره التفاعل. الزر بلا رابط لكن له تفاعل يُعرض فعّالًا.
- **التحقق:** `InteractionValidatorTest`, `InteractionGeneratorTest` (يتحقق أن بيانات القواعد JSON صالح حتى مع نصوص خبيثة), `InteractionFormatTest`, `InteractionEditTest`, `InteractionTextTest`, و`SiteExporterTest` (يُصدَّر `js/main.js` ويُتحقق من مرجعه).
- **تحقق إضافي أجريته خارج CI:** سكربت التشغيل الثابت جرى اختباره في Node ضد DOM مبسّط (24 فحصًا: الشروط كلها، و«وإلا»، وتبديل الظهور، ومنع الإرسال عند فشل التحقق، وعدم حقن HTML، وغياب الاستدعاءات الخطرة). **لم يُختبر بعد على WebView حقيقي ولا على متصفحات التصدير.**
- **المعاينة:** يُفعَّل JavaScript فيها فقط حين توجد تفاعلات صالحة، مع سياسة CSP تمنع أي مصدر آخر وإرسال النماذج، وتُعرض «الرسالة» كنافذة تنبيه.

## ما لا يغطيه أي اختبار آلي (فحص يدوي على الجهاز)
- [ ] تبويب «التفاعل»: «+ تفاعل جديد»، واختيار المحفّز والعنصر من المنتقي، وإضافة شرط وإجراء، وبطاقة الخطوات الثلاث.
- [ ] مثال كامل: زر + قسم مخفي «مخفي عند فتح الصفحة» + تفاعل «عند الضغط ← أظهر القسم»، وتجربته في المعاينة.
- [ ] «التحقق قبل الإرسال» على نموذج التواصل: حقل فارغ يعرض رسالة ولا يُرسَل.
- [ ] تصدير ZIP يحتوي `js/main.js`، وفتح `index.html` في متصفح وتجربة التفاعل.
- [ ] تبويب المعاينة يعرض الصفحة فعلًا (إصلاح خطأ ERR_HTTP_RESPONSE_CODE_FAILURE).
- [ ] إضافة «شريط علوي مع قائمة» من المكتبة: ظهوره في المعاينة، وزر ☰ على الهاتف، والقائمة الفرعية عند اللمس.
- [ ] محرر عناصر القائمة: إضافة عنصر وعنصر فرعي، والأزرار ▲ ▼، وتحويل عنصر إلى فرعي، والحذف، وظهور كل ذلك في المعاينة.
- [ ] تغيير خصائص القائمة (الشكل، الألوان، تأثير المرور) وظهور أثرها في المعاينة.
- [ ] حركة الظهور وتأثير المرور على بطاقة (يعمل اللمس على معظم الهواتف كمرور).
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
