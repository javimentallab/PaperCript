package com.javimetallab.papercript.autofill

import android.text.InputType

/**
 * Whether a field of another app's screen takes a password. Plain values in,
 * so it can be tested without a device.
 */
object PasswordFields {

    fun isPassword(
        autofillHints: Array<String>?,
        inputType: Int,
        htmlTag: String?,
        htmlAttributes: List<Pair<String, String>>?
    ): Boolean {
        if (autofillHints?.any { it.contains("password", ignoreCase = true) } == true) {
            return true
        }

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> if (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) return true

            InputType.TYPE_CLASS_NUMBER ->
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return true
        }

        // Web pages inside browsers that support autofill.
        return htmlTag.equals("input", ignoreCase = true) &&
            htmlAttributes?.any {
                it.first.equals("type", ignoreCase = true) &&
                    it.second.equals("password", ignoreCase = true)
            } == true
    }
}
