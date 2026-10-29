# دليل إدارة وتحديث التطبيق عبر GitHub Releases و Firestore
## (نظام الإصدار الموحد - Unified Versioning System)

يعتمد التطبيق الآن على **ملف `app/build.gradle.kts` كمصدر وحيد وأساسي للحقيقة (Single Source of Truth)** لجميع أرقام الإصدارات في:
1. **داخل التطبيق (About Screen)**: يعرض الإصدار ديناميكياً من `BuildConfig.VERSION_NAME`.
2. **فحص التحديثات التلقائي**: يقارن رقم `BuildConfig.VERSION_CODE` مع الرقم في خادم Firestore.
3. **وسم الإصدار على GitHub Actions**: يتم استخراج `versionName` تلقائياً ليصبح وسم و عنوان الإصدار `v[VERSION_NAME]`.
4. **قاعدة بيانات Firestore**: تُطابق تماماً الأرقام المحددة في `build.gradle.kts`.

---

## 🚀 خطوات إصدار تحديث جديد (بالترتيب الإلزامي)

عند رغبتك في إطلاق أي تحديث جديد للتطبيق، اتبع الخطوات التالية **بالترتيب الدقيق**:

### 1️⃣ فتح ملف الإعدادات `app/build.gradle.kts`
افتح الملف وانتقل إلى قسم `defaultConfig`:

### 2️⃣ زيادة رقم الإصدار `versionCode` بمقدار 1
قم بزيادة الرقم بمقدار واحد (مثلاً من `3` إلى `4`).
> ⚠️ **تنبيه حاسم**: عدم زيادة هذا الرقم سيجعل التطبيق يقع في حلقة تحديث متكررة، لأن التطبيق يتحقق من هذا الرقم ليحدد إن كان بحاجة لتحديث.

### 3️⃣ تحديث اسم الإصدار `versionName`
قم بتحديث اسم الإصدار التسلسلي (مثلاً من `"1.0.3"` إلى `"1.0.4"`).

```kotlin
defaultConfig {
    applicationId = "com.example"
    minSdk = 24
    targetSdk = 36
    versionCode = 4       // ⬅️ زيادة بمقدار 1 دائماً
    versionName = "1.0.4" // ⬅️ الاسم الجديد للإصدار
    ...
}
```

### 4️⃣ دفع الكود إلى GitHub (Push)
- من واجهة **Google AI Studio**، اضغط على **Push to GitHub** للمزامنة مع فرع `main`.
- افتح تبويب **Actions** في مستودع GitHub:
  `https://github.com/amerlalam92-source/Sirb-Ennujub-App/actions`
- انتظر حتى يكتمل البناء وتظهر علامة الصح الخضراء (تستغرق عادةً دقيقتين إلى 3 دقائق).

### 5️⃣ نسخ رابط ملف الـ APK الجديد من GitHub Releases
- اذهب إلى صفحة الإصدارات:
  `https://github.com/amerlalam92-source/Sirb-Ennujub-App/releases`
- ستجد الإصدار الجديد باسم ووسم يطابق رقم نسختك تماماً (مثلاً: `Release v1.0.4` و الوسم `v1.0.4`).
- تحت قسم **Assets**، اضغط بزر الفأرة الأيمن على `app-release.apk` واختر **Copy link address** (نسخ عنوان الرابط).
- *سيكون الرابط المباشر دائماً بالصيغة:*
  ```text
  https://github.com/amerlalam92-source/Sirb-Ennujub-App/releases/download/v[VERSION_NAME]/app-release.apk
  ```
  *(مثال: `https://github.com/amerlalam92-source/Sirb-Ennujub-App/releases/download/v1.0.4/app-release.apk`)*

### 6️⃣ فتح قاعدة بيانات Firestore
افتح [Firebase Console](https://console.firebase.google.com/) ثم توجه إلى:
`Firestore Database` > المجموعة `settings` > المستند `app_update`.

### 7️⃣ تعيين `latestVersionCode`
ضع نفس الرقم الذي حددته في `versionCode` داخل `build.gradle.kts` (مثلاً: `4` كنوع `number`).

### 8️⃣ تعيين `latestVersionName`
ضع نفس النص الذي حددته في `versionName` داخل `build.gradle.kts` (مثلاً: `"1.0.4"` كنوع `string`).

### 9️⃣ لصق رابط الـ APK في `apkUrl`
الصق رابط الـ APK المباشر المنسوخ من الخطوة 5 في حقل `apkUrl` (كنوع `string`).
*(يمكنك أيضاً تحديث حقل `changelog` لبيان الجديد في الإصدار).*

### 🔟 حفظ التعديلات (Save)
اضغط **Save** في Firestore.

🎉 **النتيجة**: فور فتح التطبيق من قبل أي قائد أو مستخدم، سيتعرف التطبيق فوراً على وجود التحديث `1.0.4` ويعرض نافذة التحديث لتحميل الـ APK وتثبيته تلقائياً!

---

## ⚙️ متطلبات أولية (تم إعدادها مسبقاً)

1. **المستودع عام (Public Repository)**:
   - يجب أن يبقى المستودع عاماً لتتمكن الهواتف من تنزيل الـ APK بدون قيود مصادقة.
2. **السر `GOOGLE_SERVICES_JSON`**:
   - مُخزن في **Settings** > **Secrets and variables** > **Actions** في GitHub ليتمكن سير العمل من بناء التطبيق بنجاح.
