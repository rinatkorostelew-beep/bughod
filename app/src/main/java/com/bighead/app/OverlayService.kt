package com.bighead.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var rootView: View? = null
    private var circleView: CircleOverlayView? = null

    // Drag
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private var currentMode = Mode.BIGHEAD
    enum class Mode { BIGHEAD, DIGTER }

    companion object {
        const val CHANNEL_ID   = "overlay_channel"
        const val NOTIF_ID     = 1
        const val ACTION_STOP  = "com.bighead.app.STOP_OVERLAY"

        fun start(context: Context) =
            context.startForegroundService(Intent(context, OverlayService::class.java))

        fun stop(context: Context) =
            context.startService(Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP
            })
    }

    // ────────────────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        inflateOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        rootView?.let { wm.removeView(it) }
        circleView?.let { wm.removeView(it) }
        super.onDestroy()
    }

    // ── Inflate overlay views ────────────────────────────────────────────────

    private fun inflateOverlay() {
        val inflater = LayoutInflater.from(this)

        // ── Circle overlay (full-screen, behind panel) ──
        circleView = CircleOverlayView(this)
        val circleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        circleView!!.visibility = View.INVISIBLE
        circleView!!.alpha = 0f
        wm.addView(circleView, circleParams)

        // ── Draggable panel ──
        rootView = inflater.inflate(R.layout.overlay_panel, null)

        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        wm.addView(rootView, panelParams)

        // Entrance animation
        rootView!!.alpha = 0f
        rootView!!.scaleX = 0.75f
        rootView!!.scaleY = 0.75f
        rootView!!.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(380)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()

        setupPanelLogic(panelParams)
    }

    // ── Panel logic ──────────────────────────────────────────────────────────

    private fun setupPanelLogic(params: WindowManager.LayoutParams) {
        val root         = rootView!!
        val dragHandle   = root.findViewById<View>(R.id.dragHandle)
        val btnBigHead   = root.findViewById<TextView>(R.id.btnBigHead)
        val btnDigter    = root.findViewById<TextView>(R.id.btnDigter)
        val bigheadSlider = root.findViewById<View>(R.id.bigheadSliderContainer)
        val digterSlider  = root.findViewById<View>(R.id.digterSliderContainer)
        val seekBighead  = root.findViewById<SeekBar>(R.id.bigheadSlider)
        val seekDigter   = root.findViewById<SeekBar>(R.id.digterSlider)
        val btnClose     = root.findViewById<View>(R.id.btnOverlayClose)

        // Close button
        btnClose.setOnClickListener { stopSelf() }

        // Drag
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragOffsetX = event.rawX - params.x
                    dragOffsetY = event.rawY - params.y
                    root.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - dragOffsetX).toInt()
                    params.y = (event.rawY - dragOffsetY).toInt()
                    wm.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    root.animate().scaleX(1f).scaleY(1f).setDuration(200)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                    true
                }
                else -> false
            }
        }

        // Mode toggle
        btnBigHead.setOnClickListener {
            if (currentMode != Mode.BIGHEAD) {
                currentMode = Mode.BIGHEAD
                applyToggle(btnBigHead, btnDigter)
                revealView(bigheadSlider)
                hideView(digterSlider)
                hideCircle()
            }
        }
        btnDigter.setOnClickListener {
            if (currentMode != Mode.DIGTER) {
                currentMode = Mode.DIGTER
                applyToggle(btnDigter, btnBigHead)
                revealView(digterSlider)
                hideView(bigheadSlider)
                revealCircle()
            }
        }

        // Sliders
        seekDigter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) {
                circleView?.setRadius(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        circleView?.setRadius(40)
    }

    private fun applyToggle(selected: TextView, unselected: TextView) {
        selected.setBackgroundResource(R.drawable.toggle_selected)
        selected.setTextColor(0xFFFFFFFF.toInt())
        unselected.setBackgroundResource(R.drawable.toggle_unselected)
        unselected.setTextColor(0xFF5C647E.toInt())
        selected.animate().scaleX(1.05f).scaleY(1.05f).setDuration(80).withEndAction {
            selected.animate().scaleX(1f).scaleY(1f)
                .setDuration(120).setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    private fun revealView(v: View) {
        v.visibility = View.VISIBLE
        v.alpha = 0f; v.translationY = -20f
        v.animate().alpha(1f).translationY(0f).setDuration(300)
            .setInterpolator(AccelerateInterpolator()).start()
    }

    private fun hideView(v: View) {
        v.animate().alpha(0f).translationY(-16f).setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { v.visibility = View.GONE }.start()
    }

    private fun revealCircle() {
        circleView?.let {
            it.visibility = View.VISIBLE
            it.scaleX = 0.3f; it.scaleY = 0.3f; it.alpha = 0f
            it.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(400).setInterpolator(OvershootInterpolator(1.1f)).start()
        }
    }

    private fun hideCircle() {
        circleView?.let {
            it.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(260)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { it.visibility = View.INVISIBLE }.start()
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "BigHead Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Панель работает поверх приложений" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BigHead активен")
            .setContentText("Нажми, чтобы остановить оверлей")
            .setSmallIcon(R.drawable.ic_launch)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_close, "Остановить", stopIntent)
            .build()
    }
}
