package com.javimetallab.papercript.sheet

import kotlin.math.max

/**
 * How cards are laid out on the sheet, chosen by the user.
 *
 * Single source of truth: it drives both the on-screen preview and the printed
 * HTML, so what you see really is what comes out of the printer. All measures
 * are millimetres on A4.
 *
 * Fewer cards per sheet means bigger cards and, above all, bigger QR codes,
 * which is what decides whether they scan well.
 */
data class SheetLayout(
    val columns: Int = 4,
    val rows: Int = 4
) {

    val cardsPerPage: Int get() = columns * rows

    /** Usable card width, margins and gaps already deducted. */
    val cardWidthMm: Double
        get() = (USABLE_WIDTH - (columns - 1) * COLUMN_GAP) / columns

    val cardHeightMm: Double
        get() = (USABLE_HEIGHT - HEADER - (rows - 1) * ROW_GAP) / rows

    /**
     * QR side once room is reserved for the label, the printed code and the
     * inner padding. The narrower side of the card wins.
     */
    val qrSideMm: Double
        get() = max(
            0.0,
            minOf(
                cardWidthMm - 2 * CARD_PADDING,
                cardHeightMm - LABEL_HEIGHT - CODE_HEIGHT - 2 * CARD_PADDING
            )
        )

    /**
     * Too small a QR stops scanning reliably. These cards produce a symbol of
     * about 33 modules which, with its quiet zone, comes to roughly 41; below
     * ~0.45 mm per module a phone camera starts to struggle, and that is where
     * this limit comes from.
     */
    val isQrComfortable: Boolean get() = qrSideMm >= MIN_COMFORTABLE_QR_MM

    /** Printed label font size, in points. */
    val labelPt: Double get() = (44.0 / columns).coerceIn(6.0, 13.0)

    /** Printed code font size, in points. */
    val codePt: Double get() = (22.0 / columns).coerceIn(3.5, 8.0)

    companion object {
        const val PAGE_WIDTH = 210.0
        const val PAGE_HEIGHT = 297.0
        const val MARGIN = 10.0
        const val HEADER = 8.0
        const val COLUMN_GAP = 3.0
        const val ROW_GAP = 6.0

        /** Space reserved inside each card. */
        const val CARD_PADDING = 2.0
        const val LABEL_HEIGHT = 5.0
        const val CODE_HEIGHT = 6.5

        const val MIN_COMFORTABLE_QR_MM = 18.0

        const val USABLE_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val USABLE_HEIGHT = PAGE_HEIGHT - 2 * MARGIN

        val COLUMN_RANGE = 1..6
        val ROW_RANGE = 1..8
    }
}
