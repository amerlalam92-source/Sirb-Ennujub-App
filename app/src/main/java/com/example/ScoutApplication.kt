package com.example

import android.app.Application
import android.util.Log
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import com.google.firebase.FirebaseApp

/**
 * فئة التطبيق الرئيسية (Scout Application Class)
 * تقوم بتهيئة خدمات Firebase وإعداد حاوية التبعيات (Dependency Injection) ومسجل الأعطال العام (Crash Logger).
 */
class ScoutApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // مسجل الأعطال العام لمنع الأعطال الصامتة وتتبع الاستثناءات غير المعالجة
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("APP_CRASH", "FATAL EXCEPTION on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // تهيئة Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (_: Exception) {
            // يتم استخدام التهيئة الافتراضية
        }

        // تهيئة حاوية التبعيات
        container = DefaultAppContainer(this)
    }
}
