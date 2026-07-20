package com.javimetallab.papercript.sheet

import com.javimetallab.papercript.crypto.Base32
import com.javimetallab.papercript.qr.QrCodec
import java.util.Locale

/**
 * Builds the cut-out A4 sheet from the chosen layout.
 *
 * All geometry comes from [SheetLayout], the same one the preview uses. Changing
 * columns or rows shrinks or grows the cards — and with them the QR — without
 * touching anything here.
 *
 * The gap between rows is not decorative: it leaves room for scissors without
 * eating into the neighbouring card's QR.
 *
 * Each card carries the label, the QR and the code in small print. That text is
 * the only rescue path if a QR ends up damaged: it gets typed back in the app.
 *
 * The HTML is self-contained, with the QR codes embedded as data URIs: the app
 * has no network permission, so the WebView could not load anything external.
 */
object SheetHtml {

    private const val QR_PIXELS = 600

    /**
     * Strings arrive already translated: this class has no Android context from
     * which to read resources.
     */
    fun build(
        entries: List<SheetEntry>,
        layout: SheetLayout,
        headerTitle: String,
        headerWarning: String,
        noLabel: String
    ): String {
        val pages = entries.chunked(layout.cardsPerPage)
        val body = pages.joinToString("\n") { page ->
            renderPage(page, layout, headerTitle, headerWarning, noLabel)
        }

        return """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>PaperCript</title>
<style>
  @page { size: A4; margin: ${mm(SheetLayout.MARGIN)}; }

  * { box-sizing: border-box; }

  body {
    margin: 0;
    font-family: 'JetBrains Mono', 'Roboto Mono', 'DejaVu Sans Mono', monospace;
    color: #000;
    background: #fff;
  }

  .page { page-break-after: always; }
  .page:last-child { page-break-after: auto; }

  .header {
    height: ${mm(SheetLayout.HEADER)};
    font-size: 7pt;
    color: #444;
    display: flex;
    justify-content: space-between;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(${layout.columns}, 1fr);
    column-gap: ${mm(SheetLayout.COLUMN_GAP)};
    row-gap: ${mm(SheetLayout.ROW_GAP)};
  }

  .card {
    height: ${mm(layout.cardHeightMm)};
    border: 1px dashed #888;
    border-radius: 2mm;
    padding: ${mm(SheetLayout.CARD_PADDING)};
    text-align: center;
    break-inside: avoid;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-start;
    overflow: hidden;
  }

  .label {
    height: ${mm(SheetLayout.LABEL_HEIGHT)};
    font-size: ${pt(layout.labelPt)};
    font-weight: 700;
    width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .qr {
    width: ${mm(layout.qrSideMm)};
    height: ${mm(layout.qrSideMm)};
    /* No smoothing: an interpolated QR loses definition in the small modules
       and becomes harder to read. */
    image-rendering: pixelated;
  }

  .code {
    margin-top: 1mm;
    font-size: ${pt(layout.codePt)};
    line-height: 1.25;
    word-break: break-all;
    color: #444;
    overflow: hidden;
  }
</style>
</head>
<body>
$body
</body>
</html>
        """.trimIndent()
    }

    private fun renderPage(
        page: List<SheetEntry>,
        layout: SheetLayout,
        headerTitle: String,
        headerWarning: String,
        noLabel: String
    ): String {
        val cards = page.joinToString("\n") { renderCard(it, layout, noLabel) }

        return """
<div class="page">
  <div class="header">
    <span>${escape(headerTitle)}</span>
    <span>${escape(headerWarning)}</span>
  </div>
  <div class="grid">
$cards
  </div>
</div>
        """.trimIndent()
    }

    private fun renderCard(entry: SheetEntry, layout: SheetLayout, noLabel: String): String {
        val payload = QrCodec.payload(entry.label, entry.rawCode)
        val qr = QrCodec.pngDataUri(payload, QR_PIXELS)
        val label = escape(entry.label.ifEmpty { noLabel })
        val grouped = escape(Base32.group(entry.rawCode))

        return """
  <div class="card">
    <div class="label">$label</div>
    <img class="qr" src="$qr" alt="">
    <div class="code">$grouped</div>
  </div>
        """.trimIndent()
    }

    private fun mm(value: Double): String = format(value) + "mm"

    private fun pt(value: Double): String = format(value) + "pt"

    /** Fixed locale: a decimal comma would break the CSS. */
    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
