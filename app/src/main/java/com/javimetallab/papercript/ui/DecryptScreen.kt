package com.javimetallab.papercript.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.javimetallab.papercript.R
import com.javimetallab.papercript.crypto.SecretCodec
import com.javimetallab.papercript.security.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * QR -> master password -> password.
 *
 * The QR is the normal path: it carries error correction, so it either decodes
 * whole and correct or not at all. The 32-key keypad is the rescue for a card
 * whose QR is damaged.
 */
@Composable
fun DecryptScreen(
    hasCameraPermission: Boolean,
    onRequestCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scanningQr by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var cursor by remember { mutableIntStateOf(0) }
    // Informational only: comes from the QR so you know which card you scanned.
    var scannedLabel by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }
    var password by remember { mutableStateOf<String?>(null) }
    // The result is stored, not a message: the text is resolved further down,
    // inside the composable, which is where resources can be read.
    var failure by remember { mutableStateOf<SecretCodec.DecryptResult?>(null) }
    var working by remember { mutableStateOf(false) }
    var scanNotice by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val qrNotice = stringResource(R.string.read_from_qr)

    // Same reason as on the encrypt screen: the result appears below the visible
    // edge and it looks as if nothing happened.
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(password) {
        if (password != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    if (scanningQr) {
        QrScanScreen(
            onResult = { qrLabel, qrCode ->
                code = qrCode
                cursor = 0
                scannedLabel = qrLabel
                scanNotice = qrNotice
                password = null
                failure = null
                scanningQr = false
            },
            onCancel = { scanningQr = false },
            modifier = modifier
        )
        return
    }

    fun invalidate() {
        password = null
        failure = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.decrypt_title), style = MaterialTheme.typography.headlineMedium)

        // The QR comes first because it is the reliable path: it either reads
        // correctly or not at all, with no half-readings to review.
        Button(
            onClick = { if (hasCameraPermission) scanningQr = true else onRequestCamera() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (hasCameraPermission) stringResource(R.string.scan_qr_button)
                else stringResource(R.string.grant_camera)
            )
        }

        scanNotice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        CodeEntry(
            value = code,
            cursor = cursor,
            onValueChange = { newValue, newCursor ->
                code = newValue
                cursor = newCursor
                invalidate()
            }
        )

        if (code.isNotEmpty() && !SecretCodec.looksComplete(code)) {
            Text(
                stringResource(R.string.looks_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider()

        if (scannedLabel.isNotEmpty()) {
            Text(
                stringResource(R.string.card_named, scannedLabel),
                style = MaterialTheme.typography.titleMedium
            )
        }

        SecretField(
            value = master,
            onValueChange = { master = it; invalidate() },
            label = stringResource(R.string.master_field)
        )

        Button(
            onClick = {
                scope.launch {
                    working = true
                    failure = null
                    password = null
                    val result = withContext(Dispatchers.Default) {
                        SecretCodec.decrypt(code, master)
                    }
                    if (result is SecretCodec.DecryptResult.Success) {
                        password = result.password
                    } else {
                        failure = result
                    }
                    working = false
                }
            },
            enabled = !working && code.isNotEmpty() && master.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (working) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp),
                    strokeWidth = 2.dp
                )
                Text(stringResource(R.string.deriving_key))
            } else {
                Text(stringResource(R.string.decrypt_button))
            }
        }

        failure?.let { result ->
            val message = when (result) {
                SecretCodec.DecryptResult.BadKeyOrCorrupt ->
                    stringResource(R.string.error_bad_key)

                is SecretCodec.DecryptResult.Malformed -> when (result.problem) {
                    SecretCodec.Problem.EMPTY_MASTER ->
                        stringResource(R.string.error_empty_master)

                    SecretCodec.Problem.TOO_SHORT ->
                        stringResource(R.string.error_too_short)

                    SecretCodec.Problem.INVALID_CHARACTER ->
                        stringResource(R.string.error_invalid_character, result.detail.orEmpty())

                    SecretCodec.Problem.UNSUPPORTED_VERSION ->
                        stringResource(R.string.error_unsupported_version, result.detail.orEmpty())
                }

                is SecretCodec.DecryptResult.Success -> null
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        password?.let {
            ResultCard(
                title = stringResource(R.string.password_result),
                value = it,
                monospace = false,
                hidden = true,
                footer = stringResource(
                    R.string.clipboard_cleared,
                    SecureClipboard.CLEAR_AFTER_SECONDS
                )
            )
        }
    }
}
