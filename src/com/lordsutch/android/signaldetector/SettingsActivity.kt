package com.lordsutch.android.signaldetector

import android.os.Bundle
import android.preference.PreferenceActivity
import androidx.appcompat.widget.Toolbar
import android.view.View

class SettingsActivity : PreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.settings_activity)

        val actionbar = findViewById<View>(R.id.toolbar) as Toolbar
        actionbar.title = "Settings"
        actionbar.setNavigationOnClickListener { this@SettingsActivity.finish() }
    }
}
