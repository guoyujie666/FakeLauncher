package com.guoyujie666.fakelauncher.presentation

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import androidx.wear.input.WearableButtons
import androidx.wear.tooling.preview.devices.WearDevices
import coil.compose.AsyncImage
import com.guoyujie666.fakelauncher.BuildConfig
import com.guoyujie666.fakelauncher.R
import com.guoyujie666.fakelauncher.aidl.IUserService
import com.guoyujie666.fakelauncher.service.FloatingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    MAIN, SETTINGS, ABOUT, BACKGROUND_OPTIONS, FILE_PICKER, EXERCISE_MANAGER, ABOUT_WATCH_CONFIG,
    BOOT_SCREEN_CONFIG,
    BOOT_TEXT_CONFIG,  // 保留兼容
    BUTTON_TEST
}

@Composable
fun WearApp(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    if (prefs.getString("boot_image_path_1", null) == null) {
        val defaultFile = File(context.filesDir, "DefaultBootLogo.png")
        if (!defaultFile.exists()) {
            context.resources.openRawResource(R.raw.defaultbootlogo).use { input ->
                defaultFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        prefs.edit { putString("boot_image_path_1", defaultFile.absolutePath) }
    }
    fun performShizukuGrant() {
        scope.launch {
            withContext(Dispatchers.Main) {
                try {
                    if (!Shizuku.pingBinder()) {
                        Toast.makeText(context, "Shizuku 服务未运行", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val userServiceArgs = Shizuku.UserServiceArgs(
                        ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name)
                    )
                        .daemon(false)
                        .processNameSuffix("shizuku_service")
                        .debuggable(BuildConfig.DEBUG)
                        .version(BuildConfig.VERSION_CODE)

                    Shizuku.bindUserService(userServiceArgs, object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            try {
                                val userService = IUserService.Stub.asInterface(binder)
                                val command = "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"
                                userService?.execLine(command)

                                if (Settings.canDrawOverlays(context)) {
                                    (context as? Activity)?.recreate()
                                } else {
                                    Toast.makeText(context, "命令已执行但权限未生效，请重试", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "执行命令失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                Shizuku.unbindUserService(userServiceArgs, this, true)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {}
                    })
                } catch (e: Exception) {
                    Toast.makeText(context, "Shizuku 操作异常: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val shizukuPermissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 0 && grantResult == PackageManager.PERMISSION_GRANTED) {
                // 用户已授权，自动执行悬浮窗权限授予
                performShizukuGrant()
            }
        }
    }
    DisposableEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        }
    }
    // Shizuku 授权逻辑
    val onShizukuAuthorize: () -> Unit = {
        scope.launch {
            withContext(Dispatchers.Main) {
                if (!Shizuku.pingBinder()) {
                    Toast.makeText(context, "Shizuku 服务未运行，请先启动 Shizuku", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    // 未授权，请求授权（之后由监听器自动执行）
                    Shizuku.requestPermission(0)
                    Toast.makeText(context, "请在弹出的对话框中允许授权", Toast.LENGTH_SHORT).show()
                } else {
                    // 已授权，直接执行
                    performShizukuGrant()
                }
            }
        }
    }

    var backStack by rememberSaveable { mutableStateOf(listOf(ScreenDestination.MAIN)) }
    var bgEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("bg_enabled", true)) }
    var selectedMode by rememberSaveable { mutableIntStateOf(prefs.getInt("bg_mode", 0)) }
    var blurEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("bg_blur", true)) }
    var blurAmount by rememberSaveable { mutableFloatStateOf(prefs.getFloat("bg_blur_amount", 10f)) }
    var filePickerTarget by rememberSaveable { mutableStateOf<String?>(null) }

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

    // 文件选择器启动逻辑
    val launchFilePicker = {
        val isGranted = mediaPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (isGranted) {
            backStack = backStack + ScreenDestination.FILE_PICKER
        } else {
            permissionLauncher.launch(mediaPermissions)
        }
    }

    val onPickBootImage1 = {
        filePickerTarget = "boot_image_1"
        launchFilePicker()
    }
    val onPickBootImage2 = {
        filePickerTarget = "boot_image_2"
        launchFilePicker()
    }
    val onResetBootImage1 = {
        val defaultFile = File(context.filesDir, "DefaultBootLogo.png")
        if (!defaultFile.exists()) {
            context.resources.openRawResource(R.raw.defaultbootlogo).use { input ->
                defaultFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        prefs.edit { putString("boot_image_path_1", defaultFile.absolutePath) }
    }

    val onResetBootImage2 = {
        prefs.edit { remove("boot_image_path_2") }
    }
    val onClearBootImage2 = {
        prefs.edit { remove("boot_image_path_2") }
    }

    val onFileSelected: (File) -> Unit = { file ->
        when (filePickerTarget) {
            "boot_image_1" -> {
                prefs.edit { putString("boot_image_path_1", file.absolutePath) }
                filePickerTarget = null
            }
            "boot_image_2" -> {
                prefs.edit { putString("boot_image_path_2", file.absolutePath) }
                filePickerTarget = null
            }
            else -> {
                saveBackgroundSettings(context, file.absolutePath, selectedMode, bgEnabled, blurEnabled, blurAmount)
            }
        }
        backStack = backStack.dropLast(1)
    }

    MaterialTheme {
        AppScaffold {
            if (!hasPermission) {
                PermissionDeniedScreen(
                    onRequestPermission = onRequestPermission,
                    onExitApp = onExitApp,
                    onShizukuAuthorize = onShizukuAuthorize
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    val currentStack = backStack
                    val topScreen = currentStack.last()
                    val backgroundScreen = if (currentStack.size > 1) currentStack[currentStack.size - 2] else null

                    val swipeState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(swipeState.currentValue) {
                        if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
                            if (backStack.size > 1) {
                                backStack = backStack.dropLast(1)
                            } else {
                                onExitApp()
                            }
                            swipeState.snapTo(SwipeToDismissValue.Default)
                        }
                    }

                    SwipeToDismissBox(
                        state = swipeState,
                        backgroundKey = backgroundScreen ?: "none",
                        contentKey = topScreen,
                        userSwipeEnabled = currentStack.size > 1,
                        modifier = Modifier.fillMaxSize()
                    ) { isBackground ->
                        val screenToShow = if (isBackground) backgroundScreen else topScreen
                        screenToShow?.let { screen ->
                            NavigationScreenContent(
                                screen = screen,
                                onNavigate = { next -> backStack = backStack + next },
                                onFilePickerRequest = {
                                    val isGranted = mediaPermissions.all { perm ->
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            perm
                                        ) == PackageManager.PERMISSION_GRANTED
                                    }
                                    if (isGranted) {
                                        backStack = backStack + ScreenDestination.FILE_PICKER
                                    } else {
                                        permissionLauncher.launch(mediaPermissions)
                                    }
                                },
                                onFileSelected = onFileSelected,
                                onPickBootImage1 = onPickBootImage1,
                                onPickBootImage2 = onPickBootImage2,
                                onClearBootImage2 = onClearBootImage2,
                                bgEnabled = bgEnabled,
                                onBgEnabledChange = {
                                    bgEnabled = it
                                    prefs.edit { putBoolean("bg_enabled", it) }
                                },
                                selectedMode = selectedMode,
                                onModeChange = {
                                    selectedMode = it
                                    prefs.edit { putInt("bg_mode", it) }
                                },
                                blurEnabled = blurEnabled,
                                onBlurEnabledChange = {
                                    blurEnabled = it
                                    prefs.edit { putBoolean("bg_blur", it) }
                                },
                                blurAmount = blurAmount,
                                onBlurAmountChange = {
                                    blurAmount = it
                                    prefs.edit { putFloat("bg_blur_amount", it) }
                                },
                                onResetBootImage1 = onResetBootImage1,
                                onResetBootImage2 = onResetBootImage2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationScreenContent(
    screen: ScreenDestination,
    onNavigate: (ScreenDestination) -> Unit,
    onFilePickerRequest: () -> Unit,
    onFileSelected: (File) -> Unit,
    onPickBootImage1: () -> Unit,
    onPickBootImage2: () -> Unit,
    onClearBootImage2: () -> Unit,
    onResetBootImage1: () -> Unit,
    onResetBootImage2: () -> Unit,
    bgEnabled: Boolean,
    onBgEnabledChange: (Boolean) -> Unit,
    selectedMode: Int,
    onModeChange: (Int) -> Unit,
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    blurAmount: Float,
    onBlurAmountChange: (Float) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            ScreenDestination.MAIN -> MainScreen(
                onSettingsClick = { onNavigate(ScreenDestination.SETTINGS) }
            )
            ScreenDestination.SETTINGS -> SettingsScreen(
                onAboutClick = { onNavigate(ScreenDestination.ABOUT) },
                onBackgroundClick = { onNavigate(ScreenDestination.BACKGROUND_OPTIONS) },
                onExerciseClick = { onNavigate(ScreenDestination.EXERCISE_MANAGER) },
                onAboutWatchClick = { onNavigate(ScreenDestination.ABOUT_WATCH_CONFIG) },
                onBootScreenClick = { onNavigate(ScreenDestination.BOOT_SCREEN_CONFIG) },
                onButtonTestClick = { onNavigate(ScreenDestination.BUTTON_TEST) }
            )
            ScreenDestination.ABOUT -> AboutScreen()
            ScreenDestination.EXERCISE_MANAGER -> ExerciseManagerScreen()
            ScreenDestination.ABOUT_WATCH_CONFIG -> AboutWatchConfigScreen()
            ScreenDestination.BOOT_SCREEN_CONFIG,
            ScreenDestination.BOOT_TEXT_CONFIG -> BootScreenConfigScreen(
                onPickImage1 = onPickBootImage1,
                onPickImage2 = onPickBootImage2,
                onClearImage2 = onClearBootImage2,
                onResetImage1 = onResetBootImage1,    // 新增
                onResetImage2 = onResetBootImage2
            )
            ScreenDestination.BACKGROUND_OPTIONS -> BackgroundOptionsScreen(
                enabled = bgEnabled,
                onEnabledChange = onBgEnabledChange,
                selectedMode = selectedMode,
                onModeChange = onModeChange,
                onPickFile = onFilePickerRequest,
                blurEnabled = blurEnabled,
                onBlurEnabledChange = onBlurEnabledChange,
                blurAmount = blurAmount,
                onBlurAmountChange = onBlurAmountChange
            )
            ScreenDestination.FILE_PICKER -> MiniFileManager(
                onFileSelected = onFileSelected
            )
            ScreenDestination.BUTTON_TEST -> ButtonTestScreen(
                onBack = { onNavigate(ScreenDestination.SETTINGS) }
            )
        }
    }
}

private fun saveBackgroundSettings(context: Context, path: String, mode: Int, enabled: Boolean, blur: Boolean, blurAmount: Float) {
    val prefs = context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE)
    prefs.edit {
        putString("bg_path", path)
        putInt("bg_mode", mode)
        putBoolean("bg_enabled", enabled)
        putBoolean("bg_blur", blur)
        putFloat("bg_blur_amount", blurAmount)
    }
}

@Composable
fun MainScreen(onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()

    ScreenScaffold(timeText = { TimeText() }) {
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
                val scale = remember { Animatable(1f) }
                val shapeRadius = remember { Animatable(26.dp, Dp.VectorConverter) }

                Button(
                    onClick = {
                        scope.launch {
                            launch {
                                scale.animateTo(0.92f, spring(stiffness = Spring.StiffnessHigh))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                            launch {
                                shapeRadius.animateTo(12.dp, spring(stiffness = Spring.StiffnessHigh))
                                shapeRadius.animateTo(26.dp, spring(stiffness = Spring.StiffnessLow))
                            }
                            delay(150)
                            context.startService(Intent(context, FloatingService::class.java))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        },
                    shape = RoundedCornerShape(shapeRadius.value),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("开启悬浮窗")
                    }
                }
            }
            item {
                val scale = remember { Animatable(1f) }
                val shapeRadius = remember { Animatable(24.dp, Dp.VectorConverter) }

                Button(
                    onClick = {
                        scope.launch {
                            launch {
                                scale.animateTo(0.92f, spring(stiffness = Spring.StiffnessHigh))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                            launch {
                                shapeRadius.animateTo(10.dp, spring(stiffness = Spring.StiffnessHigh))
                                shapeRadius.animateTo(24.dp, spring(stiffness = Spring.StiffnessLow))
                            }
                            delay(150)
                            onSettingsClick()
                        }
                    },
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        },
                    shape = RoundedCornerShape(shapeRadius.value),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
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
    onBootScreenClick: () -> Unit,
    onButtonTestClick: () -> Unit
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
                Button(onClick = onBackgroundClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Photo, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("背景图设置")
                    }
                }
            }
            item {
                Button(onClick = onExerciseClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("运动设置")
                    }
                }
            }
            item {
                Button(onClick = onAboutWatchClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Watch, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("关于手表设置")
                    }
                }
            }
            item {
                Button(onClick = onBootScreenClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("开机画面")
                    }
                }
            }
            item {
                Button(onClick = onButtonTestClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("按键检测")
                    }
                }
            }
            item {
                Button(onClick = onAboutClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
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
fun BootScreenConfigScreen(
    onPickImage1: () -> Unit,
    onPickImage2: () -> Unit,
    onClearImage2: () -> Unit,
    onResetImage1: () -> Unit,        // 新增
    onResetImage2: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }

    var imagePath1 by remember { mutableStateOf(prefs.getString("boot_image_path_1", null)) }
    var imagePath2 by remember { mutableStateOf(prefs.getString("boot_image_path_2", null)) }
    var durationSec by remember { mutableIntStateOf(prefs.getInt("boot_image_duration", 5)) }

    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "boot_image_path_1" -> imagePath1 = prefs.getString("boot_image_path_1", null)
                "boot_image_path_2" -> imagePath2 = prefs.getString("boot_image_path_2", null)
                "boot_image_duration" -> durationSec = prefs.getInt("boot_image_duration", 5)
            }
        }
    }

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
            autoCentering = null
        ) {
            item {
                Text("开机画面设置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            }

            // 第一屏图片（必选）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPickImage1,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        label = { Text("第一屏图片 (必选)") },
                        secondaryLabel = {
                            Text(
                                text = imagePath1?.substringAfterLast('/') ?: "未选择",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = { Icon(Icons.Default.Photo, null) }
                    )
                    IconButton(
                        onClick = onResetImage1,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重置为默认",
                            tint = Color(0xFFFFA726) // 橙色
                        )
                    }
                }
            }
            if (imagePath1 != null) {
                val file = File(imagePath1!!)
                if (file.exists()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), onClick = {}) {
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // 第二屏图片（可选）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPickImage2,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        label = { Text("第二屏图片 (可选)") },
                        secondaryLabel = {
                            Text(
                                text = imagePath2?.substringAfterLast('/') ?: "未选择",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = { Icon(Icons.Default.Photo, null) }
                    )
                    IconButton(
                        onClick = {
                            onResetImage2()
                            onClearImage2() // 同时更新界面状态
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "清除",
                            tint = Color.Red
                        )
                    }
                }
            }
            if (imagePath2 != null) {
                val file = File(imagePath2!!)
                if (file.exists()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), onClick = {}) {
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // 显示时间滑块
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    onClick = {},
                    enabled = false
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("每张图片显示时间", style = MaterialTheme.typography.labelSmall)
                            Text("${durationSec} 秒", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = durationSec.toFloat(),
                            onValueChange = { newValue ->
                                val sec = newValue.toInt().coerceIn(1, 20)
                                durationSec = sec
                                prefs.edit { putInt("boot_image_duration", sec) }
                            },
                            valueRange = 1f..20f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Text(
                    text = "假重启时将先黑屏8秒，再依次显示以上图片。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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

            item {
                TextField(
                    value = systemVersion,
                    onValueChange = { systemVersion = it; prefs.edit { putString("watch_sys_ver", it) } },
                    label = { Text("系统版本") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                )
            }
            item {
                TextField(
                    value = deviceModel,
                    onValueChange = { deviceModel = it; prefs.edit { putString("watch_model", it) } },
                    label = { Text("设备型号") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                )
            }
            item {
                TextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it; prefs.edit { putString("watch_sn", it) } },
                    label = { Text("序列号") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                )
            }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundOptionsScreen(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    selectedMode: Int,
    onModeChange: (Int) -> Unit,
    onPickFile: () -> Unit,
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    blurAmount: Float,
    onBlurAmountChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    val bgPath = prefs.getString("bg_path", null)

    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = null
        ) {
            item {
                Text("背景设置", style = MaterialTheme.typography.titleMedium)
            }

            item {
                SwitchButton(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    label = { Text("启用自定义背景") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            item {
                Button(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    enabled = enabled,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { Text("选择背景文件") },
                    secondaryLabel = {
                        Text(
                            text = bgPath?.substringAfterLast('/') ?: "未选择",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = { Icon(Icons.Default.Photo, null) }
                )
            }

            item {
                ListHeader(modifier = Modifier.padding(top = 8.dp)) {
                    Text("毛玻璃设置", style = MaterialTheme.typography.labelSmall)
                }
            }

            item {
                SwitchButton(
                    checked = blurEnabled,
                    onCheckedChange = onBlurEnabledChange,
                    label = { Text("开启模糊") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }

            item {
                Card(
                    onClick = { },
                    enabled = enabled && blurEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "模糊程度",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = blurAmount.toInt().toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        val sliderState = remember { SliderState(value = blurAmount, valueRange = 0f..30f, steps = 29) }
                        LaunchedEffect(sliderState.value) {
                            onBlurAmountChange(sliderState.value)
                        }
                        Slider(
                            state = sliderState,
                            enabled = enabled && blurEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 已移除「显示范围」部分，不再显示 ListHeader 和 RadioButton
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
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = null
        ) {
            item {
                Text(
                    text = "运动管理",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("输入运动名称") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    )
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val newList = (exerciseList + inputText).distinct().sorted()
                                exerciseList = newList
                                prefs.edit { putStringSet("exercise_list", newList.toSet()) }
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        enabled = inputText.isNotBlank(),
                        label = { Text("添加运动") },
                        icon = { Icon(Icons.Default.Add, null) }
                    )
                }
            }

            if (exerciseList.isNotEmpty()) {
                item {
                    ListHeader {
                        Text("当前运动列表", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            items(exerciseList) { exercise ->
                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = exercise,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(28.dp)
                                    .clickable {
                                        val newList = exerciseList.filter { it != exercise }
                                        exerciseList = newList
                                        prefs.edit { putStringSet("exercise_list", newList.toSet()) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = Color.Red,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, null) }
                )
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    Button(
                        onClick = { currentDirPath = currentDir.parentFile!!.absolutePath },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("返回上级", style = MaterialTheme.typography.labelMedium)
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
                val extension = file.extension.lowercase()
                val isImage = extension in listOf("jpg", "jpeg", "png", "webp")
                val isVideo = extension in listOf("mp4", "mkv")

                if (file.isDirectory) {
                    Button(
                        onClick = { currentDirPath = file.absolutePath },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(file.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else if (isImage) {
                    Card(
                        onClick = { onFileSelected(file) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column {
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (isVideo) {
                    Button(
                        onClick = { onFileSelected(file) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Text(file.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
                Text(text = "v1.2.0", style = MaterialTheme.typography.labelSmall)
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
fun PermissionDeniedScreen(
    onRequestPermission: () -> Unit,
    onExitApp: () -> Unit,
    onShizukuAuthorize: () -> Unit
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
                OutlinedButton(onClick = onShizukuAuthorize, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("使用 Shizuku 授权")
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

// ==================== 按键检测页面 ====================

data class KeyEventInfo(
    val timestamp: Long,
    val keyCode: Int,
    val keyName: String,
    val repeatCount: Int
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

data class ButtonAvailability(
    val name: String,
    val available: Boolean
)

fun getKeyName(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_STEM_1 -> "STEM_1 (多功能键1)"
        KeyEvent.KEYCODE_STEM_2 -> "STEM_2 (多功能键2)"
        KeyEvent.KEYCODE_STEM_3 -> "STEM_3 (多功能键3)"
        KeyEvent.KEYCODE_BACK -> "BACK (返回键)"
        KeyEvent.KEYCODE_HOME -> "HOME (主页键)"
        KeyEvent.KEYCODE_VOLUME_UP -> "VOLUME_UP (音量+)"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "VOLUME_DOWN (音量-)"
        KeyEvent.KEYCODE_POWER -> "POWER (电源键)"
        KeyEvent.KEYCODE_DPAD_CENTER -> "DPAD_CENTER (表冠按下)"
        KeyEvent.KEYCODE_ENTER -> "ENTER (确认键)"
        KeyEvent.KEYCODE_UNKNOWN -> "UNKNOWN (未知)"
        else -> "Unknown($keyCode)"
    }
}

@Composable
fun ButtonTestScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val listState = rememberScalingLazyListState()

    var keyEvents by remember { mutableStateOf(listOf<KeyEventInfo>()) }
    var buttonInfo by remember { mutableStateOf<Map<Int, ButtonAvailability>>(emptyMap()) }

    val activity = context as? ComponentActivity
    LaunchedEffect(Unit) {
        if (activity != null) {
            val stem1Available = try {
                WearableButtons.getButtonInfo(activity, KeyEvent.KEYCODE_STEM_1) != null
            } catch (e: Exception) { false }
            val stem2Available = try {
                WearableButtons.getButtonInfo(activity, KeyEvent.KEYCODE_STEM_2) != null
            } catch (e: Exception) { false }
            val stem3Available = try {
                WearableButtons.getButtonInfo(activity, KeyEvent.KEYCODE_STEM_3) != null
            } catch (e: Exception) { false }
            val backAvailable = true

            buttonInfo = mapOf(
                KeyEvent.KEYCODE_STEM_1 to ButtonAvailability("STEM_1", stem1Available),
                KeyEvent.KEYCODE_STEM_2 to ButtonAvailability("STEM_2", stem2Available),
                KeyEvent.KEYCODE_STEM_3 to ButtonAvailability("STEM_3", stem3Available),
                KeyEvent.KEYCODE_BACK to ButtonAvailability("BACK", backAvailable),
                KeyEvent.KEYCODE_VOLUME_UP to ButtonAvailability("VOLUME_UP", false),
                KeyEvent.KEYCODE_VOLUME_DOWN to ButtonAvailability("VOLUME_DOWN", false),
            )
        }
    }

    DisposableEffect(view) {
        val keyListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val keyName = getKeyName(keyCode)
                val newEvent = KeyEventInfo(
                    timestamp = System.currentTimeMillis(),
                    keyCode = keyCode,
                    keyName = keyName,
                    repeatCount = event.repeatCount
                )
                keyEvents = listOf(newEvent) + keyEvents.take(19)
            }
            false
        }
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener(keyListener)
        onDispose {
            view.setOnKeyListener(null)
        }
    }

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "按键检测",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "设备按钮信息",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        buttonInfo.forEach { (_, info) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(info.name, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (info.available) "✓ 可用" else "✗ 不可用",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (info.available) Color(0xFF4CAF50) else Color.Gray
                                )
                            }
                        }
                        Text(
                            text = "提示：长按表冠/按钮可测试重复计数",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                ListHeader(modifier = Modifier.padding(top = 8.dp)) {
                    Text("最近按键记录", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (keyEvents.isEmpty()) {
                item {
                    Text(
                        text = "按下任意物理按键查看",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
            }

            items(keyEvents) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    onClick = { }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = event.keyName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "keyCode = ${event.keyCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = event.formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text("返回")
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
fun SettingsScreenPreview() { MaterialTheme { SettingsScreen({}, {}, {}, {}, {}, {}) } }

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun AboutScreenPreview() { MaterialTheme { AboutScreen() } }