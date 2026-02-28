package com.github.guoyujie666.fakelauncher.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.palette.graphics.Palette
import androidx.savedstate.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.lazy.*
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

class FloatingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var rootLayout: FrameLayout? = null

    private val homeEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore = _viewModelStore

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showFloatingWindow() {
        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }

        rootLayout = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
                if (event.action == AndroidKeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        AndroidKeyEvent.KEYCODE_STEM_1,
                        AndroidKeyEvent.KEYCODE_BACK -> {
                            homeEventFlow.tryEmit(Unit)
                            return true
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(this@FloatingService)
            setViewTreeViewModelStoreOwner(this@FloatingService)
            setViewTreeSavedStateRegistryOwner(this@FloatingService)

            val composeView = ComposeView(this@FloatingService).apply {
                setContent {
                    FakeLauncherServiceMainUI(
                        homeEventFlow = homeEventFlow,
                        onClose = { stopSelf() }
                    )
                }
            }
            addView(composeView)
        }

        windowManager.addView(rootLayout, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        rootLayout?.let {
            windowManager.removeView(it)
        }
        _viewModelStore.clear()
    }
}

enum class AppDestination {
    NONE, SETTINGS, HEART_RATE, EXERCISE, STOPWATCH, TIMER, ALARM
}

enum class SettingsDestination {
    MENU, DISPLAY, BATTERY, ABOUT, SYSTEM_OPS
}

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true
)

@Composable
fun FakeLauncherServiceMainUI(
    homeEventFlow: SharedFlow<Unit>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    
    var currentApp by remember { mutableStateOf(AppDestination.NONE) }
    var sharedBrightness by remember { mutableFloatStateOf(5f) }
    var currentSettingsScreen by remember { mutableStateOf<SettingsDestination?>(null) }
    var isPowerSaveMode by remember { mutableStateOf(prefs.getBoolean("is_power_save", false)) }

    // 假应用内部的二级状态，用于控制返回逻辑
    var isManualTimerSetting by remember { mutableStateOf(false) }
    var isAddingAlarm by remember { mutableStateOf(false) }

    // 假关机与重启状态
    var isFakePowerOff by remember { mutableStateOf(false) }
    var isFakeRestarting by remember { mutableStateOf(false) }
    var isPressingForPowerOn by remember { mutableStateOf(false) }
    
    // 开机文字
    var bootTextMain by remember { mutableStateOf("Xiaomi") }
    var bootTextSub by remember { mutableStateOf("HyperOS") }

    // 秒表全局状态
    var stopwatchTimeMillis by remember { mutableLongStateOf(0L) }
    var isStopwatchRunning by remember { mutableStateOf(false) }
    var stopwatchLaps by remember { mutableStateOf(listOf<Long>()) }

    // 倒计时全局状态
    var timerRemainingMillis by remember { mutableLongStateOf(0L) }
    var timerInitialMillis by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // 闹钟全局状态
    var alarms by remember { mutableStateOf(listOf<Alarm>()) }
    var ringingAlarm by remember { mutableStateOf<Alarm?>(null) }
    var ringtone by remember { mutableStateOf<Ringtone?>(null) }
    var lastFiredMinute by remember { mutableIntStateOf(-1) }

    // 控制应用退出动画是否包含淡出
    var shouldFadeOutOnAppExit by remember { mutableStateOf(true) }

    // 重启逻辑
    LaunchedEffect(isFakeRestarting) {
        if (isFakeRestarting) {
            bootTextMain = prefs.getString("boot_text_main", "Xiaomi") ?: "Xiaomi"
            bootTextSub = prefs.getString("boot_text_sub", "HyperOS") ?: "HyperOS"
            delay(5000)
            isFakeRestarting = false
            currentApp = AppDestination.NONE
            currentSettingsScreen = null
            pagerState.scrollToPage(1)
        }
    }

    // 关机后的长按 10s 开机逻辑
    LaunchedEffect(isPressingForPowerOn) {
        if (isPressingForPowerOn) {
            delay(10000)
            if (isPressingForPowerOn) {
                isFakePowerOff = false
                currentApp = AppDestination.NONE
                currentSettingsScreen = null
                pagerState.scrollToPage(1)
            }
        }
    }

    LaunchedEffect(isStopwatchRunning) {
        if (isStopwatchRunning) {
            val startTime = System.currentTimeMillis() - stopwatchTimeMillis
            while (isStopwatchRunning) {
                stopwatchTimeMillis = System.currentTimeMillis() - startTime
                delay(10)
            }
        }
    }

    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            var lastTime = System.currentTimeMillis()
            while (isTimerRunning && timerRemainingMillis > 0) {
                delay(100)
                val currentTime = System.currentTimeMillis()
                timerRemainingMillis = (timerRemainingMillis - (currentTime - lastTime)).coerceAtLeast(0)
                lastTime = currentTime
                if (timerRemainingMillis <= 0L) {
                    isTimerRunning = false
                    timerRemainingMillis = 0L
                    Toast.makeText(context.applicationContext, "倒计时结束", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(alarms) {
        while (true) {
            val now = Calendar.getInstance()
            val h = now.get(Calendar.HOUR_OF_DAY)
            val m = now.get(Calendar.MINUTE)
            if (m != lastFiredMinute) {
                alarms.find { it.isEnabled && it.hour == h && it.minute == m }?.let {
                    ringingAlarm = it
                    lastFiredMinute = m
                }
            }
            delay(1000)
        }
    }

    LaunchedEffect(ringingAlarm) {
        if (ringingAlarm != null) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            ringtone?.stop()
            ringtone = null
        }
    }

    val bgPath = remember { prefs.getString("bg_path", null) }
    val bgMode = remember { prefs.getInt("bg_mode", 0) }

    var adaptiveColor by remember { mutableStateOf(Color.White) }

    LaunchedEffect(bgPath) {
        if (bgPath != null) {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(bgPath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(bgPath)
                        if (bitmap != null) {
                            Palette.from(bitmap).generate().let { palette ->
                                val dominant = palette.dominantSwatch
                                if (dominant != null) {
                                    val luminance = 0.299 * dominant.rgb.let { Color(it).red } + 
                                                    0.587 * dominant.rgb.let { Color(it).green } + 
                                                    0.114 * dominant.rgb.let { Color(it).blue }
                                    adaptiveColor = if (luminance > 0.5) Color.Black else Color.White
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    adaptiveColor = Color.White
                }
            }
        } else {
            adaptiveColor = Color.White
        }
    }

    // 统一的应用内返回处理函数
    fun handleAppBack() {
        when (currentApp) {
            AppDestination.SETTINGS -> {
                if (currentSettingsScreen != null && currentSettingsScreen != SettingsDestination.MENU) {
                    currentSettingsScreen = SettingsDestination.MENU
                } else {
                    currentApp = AppDestination.NONE
                    currentSettingsScreen = null
                }
            }
            AppDestination.TIMER -> {
                if (isManualTimerSetting) isManualTimerSetting = false
                else currentApp = AppDestination.NONE
            }
            AppDestination.ALARM -> {
                if (isAddingAlarm) isAddingAlarm = false
                else currentApp = AppDestination.NONE
            }
            else -> currentApp = AppDestination.NONE
        }
    }

    // 当有应用进入时，重置退出动画的淡出效果为开启状态
    LaunchedEffect(currentApp) {
        if (currentApp != AppDestination.NONE) {
            shouldFadeOutOnAppExit = true
        }
    }

    LaunchedEffect(homeEventFlow) {
        homeEventFlow.collect {
            if (currentApp != AppDestination.NONE) {
                currentApp = AppDestination.NONE
                currentSettingsScreen = null
                isManualTimerSetting = false
                isAddingAlarm = false
            } else if (pagerState.currentPage != 1) {
                pagerState.animateScrollToPage(1)
            }
        }
    }

    var accumulatedDelta by remember { mutableFloatStateOf(0f) }
    val scrollThreshold = 50f

    MaterialTheme {
        AppScaffold {
            Box(modifier = Modifier.fillMaxSize()) {
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = currentApp == AppDestination.NONE,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .focusRequester(focusRequester)
                        .focusable()
                        .onRotaryScrollEvent { event ->
                            if (currentApp == AppDestination.NONE) {
                                val delta = event.verticalScrollPixels
                                accumulatedDelta += delta
                                if (!pagerState.isScrollInProgress) {
                                    if (abs(accumulatedDelta) > scrollThreshold) {
                                        val direction = if (accumulatedDelta > 0) 1 else -1
                                        val targetPage = (pagerState.currentPage + direction).coerceIn(0, 2)
                                        if (targetPage != pagerState.currentPage) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(targetPage)
                                            }
                                        }
                                        accumulatedDelta = 0f
                                    }
                                }
                            } else {
                                accumulatedDelta = 0f
                            }
                            true
                        }
                ) { page ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (bgMode == 1 && bgPath != null) {
                            val file = File(bgPath)
                            if (file.exists()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        when (page) {
                            0 -> ScreenScaffold(timeText = { TimeText() }) {
                                ControlCenterFloatingPage(
                                    brightness = sharedBrightness,
                                    onBrightnessChange = { sharedBrightness = it },
                                    textColor = adaptiveColor
                                )
                            }
                            1 -> WatchFaceFloatingPage(
                                onClose = onClose,
                                customBgPath = if (bgMode == 0) bgPath else null,
                                textColor = adaptiveColor,
                                isStopwatchRunning = isStopwatchRunning,
                                stopwatchTime = stopwatchTimeMillis,
                                isTimerRunning = isTimerRunning,
                                timerTime = timerRemainingMillis,
                                onIslandClick = { 
                                    if (isTimerRunning) currentApp = AppDestination.TIMER
                                    else if (isStopwatchRunning) currentApp = AppDestination.STOPWATCH 
                                }
                            )
                            2 -> AppDrawerFloatingPage(onAppClick = { currentApp = it })
                        }
                    }
                }

                // --- 全局应用覆盖层 ---
                AnimatedVisibility(
                    visible = currentApp != AppDestination.NONE,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + if (shouldFadeOutOnAppExit) fadeOut() else ExitTransition.None
                ) {
                    val swipeState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(swipeState.currentValue) {
                        if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
                            shouldFadeOutOnAppExit = false // Disable fade out for swipe dismiss
                            handleAppBack()
                            swipeState.snapTo(SwipeToDismissValue.Default)
                        }
                    }

                    SwipeToDismissBox(
                        state = swipeState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ScreenScaffold(timeText = { TimeText() }) {
                            when (currentApp) {
                                AppDestination.SETTINGS -> SettingsApp(
                                    currentScreen = currentSettingsScreen ?: SettingsDestination.MENU,
                                    onNavigate = { currentSettingsScreen = it },
                                    onBack = { handleAppBack() },
                                    brightness = sharedBrightness,
                                    onBrightnessChange = { sharedBrightness = it },
                                    isPowerSaveMode = isPowerSaveMode,
                                    onPowerSaveModeChange = { 
                                        isPowerSaveMode = it
                                        prefs.edit { putBoolean("is_power_save", it) }
                                    },
                                    onFakePowerOff = { isFakePowerOff = true },
                                    onFakeRestart = { isFakeRestarting = true }
                                )
                                AppDestination.HEART_RATE -> HeartRateApp(
                                    isPowerSaveMode = isPowerSaveMode,
                                    onBack = { handleAppBack() }
                                )
                                AppDestination.EXERCISE -> ExerciseApp(
                                    onBack = { handleAppBack() }
                                )
                                AppDestination.STOPWATCH -> StopwatchApp(
                                    isRunning = isStopwatchRunning,
                                    timeMillis = stopwatchTimeMillis,
                                    laps = stopwatchLaps,
                                    onToggleRunning = { isStopwatchRunning = !isStopwatchRunning },
                                    onAddLap = { stopwatchLaps = listOf(stopwatchTimeMillis) + stopwatchLaps },
                                    onReset = { stopwatchTimeMillis = 0L; stopwatchLaps = emptyList() },
                                    onBack = { handleAppBack() }
                                )
                                AppDestination.TIMER -> TimerApp(
                                    isRunning = isTimerRunning,
                                    remainingMillis = timerRemainingMillis,
                                    initialMillis = timerInitialMillis,
                                    isManualSetting = isManualTimerSetting,
                                    onManualSettingChange = { isManualTimerSetting = it },
                                    onStart = { duration -> 
                                        timerInitialMillis = duration
                                        timerRemainingMillis = duration
                                        isTimerRunning = true
                                    },
                                    onToggleRunning = { isTimerRunning = !isTimerRunning },
                                    onReset = { isTimerRunning = false; timerRemainingMillis = 0L },
                                    onBack = { handleAppBack() }
                                )
                                AppDestination.ALARM -> AlarmApp(
                                    alarms = alarms,
                                    isAdding = isAddingAlarm,
                                    onAddingChange = { isAddingAlarm = it },
                                    onAdd = { h, m -> alarms = alarms + Alarm(hour = h, minute = m) },
                                    onToggle = { alarm -> 
                                        alarms = alarms.map { if (it.id == alarm.id) it.copy(isEnabled = !it.isEnabled) else it } 
                                    },
                                    onDelete = { alarm -> alarms = alarms.filter { it.id != alarm.id } },
                                    onBack = { handleAppBack() }
                                )
                                else -> {}
                            }
                        }
                    }
                }

                // --- 闹钟响铃全屏覆盖 ---
                AnimatedVisibility(
                    visible = ringingAlarm != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFFF44336))
                            Spacer(Modifier.height(16.dp))
                            Text("闹钟响了", style = MaterialTheme.typography.titleLarge)
                            Text(String.format(Locale.getDefault(), "%02d:%02d", ringingAlarm?.hour ?: 0, ringingAlarm?.minute ?: 0), style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(32.dp))
                            Button(onClick = { ringingAlarm = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                Text("停止响铃")
                            }
                        }
                    }
                }

                // --- 假关机与重启全屏覆盖层 ---
                if (isFakePowerOff || isFakeRestarting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                if (isFakePowerOff) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressingForPowerOn = true
                                            try { awaitRelease() } finally { isPressingForPowerOn = false }
                                        }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFakeRestarting) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(bootTextMain, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(bootTextSub, color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

// 格式化时间的公用工具
fun formatTime(totalMillis: Long, showMillis: Boolean = true): String {
    val minutes = (totalMillis / 1000 / 60) % 60
    val seconds = (totalMillis / 1000) % 60
    return if (showMillis) {
        val millis = (totalMillis % 1000) / 10
        String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
    } else {
        val hours = (totalMillis / 1000 / 3600)
        if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

// --- 倒计时应用 ---
@Composable
fun TimerApp(
    isRunning: Boolean,
    remainingMillis: Long,
    initialMillis: Long,
    isManualSetting: Boolean,
    onManualSettingChange: (Boolean) -> Unit,
    onStart: (Long) -> Unit,
    onToggleRunning: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    val presets = listOf(
        "1分钟" to 60000L, "3分钟" to 180000L,
        "5分钟" to 300000L, "10分钟" to 600000L,
        "30分钟" to 1800000L, "1小时" to 3600000L
    )
    val listState = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (remainingMillis > 0 || isRunning) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val progress = if (initialMillis > 0) remainingMillis.toFloat() / initialMillis else 0f
                drawArc(
                    color = Color(0xFFFB8C00),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(formatTime(remainingMillis, false), style = MaterialTheme.typography.displayMedium.copy(fontSize = 42.sp))
                Row(modifier = Modifier.padding(top = 20.dp)) {
                    IconButton(onClick = onToggleRunning, modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                        Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { onReset() }, modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
                Button(onClick = onBack, modifier = Modifier.padding(top = 24.dp)) { Text("返回") }
            }
        } else if (isManualSetting) {
            ManualTimerPicker(
                onConfirm = { duration -> 
                    onStart(duration)
                    onManualSettingChange(false)
                },
                onCancel = { onManualSettingChange(false) }
            )
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp, start = 12.dp, end = 12.dp)
            ) {
                item { Text("倒计时", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
                items(presets) { preset ->
                    Button(onClick = { onStart(preset.second) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(preset.first)
                    }
                }
                item {
                    Button(
                        onClick = { onManualSettingChange(true) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("自定义时长")
                        }
                    }
                }
                item { Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) { Text("返回") } }
            }
        }
    }
}

@Composable
fun ManualTimerPicker(onConfirm: (Long) -> Unit, onCancel: () -> Unit) {
    val hoursState = rememberPickerState(24)
    val minutesState = rememberPickerState(60)
    val secondsState = rememberPickerState(60)

    var focusedColumn by remember { mutableIntStateOf(0) }
    val frHours = remember { FocusRequester() }
    val frMinutes = remember { FocusRequester() }
    val frSeconds = remember { FocusRequester() }

    LaunchedEffect(focusedColumn) {
        when (focusedColumn) {
            0 -> frHours.requestFocus()
            1 -> frMinutes.requestFocus()
            2 -> frSeconds.requestFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("选择时长", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Picker(
                state = hoursState,
                contentDescription = { "时" },
                modifier = Modifier.weight(1f).focusRequester(frHours).focusable().pointerInput(Unit) { detectTapGestures { focusedColumn = 0 } },
                readOnly = focusedColumn != 0
            ) {
                val isSelected = it == hoursState.selectedOptionIndex
                Text(
                    text = "${it}时",
                    style = if (isSelected) MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
                    color = if (isSelected) Color(0xFFFB8C00) else Color.White
                )
            }
            Picker(
                state = minutesState,
                contentDescription = { "分" },
                modifier = Modifier.weight(1f).focusRequester(frMinutes).focusable().pointerInput(Unit) { detectTapGestures { focusedColumn = 1 } },
                readOnly = focusedColumn != 1
            ) {
                val isSelected = it == minutesState.selectedOptionIndex
                Text(
                    text = "${it}分",
                    style = if (isSelected) MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
                    color = if (isSelected) Color(0xFFFB8C00) else Color.White
                )
            }
            Picker(
                state = secondsState,
                contentDescription = { "秒" },
                modifier = Modifier.weight(1f).focusRequester(frSeconds).focusable().pointerInput(Unit) { detectTapGestures { focusedColumn = 2 } },
                readOnly = focusedColumn != 2
            ) {
                val isSelected = it == secondsState.selectedOptionIndex
                Text(
                    text = "${it}秒",
                    style = if (isSelected) MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
                    color = if (isSelected) Color(0xFFFB8C00) else Color.White
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onCancel, modifier = Modifier.size(42.dp).background(Color.Red.copy(alpha = 0.2f), CircleShape)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
            }
            IconButton(
                onClick = {
                    val total = (hoursState.selectedOptionIndex * 3600 + minutesState.selectedOptionIndex * 60 + secondsState.selectedOptionIndex) * 1000L
                    if (total > 0) onConfirm(total)
                },
                modifier = Modifier.size(42.dp).background(Color.Green.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green)
            }
        }
    }
}

// --- 闹钟应用 ---
@Composable
fun AlarmApp(
    alarms: List<Alarm>,
    isAdding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    onAdd: (Int, Int) -> Unit,
    onToggle: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isAdding) {
            ManualAlarmPicker(
                onConfirm = { h, m -> onAdd(h, m); onAddingChange(false) },
                onCancel = { onAddingChange(false) }
            )
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp, start = 8.dp, end = 8.dp)
            ) {
                item { Text("闹钟", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
                items(alarms) { alarm ->
                    Button(
                        onClick = { onToggle(alarm) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = if (alarm.isEnabled) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onDelete(alarm) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item {
                    Button(onClick = { onAddingChange(true) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加闹钟")
                        }
                    }
                }
                item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("返回") } }
            }
        }
    }
}

@Composable
fun ManualAlarmPicker(onConfirm: (Int, Int) -> Unit, onCancel: () -> Unit) {
    val hState = rememberPickerState(24)
    val mState = rememberPickerState(60)
    var focusedCol by remember { mutableIntStateOf(0) }
    val frH = remember { FocusRequester() }
    val frM = remember { FocusRequester() }

    LaunchedEffect(focusedCol) { if (focusedCol == 0) frH.requestFocus() else frM.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("设置时间", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Picker(state = hState, contentDescription = { "" }, modifier = Modifier.weight(1f).focusRequester(frH).focusable().pointerInput(Unit) { detectTapGestures { focusedCol = 0 } }, readOnly = focusedCol != 0) {
                val isSelected = it == hState.selectedOptionIndex
                Text("${it}时", style = if (isSelected) MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, color = Color.Cyan) else MaterialTheme.typography.titleMedium)
            }
            Picker(state = mState, contentDescription = { "" }, modifier = Modifier.weight(1f).focusRequester(frM).focusable().pointerInput(Unit) { detectTapGestures { focusedCol = 1 } }, readOnly = focusedCol != 1) {
                val isSelected = it == mState.selectedOptionIndex
                Text("${it}分", style = if (isSelected) MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, color = Color.Cyan) else MaterialTheme.typography.titleMedium)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onCancel, modifier = Modifier.size(42.dp).background(Color.Red.copy(alpha = 0.2f), CircleShape)) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red) }
            IconButton(onClick = { onConfirm(hState.selectedOptionIndex, mState.selectedOptionIndex) }, modifier = Modifier.size(42.dp).background(Color.Green.copy(alpha = 0.2f), CircleShape)) { Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green) }
        }
    }
}

// --- 秒表应用 ---
@Composable
fun StopwatchApp(
    isRunning: Boolean,
    timeMillis: Long,
    laps: List<Long>,
    onToggleRunning: () -> Unit,
    onAddLap: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = (timeMillis % 60000) / 60000f
            drawArc(
                color = Color(0xFF4CAF50),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Text(text = formatTime(timeMillis), style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp), color = Color.White) }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = onToggleRunning, modifier = Modifier.size(48.dp).background(if (isRunning) Color.Red.copy(alpha = 0.2f) else Color.Green.copy(alpha = 0.2f), CircleShape)) {
                        Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = if (isRunning) Color.Red else Color.Green)
                    }
                    IconButton(onClick = { if (isRunning) onAddLap() else onReset() }, modifier = Modifier.size(48.dp).background(Color.Gray.copy(alpha = 0.2f), CircleShape)) {
                        Icon(if (isRunning) Icons.Default.Flag else Icons.Default.Refresh, contentDescription = null)
                    }
                }
            }
            itemsIndexed(laps) { index, lapTime ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("计次 ${laps.size - index}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(formatTime(lapTime), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) { Text("返回") } }
        }
    }
}

@Composable
fun ExerciseApp(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    val exerciseList = remember { prefs.getStringSet("exercise_list", setOf("户外跑", "健走", "骑行"))?.toList()?.sorted() ?: emptyList() }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp), autoCentering = null) {
            item { Text("运动", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            items(exerciseList) { exercise ->
                Button(onClick = { toastMessage = "传感器错误，请连接手机同步后重试" }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(exercise, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Button(onClick = { toastMessage = "请在手机上操作" }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("添加运动")
                        }
                    }
                }
            item { Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors()) { Text("返回") } }
        }
        CustomToast(message = toastMessage, onDismiss = { toastMessage = null })
    }
}

@Composable
fun HeartRateApp(isPowerSaveMode: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    var selectedSpeed by remember { mutableStateOf(prefs.getString("hr_speed", "智能") ?: "智能") }
    var heartRate by remember { mutableIntStateOf(72) }
    val speeds = listOf("智能", "1分钟", "5分钟", "10分钟", "1小时")
    LaunchedEffect(isPowerSaveMode) {
        if (isPowerSaveMode && selectedSpeed == "1分钟") {
            selectedSpeed = "5分钟"
            prefs.edit { putString("hr_speed", "5分钟") }
        }
    }
    LaunchedEffect(selectedSpeed) {
        val simulationDelay = when (selectedSpeed) {
            "智能" -> 2000L
            "1分钟" -> 10000L
            "5分钟" -> 30000L
            "10分钟" -> 60000L
            "1小时" -> 300000L
            else -> 2000L
        }
        while (true) { heartRate = Random.nextInt(68, 88); delay(simulationDelay) }
    }
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(40.dp)) }
            item {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Text(text = "$heartRate", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(text = "次/分钟", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp), color = Color.Gray)
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "心率检测速度", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(start = 8.dp))
            }
            items(speeds) { speed ->
                val isActuallyLocked = isPowerSaveMode && speed == "1分钟"
                Button(onClick = { if (!isActuallyLocked) { selectedSpeed = speed; prefs.edit { putString("hr_speed", speed) } } }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = if (selectedSpeed == speed) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(), enabled = !isActuallyLocked) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = speed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = if (isActuallyLocked) Color.DarkGray else Color.Unspecified)
                        if (isActuallyLocked) { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.DarkGray) }
                        else if (selectedSpeed == speed) { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
            item { Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) { Text("返回") } }
        }
    }
}

@Composable
fun SettingsApp(
    currentScreen: SettingsDestination, 
    onNavigate: (SettingsDestination) -> Unit, 
    onBack: () -> Unit, 
    brightness: Float, 
    onBrightnessChange: (Float) -> Unit, 
    isPowerSaveMode: Boolean, 
    onPowerSaveModeChange: (Boolean) -> Unit,
    onFakePowerOff: () -> Unit,
    onFakeRestart: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (currentScreen) {
            SettingsDestination.MENU -> SettingsMenuPage(onNavigate, onBack)
            SettingsDestination.DISPLAY -> DisplaySettingsPage(brightness, onBrightnessChange, onBack)
            SettingsDestination.BATTERY -> BatterySettingsPage(isPowerSaveMode, onPowerSaveModeChange, onBack)
            SettingsDestination.ABOUT -> AboutWatchPage(onBack)
            SettingsDestination.SYSTEM_OPS -> SystemOpsPage(
                onPowerOff = onFakePowerOff,
                onRestart = onFakeRestart,
                onBack = { onBack() }
            )
        }
    }
}

@Composable
fun SettingsMenuPage(onNavigate: (SettingsDestination) -> Unit, onBack: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp), autoCentering = null) {
        item { Text("设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
        item { SettingsItem(Icons.Default.Brightness6, "显示与亮度") { onNavigate(SettingsDestination.DISPLAY) } }
        item { SettingsItem(Icons.Default.BatteryFull, "电池") { onNavigate(SettingsDestination.BATTERY) } }
        item { SettingsItem(Icons.Default.Watch, "关于手表") { onNavigate(SettingsDestination.ABOUT) } }
        item { SettingsItem(Icons.Default.PowerSettingsNew, "系统操作") { onNavigate(SettingsDestination.SYSTEM_OPS) } }
        item { Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors()) { Text("退出设置") } }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp) ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun DisplaySettingsPage(brightness: Float, onBrightnessChange: (Float) -> Unit, onBack: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp)) {
        item { Text("显示与亮度", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("亮度: ${brightness.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Slider(value = brightness, onValueChange = onBrightnessChange, valueRange = 0f..10f, steps = 9, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("息屏显示", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text("开启后续航时间变短", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                }
                Icon(imageVector = Icons.Default.ToggleOff, contentDescription = null, tint = Color.DarkGray)
            }
        }
        item { Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) { Text("返回") } }
    }
}

@Composable
fun BatterySettingsPage(isPowerSaveMode: Boolean, onPowerSaveModeChange: (Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var batteryLevel by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter -> context.registerReceiver(null, filter) }
        batteryLevel = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
    }
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("电池", style = MaterialTheme.typography.titleSmall) }
        item {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp).padding(10.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(color = Color.DarkGray, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(color = if (batteryLevel > 20) Color(0xFF4CAF50) else Color(0xFFF44336), startAngle = -90f, sweepAngle = (batteryLevel / 100f) * 360f, useCenter = false, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$batteryLevel%", style = MaterialTheme.typography.titleLarge)
                    Text(text = "剩余电量", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp).clickable { onPowerSaveModeChange(!isPowerSaveMode) }, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) { Text("省电模式", style = MaterialTheme.typography.bodyMedium) }
                Icon(imageVector = if (isPowerSaveMode) Icons.Default.ToggleOn else Icons.Default.ToggleOff, contentDescription = null, tint = if (isPowerSaveMode) Color(0xFF4CAF50) else Color.DarkGray, modifier = Modifier.size(32.dp))
            }
        }
        item { Text(text = "开启省电模式后，将限制心率检测速度至每五分钟检测。", style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Start, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
        item { Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) { Text("返回") } }
    }
}

@Composable
fun AboutWatchPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }
    val systemVersion = remember { prefs.getString("watch_sys_ver", "Xiaomi HyperOS Lite 1.5.3") ?: "Xiaomi HyperOS Lite 1.5.3" }
    val deviceModel = remember { prefs.getString("watch_model", "Xiaomi Watch S4") ?: "Xiaomi Watch S4" }
    val serialNumber = remember { prefs.getString("watch_sn", "A2304S40001298X") ?: "A2304S40001298X" }
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 16.dp, end = 16.dp)) {
        item { Text("关于手表", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item { AboutInfoRow("系统版本", systemVersion) }
        item { AboutInfoRow("设备型号", deviceModel) }
        item { AboutInfoRow("序列号", serialNumber) }
        item { AboutInfoRow("法律信息", "点击查看") }
        item { Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) { Text("返回") } }
    }
}

@Composable
fun AboutInfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.5f)))
    }
}

@Composable
fun SystemOpsPage(onPowerOff: () -> Unit, onRestart: () -> Unit, onBack: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp)) {
        item { Text("系统操作", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item { 
            Button(
                onClick = onPowerOff, 
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.5f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("关机")
                }
            } 
        }
        item { 
            Button(
                onClick = onRestart, 
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("重启")
                }
            } 
        }
        item { Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) { Text("返回") } }
    }
}

@Composable
fun WatchFaceFloatingPage(onClose: () -> Unit, customBgPath: String?, textColor: Color, isStopwatchRunning: Boolean, stopwatchTime: Long, isTimerRunning: Boolean, timerTime: Long, onIslandClick: () -> Unit) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { currentTime = System.currentTimeMillis(); delay(1000) } }
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    var clickCount by remember { mutableIntStateOf(0) }
    var windowStartTime by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (customBgPath != null) {
            val file = File(customBgPath)
            if (file.exists()) {
                AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = isStopwatchRunning || isTimerRunning, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(modifier = Modifier.padding(bottom = 8.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).clickable { onIslandClick() }.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isTimerRunning) Icons.Default.HourglassTop else Icons.Default.Timer, contentDescription = null, tint = if (isTimerRunning) Color(0xFFFB8C00) else Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(text = if (isTimerRunning) formatTime(timerTime, false) else formatTime(stopwatchTime), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }

            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (now - windowStartTime > 5000) {
                                clickCount = 1
                                windowStartTime = now
                            } else {
                                clickCount++
                            }
                        },
                        onLongPress = {
                            if (clickCount >= 10) {
                                onClose()
                            }
                        }
                    )
                }
            ) {
                Text(
                    text = timeFormat.format(Date(currentTime)),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 42.sp, fontWeight = FontWeight.Bold),
                    color = textColor,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun ControlCenterFloatingPage(brightness: Float, onBrightnessChange: (Float) -> Unit, textColor: Color) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 24.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, autoCentering = null) {
        item { Text("控制中心", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp), color = textColor) }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { FloatingToggleItem(Icons.Default.BatteryChargingFull, "省电"); FloatingToggleItem(Icons.Default.Bluetooth, "蓝牙") } }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { FloatingToggleItem(Icons.Default.DoNotDisturbOn, "勿扰"); FloatingToggleItem(Icons.Default.AirplanemodeActive, "飞行") } }
        item { Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) { IconButton(onClick = { if (brightness > 0) onBrightnessChange(brightness - 1) }) { Icon(Icons.Default.Remove, contentDescription = null, tint = textColor) }
                Text(text = "亮度: ${brightness.toInt()}", style = MaterialTheme.typography.labelMedium, color = textColor)
                IconButton(onClick = { if (brightness < 10) onBrightnessChange(brightness + 1) }) { Icon(Icons.Default.Add, contentDescription = null, tint = textColor) } } }
    }
}

@Composable
fun AppDrawerFloatingPage(onAppClick: (AppDestination) -> Unit) {
    val listState = rememberScalingLazyListState()
    val apps = listOf(
        "设置" to Icons.Default.Settings to AppDestination.SETTINGS,
        "心率" to Icons.Default.Favorite to AppDestination.HEART_RATE,
        "运动" to Icons.AutoMirrored.Filled.DirectionsRun to AppDestination.EXERCISE,
        "秒表" to Icons.Default.Timer to AppDestination.STOPWATCH,
        "倒计时" to Icons.Default.HourglassBottom to AppDestination.TIMER,
        "闹钟" to Icons.Default.Alarm to AppDestination.ALARM
    )
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp), autoCentering = null) {
        items(apps) { item -> val (appInfo, dest) = item
            Button(onClick = { if (dest != AppDestination.NONE) onAppClick(dest) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(appInfo.second, contentDescription = null); Spacer(Modifier.width(12.dp)); Text(appInfo.first) } } }
    }
}

@Composable
fun FloatingToggleItem(icon: ImageVector, label: String) {
    var checked by remember { mutableStateOf(false) }
    IconToggleButton(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.padding(4.dp)) { Icon(icon, contentDescription = label) }
}

@Composable
fun CustomToast(message: String?, onDismiss: () -> Unit) {
    LaunchedEffect(message) { if (message != null) { delay(2000); onDismiss() } }
    AnimatedVisibility(visible = message != null, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.padding(horizontal = 24.dp).background(Color.DarkGray.copy(alpha = 0.9f), CircleShape).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(text = message ?: "", style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}
