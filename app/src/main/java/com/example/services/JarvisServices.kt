package com.example.services

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared reactive bridge for Foreground Service, Screen Sharing (MediaProjection),
 * Floating Orb Overlay, and Notification Assistant.
 */
data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appTitle: String,
    val text: String,
    val timestamp: Long
)

object JarvisServiceRuntime {
    private val _isWakeServiceRunning = MutableStateFlow(false)
    val isWakeServiceRunning: StateFlow<Boolean> = _isWakeServiceRunning.asStateFlow()

    private val _isFloatingOrbRunning = MutableStateFlow(false)
    val isFloatingOrbRunning: StateFlow<Boolean> = _isFloatingOrbRunning.asStateFlow()

    private val _isScreenSharingActive = MutableStateFlow(false)
    val isScreenSharingActive: StateFlow<Boolean> = _isScreenSharingActive.asStateFlow()

    private val _latestScreenBitmap = MutableStateFlow<Bitmap?>(null)
    val latestScreenBitmap: StateFlow<Bitmap?> = _latestScreenBitmap.asStateFlow()

    private val _capturedNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val capturedNotifications: StateFlow<List<CapturedNotification>> = _capturedNotifications.asStateFlow()

    private val _floatingOrbCommand = MutableStateFlow<String?>(null)
    val floatingOrbCommand: StateFlow<String?> = _floatingOrbCommand.asStateFlow()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    fun setWakeServiceRunning(running: Boolean) {
        _isWakeServiceRunning.value = running
    }

    fun setFloatingOrbRunning(running: Boolean) {
        _isFloatingOrbRunning.value = running
    }

    fun submitFloatingOrbCommand(cmd: String) {
        _floatingOrbCommand.value = cmd
    }

    fun consumeFloatingOrbCommand() {
        _floatingOrbCommand.value = null
    }

    fun updateNotifications(list: List<CapturedNotification>) {
        _capturedNotifications.value = list
    }

    fun startMediaProjectionSession(
        context: Context,
        resultCode: Int,
        resultData: Intent
    ): Result<Unit> {
        return try {
            stopMediaProjectionSession()
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, resultData)
                ?: return Result.failure(IllegalStateException("Android did not grant MediaProjection session."))

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopMediaProjectionSession()
                }
            }, null)

            val metrics: DisplayMetrics = context.resources.displayMetrics
            val width = (metrics.widthPixels / 2).coerceIn(360, 1080)
            val height = (metrics.heightPixels / 2).coerceIn(640, 1920)
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val vDisplay = projection.createVirtualDisplay(
                "JARVIS_Visual_Intelligence",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )

            reader.setOnImageAvailableListener({ imgReader ->
                val image = try {
                    imgReader.acquireLatestImage()
                } catch (_: Exception) {
                    null
                } ?: return@setOnImageAvailableListener

                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bmp = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                    _latestScreenBitmap.value = cropped
                } catch (e: Exception) {
                    Log.w("ScreenCapture", "Frame decode error", e)
                } finally {
                    image.close()
                }
            }, null)

            mediaProjection = projection
            imageReader = reader
            virtualDisplay = vDisplay
            _isScreenSharingActive.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Failed to start MediaProjection", e)
            stopMediaProjectionSession()
            Result.failure(e)
        }
    }

    fun stopMediaProjectionSession() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        try {
            imageReader?.close()
        } catch (_: Exception) {}
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        _isScreenSharingActive.value = false
    }
}

/**
 * Foreground Service providing transparent status notification for
 * user-enabled "Hey JARVIS" background/foreground listening or Live Screen Sharing.
 */
class JarvisForegroundService : Service() {

    companion object {
        const val ACTION_START_WAKE_SERVICE = "com.example.action.START_WAKE_SERVICE"
        const val ACTION_START_SCREEN_SHARE = "com.example.action.START_SCREEN_SHARE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_JARVIS_SERVICE"
        const val EXTRA_RESULT_CODE = "extra_media_projection_result_code"
        const val EXTRA_RESULT_DATA = "extra_media_projection_result_data"
        private const val CHANNEL_ID = "jarvis_foreground_channel"
        private const val NOTIF_ID = 1042
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                JarvisServiceRuntime.stopMediaProjectionSession()
                JarvisServiceRuntime.setWakeServiceRunning(false)
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (_: Exception) {}
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_SCREEN_SHARE -> {
                createChannel()
                val notification = buildNotification(
                    title = "JARVIS Screen Sharing Active",
                    text = "Tap Stop in JARVIS or this notification to end screen sharing immediately."
                )
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIF_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    } else {
                        startForeground(NOTIF_ID, notification)
                    }

                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    @Suppress("DEPRECATION")
                    val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                    } else {
                        intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    }
                    if (resultData != null) {
                        JarvisServiceRuntime.startMediaProjectionSession(this, resultCode, resultData)
                    }
                } catch (e: Exception) {
                    Log.e("JarvisFGS", "Could not start MediaProjection Foreground Service: ${e.localizedMessage}", e)
                    JarvisServiceRuntime.stopMediaProjectionSession()
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                val hasMicPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasMicPerm) {
                    Log.w("JarvisFGS", "Skipping microphone FGS start because RECORD_AUDIO is not granted yet.")
                    JarvisServiceRuntime.setWakeServiceRunning(false)
                    stopSelf()
                    return START_NOT_STICKY
                }

                createChannel()
                val notification = buildNotification(
                    title = "JARVIS Wake-Word Active (\"Hey JARVIS\")",
                    text = "Microphone wake-word listener armed. Tap Stop to disable."
                )
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startForeground(
                            NOTIF_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } else {
                        startForeground(NOTIF_ID, notification)
                    }
                    JarvisServiceRuntime.setWakeServiceRunning(true)
                } catch (e: Exception) {
                    Log.e("JarvisFGS", "Could not start Microphone Foreground Service: ${e.localizedMessage}", e)
                    JarvisServiceRuntime.setWakeServiceRunning(false)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        JarvisServiceRuntime.setWakeServiceRunning(false)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Active Assistant Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when JARVIS Wake-Word or Screen Sharing is active"
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop Listening / Share", stopPending)
            .build()
    }
}

/**
 * Floating JARVIS Orb ("Display over other apps") using WindowManager overlay.
 * Renders a draggable futuristic cyan orb that expands into a compact assistant command bar.
 */
class FloatingOrbService : Service() {

    private var windowManager: WindowManager? = null
    private var rootContainer: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            JarvisServiceRuntime.setFloatingOrbRunning(false)
            stopSelf()
            return
        }
        showFloatingOrbOverlay()
    }

    private fun showFloatingOrbOverlay() {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 32
                y = 280
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }

            // Compact Expanded Panel (initially hidden)
            val expandedPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(28, 24, 28, 24)
                background = GradientDrawable().apply {
                    setColor(0xEE081222.toInt())
                    cornerRadius = 36f
                    setStroke(3, 0xFF00E5FF.toInt())
                }
            }

            val titleView = TextView(this).apply {
                text = "JARVIS COMPACT ORB"
                setTextColor(0xFF00E5FF.toInt())
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }

            val inputField = EditText(this).apply {
                hint = "Ask JARVIS or give command..."
                setHintTextColor(0xFF6B8BAE.toInt())
                setTextColor(0xFFE6F4FF.toInt())
                textSize = 13f
                minWidth = 440
            }

            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val sendBtn = Button(this).apply {
                text = "Execute"
                textSize = 11f
                setOnClickListener {
                    val cmd = inputField.text?.toString()?.trim().orEmpty()
                    if (cmd.isNotBlank()) {
                        JarvisServiceRuntime.submitFloatingOrbCommand(cmd)
                        inputField.setText("")
                    }
                    val openApp = Intent(this@FloatingOrbService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(openApp)
                }
            }

            val minimizeBtn = Button(this).apply {
                text = "Minimize"
                textSize = 11f
                setOnClickListener {
                    expandedPanel.visibility = View.GONE
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    wm.updateViewLayout(container, params)
                }
            }

            val closeBtn = Button(this).apply {
                text = "Close"
                textSize = 11f
                setOnClickListener {
                    stopSelf()
                }
            }

            buttonRow.addView(sendBtn)
            buttonRow.addView(minimizeBtn)
            buttonRow.addView(closeBtn)

            expandedPanel.addView(titleView)
            expandedPanel.addView(inputField)
            expandedPanel.addView(buttonRow)

            // The Glowing Orb Bubble
            val orbButton = TextView(this).apply {
                text = "◉ JARVIS"
                setTextColor(0xFF00E5FF.toInt())
                typeface = Typeface.DEFAULT_BOLD
                textSize = 13f
                setPadding(32, 22, 32, 22)
                background = GradientDrawable().apply {
                    setColor(0xDD050B14.toInt())
                    cornerRadius = 100f
                    setStroke(4, 0xFF00E5FF.toInt())
                }
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false

            orbButton.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            wm.updateViewLayout(container, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            if (expandedPanel.visibility == View.VISIBLE) {
                                expandedPanel.visibility = View.GONE
                                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            } else {
                                expandedPanel.visibility = View.VISIBLE
                                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            }
                            wm.updateViewLayout(container, params)
                        }
                        true
                    }
                    else -> false
                }
            }

            container.addView(orbButton)
            container.addView(expandedPanel)

            wm.addView(container, params)
            rootContainer = container
            JarvisServiceRuntime.setFloatingOrbRunning(true)
        } catch (e: Exception) {
            Log.e("FloatingOrbService", "Failed to display floating orb", e)
            JarvisServiceRuntime.setFloatingOrbRunning(false)
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            rootContainer?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        rootContainer = null
        JarvisServiceRuntime.setFloatingOrbRunning(false)
        super.onDestroy()
    }
}

/**
 * Optional User-Authorized Notification Listener Assistant.
 * Only reads notifications after the user explicitly enables Notification Access in Android Settings.
 */
class JarvisNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        refreshActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        refreshActiveNotifications()
    }

    private fun refreshActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            val items = active.mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                CapturedNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appTitle = title.ifBlank { sbn.packageName.substringAfterLast('.') },
                    text = text,
                    timestamp = sbn.postTime
                )
            }.take(25)
            JarvisServiceRuntime.updateNotifications(items)
        } catch (e: Exception) {
            Log.w("JarvisNotifListener", "Unable to read active notifications", e)
        }
    }
}
