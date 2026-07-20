package com.javimetallab.papercript.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An expiring clipboard, as far as Android allows.
 *
 * Since Android 10 the system denies clipboard access to any app without focus
 * ("Denying clipboard access ... application is not in focus"). A timer firing
 * while the user is in another app pasting the password clears nothing: the
 * call is ignored without warning.
 *
 * Hence two complementary paths:
 *  - [copy] schedules a timed clear, useful only if the app is still in the
 *    foreground when it fires.
 *  - [clearIfExpired] runs on returning to the app, which is when focus, and
 *    therefore permission, comes back. It covers the usual case: copy, leave to
 *    paste, come back.
 *
 * One gap is unavoidable: if you never return to the app, the password stays on
 * the clipboard until the system clears it on its own. That is what [clearNow]
 * is for.
 */
object SecureClipboard {

    const val CLEAR_AFTER_SECONDS = 45

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    /** The last thing we copied, while it is still pending a clear. */
    private var trackedText: String? = null
    private var deadlineElapsed: Long = 0

    fun copy(context: Context, text: String) {
        val clipboard = clipboardOf(context)

        val clip = ClipData.newPlainText("", text).apply {
            // Literal strings instead of ClipDescription.EXTRA_IS_SENSITIVE
            // (API 33), to avoid a version check: older ROMs just ignore them.
            // They keep the content out of the system's clipboard preview.
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
                putBoolean("androidx.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)

        trackedText = text
        deadlineElapsed = SystemClock.elapsedRealtime() + CLEAR_AFTER_SECONDS * 1000L

        pending?.cancel()
        pending = scope.launch {
            delay(CLEAR_AFTER_SECONDS * 1000L)
            clearIfOurs(clipboard)
        }
    }

    /**
     * Clears if the deadline has passed. Called on regaining focus, the only
     * moment the system lets us touch the clipboard.
     */
    fun clearIfExpired(context: Context) {
        if (trackedText == null) return
        if (SystemClock.elapsedRealtime() < deadlineElapsed) return
        clearIfOurs(clipboardOf(context))
    }

    /** Immediate clear, at the user's request. */
    fun clearNow(context: Context) {
        clearIfOurs(clipboardOf(context))
    }

    /** Whether something of ours is still pending a clear. */
    fun hasPendingCopy(): Boolean = trackedText != null

    /**
     * Only clears if what is there is still what we copied: if the user has
     * copied something else meanwhile, it is none of our business.
     */
    private fun clearIfOurs(clipboard: ClipboardManager) {
        val text = trackedText ?: return

        // Returns null when we lack focus, indistinguishable from an empty
        // clipboard, so leave it for the next attempt rather than assuming it
        // is already clean.
        val current = clipboard.primaryClip ?: return
        if (current.itemCount == 0) return
        if (current.getItemAt(0).text?.toString() != text) {
            trackedText = null
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        trackedText = null
    }

    // Application context: this object outlives the Activity, and holding on to
    // the Activity's would retain the whole thing.
    private fun clipboardOf(context: Context): ClipboardManager =
        context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
