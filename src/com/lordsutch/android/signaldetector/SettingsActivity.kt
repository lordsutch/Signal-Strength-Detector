package com.lordsutch.android.signaldetector

import android.os.Bundle
import android.preference.PreferenceActivity
import androidx.appcompat.widget.Toolbar
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.settings_activity)

        val actionbar = supportActionBar
        actionbar.title = "Settings"
        actionbar.setToolbarNavigationOnClickListener { this@SettingsActivity.finish() }
    }
}
