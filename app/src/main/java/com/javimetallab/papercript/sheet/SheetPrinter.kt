package com.javimetallab.papercript.sheet

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Hands the sheet to Android's print system.
 *
 * It renders in an offscreen WebView and prints through PrintManager, which
 * offers both real printers and "Save as PDF". The HTML is self-contained and
 * the WebView never touches the network.
 */
object SheetPrinter {

    /**
     * The WebView must stay alive until the print adapter finishes generating
     * the document. Left as a local variable, the collector takes it and the
     * job comes out blank.
     */
    private var pending: WebView? = null

    fun print(context: Context, html: String, jobName: String = "PaperCript") {
        val webView = WebView(context)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                    .build()

                printManager.print(jobName, view.createPrintDocumentAdapter(jobName), attributes)
                pending = null
            }
        }

        pending = webView
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
