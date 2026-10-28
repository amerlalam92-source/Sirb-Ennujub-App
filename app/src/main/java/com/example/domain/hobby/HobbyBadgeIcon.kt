package com.example.domain.hobby

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * رموز شارات الهواية المتاحة في الكتالوج
 */
enum class HobbyBadgeIcon(val key: String, val labelAr: String) {
    TOOLS("tools", "أدوات وحرف"),
    FOOD("food", "طهي وتغذية"),
    ART("art", "فنون ورسم"),
    SPORT("sport", "رياضة ولياقة"),
    MEDICAL("medical", "إسعاف وصحة"),
    BOOK("book", "مطالعة وقراءة"),
    COMPUTER("computer", "حاسوب وبرمجة"),
    MUSIC("music", "موسيقى وأناشيد"),
    PLANT("plant", "بيئة وزراعة"),
    ROPE("rope", "عقد وحبال"),
    MAP("map", "خرائط وملاحة"),
    SIGNAL("signal", "إشارات واتصال"),
    OTHER("other", "عامة ومتنوعة");

    companion object {
        fun fromKey(key: String?): HobbyBadgeIcon =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OTHER

        fun resolveIcon(key: String?): ImageVector {
            return when (fromKey(key)) {
                TOOLS -> Icons.Default.Build
                FOOD -> Icons.Default.Restaurant
                ART -> Icons.Default.Palette
                SPORT -> Icons.Default.SportsSoccer
                MEDICAL -> Icons.Default.LocalHospital
                BOOK -> Icons.Default.MenuBook
                COMPUTER -> Icons.Default.Computer
                MUSIC -> Icons.Default.MusicNote
                PLANT -> Icons.Default.Park
                ROPE -> Icons.Default.AllInclusive
                MAP -> Icons.Default.Map
                SIGNAL -> Icons.Default.Sensors
                OTHER -> Icons.Default.MilitaryTech
            }
        }
    }
}
