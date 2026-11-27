package com.easyclassshare.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.easyclassshare.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release on GitHub. */
data class UpdateInfo(val version: String, val apkUrl: String, val apkSize: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState

    /** [progress] from 0 to 1, or null while the size is unknown. */
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState

    /** Downloaded; Android's installer was opened (or the "install unknown apps" switch). */
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data object Failed : UpdateState
}

/**
 * Updates the app from the project's GitHub releases (the same APKs as on the Releases page).
 *
 * Android never lets a normal app replace itself silently: the app downloads the new APK and
 * opens Android's installer, where someone taps "Update". The first time, Android also asks
 * to allow Easy Class Share to install apps. The new APK has to be signed with the same key
 * as the installed one, which is true for every release built by GitHub Actions.
 */
object AppUpdater {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/sams7891/easyClassShare/releases/latest"
    private const val PREFS_NAME = "app_updates"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_SNOOZED_VERSION = "snoozed_version"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60_000L
    private const val TIMEOUT_MS = 15_000

    // Lazy, so the unit tests can use the parsing without Android's main thread.
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val mutex = Mutex()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /**
     * Checks GitHub if the last check was more than a few hours ago. Returns the newer
     * release if there is one, else null (also when it isn't time yet or the check failed).
     */
    suspend fun checkIfDue(context: Context): UpdateInfo? {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return null
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        return check()
    }

    /** Checks now (the Settings button). Never throws; the result is in [state]. */
    suspend fun check(): UpdateInfo? = mutex.withLock {
        val busy = _state.value
        if (busy is UpdateState.Downloading) return@withLock busy.info
        _state.value = UpdateState.Checking
        try {
            val info = withContext(Dispatchers.IO) { fetchLatest() }
            _state.value = if (info != null) UpdateState.Available(info) else UpdateState.UpToDate
            info
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed
            null
        }
    }

    /** Fire-and-forget version of [checkIfDue], for when the app opens. */
    fun checkInBackgroundIfDue(context: Context) {
        val app = context.applicationContext
        scope.launch { checkIfDue(app) }
    }

    fun checkInBackground() {
        scope.launch { check() }
    }

    /** "Later" in the update question: don't ask again for this version on this device. */
    fun snooze(context: Context, info: UpdateInfo) {
        prefs(context).edit().putString(KEY_SNOOZED_VERSION, info.version).apply()
    }

    fun isSnoozed(context: Context, info: UpdateInfo): Boolean =
        prefs(context).getString(KEY_SNOOZED_VERSION, null) == info.version

    /** Downloads the APK and opens Android's installer. */
    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        val app = context.applicationContext
        if (_state.value is UpdateState.Downloading) return
        scope.launch {
            _state.value = UpdateState.Downloading(info, null)
            val file = try {
                withContext(Dispatchers.IO) {
                    download(app, info) { progress -> _state.value = UpdateState.Downloading(info, progress) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed
                return@launch
            }
            _state.value = UpdateState.ReadyToInstall(info, file)
            install(app, file)
        }
    }

    /**
     * Opens Android's installer for a downloaded APK. If the app may not install apps yet,
     * opens that switch instead; tapping "Install" again afterwards continues.
     */
    fun install(context: Context, file: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            val allow = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (start(context, allow)) return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(context, intent)) _state.value = UpdateState.Failed
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** The newest release on GitHub if it is newer than this app and has an APK, else null. */
    private fun fetchLatest(): UpdateInfo? {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("GitHub answered ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return parseRelease(json, currentVersion)
        } finally {
            connection.disconnect()
        }
    }

    private fun download(context: Context, info: UpdateInfo, onProgress: (Float?) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "easyClassShare-${info.version}.apk")
        val partial = File(dir, "download.part")
        // GitHub sends the file from another address; HttpURLConnection follows https -> https.
        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Download answered ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.apkSize.takeIf { it > 0 }
            var done = 0L
            var lastShown = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastShown) {
                                lastShown = percent
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (total != null && done != total) throw IllegalStateException("Download incomplete")
        } finally {
            connection.disconnect()
        }
        if (!partial.renameTo(target)) throw IllegalStateException("Could not save the download")
        return target
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Reads a GitHub "latest release" answer. Internal for the tests. */
    internal fun parseRelease(json: JSONObject, current: String): UpdateInfo? {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val version = json.optString("tag_name").removePrefix("v").ifBlank { return null }
        if (!isNewer(version, current)) return null
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val url = asset.optString("browser_download_url")
            if (asset.optString("name").endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                return UpdateInfo(version, url, asset.optLong("size"))
            }
        }
        return null
    }

    /**
     * True if [candidate] (e.g. "1.4.0") is a newer version than [current] ("1.3.1").
     * Pre-release versions ("1.4.0-beta.1") are never offered.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        if ('-' in candidate) return false
        val a = parts(candidate) ?: return false
        val b = parts(current.substringBefore('-')) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        // Same numbers: a release is newer than a pre-release of it (1.4.0 after 1.4.0-beta.1).
        return '-' in current
    }

    private fun parts(version: String): List<Int>? =
        version.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }.ifEmpty { null }
}
