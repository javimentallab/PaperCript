package com.javimetallab.papercript.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.javimetallab.papercript.R
import com.javimetallab.papercript.security.SecureClipboard
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/** Masked text field with an eye toggle. */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        // The normal Android keyboard, same as the label field.
        // KeyboardType.Password makes several IMEs hide or trim the symbol row,
        // and here the opposite is wanted: every character reachable.
        // Autocorrect is switched off, because "fixing" a password breaks it.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None
        ),
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.hide else R.string.show
                    )
                )
            }
        }
    )
}

/**
 * Shows a highlighted result (the code to copy, or the recovered password)
 * with a copy button.
 */
@Composable
fun ResultCard(
    title: String,
    value: String,
    monospace: Boolean = true,
    hidden: Boolean = false,
    footer: String? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var revealed by remember(value) { mutableStateOf(!hidden) }
    var copied by remember(value) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)

            Text(
                text = if (revealed) value else "•".repeat(value.length.coerceAtMost(24)),
                style = if (monospace) CodeStyle else MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )

            footer?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hidden) {
                    TextButton(onClick = { revealed = !revealed }) {
                        Text(stringResource(if (revealed) R.string.hide else R.string.show))
                    }
                }
                TextButton(onClick = {
                    SecureClipboard.copy(context, value)
                    copied = true
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("  " + stringResource(R.string.copy), Modifier.padding(start = 4.dp))
                }

                // Manual clear: the only immediate and always-reliable path,
                // because from here the app has focus.
                if (copied) {
                    TextButton(onClick = {
                        SecureClipboard.clearNow(context)
                        copied = false
                    }) {
                        Text(stringResource(R.string.clipboard_clear_now))
                    }
                }
            }
        }
    }
}

