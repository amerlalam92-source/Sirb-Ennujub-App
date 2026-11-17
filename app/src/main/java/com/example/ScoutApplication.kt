package com.example

import android.app.Application
import android.util.Log
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * فئة التطبيق الرئيسية (Scout Application Class)
 * تقوم بتهيئة خدمات Firebase و App Check وإعداد حاوية التبعيات (Dependency Injection) ومسجل الأعطال العام (Crash Logger).
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

        // تهيئة Firebase و App Check قبل أي تهيئة لـ Auth أو Firestore
        try {
            FirebaseApp.initializeApp(this)
            if (BuildConfig.DEBUG) {
                AppCheckDebugInstaller.install()
            } else {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } catch (e: Exception) {
            Log.w("ScoutApplication", "Firebase/AppCheck initialization skipped or deferred", e)
        }

        // تهيئة حاوية التبعيات (تحتوي على FirebaseAuth و FirebaseFirestore)
        container = DefaultAppContainer(this)
    }
}
