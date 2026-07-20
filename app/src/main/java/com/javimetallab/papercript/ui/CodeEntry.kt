package com.javimetallab.papercript.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javimetallab.papercript.R

private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

/**
 * Code entry with a bespoke 32-key keypad, to rescue a card whose QR is
 * unreadable by typing the code printed underneath it.
 *
 * Against the system keyboard: it only offers alphabet characters, so an O or
 * an I cannot be typed by mistake, and tapping a box moves the cursor there to
 * change a single character in two taps.
 *
 * @param value code without separators, already normalized.
 * @param cursor edit position, between 0 and value.length.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodeEntry(
    value: String,
    cursor: Int,
    onValueChange: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeCursor = cursor.coerceIn(0, value.length)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // One box per character, plus a trailing one meaning "append" so the
            // cursor can sit past the last character.
            for (index in 0..value.length) {
                val isCursor = index == safeCursor
                val char = value.getOrNull(index)

                Box(
                    Modifier
                        .size(width = 30.dp, height = 40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isCursor) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (isCursor) 2.dp else 0.dp,
                            color = if (isCursor) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onValueChange(value, index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char?.toString() ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Visual break every 4 characters, matching the printed grouping.
                if (index < value.length && (index + 1) % 4 == 0) {
                    Spacer(Modifier.width(10.dp))
                }
            }
        }

        Text(
            stringResource(R.string.entry_status, value.length, safeCursor + 1),
            style = MaterialTheme.typography.bodySmall
        )

        // Keypad: 4 rows of 8.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ALPHABET.chunked(8).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    // Overwrites the current box, or appends when
                                    // the cursor is at the end. Fixing a character
                                    // is then: tap the box, tap the key.
                                    val updated = if (safeCursor < value.length) {
                                        value.substring(0, safeCursor) + key +
                                            value.substring(safeCursor + 1)
                                    } else {
                                        value + key
                                    }
                                    onValueChange(updated, safeCursor + 1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                key.toString(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    if (safeCursor > 0) {
                        val updated = value.substring(0, safeCursor - 1) + value.substring(safeCursor)
                        onValueChange(updated, safeCursor - 1)
                    }
                },
                enabled = safeCursor > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                Text("  " + stringResource(R.string.backspace), Modifier.padding(start = 4.dp))
            }
            TextButton(
                onClick = { onValueChange("", 0) },
                enabled = value.isNotEmpty()
            ) {
                Text(stringResource(R.string.clear))
            }
        }
    }
}
