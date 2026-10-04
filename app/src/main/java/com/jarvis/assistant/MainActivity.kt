package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.JarvisViewModel
import com.jarvis.assistant.data.PreferencesManager
import com.jarvis.assistant.service.RemoteCommandServerService
import com.jarvis.assistant.ui.screens.JarvisApp
import com.jarvis.assistant.ui.theme.JarvisAssistantTheme

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results ignored: features simply no-op / explain if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMissingPermissions()
        ensureRemoteServerRunningIfEnabled()

        setContent {
            JarvisAssistantTheme {
                JarvisApp(
                    viewModel = viewModel,
                    onRequestMicPermission = { requestMissingPermissions() }
                )
            }
        }
    }

    /**
     * The Settings switch only starts the service at the moment it's flipped — if the process
     * gets killed later (backgrounded too long, low memory, phone reboot) the saved preference
     * still says "enabled" but nothing is actually listening. Re-assert it here so opening the
     * app is enough to bring the receiver back up.
     */
    private fun ensureRemoteServerRunningIfEnabled() {
        if (!PreferencesManager.isRemoteServerEnabled(this)) return
        val intent = Intent(this, RemoteCommandServerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
