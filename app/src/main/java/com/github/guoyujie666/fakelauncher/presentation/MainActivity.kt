package com.github.guoyujie666.fakelauncher.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.*
import androidx.wear.tooling.preview.devices.WearDevices
import com.github.guoyujie666.fakelauncher.R
import com.github.guoyujie666.fakelauncher.service.FloatingService
import java.io.File

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
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        overlayPermissionLauncher.launch(intent)
    }
}

enum class ScreenDestination {
    MAIN, SETTINGS, ABOUT, BACKGROUND_OPTIONS, FILE_PICKER, EXERCISE_MANAGER, ABOUT_WATCH_CONFIG, BOOT_TEXT_CONFIG
}

@Composable
fun WearApp(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    var backStack by rememberSaveable { mutableStateOf(listOf(ScreenDestination.MAIN)) }
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }

    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            backStack = backStack + ScreenDestination.FILE_PICKER
        }
    }

    MaterialTheme {
        AppScaffold {
            if (!hasPermission) {
                PermissionDeniedScreen(
                    onRequestPermission = onRequestPermission,
                    onExitApp = onExitApp
                )
            } else {
                val swipeState = rememberSwipeToDismissBoxState()

                LaunchedEffect(swipeState.currentValue) {
                    if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
                        if (backStack.size > 1) {
                            backStack = backStack.dropLast(1)
                            swipeState.snapTo(SwipeToDismissValue.Default)
                        } else {
                            onExitApp()
                        }
                    }
                }

                SwipeToDismissBox(
                    state = swipeState,
                    backgroundKey = if (backStack.size > 1) backStack[backStack.size - 2] else "none",
                    contentKey = backStack.last(),
                    userSwipeEnabled = backStack.size > 1
                ) { isBackground ->
                    val screenToShow = if (isBackground) {
                        if (backStack.size > 1) backStack[backStack.size - 2] else null
                    } else {
                        backStack.last()
                    }

                    when (screenToShow) {
                        ScreenDestination.MAIN -> MainScreen(
                            onSettingsClick = { backStack = backStack + ScreenDestination.SETTINGS }
                        )
                        ScreenDestination.SETTINGS -> SettingsScreen(
                            onAboutClick = { backStack = backStack + ScreenDestination.ABOUT },
                            onBackgroundClick = { backStack = backStack + ScreenDestination.BACKGROUND_OPTIONS },
                            onExerciseClick = { backStack = backStack + ScreenDestination.EXERCISE_MANAGER },
                            onAboutWatchClick = { backStack = backStack + ScreenDestination.ABOUT_WATCH_CONFIG },
                            onBootTextClick = { backStack = backStack + ScreenDestination.BOOT_TEXT_CONFIG }
                        )
                        ScreenDestination.ABOUT -> AboutScreen()
                        ScreenDestination.EXERCISE_MANAGER -> ExerciseManagerScreen()
                        ScreenDestination.ABOUT_WATCH_CONFIG -> AboutWatchConfigScreen()
                        ScreenDestination.BOOT_TEXT_CONFIG -> BootTextConfigScreen()
                        ScreenDestination.BACKGROUND_OPTIONS -> BackgroundOptionsScreen(
                            onOptionSelected = { mode ->
                                selectedMode = mode
                                val isGranted = mediaPermissions.all {
                                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                }
                                if (isGranted) {
                                    backStack = backStack + ScreenDestination.FILE_PICKER
                                } else {
                                    permissionLauncher.launch(mediaPermissions)
                                }
                            }
                        )
                        ScreenDestination.FILE_PICKER -> MiniFileManager(
                            onFileSelected = { file ->
                                saveBackgroundSettings(context, file.absolutePath, selectedMode)
                                backStack = backStack.dropLast(2)
                            }
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

private fun saveBackgroundSettings(context: Context, path: String, mode: Int) {
    val prefs = context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE)
    prefs.edit {
        putString("bg_path", path)
        putInt("bg_mode", mode)
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
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
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
                    modifier = Modifier.padding(top = 16.dp).size(48.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onAboutClick: () -> Unit, 
    onBackgroundClick: () -> Unit, 
    onExerciseClick: () -> Unit,
    onAboutWatchClick: () -> Unit,
    onBootTextClick: () -> Unit
) {
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
                Button(onClick = onBackgroundClick, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Photo, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("自定义伪装背景图")
                    }
                }
            }
            item {
                Button(onClick = onExerciseClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("运动设置")
                    }
                }
            }
            item {
                Button(onClick = onAboutWatchClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Watch, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("关于手表设置")
                    }
                }
            }
            item {
                Button(onClick = onBootTextClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("开机文字设置")
                    }
                }
            }
            item {
                Button(onClick = onAboutClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("关于")
                    }
                }
            }
        }
    }
}

@Composable
fun BootTextConfigScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    
    var main by remember { mutableStateOf(prefs.getString("boot_text_main", "Xiaomi") ?: "") }
    var sub by remember { mutableStateOf(prefs.getString("boot_text_sub", "HyperOS") ?: "") }

    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
            autoCentering = null
        ) {
            item {
                Text("开机文字设置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            
            item { ConfigInputField("主文字 (如 Xiaomi)", main) { main = it; prefs.edit { putString("boot_text_main", it) } } }
            item { ConfigInputField("副文字 (如 HyperOS)", sub) { sub = it; prefs.edit { putString("boot_text_sub", it) } } }
            
            item {
                Text(
                    text = "重启生效，仅在假重启功能中显示。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp).padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AboutWatchConfigScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    
    var systemVersion by remember { mutableStateOf(prefs.getString("watch_sys_ver", "Xiaomi HyperOS Lite 1.5.3") ?: "") }
    var deviceModel by remember { mutableStateOf(prefs.getString("watch_model", "Xiaomi Watch S4") ?: "") }
    var serialNumber by remember { mutableStateOf(prefs.getString("watch_sn", "A2304S40001298X") ?: "") }

    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
            autoCentering = null
        ) {
            item {
                Text("关于手表设置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            
            item { ConfigInputField("系统版本", systemVersion) { systemVersion = it; prefs.edit { putString("watch_sys_ver", it) } } }
            item { ConfigInputField("设备型号", deviceModel) { deviceModel = it; prefs.edit { putString("watch_model", it) } } }
            item { ConfigInputField("序列号", serialNumber) { serialNumber = it; prefs.edit { putString("watch_sn", it) } } }
            
            item {
                Text(
                    text = "修改后即时生效，请在悬浮窗设置中查看效果。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp).padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ConfigInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .background(Color.DarkGray, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ExerciseManagerScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    var exerciseList by remember { 
        mutableStateOf(prefs.getStringSet("exercise_list", setOf("户外跑", "健走", "骑行"))?.toList()?.sorted() ?: emptyList<String>()) 
    }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
            autoCentering = null
        ) {
            item {
                Text("添加运动", style = MaterialTheme.typography.titleSmall)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.DarkGray, MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        if (inputText.isEmpty()) {
                            Text("输入名称", style = TextStyle(color = Color.Gray, fontSize = 12.sp))
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val newList = (exerciseList + inputText).distinct().sorted()
                                exerciseList = newList
                                prefs.edit { putStringSet("exercise_list", newList.toSet()) }
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "添加", tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }
            items(exerciseList) { exercise ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = exercise, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp).clickable {
                            val newList = exerciseList.filter { it != exercise }
                            exerciseList = newList
                            prefs.edit { putStringSet("exercise_list", newList.toSet()) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BackgroundOptionsScreen(onOptionSelected: (Int) -> Unit) {
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
                Text("背景应用范围", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            item {
                Button(onClick = { onOptionSelected(0) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("仅时间主界面", textAlign = TextAlign.Center)
                }
            }
            item {
                Button(onClick = { onOptionSelected(1) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("应用到全局", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun MiniFileManager(onFileSelected: (File) -> Unit) {
    var currentDirPath by rememberSaveable { 
        mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) 
    }
    val currentDir = File(currentDirPath)
    val files = remember(currentDirPath) { 
        currentDir.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList() 
    }
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
            autoCentering = null
        ) {
            item {
                Text(
                    text = if (currentDirPath == Environment.getExternalStorageDirectory().absolutePath) "内部存储" else currentDir.name,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (currentDir.parentFile != null && currentDirPath != Environment.getExternalStorageDirectory().absolutePath) {
                item {
                    Button(onClick = { currentDirPath = currentDir.parentFile!!.absolutePath }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("返回上级")
                        }
                    }
                }
            }
            
            if (files.isEmpty()) {
                item {
                    Text("此文件夹为空", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 20.dp))
                }
            }

            items(files) { file ->
                val isSupportedMedia = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "mp4", "mkv")
                if (file.isDirectory || isSupportedMedia) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (file.isDirectory) {
                                    currentDirPath = file.absolutePath
                                } else {
                                    onFileSelected(file)
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                file.isDirectory -> Icons.Default.Folder
                                file.extension.lowercase() in listOf("mp4", "mkv") -> Icons.Default.Movie
                                else -> Icons.Default.Image
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else Color.White
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(text = file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
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
                Icon(painter = painterResource(id = R.mipmap.ic_launcher), contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Unspecified)
            }
            item {
                Text(text = "FakeLauncher", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Text(text = "v1.0.4", style = MaterialTheme.typography.labelSmall)
            }
            item {
                Text(text = "by guoyujie666", style = MaterialTheme.typography.labelSmall)
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
fun PermissionDeniedScreen(onRequestPermission: () -> Unit, onExitApp: () -> Unit) {
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
                Icon(imageVector = Icons.Default.Warning, contentDescription = "警告", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
            }
            item {
                Text(text = "未获取到悬浮窗权限", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("去启用")
                    }
                }
            }
            item {
                FilledTonalButton(onClick = onExitApp, modifier = Modifier.fillMaxWidth()) {
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
fun MainScreenPreview() { MaterialTheme { MainScreen {} } }

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun SettingsScreenPreview() { MaterialTheme { SettingsScreen({}, {}, {}, {}, {}) } }

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun AboutScreenPreview() { MaterialTheme { AboutScreen() } }
