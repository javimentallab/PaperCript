package com.javimetallab.papercript.autofill

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.javimetallab.papercript.R
import com.javimetallab.papercript.ui.DecryptScreen
import com.javimetallab.papercript.ui.PaperCriptTheme

/**
 * Opened by the autofill suggestion. The usual decrypt screen; on success the
 * password goes back to the system as the dataset to fill, and this closes.
 */
class AutofillActivity : ComponentActivity() {

    private var cameraGranted by mutableStateOf(false)

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val fields = fieldsFrom(intent)
        if (fields.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Already decrypted in the app: hand it over without showing anything.
        // If it expired meanwhile, fall through to the usual decrypt screen.
        if (intent.getBooleanExtra(EXTRA_USE_DECRYPTED, false)) {
            DecryptedForFill.take()?.let {
                fill(fields, it)
                return
            }
        }

        cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        enableEdgeToEdge()
        setContent {
            PaperCriptTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DecryptScreen(
                        hasCameraPermission = cameraGranted,
                        onRequestCamera = { requestCamera.launch(Manifest.permission.CAMERA) },
                        onFill = { password -> fill(fields, password) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun fill(fields: List<AutofillId>, password: String) {
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_text, getString(R.string.autofill_fill_with))
        }

        @Suppress("DEPRECATION") // Presentations needs API 33; minSdk is 26.
        val dataset = Dataset.Builder(presentation).apply {
            fields.forEach { setValue(it, AutofillValue.forText(password)) }
        }.build()

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        )
        finish()
    }

    private fun fieldsFrom(intent: Intent): List<AutofillId> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_FIELDS, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_FIELDS)
        }.orEmpty()

    companion object {
        const val EXTRA_FIELDS = "com.javimetallab.papercript.autofill.FIELDS"
        const val EXTRA_USE_DECRYPTED = "com.javimetallab.papercript.autofill.USE_DECRYPTED"
    }
}
