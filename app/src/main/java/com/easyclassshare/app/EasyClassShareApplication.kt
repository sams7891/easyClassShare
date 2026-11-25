package com.easyclassshare.app

import android.app.Application
import android.content.Context
import com.easyclassshare.app.online.ClassRepository
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.service.SoundEnforcer
import com.easyclassshare.app.settings.AppSettings

class EasyClassShareApplication : Application() {
    override fun attachBaseContext(base: Context) {
        // Chosen app language (Settings) also for texts made outside the screens.
        super.attachBaseContext(AppSettings.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        LocalSetup.init(this)
        ClassRepository.init(this)
        // If the tablet left its class while muted (e.g. the app was updated), give the sound back.
        if (!LocalSetup.isStudentDevice) SoundEnforcer(this).restore()
    }
}

/** Whether a screen of the app is visible, so new links chime instead of showing a notification. */
object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
}
