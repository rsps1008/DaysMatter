package com.rsps1008.daymatter

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.rsps1008.daymatter.ui.DayMatterApp
import com.rsps1008.daymatter.ui.DayMatterTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DayMatterTheme {
                Surface {
                    NotificationPermissionGate {
                        DayMatterApp()
                    }
                }
            }
        }
    }

    @Composable
    private fun NotificationPermissionGate(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val prefs = remember(context) {
            context.getSharedPreferences("daymatter_permission_state", MODE_PRIVATE)
        }
        var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted) {
                val requestedBefore = prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
                if (requestedBefore && !ActivityCompat.shouldShowRequestPermissionRationale(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                ) {
                    showSettingsDialog = true
                }
            }
        }

        fun requestNotificationPermissionIfNeeded() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                showSettingsDialog = false
                return
            }

            val requestedBefore = prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
            if (!requestedBefore) {
                prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                showSettingsDialog = true
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    requestNotificationPermissionIfNeeded()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        content()

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("通知權限") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("通知權限已關閉，系統可能不再跳出授權視窗。")
                        Text("請到通知設定頁手動開啟，才能收到提醒。")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showSettingsDialog = false
                        val packageName = context.packageName
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        runCatching {
                            context.startActivity(intent)
                        }.getOrElse {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", packageName, null)
                                }
                            )
                        }
                    }) {
                        Text("開啟設定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }

    companion object {
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
