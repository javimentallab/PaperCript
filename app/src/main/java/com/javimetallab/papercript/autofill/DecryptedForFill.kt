package com.javimetallab.papercript.autofill

import android.content.Context
import android.os.SystemClock
import com.javimetallab.papercript.R

/**
 * The password just decrypted in the app, kept in memory for a short while so
 * the next password field can be filled with it without scanning the card again.
 *
 * Memory only, like everything else in PaperCript: never written anywhere, gone
 * when the process dies. It is handed over once and then forgotten.
 */
object DecryptedForFill {

    const val WINDOW_SECONDS = 120

    /** Replaceable in tests, which run without Android. */
    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    private var password: String? = null
    private var label: String = ""
    private var deadline = 0L

    @Synchronized
    fun set(password: String, label: String) {
        this.password = password
        this.label = label
        deadline = clock() + WINDOW_SECONDS * 1000L
    }

    @Synchronized
    fun clear() {
        password = null
        label = ""
    }

    /** True while there is one waiting; does not hand it over. */
    @Synchronized
    fun isReady(): Boolean {
        if (password != null && clock() > deadline) clear()
        return password != null
    }

    /** Hands the password over once and forgets it. */
    @Synchronized
    fun take(): String? {
        if (!isReady()) return null
        return password.also { clear() }
    }

    /** What the suggestion under the field says: the card's name when known. */
    @Synchronized
    fun suggestion(context: Context): String =
        if (label.isNotBlank()) context.getString(R.string.autofill_fill_card, label)
        else context.getString(R.string.autofill_fill_decrypted)
}
