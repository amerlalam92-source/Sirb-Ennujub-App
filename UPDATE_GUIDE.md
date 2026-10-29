# دليل التحديث التلقائي للتطبيق عبر Firebase Storage (OTA Update Guide)

نظراً لأن مستودع GitHub خاص (Private Repository)، يتم رفع ملف الـ APK تلقائياً عبر **GitHub Actions** مباشرة إلى **Firebase Storage** لتمكين التطبيق من تحميله وتثبيته فورياً دون الحاجة لجعل المستودع عاماً.

---

## 1. إعداد مفتاح الخدمة (GCP_SA_KEY) لمرة واحدة فقط

لكي يتمكن GitHub Actions من رفع ملف الـ APK إلى Firebase Storage في مشروعك، يجب تزويده بمفتاح حساب خدمة:

### أ) استخراج المفتاح من Firebase Console:
1. افتح [Firebase Console](https://console.firebase.google.com/) واختر مشروعك (`sirb-ennujub`).
2. انقر على أيقونة الترس ⚙️ بجوار "Project Overview" واختر **Project settings** (إعدادات المشروع).
3. انتقل إلى علامة التبويب **Service accounts** (حسابات الخدمة).
4. اضغط على زر **Generate new private key** (إنشاء مفتاح خاص جديد)، ثم أكد بالضغط على **Generate key**.
5. سيتم تحميل ملف بصيغة `.json` على حاسوبك يحتوي على مفتاح المصادقة.

### ب) إضافة المفتاح إلى أسرار GitHub (GitHub Secrets):
1. افتح مستودع المشروع على **GitHub**.
2. انتقل إلى تبويب **Settings** في المستودع.
3. في القائمة الجانبية اليسرى، اختر **Secrets and variables** ثم اضغط على **Actions**.
4. اضغط على زر **New repository secret**.
5. املأ البيانات كالتالي:
   - **Name**: `GCP_SA_KEY`
   - **Secret**: افتح ملف الـ `.json` الذي قمت بتحميله وانسخ كامل محتواه وألصقه هنا.
6. اضغط على **Add secret**.

---

## 2. رفع الكود من Google AI Studio إلى GitHub
1. من واجهة **Google AI Studio**، اضغط على زر الإعدادات / المزامنة مع GitHub.
2. اختر **Export to GitHub** أو **Push to GitHub** إلى فرع `main`.
3. سيبدأ تلقائياً سير العمل **GitHub Actions** في بناء نسخة الإنتاج `assembleRelease` ورفع ملف الـ APK مباشرة إلى Firebase Storage تحت المسار:
   `gs://sirb-ennujub.firebasestorage.app/apk/app-release.apk`

---

## 3. رابط التحميل المباشر للـ APK
بفضل إعداد `predefinedAcl: publicRead`، يصبح ملف الـ APK متاحاً للتحميل المباشر عبر الرابط الثابت التالي:

```text
https://storage.googleapis.com/sirb-ennujub.firebasestorage.app/apk/app-release.apk
```

*(أو يمكن نسخه يدوياً من Firebase Console > Storage > apk > app-release.apk).*

---

## 4. إطلاق التحديث في Firestore (settings/app_update)
لكي تظهر رسالة التحديث لجميع القادة عند فتح التطبيق:
1. افتح [Firebase Console](https://console.firebase.google.com/) > **Firestore Database**.
2. ادخل إلى المجموعة: `settings`.
3. افتح المستند: `app_update`.
4. حدّث الحقول التالية:
   - **`latestVersionCode`** (نوع `number`): رقم الإصدار الجديد (مثلاً `2`).
   - **`latestVersionName`** (نوع `string`): اسم الإصدار الظاهر للمستخدم (مثلاً `"1.1"`).
   - **`apkUrl`** (نوع `string`):
     `https://storage.googleapis.com/sirb-ennujub.firebasestorage.app/apk/app-release.apk`
   - **`changelog`** (نوع `string`): ملخص ما تم إضافته أو إصلاحه في هذا التحديث.
5. احفظ التعديلات. بمجرد فتح التطبيق، سيظهر حوار التحديث التلقائي للقادة ويبدأ التحميل والتثبيت بضغطة زر.

---

## 5. زيادة رقم الإصدار للتحديثات اللاحقة
قبل كل تحديث قادم، افتح `app/build.gradle.kts`:
```kotlin
defaultConfig {
    ...
    versionCode = 2 // زِد الرقم في كل تحديث (2 ثم 3 ثم 4...)
    versionName = "1.1" // اسم الإصدار الجديد
}
```
ثم قم بالدفع (Push) إلى GitHub ليتولى سير العمل كل شيء تلقائياً!
