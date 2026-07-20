package com.javimetallab.papercript.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.javimetallab.papercript.crypto.Base32

/** One card on the sheet: the label and the already-encrypted code. */
data class SheetEntry(val label: String, val code: String) {
    /** The code without separators, exactly as it travels inside the QR. */
    val rawCode: String get() = Base32.normalize(code)
}

/**
 * The list of cards waiting to be printed.
 *
 * Lives in memory only, while the app is open. It could be persisted without
 * real risk (an encrypted code is worthless without the master password), but
 * staying in memory keeps the promise that the app writes nothing to the phone.
 * The intended flow is a single sitting: enter the passwords, print, cut.
 *
 * Being a ViewModel, it survives configuration changes, including folding and
 * unfolding the phone.
 */
class SheetViewModel : ViewModel() {

    private val _entries = mutableStateListOf<SheetEntry>()
    val entries: List<SheetEntry> get() = _entries

    /**
     * The layout the user picked. Shared by the preview and the printout, so
     * what is on screen is what comes out of the printer.
     */
    var layout by mutableStateOf(SheetLayout())
        private set

    fun setColumns(columns: Int) {
        layout = layout.copy(columns = columns.coerceIn(SheetLayout.COLUMN_RANGE))
    }

    fun setRows(rows: Int) {
        layout = layout.copy(rows = rows.coerceIn(SheetLayout.ROW_RANGE))
    }

    fun add(entry: SheetEntry) {
        _entries.add(entry)
    }

    fun removeAt(index: Int) {
        if (index in _entries.indices) _entries.removeAt(index)
    }

    fun clear() {
        _entries.clear()
    }
}
