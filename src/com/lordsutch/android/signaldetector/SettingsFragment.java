package com.lordsutch.android.signaldetector;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        Preference myPref = findPreference("clear_cache");
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (SignalDetector.mapView != null) {
                    SignalDetector.mapView.getTileProvider().clearTileDiskCache();
                    Context context = getActivity();
                    CharSequence text = context.getString(R.string.map_tile_cache_cleared);
                    int duration = Toast.LENGTH_SHORT;

                    Toast.makeText(context, text, duration).show();
                }
                return false;
            }
        });

    }
}
