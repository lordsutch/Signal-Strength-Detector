package com.lordsutch.android.signaldetector

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat
import java.io.File

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey)

        var myPref = findPreference("clear_cache")
        myPref.setOnPreferenceClickListener { preference ->
            val context = activity
            val text = context?.getString(R.string.map_tile_cache_cleared)
            val duration = Toast.LENGTH_SHORT

            val webView = WebView(context)
            webView.clearCache(true)

            Toast.makeText(context, text, duration).show()
            false
        }

        myPref = findPreference("clear_log")
        myPref.setOnPreferenceClickListener { preference ->
            val context = activity
            val text = context?.getString(R.string.logFilesToast)
            val duration = Toast.LENGTH_SHORT

            val logPath = context?.getExternalFilesDir("")
            val logNames = arrayOf("cellinfolte.csv", "ltecells.csv",
                    "esmrcells.csv", "cdmacells.csv", "gsmcells.csv")

            for (fileName in logNames) {
                val logFile = File(logPath, fileName)

                if (logFile.exists()) {
                    val deleted = logFile.delete()
                }
            }

            Toast.makeText(context, text, duration).show()
            false
        }

    }
}
