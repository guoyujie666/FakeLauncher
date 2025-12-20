package com.github.guoyujie666.fakelauncher.presentation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.wear.tooling.preview.devices.WearDevices
import com.github.guoyujie666.fakelauncher.R
import com.github.guoyujie666.fakelauncher.service.FloatingService

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (hasOverlayPermission()) {
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            WearApp(
                hasPermission = hasOverlayPermission(),
                onRequestPermission = { requestPermission() },
                onExitApp = { finish() }
            )
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestPermission() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        overlayPermissionLauncher.launch(intent)
    }
}

enum class ScreenDestination {
    MAIN, SETTINGS, ABOUT
}

@Composable
fun WearApp(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val backStack = remember { mutableStateListOf(ScreenDestination.MAIN) }

    MaterialTheme {
        AppScaffold {
            if (!hasPermission) {
                PermissionDeniedScreen(
                    onRequestPermission = onRequestPermission,
                    onExitApp = onExitApp
                )
            } else {
                SwipeToDismissBox(
                    state = androidx.wear.compose.foundation.rememberSwipeToDismissBoxState(),
                    onDismissed = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    }
                ) { isBackground ->
                    val screenToShow = if (isBackground) {
                        if (backStack.size > 1) backStack[backStack.size - 2] else null
                    } else {
                        backStack.last()
                    }

                    when (screenToShow) {
                        ScreenDestination.MAIN -> MainScreen(
                            onSettingsClick = { backStack.add(ScreenDestination.SETTINGS) }
                        )
                        ScreenDestination.SETTINGS -> SettingsScreen(
                            onAboutClick = { backStack.add(ScreenDestination.ABOUT) },
                            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                        )
                        ScreenDestination.ABOUT -> AboutScreen(
                            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = null
        ) {
            item {
                Text(
                    text = "FakeLauncher",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            item {
                Button(
                    onClick = { context.startService(Intent(context, FloatingService::class.java)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("开启悬浮窗")
                    }
                }
            }
            item {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onAboutClick: () -> Unit, onBack: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = null
        ) {
            item {
                Text("设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            item {
                Button(
                    onClick = { /* TODO */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("自定义伪装背景图")
                    }
                }
            }
            item {
                Button(
                    onClick = onAboutClick,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("关于")
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = null
        ) {
            item {
                Icon(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Unspecified
                )
            }
            item {
                Text(
                    text = "FakeLauncher",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Text(
                    text = "v1.0.1",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            item {
                Text(
                    text = "by guoyujie666",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            item {
                Text(
                    text = "一款简单的 Wear OS 伪装启动器",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).padding(horizontal = 12.dp)
                )
            }
            item {
                Text(
                    text = "https://github.com/guoyujie666/FakeLauncher",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionDeniedScreen(
    onRequestPermission: () -> Unit,
    onExitApp: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = null
        ) {
            item {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "警告",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }

            item {
                Text(
                    text = "未获取到悬浮窗权限",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Text(
                    text = "此应用需要启用「显示在其他应用的上层」权限才可正常运行。",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("去启用")
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = onExitApp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("退出程序")
                    }
                }
            }
        }
    }
}

// --- Previews ---
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun MainScreenPreview() { MaterialTheme { MainScreen({}) } }

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun SettingsScreenPreview() { MaterialTheme { SettingsScreen({}, {}) } }

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun AboutScreenPreview() { MaterialTheme { AboutScreen({}) } }

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PermissionDeniedPreview() {
    MaterialTheme {
        PermissionDeniedScreen(onRequestPermission = {}, onExitApp = {})
    }
}