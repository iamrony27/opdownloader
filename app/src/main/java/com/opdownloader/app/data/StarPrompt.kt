package com.opdownloader.app.data

import android.content.Context

/**
 * Keeps the "star the repo" nudge tied to real usage: the clock starts on the
 * first launch, the prompt only becomes due after three days, and it never
 * nags more than once a week until the user stars or opts out.
 */
object StarPrompt {
    private const val PREFS = "op_star_prompt"
    private const val KEY_INSTALLED_AT = "installed_at"
    private const val KEY_LAST_SHOWN = "last_shown"
    private const val KEY_DONE = "done"

    private const val ASK_AFTER_DAYS = 3L
    private const val SNOOZE_DAYS = 7L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Records the first launch exactly once. Safe to call on every start. */
    fun ensureInstalled(context: Context, now: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        if (p.getLong(KEY_INSTALLED_AT, 0L) == 0L) {
            p.edit().putLong(KEY_INSTALLED_AT, now).apply()
        }
    }

    /** Number of whole days since the first launch (0 on day one). */
    fun daysInUse(context: Context, now: Long = System.currentTimeMillis()): Int {
        val installedAt = prefs(context).getLong(KEY_INSTALLED_AT, 0L)
        if (installedAt <= 0L) return 0
        return ((now - installedAt) / DAY_MS).toInt().coerceAtLeast(0)
    }

    /** True when the prompt should be shown right now. */
    fun isDue(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_DONE, false)) return false
        val installedAt = p.getLong(KEY_INSTALLED_AT, 0L)
        if (installedAt <= 0L) {
            p.edit().putLong(KEY_INSTALLED_AT, now).apply()
            return false
        }
        if (now < installedAt + ASK_AFTER_DAYS * DAY_MS) return false
        val lastShown = p.getLong(KEY_LAST_SHOWN, 0L)
        if (lastShown > 0L && now < lastShown + SNOOZE_DAYS * DAY_MS) return false
        return true
    }

    /** Call when the dialog is displayed so "Later" snoozes for a week. */
    fun markShown(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_SHOWN, now).apply()
    }

    /** The user starred the repo or asked not to be reminded again. */
    fun markDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).apply()
    }
}
