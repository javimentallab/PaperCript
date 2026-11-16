package com.javimetallab.papercript.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.javimetallab.papercript.R
import com.javimetallab.papercript.autofill.DecryptedForFill

/**
 * Under the decrypted password: the way to fill it without the clipboard. Once
 * PaperCript is the autofill service, just how to use it.
 */
@Composable
fun AutofillHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(AutofillManager::class.java) }
    var enabled by remember { mutableStateOf(manager?.hasEnabledAutofillServices() == true) }

    // Coming back from system Settings.
    LifecycleResumeEffect(manager) {
        enabled = manager?.hasEnabledAutofillServices() == true
        onPauseOrDispose { }
    }

    if (manager == null || !manager.isAutofillSupported) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (enabled) {
                stringResource(
                    R.string.autofill_on,
                    DecryptedForFill.WINDOW_SECONDS,
                    DecryptedForFill.suggestion(context)
                )
            } else {
                stringResource(R.string.autofill_off)
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (!enabled) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                        Uri.parse("package:${context.packageName}")
                    )
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // Some ROMs lack the shortcut: the general settings screen.
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.autofill_enable))
            }
        }
    }
}
