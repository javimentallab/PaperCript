package com.javimetallab.papercript.ui

import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.Text as MlText
import com.javimetallab.papercript.R
import com.javimetallab.papercript.ocr.ImagePrep
import com.javimetallab.papercript.ocr.TextExtract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Fraction of the frame taken up by the guide rectangle. */
private const val GUIDE_WIDTH = 0.90f

/**
 * Single-shot camera: preview with a guide rectangle, shutter and torch.
 *
 * It takes a full-resolution photo rather than analysing a video stream. On
 * paper the difference is large: video frames arrive compressed and at lower
 * resolution, and fine strokes simply are not in them.
 *
 * The preview uses FIT_CENTER on purpose, so that what you see is the whole
 * frame and the drawn rectangle matches exactly the crop handed to the OCR.
 */
@Composable
fun CaptureScreen(
    hint: String,
    guideHeightFraction: Float,
    onResult: (MlText) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var working by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            try {
                provider?.unbindAll()
                camera = provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                failure = context.getString(R.string.camera_open_failed, e.message ?: "")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    fun shoot() {
        if (working) return
        working = true
        failure = null

        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        try {
                            val text = withContext(Dispatchers.Default) {
                                val upright = ImagePrep.toUprightBitmap(image)
                                    ?: error(context.getString(R.string.photo_unreadable))
                                val prepared = prepare(upright, guideHeightFraction)
                                TextExtract.recognize(prepared)
                            }
                            onResult(text)
                        } catch (e: Exception) {
                            failure = e.message ?: context.getString(R.string.photo_unreadable)
                        } finally {
                            image.close()
                            working = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    scope.launch {
                        failure = context.getString(R.string.photo_failed, exception.message ?: "")
                        working = false
                    }
                }
            }
        )
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Guide rectangle. The same fractions are used to crop the photo.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(GUIDE_WIDTH)
                .fillMaxHeight(guideHeightFraction)
                .border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    RoundedCornerShape(12.dp)
                )
        )

        IconButton(
            onClick = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = stringResource(R.string.torch),
                tint = Color.White
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = failure ?: hint,
                color = if (failure != null) MaterialTheme.colorScheme.error else Color.White,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel), color = Color.White)
                }
                FloatingActionButton(onClick = ::shoot, modifier = Modifier.size(72.dp)) {
                    if (working) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.take_photo))
                    }
                }
                // Symmetric spacer so the shutter stays centred.
                TextButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.cancel), color = Color.Transparent)
                }
            }
        }
    }
}

private fun prepare(upright: Bitmap, guideHeightFraction: Float): Bitmap {
    val cropped = ImagePrep.cropToGuide(upright, GUIDE_WIDTH, guideHeightFraction)
    val scaled = ImagePrep.upscaleIfSmall(cropped)
    return ImagePrep.enhance(scaled)
}
