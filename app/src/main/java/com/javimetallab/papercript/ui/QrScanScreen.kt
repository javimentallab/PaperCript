package com.javimetallab.papercript.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.javimetallab.papercript.R
import com.javimetallab.papercript.qr.QrCodec
import java.util.concurrent.Executors

/** Consecutive frames showing the same QR before it is offered. */
private const val FRAMES_TO_CONFIRM = 2

/**
 * Scans the QR of a printed card.
 *
 * It deliberately does NOT accept the first QR it sees. With a sheet of 16 cards
 * on the table, accepting automatically means reading the neighbouring one
 * without noticing and decrypting the wrong password. So the camera proposes —
 * naming the card — and the user decides by pressing the button.
 *
 * Until confirmed, moving the phone changes the proposal: pointing at another
 * card simply replaces the candidate.
 */
@Composable
fun QrScanScreen(
    onResult: (label: String, code: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    var notice by remember { mutableStateOf<String?>(null) }
    var candidate by remember { mutableStateOf<QrCodec.Parsed?>(null) }

    val noLabel = stringResource(R.string.no_label)

    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    // Haptic cue when a new card appears: you feel it without having to watch
    // the text while framing.
    LaunchedEffect(candidate?.code) {
        if (candidate != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    DisposableEffect(Unit) {
        val reader = QRCodeMultiReader()
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )

        // Debounce: the same code must appear in several consecutive frames
        // before being offered, so the proposal does not flicker when two cards
        // cross the viewfinder.
        var lastSeen: String? = null
        var repeats = 0

        val analyzer = ImageAnalysis.Analyzer { proxy: ImageProxy ->
            val texts = try {
                decodeQrs(proxy, reader, hints)
            } finally {
                proxy.close()
            }

            val parsed = texts
                .asSequence()
                .mapNotNull(QrCodec::parse)
                .firstOrNull()

            // The analyzer runs on its own thread; state is touched on the main one.
            ContextCompat.getMainExecutor(context).execute {
                when {
                    parsed != null -> {
                        if (parsed.code == lastSeen) {
                            repeats++
                        } else {
                            lastSeen = parsed.code
                            repeats = 1
                        }
                        if (repeats >= FRAMES_TO_CONFIRM) {
                            notice = null
                            candidate = parsed
                        }
                    }

                    texts.isNotEmpty() -> {
                        lastSeen = null
                        repeats = 0
                        notice = context.getString(R.string.qr_not_ours)
                    }

                    else -> {
                        lastSeen = null
                        repeats = 0
                    }
                }
            }
        }

        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }

            try {
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                notice = context.getString(R.string.camera_open_failed, e.message ?: "")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // The rectangle changes colour once a card is ready, so you need not
        // read the text below while framing.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .aspectRatio(1f)
                .border(
                    BorderStroke(
                        width = if (candidate != null) 3.dp else 2.dp,
                        color = if (candidate != null) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary
                    ),
                    RoundedCornerShape(16.dp)
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val found = candidate

            Text(
                text = when {
                    notice != null -> notice.orEmpty()
                    found != null -> stringResource(
                        R.string.qr_found,
                        found.label.ifEmpty { noLabel }
                    )
                    else -> stringResource(R.string.qr_aim)
                },
                color = if (notice != null) MaterialTheme.colorScheme.error else Color.White,
                style = MaterialTheme.typography.bodyLarge
            )

            Button(
                onClick = { found?.let { onResult(it.label, it.code) } },
                enabled = found != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.qr_use))
            }

            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
        }
    }
}

/**
 * Reads every QR in the frame from its luminance (Y) plane, which is all ZXing
 * needs. QR finder patterns work at any rotation, so the frame is not rotated.
 */
private fun decodeQrs(
    proxy: ImageProxy,
    reader: QRCodeMultiReader,
    hints: Map<DecodeHintType, Any>
): List<String> {
    val plane = proxy.planes.firstOrNull() ?: return emptyList()
    val buffer = plane.buffer.duplicate().apply { rewind() }
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }

    // Rows can be padded: rowStride is the real width of the buffer.
    val rowStride = plane.rowStride
    val dataHeight = bytes.size / rowStride
    val source = PlanarYUVLuminanceSource(
        bytes, rowStride, dataHeight,
        0, 0, proxy.width, minOf(proxy.height, dataHeight),
        false
    )

    return try {
        reader.decodeMultiple(BinaryBitmap(HybridBinarizer(source)), hints)
            .mapNotNull { it.text }
    } catch (_: NotFoundException) {
        emptyList()
    } finally {
        reader.reset()
    }
}
