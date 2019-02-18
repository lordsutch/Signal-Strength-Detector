package com.lordsutch.android.signaldetector

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.settings_activity)
        setSupportActionBar(findViewById(R.id.toolbar))

        supportActionBar?.setTitle(R.string.title_activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}
