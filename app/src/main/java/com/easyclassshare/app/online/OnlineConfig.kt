package com.easyclassshare.app.online

import android.content.Context
import com.easyclassshare.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/** Firebase settings baked in at build time from app/google-services.json (see README). */
object OnlineConfig {
    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_API_KEY.isNotEmpty() &&
            BuildConfig.FIREBASE_APP_ID.isNotEmpty() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotEmpty() &&
            BuildConfig.FIREBASE_DATABASE_URL.isNotEmpty()

    @Synchronized
    fun firebaseApp(context: Context): FirebaseApp {
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL)
            .build()
        return FirebaseApp.initializeApp(context.applicationContext, options)
    }
}
