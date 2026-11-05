package com.dualaudio.streamer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.dualaudio.streamer.ui.DualAudioViewModel
import com.dualaudio.streamer.ui.MainScreen
import com.dualaudio.streamer.ui.theme.DualAudioStreamerTheme
import com.dualaudio.streamer.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: DualAudioViewModel by viewModels()

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // MediaProjection screen audio capture permission launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.onMediaProjectionPermissionGranted(result.resultCode, result.data!!)
        } else {
            Toast.makeText(
                this,
                "Internal audio capture permission denied. Cannot route dual audio.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Runtime permissions launcher (Bluetooth connect/scan, Record Audio, Notifications)
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.checkPermissionsAndInit()
        } else {
            Toast.makeText(
                this,
                "Bluetooth and Audio permissions are required to detect and route to earphones.",
                Toast.LENGTH_LONG
            ).show()
            viewModel.checkPermissionsAndInit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Observe MediaProjection capture request from ViewModel
        lifecycleScope.launch {
            viewModel.requestMediaProjectionEvent.collect {
                val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                mediaProjectionLauncher.launch(captureIntent)
            }
        }

        // Request runtime permissions if not yet granted
        requestRequiredPermissions()

        setContent {
            DualAudioStreamerTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestPermissions = { requestRequiredPermissions() }
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = PermissionHelper.getRequiredPermissions().toTypedArray()
        runtimePermissionLauncher.launch(permissions)
    }
}

