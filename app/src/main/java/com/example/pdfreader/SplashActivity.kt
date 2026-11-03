package com.example.pdfreader

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Animated splash shown briefly before the home screen. */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<TextView>(R.id.logoMark)
        val title = findViewById<TextView>(R.id.logoText)
        logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_logo_anim))
        title.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.activity_fade_in, R.anim.activity_fade_out)
            finish()
        }, 1500)
    }
}
