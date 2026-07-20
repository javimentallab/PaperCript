package com.javimetallab.papercript

import com.javimetallab.papercript.sheet.SheetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetLayoutTest {

    @Test
    fun `cards per sheet is columns times rows`() {
        assertEquals(16, SheetLayout(columns = 4, rows = 4).cardsPerPage)
        assertEquals(6, SheetLayout(columns = 2, rows = 3).cardsPerPage)
        assertEquals(48, SheetLayout(columns = 6, rows = 8).cardsPerPage)
    }

    @Test
    fun `cards fit on the sheet including their gaps`() {
        for (columns in SheetLayout.COLUMN_RANGE) {
            for (rows in SheetLayout.ROW_RANGE) {
                val layout = SheetLayout(columns, rows)

                val totalWidth = layout.cardWidthMm * columns +
                    SheetLayout.COLUMN_GAP * (columns - 1)
                val totalHeight = layout.cardHeightMm * rows +
                    SheetLayout.ROW_GAP * (rows - 1) +
                    SheetLayout.HEADER

                assertTrue(
                    "Too wide at ${columns}x$rows: $totalWidth mm",
                    totalWidth <= SheetLayout.USABLE_WIDTH + 0.01
                )
                assertTrue(
                    "Too tall at ${columns}x$rows: $totalHeight mm",
                    totalHeight <= SheetLayout.USABLE_HEIGHT + 0.01
                )
            }
        }
    }

    @Test
    fun `fewer cards per sheet give a bigger QR`() {
        val tight = SheetLayout(columns = 6, rows = 8)
        val roomy = SheetLayout(columns = 2, rows = 2)
        assertTrue(roomy.qrSideMm > tight.qrSideMm)
    }

    @Test
    fun `the QR never goes negative however tight the layout`() {
        for (columns in SheetLayout.COLUMN_RANGE) {
            for (rows in SheetLayout.ROW_RANGE) {
                assertTrue(SheetLayout(columns, rows).qrSideMm >= 0.0)
            }
        }
    }

    @Test
    fun `the default layout leaves a comfortably scannable QR`() {
        val layout = SheetLayout()
        assertTrue(
            "Default QR too small: ${layout.qrSideMm} mm",
            layout.isQrComfortable
        )
    }

    @Test
    fun `a very dense layout is flagged as uncomfortable`() {
        assertTrue(!SheetLayout(columns = 6, rows = 8).isQrComfortable)
    }

    @Test
    fun `the QR is never wider than its card`() {
        for (columns in SheetLayout.COLUMN_RANGE) {
            for (rows in SheetLayout.ROW_RANGE) {
                val layout = SheetLayout(columns, rows)
                assertTrue(layout.qrSideMm <= layout.cardWidthMm)
                assertTrue(layout.qrSideMm <= layout.cardHeightMm)
            }
        }
    }
}
