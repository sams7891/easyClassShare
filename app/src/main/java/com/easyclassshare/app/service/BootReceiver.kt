package com.easyclassshare.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Role

/** Reconnects student tablets to their class after a restart or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        LocalSetup.init(context)
        if (LocalSetup.membership.value?.role == Role.STUDENT) ClassService.start(context)
    }
}
