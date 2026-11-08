package com.example.aniflow.data.update

import android.content.Context
import java.util.UUID

/** [UpdateChannel.StateStore] in plain SharedPreferences (tiny, read once per check). */
class UpdatePrefs(context: Context) : UpdateChannel.StateStore {
    private val prefs = context.applicationContext.getSharedPreferences("aniflow_update_channel", Context.MODE_PRIVATE)

    override var highestSequence: Long
        get() = prefs.getLong("highestSequence", 0L)
        set(value) { prefs.edit().putLong("highestSequence", value).apply() }

    override val installId: String
        get() = prefs.getString("installId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installId", it).apply()
        }
}
