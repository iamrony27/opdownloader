package com.opdownloader.app.ui

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Receives links shared into the app from the Android share sheet. */
object ShareInbox {
    var pending by mutableStateOf<String?>(null)
        private set

    fun consume(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_VIEW) return
        val raw = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.dataString
            ?: return
        val match = Regex("""https?://\S+""").find(raw)?.value ?: return
        pending = match.trim().trimEnd('.', ',', ')', ']')
    }

    fun clear() {
        pending = null
    }
}
