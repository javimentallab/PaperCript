package com.javimetallab.papercript.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javimetallab.papercript.R
import com.javimetallab.papercript.crypto.Base32
import com.javimetallab.papercript.qr.QrCodec
import com.javimetallab.papercript.sheet.SheetEntry
import com.javimetallab.papercript.sheet.SheetHtml
import com.javimetallab.papercript.sheet.SheetLayout
import com.javimetallab.papercript.sheet.SheetPrinter
import com.javimetallab.papercript.sheet.SheetViewModel
import java.util.Locale
import kotlin.math.ceil

/**
 * Sheet preview, layout controls and the print button.
 *
 * Cards are drawn with the same columns they will be printed in, so what you
 * see is the real page layout. They are grouped per sheet, because knowing that
 * card 17 spills onto a second page is exactly what one wants to see here.
 */
@Composable
fun SheetScreen(
    model: SheetViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    val entries = model.entries
    val layout = model.layout
    val sheets = ceil(entries.size / layout.cardsPerPage.toFloat()).toInt()

    val noLabel = stringResource(R.string.no_label)
    val printHeaderTitle = stringResource(R.string.print_header_title)
    val printHeaderWarning = stringResource(R.string.print_header_warning)
    val jobName = stringResource(R.string.app_name)

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.confirm_clear_title)) },
            text = { Text(stringResource(R.string.confirm_clear_text, entries.size)) },
            confirmButton = {
                TextButton(onClick = {
                    model.clear()
                    confirmClear = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.sheet_title), style = MaterialTheme.typography.headlineMedium)

        LayoutControls(
            layout = layout,
            onColumns = model::setColumns,
            onRows = model::setRows
        )

        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.sheet_empty, layout.cardsPerPage),
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        Text(
            pluralStringResource(R.plurals.card_count, entries.size, entries.size) +
                " · " +
                pluralStringResource(R.plurals.sheet_count, sheets, sheets),
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            stringResource(R.string.sheet_memory_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        Button(
            onClick = {
                context.findActivity()?.let { activity ->
                    val html = SheetHtml.build(
                        entries = entries.toList(),
                        layout = layout,
                        headerTitle = printHeaderTitle,
                        headerWarning = printHeaderWarning,
                        noLabel = noLabel
                    )
                    SheetPrinter.print(activity, html, jobName)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Print, contentDescription = null)
            Text("  " + stringResource(R.string.print_button), Modifier.padding(start = 4.dp))
        }

        // Walk sheet by sheet, and within each sheet row by row.
        entries.chunked(layout.cardsPerPage).forEachIndexed { pageIndex, page ->
            HorizontalDivider()
            Text(
                stringResource(R.string.sheet_page, pageIndex + 1),
                style = MaterialTheme.typography.titleSmall
            )

            page.chunked(layout.columns).forEachIndexed { rowIndex, row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEachIndexed { columnIndex, entry ->
                        // The position is computed, not looked up with indexOf:
                        // two identical entries would always return the first and
                        // the wrong card would be deleted.
                        val globalIndex = pageIndex * layout.cardsPerPage +
                            rowIndex * layout.columns +
                            columnIndex

                        CardPreview(
                            entry = entry,
                            columns = layout.columns,
                            noLabel = noLabel,
                            onRemove = { model.removeAt(globalIndex) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Spacer so an incomplete row does not stretch its cards.
                    repeat(layout.columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { confirmClear = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.clear_sheet))
        }
    }
}

/**
 * Columns and rows per sheet. Card and QR sizes derive from A4 geometry, so
 * they are shown in real millimetres: it is the only way to judge how many
 * actually fit.
 */
@Composable
private fun LayoutControls(
    layout: SheetLayout,
    onColumns: (Int) -> Unit,
    onRows: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.layout_title), style = MaterialTheme.typography.titleSmall)

        Text(stringResource(R.string.layout_columns, layout.columns))
        Slider(
            value = layout.columns.toFloat(),
            onValueChange = { onColumns(it.toInt()) },
            valueRange = SheetLayout.COLUMN_RANGE.first.toFloat()..
                SheetLayout.COLUMN_RANGE.last.toFloat(),
            steps = SheetLayout.COLUMN_RANGE.count() - 2,
            modifier = Modifier.fillMaxWidth()
        )

        Text(stringResource(R.string.layout_rows, layout.rows))
        Slider(
            value = layout.rows.toFloat(),
            onValueChange = { onRows(it.toInt()) },
            valueRange = SheetLayout.ROW_RANGE.first.toFloat()..
                SheetLayout.ROW_RANGE.last.toFloat(),
            steps = SheetLayout.ROW_RANGE.count() - 2,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            stringResource(
                R.string.layout_summary,
                layout.cardsPerPage,
                mm(layout.cardWidthMm),
                mm(layout.cardHeightMm),
                mm(layout.qrSideMm)
            ),
            style = MaterialTheme.typography.bodySmall
        )

        if (!layout.isQrComfortable) {
            Text(
                stringResource(
                    R.string.layout_qr_small,
                    mm(layout.qrSideMm),
                    mm(SheetLayout.MIN_COMFORTABLE_QR_MM)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun mm(value: Double): String = String.format(Locale.getDefault(), "%.0f", value)

/**
 * A card as it will be printed.
 *
 * The QR is generated once per entry; regenerating it on every recomposition
 * would throw work away on every scroll.
 */
@Composable
private fun CardPreview(
    entry: SheetEntry,
    columns: Int,
    noLabel: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qr = remember(entry) {
        QrCodec.bitmap(QrCodec.payload(entry.label, entry.rawCode), sizePx = 240)
            .asImageBitmap()
    }

    // Everything scales with the column count: at six per sheet each card is
    // about 55 dp wide and has to be tight; at two there is room to spare.
    val labelSp = (36f / columns).coerceIn(7f, 15f).sp
    val codeSp = (18f / columns).coerceIn(3f, 8f).sp
    val closeSize = (80f / columns).coerceIn(18f, 34f).dp

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = entry.label.ifEmpty { noLabel },
                fontSize = labelSp,
                lineHeight = labelSp * 1.25f,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Fixed white background: in dark theme a QR on grey does not scan,
            // and this previews something destined for white paper.
            Image(
                bitmap = qr,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.White)
                    .padding(2.dp)
            )

            Text(
                text = Base32.group(entry.rawCode),
                fontFamily = FontFamily.Monospace,
                fontSize = codeSp,
                lineHeight = codeSp * 1.4f,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(closeSize)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.remove),
                modifier = Modifier.size(closeSize * 0.6f)
            )
        }
    }
}

/** The print system needs an Activity, not just any context. */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
