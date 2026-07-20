package com.javimetallab.papercript

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javimetallab.papercript.security.SecureClipboard
import com.javimetallab.papercript.sheet.SheetEntry
import com.javimetallab.papercript.sheet.SheetViewModel
import com.javimetallab.papercript.ui.DecryptScreen
import com.javimetallab.papercript.ui.EncryptScreen
import com.javimetallab.papercript.ui.PaperCriptTheme
import com.javimetallab.papercript.ui.SheetScreen

class MainActivity : ComponentActivity() {

    private var cameraGranted by mutableStateOf(false)

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Passwords on screen: no screenshots, no thumbnail in recents.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        enableEdgeToEdge()
        setContent {
            PaperCriptTheme {
                PaperCriptApp(
                    hasCameraPermission = cameraGranted,
                    onRequestCamera = { requestCamera.launch(Manifest.permission.CAMERA) }
                )
            }
        }
    }

    /**
     * Regaining focus is the only moment Android lets us touch the clipboard,
     * so this is where a clear that fell due while we were away pasting the
     * password finally happens.
     */
    override fun onResume() {
        super.onResume()
        SecureClipboard.clearIfExpired(this)
    }
}

@Composable
private fun PaperCriptApp(
    hasCameraPermission: Boolean,
    onRequestCamera: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val sheet: SheetViewModel = viewModel()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.tab_encrypt)) },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.tab_decrypt)) },
                    icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) }
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text(stringResource(R.string.tab_sheet)) },
                    icon = {
                        BadgedBox(badge = {
                            if (sheet.entries.isNotEmpty()) {
                                Badge { Text("${sheet.entries.size}") }
                            }
                        }) {
                            Icon(Icons.Filled.Print, contentDescription = null)
                        }
                    }
                )
            }

            // Each tab keeps its state while the app lives, but nothing is
            // written to the phone: closing it leaves no trace.
            when (tab) {
                0 -> EncryptScreen(
                    hasCameraPermission = hasCameraPermission,
                    onRequestCamera = onRequestCamera,
                    sheetCount = sheet.entries.size,
                    onAddToSheet = { label, code ->
                        sheet.add(SheetEntry(label = label, code = code))
                    },
                    modifier = Modifier.fillMaxSize()
                )
                1 -> DecryptScreen(
                    hasCameraPermission = hasCameraPermission,
                    onRequestCamera = onRequestCamera,
                    modifier = Modifier.fillMaxSize()
                )
                else -> SheetScreen(
                    model = sheet,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
