package com.easyclassshare.app.data

/**
 * Helpers to decide whether a shared text is a web link.
 * Only http/https links are ever opened, so kids can't be sent to
 * odd app schemes like `intent:` or `javascript:`.
 */
object LinkUtils {
    private val withScheme = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    private val bareDomain = Regex(
        "^(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}(:\\d+)?(/\\S*)?$",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeLink(text: String): Boolean {
        val t = text.trim()
        return withScheme.matches(t) || bareDomain.matches(t)
    }

    /** Returns an openable https/http URL, or null if [text] is not a link. */
    fun normalize(text: String): String? {
        if (!looksLikeLink(text)) return null
        val t = text.trim()
        return if (t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true)
        ) t else "https://$t"
    }
}
