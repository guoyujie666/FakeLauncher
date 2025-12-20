package com.github.guoyujie666.fakelauncher.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.wear.compose.foundation.lazy.*
import androidx.wear.compose.material3.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class FloatingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var rootLayout: FrameLayout? = null

    // 用于向 Compose 传递“返回主页”信号的流
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
        // 关键：Service 环境下需要手动切换到 RESUME 状态以确保 UI 正常交互
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showFloatingWindow() {
        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            // 必须移除 FLAG_NOT_FOCUSABLE 才能接收按键事件
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }

        // 使用 FrameLayout 作为根布局来拦截 KeyEvents（参考官方文档建议）
        rootLayout = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
                if (event.action == AndroidKeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        // KEYCODE_STEM_1 是表冠点击，KEYCODE_BACK 是返回键
                        AndroidKeyEvent.KEYCODE_STEM_1,
                        AndroidKeyEvent.KEYCODE_BACK -> {
                            // 发送信号让 Pager 滚动回主页
                            homeEventFlow.tryEmit(Unit)
                            return true // 消费事件，防止系统拦截
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            // 在根视图上设置 Owners，子视图 (ComposeView) 会自动向上查找
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

@Composable
fun FakeLauncherServiceMainUI(
    homeEventFlow: SharedFlow<Unit>,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var accumulatedDelta by remember { mutableFloatStateOf(0f) }
    val scrollThreshold = 50f

    // 监听来自 View 层的物理按键信号
    LaunchedEffect(homeEventFlow) {
        homeEventFlow.collect {
            if (pagerState.currentPage != 1) {
                pagerState.animateScrollToPage(1)
            }
        }
    }

    MaterialTheme {
        AppScaffold {
            ScreenScaffold(
                timeText = { TimeText() }
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .focusRequester(focusRequester)
                        .focusable()
                        // 保持表冠滚动的段落感处理
                        .onRotaryScrollEvent { event ->
                            accumulatedDelta += event.verticalScrollPixels
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
                            true
                        }
                ) { page ->
                    when (page) {
                        0 -> ControlCenterFloatingPage()
                        1 -> WatchFaceFloatingPage(onClose)
                        2 -> AppDrawerFloatingPage()
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

@Composable
fun WatchFaceFloatingPage(onClose: () -> Unit) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onClose() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeFormat.format(Date(currentTime)),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun ControlCenterFloatingPage() {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 36.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        autoCentering = null
    ) {
        item {
            Text("控制中心", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FloatingToggleItem(Icons.Default.BatteryChargingFull, "省电")
                FloatingToggleItem(Icons.Default.Bluetooth, "蓝牙")
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FloatingToggleItem(Icons.Default.DoNotDisturbOn, "勿扰")
                FloatingToggleItem(Icons.Default.AirplanemodeActive, "飞行")
            }
        }
        item {
            var brightness by remember { mutableFloatStateOf(5f) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { if (brightness > 0) brightness-- }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = null)
                }
                Text(text = "亮度: ${brightness.toInt()}", style = MaterialTheme.typography.labelMedium)
                Button(onClick = { if (brightness < 10) brightness++ }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun AppDrawerFloatingPage() {
    val listState = rememberScalingLazyListState()
    val apps = listOf(
        "设置" to Icons.Default.Settings,
        "心率" to Icons.Default.Favorite,
        "运动" to Icons.AutoMirrored.Filled.DirectionsRun,
        "秒表" to Icons.Default.Timer,
        "倒计时" to Icons.Default.HourglassBottom,
        "闹钟" to Icons.Default.Alarm
    )
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 36.dp, bottom = 32.dp, start = 12.dp, end = 12.dp),
        autoCentering = null
    ) {
        items(apps) { app ->
            Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(app.second, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(app.first)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun FloatingToggleItem(icon: ImageVector, label: String) {
    var checked by remember { mutableStateOf(false) }
    IconToggleButton(
        checked = checked,
        onCheckedChange = { isChecked -> checked = isChecked },
        modifier = Modifier.padding(4.dp)
    ) {
        Icon(icon, contentDescription = label)
    }
}