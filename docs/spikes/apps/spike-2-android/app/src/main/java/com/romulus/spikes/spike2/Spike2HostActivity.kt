package com.romulus.spikes.spike2

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class Spike2HostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this)
        view.text = getString(R.string.app_name)
        setContentView(view)
    }
}
