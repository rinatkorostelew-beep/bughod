package com.bighead.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.bighead.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val REQUEST_OVERLAY = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        animateEntrance()
        setupButtons()
        updateState()
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }

    private fun animateEntrance() {
        listOf(binding.ivMainLogo, binding.tvMainTitle, binding.tvMainSub,
            binding.btnLaunch, binding.btnLogout).forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 30f
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(100L * i).setDuration(400)
                .setInterpolator(OvershootInterpolator(1.1f)).start()
        }
    }

    private fun updateState() {
        val running = isServiceRunning()
        binding.btnLaunch.text = if (running) "ОСТАНОВИТЬ ОВЕРЛЕЙ" else "ЗАПУСТИТЬ ОВЕРЛЕЙ"
    }

    private fun setupButtons() {
        binding.btnLaunch.setOnClickListener {
            if (isServiceRunning()) {
                OverlayService.stop(this)
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    requestOverlayPermission()
                } else {
                    OverlayService.start(this)
                }
            }
            updateState()
            it.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(160)
                    .setInterpolator(OvershootInterpolator(2f)).start()
            }.start()
        }

        binding.btnLogout.setOnClickListener {
            getSharedPreferences("bighead_prefs", MODE_PRIVATE).edit().clear().apply()
            OverlayService.stop(this)
            startActivity(Intent(this, KeyActivity::class.java))
            finish()
        }
    }

    private fun requestOverlayPermission() {
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")),
            REQUEST_OVERLAY
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY && Settings.canDrawOverlays(this)) {
            OverlayService.start(this); updateState()
        }
    }

    private fun isServiceRunning(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == OverlayService::class.java.name }
    }
}
