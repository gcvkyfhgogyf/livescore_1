package com.livescore.floating

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating score widget. Plain Android Views (not Compose) are used on purpose:
 * lighter on memory/battery and no lifecycle plumbing is needed inside a Service.
 */
class OverlayService : Service() {

    companion object {
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
        const val ACTION_STOP = "com.livescore.floating.STOP"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    private val prefs by lazy { Prefs(this) }
    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var storeStarted = false
    private var expanded = false

    private lateinit var tvLeague: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvEvents: TextView

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "opacity") render()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        ServiceCompat.startForeground(this, 1, buildNotification(), type)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildView()
        MatchStore.start(this)
        storeStarted = true
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        scope.launch {
            combine(MatchStore.matches, MatchStore.pinnedId) { a, b -> a to b }
                .collect { render() }
        }
        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        root?.let { runCatching { wm?.removeView(it) } }
        root = null
        if (storeStarted) MatchStore.stop()
        _running.value = false
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun iconButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(0xFFB0BEC5.toInt())
        setPadding(dp(8), dp(2), dp(4), dp(2))
        setOnClickListener { onClick() }
    }

    private fun buildView() {
        val bg = GradientDrawable().apply {
            setColor(0xEE101820.toInt())
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x5500E676)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(12), dp(6), dp(10), dp(8))
            minimumWidth = dp(170)
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tvLeague = label(10f, 0xFF90A4AE.toInt()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        top.addView(tvLeague)
        top.addView(iconButton("⇄") { MatchStore.cycle() })
        top.addView(iconButton("✕") { stopSelf() })
        box.addView(top)

        tvScore = label(17f, Color.WHITE, true).apply { gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(tvScore)

        tvClock = label(13f, 0xFF00E676.toInt(), true).apply { gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(tvClock)

        tvEvents = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xFFCFD8DC.toInt())
            setPadding(0, dp(6), 0, 0)
            visibility = View.GONE
        }
        box.addView(tvEvents)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.overlayX
            y = prefs.overlayY
        }

        box.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = e.rawX; touchY = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                        if (moved) {
                            params.x = (startX + dx).coerceAtLeast(0)
                            params.y = (startY + dy).coerceAtLeast(0)
                            wm?.updateViewLayout(v, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            prefs.overlayX = params.x
                            prefs.overlayY = params.y
                        } else {
                            expanded = !expanded
                            render()
                        }
                        return true
                    }
                }
                return false
            }
        })

        root = box
        wm?.addView(box, params)
    }

    private fun short(name: String) = if (name.length > 12) name.take(11) + "…" else name

    private fun render() {
        val box = root ?: return
        box.alpha = prefs.opacity / 100f
        val m = MatchStore.widgetMatch()
        if (m == null) {
            tvLeague.text = "Live Score"
            tvScore.text = "No live match"
            tvClock.text = ""
            tvEvents.visibility = View.GONE
            return
        }
        tvLeague.text = m.league
        tvScore.text = "${short(m.home)}  ${m.homeScore} - ${m.awayScore}  ${short(m.away)}"
        tvClock.setTextColor(if (m.isLive) 0xFF00E676.toInt() else 0xFFB0BEC5.toInt())
        tvClock.text = if (m.isLive) "● ${m.clock}" else m.clock

        if (expanded) {
            val lines = m.events.takeLast(4).reversed()
                .joinToString("\n") { "${it.icon} ${it.minute}'  ${it.player} (${short(it.team)})" }
            tvEvents.text = lines.ifEmpty { "No events yet" }
            tvEvents.visibility = View.VISIBLE
        } else {
            tvEvents.visibility = View.GONE
        }
    }

    private fun buildNotification() = run {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(this, Notifier.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setContentTitle("Live score widget is on")
            .setContentText("Tap to open • Stop to hide the widget")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
