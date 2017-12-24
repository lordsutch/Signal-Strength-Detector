package com.lordsutch.android.signaldetector;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        Preference myPref = findPreference("clear_cache");
        myPref.setOnPreferenceClickListener(preference -> {
            Context context = getActivity();
            CharSequence text = context.getString(R.string.map_tile_cache_cleared);
            int duration = Toast.LENGTH_SHORT;

            WebView webView = new WebView(context);
            webView.clearCache(true);

            Toast.makeText(context, text, duration).show();
            return false;
        });

        myPref = findPreference("clear_log");
        myPref.setOnPreferenceClickListener(preference -> {
            Context context = getActivity();
            CharSequence text = context.getString(R.string.logFilesToast);
            int duration = Toast.LENGTH_SHORT;

            File logPath = context.getExternalFilesDir("");
            List<String> logNames = Arrays.asList("cellinfolte.csv", "ltecells.csv",
                    "esmrcells.csv", "cdmacells.csv", "gsmcells.csv");

            for (String fileName : logNames) {
                File logFile = new File(logPath, fileName);

                if (logFile.exists()) {
                    boolean deleted = logFile.delete();
                }
            }

            Toast.makeText(context, text, duration).show();
            return false;
        });

    }
}
