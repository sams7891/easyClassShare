package com.easyclassshare.app.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun newerVersions() {
        assertTrue(AppUpdater.isNewer("1.3.2", "1.3.1"))
        assertTrue(AppUpdater.isNewer("1.4.0", "1.3.9"))
        assertTrue(AppUpdater.isNewer("2.0", "1.9.9"))
        assertTrue(AppUpdater.isNewer("1.3.10", "1.3.9"))
        assertTrue(AppUpdater.isNewer("v1.4.0", "1.3.1"))
        // The release after its own beta.
        assertTrue(AppUpdater.isNewer("1.4.0", "1.4.0-beta.1"))
    }

    @Test
    fun notNewer() {
        assertFalse(AppUpdater.isNewer("1.3.1", "1.3.1"))
        assertFalse(AppUpdater.isNewer("1.3.0", "1.3.1"))
        assertFalse(AppUpdater.isNewer("1.3", "1.3.0"))
        assertFalse(AppUpdater.isNewer("1.4.0-beta.1", "1.3.1"))
        assertFalse(AppUpdater.isNewer("latest", "1.3.1"))
        assertFalse(AppUpdater.isNewer("", "1.3.1"))
    }

    private fun release(tag: String, assetName: String = "easyClassShare-$tag.apk", prerelease: Boolean = false) =
        JSONObject(
            """
            {"tag_name": "$tag", "prerelease": $prerelease, "draft": false,
             "assets": [
               {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 10},
               {"name": "$assetName", "browser_download_url": "https://example.com/$assetName", "size": 1234}
             ]}
            """.trimIndent(),
        )

    @Test
    fun parsesNewerRelease() {
        val info = AppUpdater.parseRelease(release("v1.4.0"), current = "1.3.1")
        assertEquals(UpdateInfo("1.4.0", "https://example.com/easyClassShare-v1.4.0.apk", 1234), info)
    }

    @Test
    fun ignoresSameOrOlderReleaseAndPrereleases() {
        assertNull(AppUpdater.parseRelease(release("v1.3.1"), current = "1.3.1"))
        assertNull(AppUpdater.parseRelease(release("v1.4.0", prerelease = true), current = "1.3.1"))
    }

    @Test
    fun needsAnApk() {
        assertNull(AppUpdater.parseRelease(release("v1.4.0", assetName = "source.zip"), current = "1.3.1"))
    }
}
