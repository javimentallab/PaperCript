package com.javimetallab.papercript.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import com.javimetallab.papercript.R

/**
 * Types a decrypted password straight into another app's password field, so it
 * never touches the clipboard, where keyboards and clipboard managers keep a
 * readable copy whatever the app asks.
 *
 * PaperCript stores nothing, so there is nothing to offer up front: the
 * suggestion is a locked entry that opens [AutofillActivity], where the card is
 * decrypted as usual and the password comes back as the dataset to fill.
 */
class PaperCriptAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null || structure.activityComponent?.packageName == packageName) {
            callback.onSuccess(null)
            return
        }

        val fields = passwordFields(structure)
        if (fields.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val response = FillResponse.Builder()

        // Just decrypted in the app: fill with it, no scanning. The value only
        // leaves the app when this suggestion is tapped (it is locked too).
        if (DecryptedForFill.isReady()) {
            response.addDataset(
                lockedDataset(fields, DecryptedForFill.suggestion(this), request.id * 2, useDecrypted = true)
            )
        }
        response.addDataset(
            lockedDataset(fields, getString(R.string.autofill_fill_with), request.id * 2 + 1, useDecrypted = false)
        )

        callback.onSuccess(response.build())
    }

    /**
     * A suggestion with no value: tapping it opens [AutofillActivity], which
     * returns the password. Each one needs its own request code, or the second
     * PendingIntent would replace the first.
     */
    private fun lockedDataset(
        fields: List<AutofillId>,
        text: String,
        requestCode: Int,
        useDecrypted: Boolean
    ): Dataset {
        val intent = Intent(this, AutofillActivity::class.java)
            .putParcelableArrayListExtra(AutofillActivity.EXTRA_FIELDS, ArrayList(fields))
            .putExtra(AutofillActivity.EXTRA_USE_DECRYPTED, useDecrypted)

        // Mutable on purpose: the system adds the request's extras to it.
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val sender = PendingIntent.getActivity(this, requestCode, intent, flags).intentSender

        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_text, text)
        }

        @Suppress("DEPRECATION") // Presentations needs API 33; minSdk is 26.
        return Dataset.Builder(presentation).apply {
            fields.forEach { setValue(it, null) }
            setAuthentication(sender)
        }.build()
    }

    // Nothing is ever saved: PaperCript keeps no passwords.
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun passwordFields(structure: AssistStructure): List<AutofillId> {
        val found = mutableListOf<AutofillId>()
        fun walk(node: AssistStructure.ViewNode) {
            val id = node.autofillId
            if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && isPassword(node)) {
                found += id
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
        return found
    }

    private fun isPassword(node: AssistStructure.ViewNode): Boolean =
        PasswordFields.isPassword(
            autofillHints = node.autofillHints,
            inputType = node.inputType,
            htmlTag = node.htmlInfo?.tag,
            htmlAttributes = node.htmlInfo?.attributes?.map { it.first to it.second }
        )
}
