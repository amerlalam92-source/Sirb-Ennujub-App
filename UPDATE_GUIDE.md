# دليل التحديث التلقائي للتطبيق عبر GitHub Releases (OTA Update Guide)

---

## ⚠️ خطوة إلزامية قبل كل تحديث (تجنباً لحلقة التحديث المتكررة)
قبل دفع أي كود جديد إلى GitHub لإنشاء تحديث جديد، **يجب عليك أولاً وبشكل إلزامي** فتح ملف `app/build.gradle.kts` وزيادة كلا الرقمين:
1. **`versionCode`**: زيادته بمقدار 1 (مثلاً من `1` إلى `2` ثم `3` ثم `4`...).
2. **`versionName`**: تحديث اسم الإصدار (مثلاً من `"1.0.1"` إلى `"1.0.2"`).

```kotlin
defaultConfig {
    ...
    versionCode = 2       // ⬅️ زِد هذا الرقم دائماً (+1)
    versionName = "1.0.1" // ⬅️ حدّث اسم الإصدار
}
```

> ⚠️ **لماذا هذه الخطوة إلزامية؟**
> التطبيق يفحص داخلياً رقم `versionCode` الخاص به ويقارنه برقم `latestVersionCode` الموجود في Firestore. إذا لم ترفع `versionCode` في `app/build.gradle.kts` قبل البناء، فإن التطبيق بعد تثبيت التحديث سيظل يحمل الرقم القديم، وسيعتقد أن هناك تحديثاً جديداً لم يُثبّت بعد، مما يدخله في حلقة تحميل وتثبيت لا نهائية (Infinite Loop).

---

## 1. شرط أساسي: جعل المستودع عاماً (Public Repository)
⚠️ **مهم جداً**: لكي يتمكن التطبيق والمستخدمون من تحميل ملفات الـ APK من إصدارات GitHub Releases مباشرة وبدون طلب مصادقة أو رموز وصول، **يجب أن يكون مستودع GitHub عاماً (Public)**:
1. افتح مستودع المشروع على GitHub: `https://github.com/amerlalam92-source/Sirb-Ennujub-App`
2. انتقل إلى **Settings** (إعدادات المستودع).
3. انزل لأسفل الصفحة حتى قسم **Danger Zone** (منطقة الخطر).
4. عند خيار **Change repository visibility**، اضغط على **Change visibility** واختر **Make public** (تحويل إلى عام).
5. أكّد العملية باتباع التعليمات التي تظهر على الشاشة.

---

## 2. التأكد من سر `GOOGLE_SERVICES_JSON` (لمرة واحدة فقط)
يجب التأكد من وجود السر التالي في إعدادات المستودع لكي ينجح البناء:
1. اذهب إلى: **Settings** > **Secrets and variables** > **Actions**.
2. تأكد من إضافة سر بالاسم: `GOOGLE_SERVICES_JSON` وقيمته هي نص الـ Base64 لملف `google-services.json`.

*(ملاحظة: رمز `GITHUB_TOKEN` متوفر افتراضياً من GitHub ولا يحتاج إلى إنشاء يدوي).*

---

## 3. آلية البناء التلقائي عند دفع التحديثات (Push)
1. من واجهة **Google AI Studio**، اضغط على خيار **Push to GitHub** أو مزامنة الكود مع فرع `main`.
2. سيبدأ تلقائياً سير العمل في **GitHub Actions**:
   - تهيئة بيئة Java 17 و Gradle.
   - فك تشفير وإنشاء ملف `app/google-services.json`.
   - بناء ملف الـ APK الخاص بالإنتاج `assembleRelease`.
   - إنشاء إصدار جديد (Release) تلقائياً على GitHub باسم ووسم يحمل رقم التشغيل (Run Number): `v1.0.[RUN_NUMBER]`.
   - رفع ملف الـ `app-release.apk` مباشرة إلى ذلك الإصدار.

---

## 4. الحصول على رابط تحميل الـ APK
بعد اكتمال تشغيل سير العمل بنجاح (علامة صح خضراء في تبويب Actions):

### أ) صيغة الرابط المباشر:
سيكون رابط تحميل ملف الـ APK دائماً بالصيغة التالية:
```text
https://github.com/amerlalam92-source/Sirb-Ennujub-App/releases/download/v1.0.[RUN_NUMBER]/app-release.apk
```
*استبدل `[RUN_NUMBER]` برقم تشغيل الـ Action المعني (مثلاً إذا كان رقم التشغيل 5، يكون الرابط: `https://github.com/amerlalam92-source/Sirb-Ennujub-App/releases/download/v1.0.5/app-release.apk`).*

### ب) النسخ من صفحة Releases:
1. افتح صفحة المستودع على GitHub واضغط على **Releases** في الشريط الجانبي الأيمن.
2. ستجد الإصدار الأخير (مثلاً `Release v1.0.5`).
3. تحت قسم **Assets**، اضغط بزر الفأرة الأيمن على `app-release.apk` واختر **Copy link address** (نسخ عنوان الرابط).

---

## 5. تفعيل التحديث في Firestore ليظهر لجميع المستخدمين
لكي يظهر إشعار التحديث تلقائياً للقادة في التطبيق:
1. افتح [Firebase Console](https://console.firebase.google.com/) > **Firestore Database**.
2. ادخل إلى المجموعة (Collection): `settings`.
3. افتح المستند (Document): `app_update`.
4. حدّث الحقول التالية:
   - **`latestVersionCode`** (نوع `number`): رقم الإصدار الجديد المذكور في `build.gradle.kts` (مثلاً `2`).
   - **`latestVersionName`** (نوع `string`): اسم الإصدار الظاهر للمستخدم (مثلاً `"1.0.1"`).
   - **`apkUrl`** (نوع `string`):
     `https://github.com/amerlalam92-source/Sirb-Ennujub-App/releases/download/v1.0.[RUN_NUMBER]/app-release.apk`
   - **`changelog`** (نوع `string`): ملخص التغييرات والتحسينات في هذا التحديث.
5. احفظ التعديلات في المستند. عند فتح أي قائد للتطبيق، سيظهر له حوار التحديث التلقائي ويبدأ تحميل النسخة الجديدة وتثبيتها فوراً!
