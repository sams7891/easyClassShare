package com.easyclassshare.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.easyclassshare.app.AppVisibility
import com.easyclassshare.app.online.ClassRepository
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Membership
import com.easyclassshare.app.online.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Runs on student tablets after the one-time setup, also when the app is in the
 * background: keeps the tablet connected to its class, shows new links as
 * notifications and keeps the volume at zero while a teacher has sound off.
 */
class ClassService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var enforcer: SoundEnforcer
    private var tone: ToneGenerator? = null
    private var classId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        enforcer = SoundEnforcer(this)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
        Notifications.createChannels(this)
        val notification = Notifications.ongoing(this, soundAllowed = !enforcer.isMuted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ONGOING_ID, notification)
        }
        scope.launch {
            LocalSetup.membership.collectLatest { membership ->
                if (membership == null || membership.role != Role.STUDENT) {
                    enforcer.restore()
                    stopSelf()
                } else {
                    runFor(membership)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private suspend fun runFor(membership: Membership) = coroutineScope {
        classId = membership.classId

        launch {
            ClassRepository.presence(membership.classId).catch { }.collect { }
        }
        launch {
            ClassRepository.isStillMember(membership).catch { }.collect { stillMember ->
                if (!stillMember) LocalSetup.markRemoved(membership)
            }
        }
        launch {
            ClassRepository.soundAllowedForMe(membership.classId).catch { }.collectLatest { allowed ->
                Notifications.update(this@ClassService, allowed)
                if (allowed) enforcer.restore() else keepMuted()
            }
        }
        launch {
            ClassRepository.messages(membership.classId).catch { }.collect { messages ->
                val newest = messages.firstOrNull() ?: return@collect
                val lastAnnounced = LocalSetup.lastAnnouncedMessageTime
                LocalSetup.lastAnnouncedMessageTime = maxOf(lastAnnounced, newest.timestamp)
                // The first time, just remember where we are: old messages are not "new".
                if (lastAnnounced == 0L || newest.timestamp <= lastAnnounced) return@collect
                if (AppVisibility.isForeground) {
                    tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                } else {
                    Notifications.newMessage(this@ClassService, newest)
                }
            }
        }
    }

    /** Mutes now and keeps putting the volume back to zero until cancelled. */
    private suspend fun keepMuted() {
        enforcer.mute()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                enforcer.enforce()
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            coroutineScope {
                launch {
                    // Backup for devices that don't announce volume changes.
                    while (true) {
                        enforcer.enforce()
                        delay(ENFORCE_INTERVAL_MS)
                    }
                }
                awaitCancellation()
            }
        } finally {
            unregisterReceiver(receiver)
        }
    }

    override fun onDestroy() {
        classId?.let { id -> if (LocalSetup.membership.value?.classId == id) ClassRepository.markOffline(id) }
        scope.cancel()
        tone?.release()
        super.onDestroy()
    }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val ENFORCE_INTERVAL_MS = 1000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ClassService::class.java))
            } catch (e: IllegalStateException) {
                // Android may refuse to start it from the background; it starts with the app next time.
            }
        }
    }
}
