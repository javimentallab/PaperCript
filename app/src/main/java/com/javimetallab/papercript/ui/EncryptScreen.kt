package com.javimetallab.papercript.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.javimetallab.papercript.R
import com.javimetallab.papercript.crypto.Base32
import com.javimetallab.papercript.crypto.PasswordGenerator
import com.javimetallab.papercript.crypto.SecretCodec
import com.javimetallab.papercript.ocr.TextExtract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Password + master password -> card code.
 *
 * The password can be typed, generated, or read with the camera. That last one
 * does work well with OCR: what gets scanned here is printed text (a router
 * sticker, a letter from the bank), not handwriting.
 */
@Composable
fun EncryptScreen(
    hasCameraPermission: Boolean,
    onRequestCamera: () -> Unit,
    sheetCount: Int,
    onAddToSheet: (label: String, code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var label by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var master by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    var scanning by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var scanNotice by remember { mutableStateOf<String?>(null) }

    var genLength by remember { mutableIntStateOf(PasswordGenerator.DEFAULT_LENGTH) }
    var genSymbols by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val nothingRead = stringResource(R.string.nothing_read)
    val encryptFailed = stringResource(R.string.error_encrypt_failed)

    // The column is tall (label, password, generator, master password), so the
    // result is born below the visible edge and it looks as if nothing happened.
    // When a code appears, scroll down to it.
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(code) {
        if (code != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Any change to the inputs invalidates the previous result, so nobody copies
    // a code that no longer matches what is on screen.
    fun invalidate() {
        code = null
        error = null
    }

    if (scanning) {
        CaptureScreen(
            hint = stringResource(R.string.capture_hint_password),
            guideHeightFraction = 0.45f,
            onResult = { text ->
                val found = TextExtract.passwordCandidates(text)
                candidates = found
                scanNotice = if (found.isEmpty()) nothingRead else null
                scanning = false
            },
            onCancel = { scanning = false },
            modifier = modifier
        )
        return
    }

    if (candidates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { candidates = emptyList() },
            title = { Text(stringResource(R.string.pick_password_title)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.pick_password_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    candidates.forEach { candidate ->
                        Text(
                            text = candidate,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    password = candidate
                                    candidates = emptyList()
                                    invalidate()
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { candidates = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.encrypt_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.encrypt_intro), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = label,
            onValueChange = { label = it; invalidate() },
            label = { Text(stringResource(R.string.label_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(stringResource(R.string.label_field_help)) }
        )

        SecretField(
            value = password,
            onValueChange = { password = it; invalidate() },
            label = stringResource(R.string.password_field)
        )

        OutlinedButton(
            onClick = { if (hasCameraPermission) scanning = true else onRequestCamera() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (hasCameraPermission) stringResource(R.string.scan_password_button)
                else stringResource(R.string.grant_camera)
            )
        }

        scanNotice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        // Generator. No characters are barred for legibility: the password is
        // never transcribed by hand, it travels encrypted inside the QR.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.generator_length, genLength))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.generator_symbols))
                    Switch(
                        checked = genSymbols,
                        onCheckedChange = { genSymbols = it },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Slider(
                value = genLength.toFloat(),
                onValueChange = { genLength = it.toInt() },
                valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..
                    PasswordGenerator.MAX_LENGTH.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(
                        R.string.generator_entropy,
                        PasswordGenerator.entropyBits(genLength, genSymbols)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = {
                    password = PasswordGenerator.generate(genLength, genSymbols)
                    invalidate()
                }) { Text(stringResource(R.string.generate)) }
            }
        }

        SecretField(
            value = master,
            onValueChange = { master = it; invalidate() },
            label = stringResource(R.string.master_field),
            supportingText = stringResource(R.string.master_help)
        )

        Button(
            onClick = {
                scope.launch {
                    working = true
                    error = null
                    try {
                        val result = withContext(Dispatchers.Default) {
                            SecretCodec.encrypt(password, master)
                        }
                        code = result
                    } catch (e: Throwable) {
                        // Throwable, not just Exception, because scrypt asks
                        // for 64 MB at once: an OutOfMemoryError has to reach
                        // the screen rather than die silently.
                        error = encryptFailed.format(e.message ?: e::class.java.simpleName)
                    } finally {
                        working = false
                    }
                }
            },
            enabled = !working && password.isNotEmpty() && master.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (working) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 12.dp),
                    strokeWidth = 2.dp
                )
                Text(stringResource(R.string.deriving_key))
            } else {
                Text(stringResource(R.string.encrypt_button))
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        code?.let { value ->
            ResultCard(
                title = stringResource(R.string.encrypted_code),
                value = value,
                footer = stringResource(R.string.code_footer, Base32.normalize(value).length)
            )

            Button(
                onClick = {
                    onAddToSheet(SecretCodec.sanitizeLabel(label), Base32.normalize(value))
                    // Label and password are cleared but the master password
                    // stays: chaining several in a row is the normal case.
                    label = ""
                    password = ""
                    code = null
                    scanNotice = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_to_sheet))
            }

            Text(
                stringResource(R.string.handwrite_note),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (sheetCount > 0) {
            Text(
                pluralStringResource(R.plurals.cards_pending, sheetCount, sheetCount),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
