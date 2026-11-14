package com.example.presentation.common

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R

/**
 * CompositionLocal لتوفير شعار التطبيق المخصص بصيغة Base64 لجميع الشاشات
 */
val LocalAppLogo = staticCompositionLocalOf<String?> { null }

/**
 * مكون لعرض شعار التطبيق ديناميكياً
 * يقرأ الشعار المخصص من [LocalAppLogo] أو المعامل [logoBase64]،
 * وفي حال عدم توفره أو حدوث خطأ في فك التشفير يستخدم الشعار الافتراضي من الموارد [R.drawable.ennujub_logo].
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    logoBase64: String? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    val base64 = logoBase64 ?: LocalAppLogo.current
    val bitmap = remember(base64) {
        if (base64.isNullOrBlank()) null
        else {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.ennujub_logo),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
