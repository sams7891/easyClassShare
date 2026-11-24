package com.easyclassshare.app

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.net.ClassNetwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** HOME/SEND/RECEIVE: same-Wi-Fi mode. SETUP/CLASS/MANAGE: online classes. */
enum class Screen { HOME, SEND, RECEIVE, SETUP, CLASS, MANAGE, SCHOOL }

/** [id] makes every result unique so the UI can react to the same outcome twice. */
data class SendResult(val success: Boolean, val id: Long = System.nanoTime())

class ClassShareViewModel(application: Application) : AndroidViewModel(application) {
    private val network = ClassNetwork(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val seenIds = HashSet<String>()
    private var listenJob: Job? = null
    private val tone: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()

    var screen by mutableStateOf(Screen.HOME)

    var senderName by mutableStateOf(prefs.getString(KEY_NAME, null).orEmpty())
        private set

    // ---- Sender state ----
    var draft by mutableStateOf("")
        private set
    var isSending by mutableStateOf(false)
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set
    val sent = mutableStateListOf<ShareMessage>()

    // ---- Receiver state ----
    var isListening by mutableStateOf(false)
        private set
    var receiveError by mutableStateOf(false)
        private set
    val received = mutableStateListOf<ShareMessage>()

    fun updateSenderName(name: String) {
        val value = name.take(ShareMessage.MAX_NAME_LENGTH)
        senderName = value
        prefs.edit().putString(KEY_NAME, value).apply()
    }

    fun updateDraft(text: String) {
        draft = text.take(ShareMessage.MAX_CONTENT_LENGTH)
    }

    fun clearSendResult(result: SendResult) {
        if (sendResult == result) sendResult = null
    }

    fun sendDraft() {
        val content = draft.trim()
        if (content.isEmpty()) return
        val message = ShareMessage(senderName = senderName.trim(), content = content)
        broadcast(message) {
            sent.add(0, message)
            draft = ""
        }
    }

    /** Re-sends with the same id: tablets that already have it ignore it, late-comers get it. */
    fun resend(message: ShareMessage) = broadcast(message) {}

    private fun broadcast(message: ShareMessage, onSuccess: () -> Unit) {
        if (isSending) return
        isSending = true
        viewModelScope.launch {
            val ok = try {
                network.send(message)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                isSending = false
            }
            if (ok) onSuccess()
            sendResult = SendResult(ok)
        }
    }

    fun startListening() {
        if (listenJob?.isActive == true) return
        receiveError = false
        listenJob = viewModelScope.launch {
            isListening = true
            try {
                network.incoming().collect { onMessage(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                receiveError = true
            } finally {
                isListening = false
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    private fun onMessage(message: ShareMessage) {
        if (!seenIds.add(message.id)) return // duplicate copy
        received.add(0, message)
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    override fun onCleared() {
        tone?.release()
    }

    private companion object {
        const val PREFS_NAME = "easy_class_share"
        const val KEY_NAME = "sender_name"
    }
}
