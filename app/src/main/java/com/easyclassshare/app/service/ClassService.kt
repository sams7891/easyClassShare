package com.easyclassshare.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.easyclassshare.app.AppVisibility
import com.easyclassshare.app.online.ClassRepository
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Membership
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs on student tablets after the one-time setup, also when the app is in the
 * background: keeps the tablet connected to its class, shows new links as
 * notifications and keeps the volume at zero while a teacher has sound off.
 */
class ClassService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var enforcer: SoundEnforcer
    private lateinit var overlay: AttentionOverlay
    private var classIds: List<String> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    /** The context before the app language was applied, to apply a newly chosen one. */
    private lateinit var plainBase: Context

    /** Texts for notifications, in the language currently chosen in Settings. */
    private lateinit var texts: Context
    private var soundAllowedNow = true

    override fun attachBaseContext(base: Context) {
        plainBase = base
        texts = AppSettings.wrap(base)
        super.attachBaseContext(texts)
    }

    override fun onCreate() {
        super.onCreate()
        enforcer = SoundEnforcer(this)
        overlay = AttentionOverlay(this)
        Notifications.createChannels(texts)
        soundAllowedNow = !enforcer.isMuted
        val notification = Notifications.ongoing(texts, soundAllowed = soundAllowedNow)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ONGOING_ID, notification)
        }
        scope.launch {
            // Another language in Settings: show the notifications in it straight away.
            AppSettings.language.drop(1).collect {
                texts = AppSettings.wrap(plainBase)
                Notifications.createChannels(texts)
                Notifications.update(texts, soundAllowedNow)
            }
        }
        scope.launch {
            LocalSetup.memberships
                .map { list -> list.filter { it.role == Role.STUDENT } }
                .distinctUntilChanged { old, new -> old.map { it.classId } == new.map { it.classId } }
                .collectLatest { classes ->
                    if (classes.isEmpty()) {
                        enforcer.restore()
                        stopSelf()
                    } else {
                        runFor(classes)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started again each time the app opens: the "Display over other apps" switch may have changed.
        classIds.forEach { reportCanLock(it) }
        return START_STICKY
    }

    private fun reportCanLock(classId: String) {
        val canLock = AttentionOverlay.canShow(this) || LockService.instance.value != null
        ClassRepository.reportStatus(classId, mapOf("canLock" to canLock))
    }

    /** Keeps the device connected to all its classes at once. */
    private suspend fun runFor(classes: List<Membership>) = coroutineScope {
        classIds = classes.map { it.classId }

        for (membership in classes) {
            val classId = membership.classId
            launch {
                ClassRepository.presence(classId).catch { }.collect { reportCanLock(classId) }
            }
            launch {
                ClassRepository.isStillMember(membership).catch { }.collect { stillMember ->
                    if (!stillMember) LocalSetup.markRemoved(membership)
                }
            }
            launch {
                ClassRepository.messages(classId).catch { }.collect { messages ->
                    val newest = messages.firstOrNull() ?: return@collect
                    val lastAnnounced = LocalSetup.lastAnnounced(classId)
                    LocalSetup.setLastAnnounced(classId, maxOf(lastAnnounced, newest.timestamp))
                    // The first time, just remember where we are: old messages are not "new".
                    if (lastAnnounced == 0L || newest.timestamp <= lastAnnounced) return@collect
                    // While the app is open the message just appears in the inbox.
                    if (!AppVisibility.isForeground) Notifications.newMessage(texts, newest, classId)
                }
            }
        }
        launch {
            // The accessibility lock was switched on or off.
            LockService.instance.collect { classIds.forEach { reportCanLock(it) } }
        }
        launch {
            batteryStatus().collect { (level, charging) ->
                for (classId in classIds) {
                    ClassRepository.reportStatus(classId, mapOf("battery" to level, "charging" to charging))
                }
            }
        }
        launch {
            // "Eyes on me" from any class covers the screen.
            val perClass = classes.map { ClassRepository.attention(it.classId).catch { emit(null) } }
            val anyAttention = combine(perClass) { all -> all.firstOrNull { it != null } }
            try {
                combine(anyAttention, LockService.instance) { attention, lock -> attention to lock }.collect { (attention, lock) ->
                    if (attention == null) {
                        overlay.hide()
                        lock?.hideAttention()
                        Notifications.cancelAttention(texts)
                        return@collect
                    }
                    // The accessibility lock also covers the notification shade; the normal overlay is the fallback.
                    val shown = if (lock != null && lock.showAttention(attention.text, attention.from)) {
                        overlay.hide()
                        true
                    } else {
                        overlay.show(attention.text, attention.from)
                    }
                    if (!shown && !AppVisibility.isForeground) {
                        // No permission to cover the screen: the app shows it when opened.
                        Notifications.attention(texts, attention)
                    }
                }
            } finally {
                overlay.hide()
                LockService.instance.value?.hideAttention()
                Notifications.cancelAttention(texts)
            }
        }
        launch {
            // Muted if any class has sound off for this device.
            val perClass = classes.map { ClassRepository.soundAllowedForMe(it.classId).catch { emit(true) } }
            combine(perClass) { all -> all.all { it } }.distinctUntilChanged().collectLatest { allowed ->
                soundAllowedNow = allowed
                Notifications.update(texts, allowed)
                if (allowed) enforcer.restore() else keepMuted()
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
                    // Backup for devices that don't announce volume changes, and for the volume
                    // slider in the notification shade (the buttons are blocked by LockService).
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

    /** Battery level (0-100, in steps of 5 so it isn't sent all the time) and whether it is charging. */
    private fun batteryStatus(): Flow<Pair<Int, Boolean>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { batteryOf(it)?.let { status -> trySend(status) } }
            }
        }
        val sticky = ContextCompat.registerReceiver(
            this@ClassService,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { batteryOf(it)?.let { status -> trySend(status) } }
        awaitClose { unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun batteryOf(intent: Intent): Pair<Int, Boolean>? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val percent = (level * 100 / scale).coerceIn(0, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return (percent / 5 * 5) to charging
    }

    override fun onDestroy() {
        overlay.hide()
        LockService.instance.value?.hideAttention()
        val stillMine = LocalSetup.memberships.value.filter { it.role == Role.STUDENT }.map { it.classId }
        classIds.filter { it in stillMine }.forEach { ClassRepository.markOffline(it) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val ENFORCE_INTERVAL_MS = 200L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ClassService::class.java))
            } catch (e: IllegalStateException) {
                // Android may refuse to start it from the background; it starts with the app next time.
            }
        }
    }
}
