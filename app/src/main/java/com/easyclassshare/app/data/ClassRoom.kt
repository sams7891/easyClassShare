package com.easyclassshare.app.data

import androidx.annotation.StringRes
import com.easyclassshare.app.R

/**
 * A "class channel". The sender and all receivers pick the same animal,
 * so two classes on the same Wi-Fi don't get each other's messages.
 */
enum class ClassRoom(val id: String, val emoji: String, @StringRes val label: Int) {
    FOX("fox", "🦊", R.string.room_fox),
    PANDA("panda", "🐼", R.string.room_panda),
    FROG("frog", "🐸", R.string.room_frog),
    LION("lion", "🦁", R.string.room_lion),
    OCTOPUS("octopus", "🐙", R.string.room_octopus),
    UNICORN("unicorn", "🦄", R.string.room_unicorn);

    companion object {
        fun fromId(id: String?): ClassRoom = entries.firstOrNull { it.id == id } ?: FOX
    }
}
