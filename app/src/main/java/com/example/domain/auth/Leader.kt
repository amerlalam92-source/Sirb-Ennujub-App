package com.example.domain.auth

/**
 * بيانات القائد الكشفي المسجل في النظام
 */
data class Leader(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "leader",
    val isMainLeader: Boolean = false,
    val status: String = "pending", // "pending", "approved", "rejected"
    val unitId: String = "default",
    val isSuperAdmin: Boolean = false,
    val joinRequestStatus: String = "approved"
)
