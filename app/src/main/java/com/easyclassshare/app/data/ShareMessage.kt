package com.easyclassshare.app.data

import org.json.JSONObject
import java.util.UUID

/** One thing the sender shares with the class (a link or a short message). */
data class ShareMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val isLink: Boolean get() = LinkUtils.looksLikeLink(content)

    fun toJson(): String = JSONObject()
        .put(KEY_APP, APP_TAG)
        .put(KEY_VERSION, PROTOCOL_VERSION)
        .put(KEY_ID, id)
        .put(KEY_SENDER, senderName)
        .put(KEY_CONTENT, content)
        .put(KEY_TIME, timestamp)
        .toString()

    companion object {
        const val APP_TAG = "easyClassShare"
        const val PROTOCOL_VERSION = 2
        const val MAX_CONTENT_LENGTH = 2000
        const val MAX_NAME_LENGTH = 40

        private const val KEY_APP = "app"
        private const val KEY_VERSION = "v"
        private const val KEY_ID = "id"
        private const val KEY_SENDER = "from"
        private const val KEY_CONTENT = "content"
        private const val KEY_TIME = "ts"

        /** Parses a packet; returns null for anything that isn't a valid message from this app. */
        fun fromJson(raw: String): ShareMessage? {
            return try {
                val json = JSONObject(raw)
                if (json.optString(KEY_APP) != APP_TAG) return null
                val id = json.optString(KEY_ID)
                val content = json.optString(KEY_CONTENT)
                if (id.isBlank() || content.isBlank()) return null
                if (content.length > MAX_CONTENT_LENGTH) return null
                ShareMessage(
                    id = id,
                    senderName = json.optString(KEY_SENDER).take(MAX_NAME_LENGTH),
                    content = content,
                    timestamp = json.optLong(KEY_TIME, System.currentTimeMillis()),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
