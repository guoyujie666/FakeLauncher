package com.guoyujie666.fakelauncher.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.IBinder
import android.view.Gravity
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.isActive
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.combinedClickable
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.*
import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.wear.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalLocale
import androidx.wear.compose.material3.TimeText
import kotlinx.coroutines.Job

class FloatingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var rootLayout: FrameLayout? = null

    private val homeEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var key1Count = 0
    private var lastKey1Time = 0L
    private var isArmed = false
    @Volatile
    var isAppActive = false
        private set

    fun setAppActive(active: Boolean) {
        isAppActive = active
    }
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
            private var downTime = 0L
            private val LONG_PRESS_TIMEOUT = 1000L

            override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
                val keyCode = event.keyCode
                val action = event.action
                val repeatCount = event.repeatCount
                val isTargetKey = keyCode == 265 ||
                        keyCode == AndroidKeyEvent.KEYCODE_STEM_1 ||
                        keyCode == AndroidKeyEvent.KEYCODE_STEM_2 ||
                        keyCode == AndroidKeyEvent.KEYCODE_STEM_3 ||
                        keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_ENTER

                if (!isTargetKey) {
                    if (action == AndroidKeyEvent.ACTION_DOWN && keyCode == AndroidKeyEvent.KEYCODE_BACK && !isAppActive) {
                        homeEventFlow.tryEmit(Unit)
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }

                when (action) {
                    AndroidKeyEvent.ACTION_DOWN -> {
                        if (repeatCount == 0) {
                            downTime = System.currentTimeMillis()
                            if (!isAppActive) {
                                val now = downTime
                                if (now - lastKey1Time > 5000) key1Count = 0
                                key1Count++
                                lastKey1Time = now
                                if (key1Count >= 10) {
                                    isArmed = true
                                    key1Count = 0
                                }
                            }
                        }
                        return true
                    }
                    AndroidKeyEvent.ACTION_UP -> {
                        if (!isAppActive && isArmed) {
                            val duration = System.currentTimeMillis() - downTime
                            if (duration >= LONG_PRESS_TIMEOUT) {
                                stopSelf()
                                isArmed = false
                                return true
                            }
                        }
                        return true
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(this@FloatingService)
            setViewTreeViewModelStoreOwner(this@FloatingService)
            setViewTreeSavedStateRegistryOwner(this@FloatingService)
            post {
                requestFocus()
                isFocusable = true
                isFocusableInTouchMode = true
            }
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

    var isManualTimerSetting by remember { mutableStateOf(false) }
    var isAddingAlarm by remember { mutableStateOf(false) }

    var isFakePowerOff by remember { mutableStateOf(false) }
    var isFakeRestarting by remember { mutableStateOf(false) }
    var isPressingForPowerOn by remember { mutableStateOf(false) }

    // 开机画面相关状态
    var restartStage by remember { mutableIntStateOf(-1) } // -1: 未重启, 0: 黑屏, 1: 第一屏, 2: 第二屏
    var bootImagePath1 by remember { mutableStateOf<String?>(null) }
    var bootImagePath2 by remember { mutableStateOf<String?>(null) }
    var bootDurationMs by remember { mutableLongStateOf(5000L) }

    var stopwatchTimeMillis by remember { mutableLongStateOf(0L) }
    var isStopwatchRunning by remember { mutableStateOf(false) }
    var stopwatchLaps by remember { mutableStateOf(listOf<Long>()) }

    var timerRemainingMillis by remember { mutableLongStateOf(0L) }
    var timerInitialMillis by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }

    var alarms by remember { mutableStateOf(listOf<Alarm>()) }
    var ringingAlarm by remember { mutableStateOf<Alarm?>(null) }
    var ringtone by remember { mutableStateOf<Ringtone?>(null) }
    var lastFiredMinute by remember { mutableIntStateOf(-1) }

    // 隐藏时间指示器的条件：假关机/假重启时，或控制中心、应用抽屉中均不显示
    val shouldHideTimeText = when {
        isFakePowerOff || isPressingForPowerOn || isFakeRestarting -> true
        // 当未进入任何应用，且当前页面是控制中心(0)或应用抽屉(2)时隐藏；表盘页(1)由表盘自己显示时间
        currentApp == AppDestination.NONE && pagerState.currentPage != 1 -> true
        else -> false
    }

    // 假重启逻辑
    LaunchedEffect(isFakeRestarting) {
        if (isFakeRestarting) {
            // 读取配置
            bootImagePath1 = prefs.getString("boot_image_path_1", null)
            bootImagePath2 = prefs.getString("boot_image_path_2", null)
            val durationSec = prefs.getInt("boot_image_duration", 5)
            bootDurationMs = (durationSec * 1000L)

            // 第一阶段：黑屏 8 秒
            restartStage = 0
            delay(8000)

            // 第二阶段：显示第一屏（如果存在）
            if (bootImagePath1 != null && File(bootImagePath1!!).exists()) {
                restartStage = 1
                delay(bootDurationMs)
            }

            // 第三阶段：显示第二屏（如果存在）
            if (bootImagePath2 != null && File(bootImagePath2!!).exists()) {
                restartStage = 2
                delay(bootDurationMs)
            }

            // 结束重启
            isFakeRestarting = false
            restartStage = -1
            currentApp = AppDestination.NONE
            currentSettingsScreen = null
            pagerState.scrollToPage(1)
        }
    }

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
            while (isActive && isStopwatchRunning) {
                stopwatchTimeMillis = System.currentTimeMillis() - startTime
                delay(10)
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

    var bgEnabled by remember { mutableStateOf(prefs.getBoolean("bg_enabled", true)) }
    var bgPath by remember { mutableStateOf(prefs.getString("bg_path", null)) }
    var bgMode by remember { mutableIntStateOf(prefs.getInt("bg_mode", 0)) }
    var bgBlurEnabled by remember { mutableStateOf(prefs.getBoolean("bg_blur", true)) }
    var bgBlurAmount by remember { mutableFloatStateOf(prefs.getFloat("bg_blur_amount", 10f)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "bg_enabled" -> bgEnabled = p.getBoolean(key, true)
                "bg_path" -> bgPath = p.getString(key, null)
                "bg_mode" -> bgMode = p.getInt(key, 0)
                "bg_blur" -> bgBlurEnabled = p.getBoolean(key, true)
                "bg_blur_amount" -> bgBlurAmount = p.getFloat(key, 10f)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    var adaptiveColor by remember { mutableStateOf(Color.White) }

    LaunchedEffect(bgPath, bgEnabled) {
        if (bgEnabled && bgPath != null) {
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

    fun handleAppBack(isGesture: Boolean = false) {
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
                // 底层：表盘与背景
                val distanceFromPage1 = abs((pagerState.currentPage - 1) + pagerState.currentPageOffsetFraction)
                val dynamicBlurRadius = if (bgBlurEnabled && distanceFromPage1 > 0.01f) {
                    (distanceFromPage1 * bgBlurAmount).coerceAtLeast(0.1f).dp
                } else 0.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .then(if (dynamicBlurRadius > 0.dp) Modifier.blur(dynamicBlurRadius) else Modifier)
                ) {
                    if (bgEnabled && bgMode == 1 && bgPath != null) {
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

                    WatchFaceFloatingPage(
                        onClose = onClose,
                        customBgPath = if (bgEnabled && bgMode == 0) bgPath else null,
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
                }

                // 滑动覆盖层（控制中心、中心占位、应用抽屉）
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = currentApp == AppDestination.NONE,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .focusable()
                ) { page ->
                    when (page) {
                        0 -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                ScreenScaffold(timeText = {
                                    if (!shouldHideTimeText) { TimeText() }
                                }) {
                                    ControlCenterFloatingPage(
                                        brightness = sharedBrightness,
                                        onBrightnessChange = { sharedBrightness = it },
                                        textColor = if (bgEnabled && bgMode == 1) adaptiveColor else Color.White
                                    )
                                }
                            }
                        }
                        1 -> Box(modifier = Modifier.fillMaxSize())
                        2 -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                ScreenScaffold(timeText = {
                                    if (!shouldHideTimeText) { TimeText() }
                                }) {
                                    AppDrawerFloatingPage(onAppClick = { currentApp = it })
                                }
                            }
                        }
                    }
                }

                // 全局应用覆盖层
                if (currentApp != AppDestination.NONE) {
                    val swipeState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(swipeState.currentValue) {
                        if (swipeState.currentValue == SwipeToDismissValue.Dismissed) {
                            handleAppBack(isGesture = true)
                            swipeState.snapTo(SwipeToDismissValue.Default)
                        }
                    }

                    SwipeToDismissBox(
                        state = swipeState,
                        modifier = Modifier.fillMaxSize()
                    ) { isBackground ->
                        if (isBackground) {
                            Box(modifier = Modifier
                                .fillMaxSize()
                                ) {
                                when {
                                    currentApp == AppDestination.SETTINGS && currentSettingsScreen != null && currentSettingsScreen != SettingsDestination.MENU -> {
                                        SettingsMenuPage(onNavigate = {}, onBack = {})
                                    }
                                    currentApp == AppDestination.TIMER && isManualTimerSetting -> {
                                        TimerApp(
                                            isRunning = isTimerRunning, remainingMillis = timerRemainingMillis, initialMillis = timerInitialMillis,
                                            isManualSetting = false,
                                            onManualSettingChange = {}, onStart = {}, onToggleRunning = {}, onReset = {}, onBack = {}
                                        )
                                    }
                                    currentApp == AppDestination.ALARM && isAddingAlarm -> {
                                        AlarmApp(
                                            alarms = alarms, isAdding = false,
                                            onAddingChange = {}, onAdd = { _, _ -> }, onToggle = {}, onDelete = {}, onBack = {}
                                        )
                                    }
                                    else -> {
                                    }
                                }
                            }
                        } else {
                            ScreenScaffold(timeText = {
                                if (!shouldHideTimeText) { TimeText() }
                            }) {
                                when (currentApp) {
                                    AppDestination.SETTINGS -> SettingsApp(
                                        currentScreen = currentSettingsScreen ?: SettingsDestination.MENU,
                                        onNavigate = { currentSettingsScreen = it },
                                        onBack = { handleAppBack(isGesture = true) },
                                        onClose = onClose,
                                        brightness = sharedBrightness,
                                        onBrightnessChange = { sharedBrightness = it },
                                        isPowerSaveMode = isPowerSaveMode,
                                        onPowerSaveModeChange = {
                                            isPowerSaveMode = it
                                            prefs.edit { putBoolean("is_power_save", it) }
                                        },
                                        onFakePowerOff = { isFakePowerOff = true },
                                        onFakeRestart = { isFakeRestarting = true },
                                    )
                                    AppDestination.HEART_RATE -> HeartRateApp(
                                        isPowerSaveMode = isPowerSaveMode,
                                        onBack = { handleAppBack(isGesture = true) }
                                    )
                                    AppDestination.EXERCISE -> ExerciseApp(
                                        onBack = { handleAppBack(isGesture = true) }
                                    )
                                    AppDestination.STOPWATCH -> StopwatchApp(
                                        isRunning = isStopwatchRunning,
                                        timeMillis = stopwatchTimeMillis,
                                        laps = stopwatchLaps,
                                        onToggleRunning = { isStopwatchRunning = !isStopwatchRunning },
                                        onAddLap = { stopwatchLaps = listOf(stopwatchTimeMillis) + stopwatchLaps },
                                        onReset = { stopwatchTimeMillis = 0L; stopwatchLaps = emptyList() },
                                        onBack = { handleAppBack(isGesture = true) }
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
                                        onBack = { handleAppBack(isGesture = true) },
                                        onRemainingUpdate = { newRemaining ->
                                            timerRemainingMillis = newRemaining
                                        }
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
                                        onBack = { handleAppBack(isGesture = true) }
                                    )
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                // 闹钟响铃覆盖层
                AnimatedVisibility(
                    visible = ringingAlarm != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFFF44336))
                            Spacer(Modifier.height(16.dp))
                            Text("闹钟响了", style = MaterialTheme.typography.titleLarge)
                            Text(String.format(LocalLocale.current.platformLocale, "%02d:%02d", ringingAlarm?.hour ?: 0, ringingAlarm?.minute ?: 0), style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(32.dp))
                            Button(onClick = { ringingAlarm = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                Text("停止响铃")
                            }
                        }
                    }
                }

                // 假关机/重启覆盖层
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
                            when (restartStage) {
                                0 -> { /* 黑屏阶段，什么都不显示 */ }
                                1 -> {
                                    if (bootImagePath1 != null) {
                                        AsyncImage(
                                            model = File(bootImagePath1!!),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                                2 -> {
                                    if (bootImagePath2 != null) {
                                        AsyncImage(
                                            model = File(bootImagePath2!!),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        } else if (isFakePowerOff) {
                            // 保持黑屏
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
    onBack: () -> Unit,
    onRemainingUpdate: ((Long) -> Unit)? = null
) {
    // 进度动画对象，值在 0f..1f 之间（1 表示满）
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // 目标结束时间（基于系统时钟），仅在运行时有效
    var targetEndTime by remember { mutableLongStateOf(0L) }
    // 记录本次倒计时的初始时长（用于计算比例）
    var lastInitial by remember { mutableStateOf(initialMillis) }

    // 外部 initialMillis 变化时（例如用户选取了新时长），重置所有状态
    LaunchedEffect(initialMillis) {
        if (initialMillis > 0) {
            progress.snapTo(1f)
            lastInitial = initialMillis
            targetEndTime = 0L
            onRemainingUpdate?.invoke(initialMillis)
        }
    }

    // 核心：基于系统时钟同步进度，彻底消除动画滞后
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val currentRemaining = remainingMillis
            if (currentRemaining <= 0) return@LaunchedEffect

            // 设定结束时间点 = 当前系统时间 + 当前剩余毫秒数
            targetEndTime = System.currentTimeMillis() + currentRemaining

            // 帧循环更新进度，直到暂停或计时结束
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = (targetEndTime - now).coerceAtLeast(0)
                val progressValue = if (lastInitial > 0) {
                    remaining.toFloat() / lastInitial
                } else {
                    0f
                }.coerceIn(0f, 1f)

                // 直接跳转到精确进度，无需动画过渡
                progress.snapTo(progressValue)
                onRemainingUpdate?.invoke(remaining)

                if (remaining <= 0) {
                    // 计时结束，通知外层停止运行
                    onToggleRunning()
                    break
                }

                // 等待下一帧（约 16ms），降低 CPU 消耗同时保证流畅
                kotlinx.coroutines.delay(16)
            }
        } else {
            // 暂停时立即记录最终剩余时间，不再更新进度
            val now = System.currentTimeMillis()
            val remaining = (targetEndTime - now).coerceAtLeast(0)
            onRemainingUpdate?.invoke(remaining)
        }
    }

    // 重置函数（供按钮调用）
    fun resetTimer() {
        scope.launch {
            progress.snapTo(1f)
            targetEndTime = 0L
            onRemainingUpdate?.invoke(lastInitial)
        }
    }

    // UI 显示逻辑
    val showTimerFace = isRunning || (remainingMillis > 0 && !isManualSetting)

    Box(modifier = Modifier.fillMaxSize()) {
        if (showTimerFace) {
            // 计时界面
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    startAngle = 270f,
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    strokeWidth = 6.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatTime(remainingMillis, false),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        IconButton(
                            onClick = onToggleRunning,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(
                            onClick = {
                                resetTimer()
                                onReset()
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                }
            }
        } else if (isManualSetting) {
            // 手动设置时长界面
            ManualTimerPicker(
                onConfirm = { duration ->
                    onStart(duration)
                    onManualSettingChange(false)
                },
                onCancel = { onManualSettingChange(false) }
            )
        } else {
            // 预设列表
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberScalingLazyListState(),
                contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { ListHeader { Text("倒计时") } }
                items(listOf("1 分钟" to 60000L, "3 分钟" to 180000L, "5 分钟" to 300000L, "10 分钟" to 600000L)) { preset ->
                    Button(
                        onClick = { onStart(preset.second) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Text(preset.first, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
                item {
                    Button(
                        onClick = { onManualSettingChange(true) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("自定义")
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ManualTimerPicker(onConfirm: (Long) -> Unit, onCancel: () -> Unit) {
    val hoursState = rememberPickerState(24)
    val minutesState = rememberPickerState(60)
    val secondsState = rememberPickerState(60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("选择时长", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 时间单位简化显示，符合 M3 紧凑型选择器风格
            val pickerModifier = Modifier
                .weight(1f)
                .height(100.dp)

            Picker(state = hoursState, contentDescription = {"时"}, modifier = pickerModifier) {
                Text(text = "%02d".format(it), style = MaterialTheme.typography.displaySmall)
            }
            Text(":", style = MaterialTheme.typography.displaySmall)
            Picker(state = minutesState, contentDescription = {"分"}, modifier = pickerModifier) {
                Text(text = "%02d".format(it), style = MaterialTheme.typography.displaySmall)
            }
            Text(":", style = MaterialTheme.typography.displaySmall)
            Picker(state = secondsState, contentDescription = { "秒" }, modifier = pickerModifier) {
                Text(text = "%02d".format(it), style = MaterialTheme.typography.displaySmall)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = onCancel,
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(Icons.Default.Close, contentDescription = "取消")
            }
            IconButton(
                onClick = {
                    val total = (hoursState.selectedOptionIndex * 3600 +
                            minutesState.selectedOptionIndex * 60 +
                            secondsState.selectedOptionIndex) * 1000L
                    if (total > 0) onConfirm(total)
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "确认")
            }
        }
    }
}

// --- 闹钟应用（无返回按钮）---
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

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = if (alarm.isEnabled) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(String.format(LocalLocale.current.platformLocale, "%02d:%02d", alarm.hour, alarm.minute), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onDelete(alarm) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item {
                    Button(onClick = { onAddingChange(true) }, modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加闹钟")
                        }
                    }
                }
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

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("设置时间", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Picker(state = hState, contentDescription = { "" }, modifier = Modifier
                .weight(1f)
                .focusRequester(frH)
                .focusable()
                .pointerInput(Unit) { detectTapGestures { focusedCol = 0 } }, readOnly = focusedCol != 0) {
                val isSelected = it == hState.selectedOptionIndex
                Text("${it}时", style = if (isSelected) MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, color = Color.Cyan) else MaterialTheme.typography.titleMedium)
            }
            Picker(state = mState, contentDescription = { "" }, modifier = Modifier
                .weight(1f)
                .focusRequester(frM)
                .focusable()
                .pointerInput(Unit) { detectTapGestures { focusedCol = 1 } }, readOnly = focusedCol != 1) {
                val isSelected = it == mState.selectedOptionIndex
                Text("${it}分", style = if (isSelected) MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, color = Color.Cyan) else MaterialTheme.typography.titleMedium)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onCancel, modifier = Modifier
                .size(42.dp)
                .background(Color.Red.copy(alpha = 0.2f), CircleShape)) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red) }
            IconButton(onClick = { onConfirm(hState.selectedOptionIndex, mState.selectedOptionIndex) }, modifier = Modifier
                .size(42.dp)
                .background(Color.Green.copy(alpha = 0.2f), CircleShape)) { Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green) }
        }
    }
}

// --- 秒表应用（无返回按钮）---
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
    val currentTime by rememberUpdatedState(timeMillis)
    val listState = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            // 【核心修复 2】：使用 currentTime（包装后的状态）进行计算
            progress = { (currentTime % 60000).toFloat() / 60000f },
            modifier = Modifier.fillMaxSize().padding(4.dp),
            startAngle = 270f,
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                trackColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            strokeWidth = 6.dp
        )

        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 42.dp, bottom = 42.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    // 【核心修复 3】：这里也改为 currentTime，确保文字也会跳动
                    text = formatTime(currentTime),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // 控制按钮组
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 开始/暂停按钮
                    IconButton(
                        onClick = onToggleRunning,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isRunning)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isRunning) "暂停" else "开始"
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    // 计次/重置按钮
                    IconButton(
                        onClick = { if (isRunning) onAddLap() else onReset() },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors()
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Flag else Icons.Default.Refresh,
                            contentDescription = if (isRunning) "计次" else "重置"
                        )
                    }
                }
            }

            // 计次列表：使用 Card 让每一项更符合 M3 层级感
            itemsIndexed(laps) { index, lapTime ->
                Card(
                    onClick = {},
                    enabled = false, // 纯显示
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "计次 ${laps.size - index}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatTime(lapTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
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
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp), autoCentering = null) {
            item { Text("运动", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            items(exerciseList) { exercise ->
                Button(onClick = { toastMessage = "传感器错误，请连接手机同步后重试" }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp), colors = ButtonDefaults.filledTonalButtonColors()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(exercise, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Button(onClick = { toastMessage = "请在手机上操作" }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp), colors = ButtonDefaults.buttonColors()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("添加运动")
                    }
                }
            }
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
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
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
                Text(text = "心率检测速度", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp))
            }
            items(speeds) { speed ->
                val isActuallyLocked = isPowerSaveMode && speed == "1分钟"
                Button(onClick = { if (!isActuallyLocked) { selectedSpeed = speed; prefs.edit { putString("hr_speed", speed) } } }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp), colors = if (selectedSpeed == speed) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(), enabled = !isActuallyLocked) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = speed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = if (isActuallyLocked) Color.DarkGray else Color.Unspecified)
                        if (isActuallyLocked) { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.DarkGray) }
                        else if (selectedSpeed == speed) { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsApp(
    currentScreen: SettingsDestination,
    onNavigate: (SettingsDestination) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    isPowerSaveMode: Boolean,
    onPowerSaveModeChange: (Boolean) -> Unit,
    onFakePowerOff: () -> Unit,
    onFakeRestart: () -> Unit
) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
        when (currentScreen) {
            SettingsDestination.MENU -> SettingsMenuPage(onNavigate, onBack)
            SettingsDestination.DISPLAY -> DisplaySettingsPage(brightness, onBrightnessChange, onBack)
            SettingsDestination.BATTERY -> BatterySettingsPage(isPowerSaveMode, onPowerSaveModeChange, onBack)
            SettingsDestination.ABOUT -> AboutWatchPage(onBack,onClose)
            SettingsDestination.SYSTEM_OPS -> SystemOpsPage(
                onPowerOff = onFakePowerOff,
                onRestart = onFakeRestart,
                onBack = onBack
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
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp), colors = ButtonDefaults.filledTonalButtonColors() ) {
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

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp, start = 8.dp, end = 8.dp)
    ) {
        item {
            ListHeader {
                Text("显示与亮度")
            }
        }

        // 1. 将亮度文字和 Slider 包裹在 Card 中
        item {
            Card(
                onClick = { /* 仅展示调节，背景不响应 */ },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    // 第一层：左右布局 (标签 和 数值)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween, // 左右两端对齐
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "亮度",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = brightness.toInt().toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary // 数值使用主色突出
                        )
                    }

                    // 第二层：调节器
                    Slider(
                        value = brightness,
                        onValueChange = onBrightnessChange,
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp) // 与上方文字留出间距
                    )
                }
            }
        }

        // 2. 息屏显示（SwitchButton 已经是卡片样式，直接紧跟其后即可）
        item {
            SwitchButton(
                checked = false,
                onCheckedChange = {},
                enabled = false,
                label = { Text("息屏显示") },
                secondaryLabel = { Text("开启后续航时间变短") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun BatterySettingsPage(isPowerSaveMode: Boolean, onPowerSaveModeChange: (Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    // 1. 初始化为 0，确保不会默认显示 100
    var batteryLevel by remember { mutableIntStateOf(0) }

    // 2. 使用 DisposableEffect 实时监听电量变化，而不是只读一次
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
                // 计算百分比
                batteryLevel = (level * 100 / scale.toFloat()).toInt()
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { ListHeader { Text("电池") } }

        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .padding(8.dp)
            ) {
                // 3. 核心修复：确保 progress 是 0.0f - 1.0f 之间的数
                // 如果 batteryLevel 是 52，这里必须是 0.52f
                CircularProgressIndicator(
                    progress = { batteryLevel / 100f },
                    modifier = Modifier.fillMaxSize(),
                    startAngle = 120f,
                    endAngle = 60f,
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = if (batteryLevel > 20)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    strokeWidth = 8.dp
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$batteryLevel%",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "剩余电量",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 省电模式 ToggleChip (保持之前的 M3 样式)
        item {
            SwitchButton(
                checked = isPowerSaveMode,
                onCheckedChange = onPowerSaveModeChange,
                label = { Text("省电模式") },
                secondaryLabel = { Text("限制检测频率") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text(
                text = "开启省电模式后，将限制心率检测速度至每五分钟检测。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    // 使用 M3 Card
    Card(
        onClick = { onClick?.invoke() },
        onLongClick = { onLongClick?.invoke() },
        // 如果没有点击事件，禁用点击效果
        enabled = onClick != null || onLongClick != null,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary // 标签使用主色强调
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
@Composable
fun AboutWatchPage(onBack: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fake_launcher_prefs", Context.MODE_PRIVATE) }

    val systemVersion = remember { prefs.getString("watch_sys_ver", "Xiaomi HyperOS Lite 1.5.3") ?: "Xiaomi HyperOS Lite 1.5.3" }
    val deviceModel = remember { prefs.getString("watch_model", "Xiaomi Watch S4") ?: "Xiaomi Watch S4" }
    val serialNumber = remember { prefs.getString("watch_sn", "A2304S40001298X") ?: "A2304S40001298X" }

    val listState = rememberScalingLazyListState()

    // 状态保持
    var clickCount by remember { mutableIntStateOf(0) }
    var windowStartTime by remember { mutableLongStateOf(0L) }
    var isArmed by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader {
                Text("关于手表")
            }
        }

        // 系统版本卡片
        item {
            InfoCard(
                label = "系统版本",
                value = systemVersion,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // 设备型号卡片
        item {
            InfoCard(
                label = "设备型号",
                value = deviceModel,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // 序列号卡片（逻辑保留）
        item {
            InfoCard(
                label = "序列号",
                value = serialNumber,
                modifier = Modifier.padding(vertical = 2.dp),
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - windowStartTime > 5000) {
                        clickCount = 1
                    } else {
                        clickCount++
                    }
                    windowStartTime = now

                    if (clickCount >= 10) {
                        isArmed = true
                    }
                },
                onLongClick = {
                    if (isArmed) {
                        onClose() // 执行退出悬浮窗
                        isArmed = false
                        clickCount = 0
                    }
                }
            )
        }

        // 法律信息卡片
        item {
            InfoCard(
                label = "法律信息",
                value = "点击查看内容",
                modifier = Modifier.padding(vertical = 2.dp),
                onClick = { /* 导航至法律信息页 */ }
            )
        }
    }
}


@Composable
fun AboutInfoRow(label: String, value: String) {
    Card(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("重启")
                }
            }
        }
    }
}

@Composable
fun WatchFaceFloatingPage(onClose: () -> Unit, customBgPath: String?, textColor: Color, isStopwatchRunning: Boolean, stopwatchTime: Long, isTimerRunning: Boolean, timerTime: Long, onIslandClick: () -> Unit) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { currentTime = System.currentTimeMillis(); delay(1000) } }
    val timeFormat = SimpleDateFormat("HH:mm:ss", LocalLocale.current.platformLocale)

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
                Row(modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .clickable { onIslandClick() }
                    .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        item { Row(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) { IconButton(onClick = { if (brightness > 0) onBrightnessChange(brightness - 1) }) { Icon(Icons.Default.Remove, contentDescription = null, tint = textColor) }
            Text(text = "亮度: ${brightness.toInt()}", style = MaterialTheme.typography.labelMedium, color = textColor)
            IconButton(onClick = { if (brightness < 10) onBrightnessChange(brightness + 1) }) { Icon(Icons.Default.Add, contentDescription = null, tint = textColor) } } }
    }
}

@Composable
fun AppDrawerFloatingPage(onAppClick: (AppDestination) -> Unit) {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val apps = listOf(
        "设置" to Icons.Default.Settings to AppDestination.SETTINGS,
        "心率" to Icons.Default.Favorite to AppDestination.HEART_RATE,
        "运动" to Icons.AutoMirrored.Filled.DirectionsRun to AppDestination.EXERCISE,
        "秒表" to Icons.Default.Timer to AppDestination.STOPWATCH,
        "倒计时" to Icons.Default.HourglassBottom to AppDestination.TIMER,
        "闹钟" to Icons.Default.Alarm to AppDestination.ALARM
    )

    LaunchedEffect(Unit) {
        // 请求焦点，让表冠滚动优先作用于列表
        focusRequester.requestFocus()
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester),
        state = listState,
        contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp),
        autoCentering = null
    ) {
        items(apps) { item ->
            val (appInfo, dest) = item
            Button(
                onClick = { if (dest != AppDestination.NONE) onAppClick(dest) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(appInfo.second, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(appInfo.first)
                }
            }
        }
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
            Box(modifier = Modifier
                .padding(horizontal = 24.dp)
                .background(Color.DarkGray.copy(alpha = 0.9f), CircleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(text = message ?: "", style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}