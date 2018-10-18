package com.lordsutch.android.signaldetector;

// Android Packages

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.lordsutch.android.signaldetector.SignalDetectorService.LocalBinder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ShareActionProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static androidx.core.content.FileProvider.getUriForFile;

public final class SignalDetector extends AppCompatActivity {
    public static final String TAG = SignalDetector.class.getSimpleName();
    private static final int REQUEST_LOCATION = 0;
    String SIG_INFO_KEY = "mSignalInfo";

    private WebView leafletView = null;
    public boolean pageAvailable = false;
    //    private MapView mapView = null;
    private TelephonyManager mTelephonyManager = null;
    private String baseLayer = "shields";
    private String coverageLayer = "provider";
    private BroadcastReceiver receiver;
    private ShareActionProvider mShareActionProvider;

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // getWindow().requestFeature(Window.FEATURE_PROGRESS);
        if(savedInstanceState != null)
            mSignalInfo = savedInstanceState.getParcelable(SIG_INFO_KEY);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

/*
        mapView = (MapView) findViewById(R.id.2mapview);
        mapView.setZoom(14);

        UserLocationOverlay userLocationOverlay = new UserLocationOverlay(new GpsLocationProvider(this),
                mapView);
        userLocationOverlay.enableMyLocation();
        userLocationOverlay.setDrawAccuracyEnabled(true);
        mapView.getOverlays().add(userLocationOverlay);
*/

        leafletView = findViewById(R.id.leafletView);

        WebSettings webSettings = leafletView.getSettings();
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadsImagesAutomatically(true);

        leafletView.setWebChromeClient(new WebChromeClient() {
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, cm.message() + " -- From line "
                        + cm.lineNumber() + " of "
                        + cm.sourceId());
                return true;
            }
        });

        leafletView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                SignalDetector.this.pageAvailable = true;

                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(SignalDetector.this);
                setMapView(baseLayer);
                addMapOverlays(sharedPref.getString("overlay_tile_source", "provider"));
                updateGui();
            }
        });

        webSettings.setDomStorageEnabled(true);

    	/*
        This next one is crazy. It's the DEFAULT location for your app's cache
    	But it didn't work for me without this line.
    	UPDATE: no hardcoded path. Thanks to Kevin Hawkins */
        String appCachePath = getApplicationContext().getCacheDir().getAbsolutePath();
        webSettings.setAppCachePath(appCachePath);
        webSettings.setAppCacheEnabled(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setAllowFileAccess(true);

        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.i(TAG,
                    "Displaying location permission rationale to provide additional context.");
            Snackbar.make(findViewById(R.id.root), R.string.permission_location_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(SignalDetector.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION},
                                    REQUEST_LOCATION);
                        }
                    })
                    .show();

        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }

        leafletView.loadUrl("file:///android_asset/leaflet.html");
        reloadPreferences();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                signalInfo s = intent.getParcelableExtra(SignalDetectorService.SD_MESSAGE);
                updateSigInfo(s);
                updateGui();
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            if (mService != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mService.startGPS();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    private SignalDetectorService mService = null;
    private boolean mBound = false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);

        MenuItem item = menu.findItem(R.id.menu_item_share);
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        return true;
    }

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;

        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        bindSDService();
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter(SignalDetectorService.SD_RESULT)
        );
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    private void bindSDService() {
        // Bind cell tracking service
        Intent intent = new Intent(this, SignalDetectorService.class);

        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mBound = true;
    }

    private void unbindSDService() {
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        Intent intent = new Intent(this, SignalDetectorService.class);
        stopService(intent);
    }

    private void enableLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(settingsIntent);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private boolean validLTESignalStrength(int strength) {
        return (strength > -140 && strength < 0);
    }

    private boolean validRSSISignalStrength(int strength) {
        return (strength > -120 && strength < 0);
    }

    private boolean validCellID(int eci) {
        return (eci >= 0 && eci <= 0x0FFFFFFF);
    }

    private double bslat = Double.NaN;
    private double bslon = Double.NaN;

    private signalInfo mSignalInfo = null;

    double speedFactor = 3.6;
    String speedLabel = "km/h";

    double accuracyFactor = 1.0;
    String accuracyLabel = "m";

    float bearing = 0;

    // Swiped from https://en.wikipedia.org/wiki/Mobile_country_code
    private List<Integer> threeDigitMNCList = Arrays.asList(
            365, // Anguilla
            344, // Antigua and Barbuda
            722, // Argentina
            342, // Barbados
            348, // British Virgin Islands
            302, // Canada
            346, // Cayman Islands
            732, // Columbia
            366, // Dominica
            750, // Falkland Islands
            352, // Grenada
            708, // Honduras
            // India seems to be a mix of 2 and 3 digits?
            338, // Jamaica
            // Malaysia has several 3 digit codes, all >= 100
            334, // Mexico
            354, // Montserrat
            330, // Puerto Rico mostly has 3-digit codes over 100
            356, // Saint Kitts and Nevis
            358, // Saint Lucia
            360, // Saint Vincent and the Grenadines
            376, // Turks and Caicos Islands
            310, 311, 312, 313, 316 // USA; Guam
    );

    private boolean is3digitMnc(int mcc) {
        return threeDigitMNCList.contains(mcc);
    }

    private String formatPLMN(int mcc, int mnc) {
        if(mnc >= 100 || is3digitMnc(mcc)) {
            return String.format(Locale.US, "%03d-%03d", mcc, mnc);
        } else {
            return String.format(Locale.US, "%03d-%02d", mcc, mnc);
        }
    }

    private String directionForBearing(double bearing) {
        if (bearing > 0) {
            int index = (int) Math.ceil((bearing + 11.25) / 22.5);

            int dir[] = {0, R.string.bearing_north, R.string.bearing_nne, R.string.bearing_northeast,
                    R.string.bearing_ene, R.string.bearing_east, R.string.bearing_ese, R.string.bearing_southeast,
                    R.string.bearing_sse, R.string.bearing_south, R.string.bearing_ssw, R.string.bearing_southwest,
                    R.string.bearing_wsw, R.string.bearing_west, R.string.bearing_wnw, R.string.bearing_northwest,
                    R.string.bearing_nnw, R.string.bearing_north};

            return getResources().getString(dir[index]);
        } else {
            return "";
        }
    }

    private boolean validPhysicalCellID(int pci) {
        return (pci >= 0 && pci <= 503);
    }

    private Boolean validEARFCN(int earfcn) { return earfcn != Integer.MAX_VALUE; }

    private boolean tradunits = false;
    private boolean bsmarker = false;
    private boolean taAsDistance = false;
    private String ta_distance_units = "mi";

    public void updateSigInfo(signalInfo signal) {
        mSignalInfo = signal;
    }

    /* Speed of light in air at sea level is approx. 299,700 km/s according to Wikipedia
     * Android timing advance is in microseconds according to:
     * https://android.googlesource.com/platform/hardware/ril/+/master/include/telephony/ril.h
     *
     * This seems to be round-trip time, so one-way distance is half that.
     */
    private double timingAdvanceToMeters(int timingAdvance, boolean isFDD) {
        if(timingAdvance == Integer.MAX_VALUE)
            return Double.NaN;
        return (isFDD ? timingAdvance : timingAdvance-20) * 149.85;
    }

    /* Uses tradunits setting */
    private double timingAdvanceToDistance(int timingAdvance, boolean isFDD) {
        return timingAdvanceToMeters(timingAdvance, isFDD) / (tradunits ? 1609.334 : 1000.0);
    }

    private String formatTimingAdvance(int timingAdvance, boolean isFDD) {
        if(taAsDistance) {
            /* TA offset for TDD seems to be ~10 microseconds  */
            return String.format(Locale.getDefault(), "TA=%.1f\u202f%s",
                    timingAdvanceToDistance(timingAdvance, isFDD), ta_distance_units);
        } else if (timingAdvance != Integer.MAX_VALUE) {
            return String.format(Locale.getDefault(), "TA=%d\u202fµs", timingAdvance);
        } else {
            return "";
        }
    }

    private double gsmTimingAdvanceToMeters(int timingAdvance) {
        if(timingAdvance == Integer.MAX_VALUE)
            return Double.NaN;
        return timingAdvance * 550.0; // See http://www.telecomhall.com/parameter-timing-advance-ta.aspx
    }

    /* Uses tradunits setting */
    private double gsmTimingAdvanceToDistance(int timingAdvance) {
        return gsmTimingAdvanceToMeters(timingAdvance) / (tradunits ? 1609.334 : 1000.0);
    }

    private String formatGsmTimingAdvance(int timingAdvance) {
        if(taAsDistance) {
            return String.format(Locale.getDefault(), "\u00a0TA=%.1f\u202f%s",
                    gsmTimingAdvanceToDistance(timingAdvance), ta_distance_units);
        } else if (timingAdvance != Integer.MAX_VALUE) {
            return String.format(Locale.getDefault(), "\u00a0TA=%d\u202fµs", timingAdvance);
        } else {
            return "";
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)
            updateGui();
    }

    private void updateGui() {
        Log.d(TAG, "updating GUI");
        if(mSignalInfo == null || mService == null)
            return;
        Log.d(TAG, mSignalInfo.toString());

        bslat = mSignalInfo.bslat;
        bslon = mSignalInfo.bslon;

        if (mSignalInfo.bearing > 0)
            bearing = mSignalInfo.bearing;

        TextView latlon = findViewById(R.id.positionLatLon);

        latlon.setText(String.format(Locale.getDefault(), "%3.5f\u00b0%s %3.5f\u00b0%s (\u00b1%.0f\u202f%s)",
                Math.abs(mSignalInfo.latitude), getResources().getString(mSignalInfo.latitude >= 0 ? R.string.bearing_north : R.string.bearing_south),
                Math.abs(mSignalInfo.longitude), getResources().getString(mSignalInfo.longitude >= 0 ? R.string.bearing_east : R.string.bearing_west),
                mSignalInfo.accuracy * accuracyFactor, accuracyLabel));

        TextView speed = findViewById(R.id.speed);

        if (bearing > 0.0)
            speed.setText(String.format(Locale.getDefault(), "%3.1f %s %s",
                    mSignalInfo.speed * speedFactor, speedLabel, directionForBearing(bearing)));
        else
            speed.setText(String.format(Locale.getDefault(), "%3.1f %s",
                    mSignalInfo.speed * speedFactor, speedLabel));

        TextView servingid = findViewById(R.id.cellid);
        TextView bsLabel = findViewById(R.id.bsLabel);
        TextView cdmaBS = findViewById(R.id.cdma_sysinfo);
        TextView voiceSigStrengthView = findViewById(R.id.cdmaSigStrength);
        TextView otherSites = findViewById(R.id.otherLteSites);

        LinearLayout voiceSignalBlock = findViewById(R.id.voiceSignalBlock);
        LinearLayout lteBlock = findViewById(R.id.lteBlock);
        LinearLayout lteOtherBlock = findViewById(R.id.lteOtherBlock);
        LinearLayout preLteBlock = findViewById(R.id.preLteBlock);

        if (mSignalInfo.networkType == TelephonyManager.NETWORK_TYPE_LTE) {
            ArrayList<String> cellIds = mService.lteCellInfo(mSignalInfo);

            if (!cellIds.isEmpty()) {
                servingid.setText(TextUtils.join(", ", cellIds));
            } else {
                servingid.setText(R.string.missing);
            }
            lteBlock.setVisibility(View.VISIBLE);
            lteOtherBlock.setVisibility(View.VISIBLE);
        } else {
            servingid.setText(R.string.none);
            lteBlock.setVisibility(View.GONE);
            lteOtherBlock.setVisibility(View.GONE);
        }

        if (mSignalInfo.otherCells != null) {
            ArrayList<String> otherSitesList = new ArrayList<>();

            Collections.sort(mSignalInfo.otherCells, new Comparator<otherLteCell>() {
                @Override
                public int compare(otherLteCell lhs, otherLteCell rhs) {
                    int c1 = Integer.compare(lhs.lteSigStrength, rhs.lteSigStrength);

                    return (c1 != 0 ? c1 : Integer.compare(lhs.pci, rhs.pci));
                }
            });

            for (otherLteCell otherCell : mSignalInfo.otherCells) {
                if (validPhysicalCellID(otherCell.pci)) {
                    ArrayList<String> sigList = new ArrayList<>();

                    String sigInfo = String.format(Locale.getDefault(), "%03d", otherCell.pci);

                    if(validLTESignalStrength(otherCell.lteSigStrength))
                        sigList.add(String.format(Locale.getDefault(), "%d\u202FdBm", otherCell.lteSigStrength));

                    if(mService.validTimingAdvance(otherCell.timingAdvance))
                        sigList.add(formatTimingAdvance(otherCell.timingAdvance, otherCell.isFDD));

                    if(mService.validEARFCN(otherCell.earfcn) && otherCell.earfcn != mSignalInfo.earfcn)
                        sigList.add(String.format(Locale.getDefault(), "EARFCN=%d", otherCell.earfcn));

                    if(!sigList.isEmpty())
                        sigInfo += String.format(Locale.getDefault(),"\u00a0(%s)",
                                TextUtils.join(" ", sigList));

                    otherSitesList.add(sigInfo);
                }
            }
            if (otherSitesList.isEmpty())
                otherSites.setText(R.string.none);
            else
                otherSites.setText(TextUtils.join("; ", otherSitesList));
        }

        TextView network = findViewById(R.id.networkString);

        int dataSigStrength;
        boolean voiceDataSame = true;

        if (mSignalInfo.phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
            dataSigStrength = mSignalInfo.cdmaSigStrength;
        } else if (mSignalInfo.phoneType == TelephonyManager.PHONE_TYPE_GSM) {
            dataSigStrength = mSignalInfo.gsmSigStrength;
        } else {
            dataSigStrength = validRSSISignalStrength(mSignalInfo.cdmaSigStrength) ?
                    mSignalInfo.cdmaSigStrength : mSignalInfo.gsmSigStrength;
        }
        int voiceSigStrength = dataSigStrength;
        boolean lteMode = false;

        switch (mSignalInfo.networkType) {
            case TelephonyManager.NETWORK_TYPE_LTE:
                if (validLTESignalStrength(mSignalInfo.lteSigStrength)) {
//                    getSupportActionBar().setLogo(R.drawable.ic_launcher);
                    voiceDataSame = false;
                    dataSigStrength = mSignalInfo.lteSigStrength;
                    lteMode = true;
//                } else {
//                    getSupportActionBar().setLogo(R.drawable.ic_stat_non4g);
                }
                break;

            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
//                getSupportActionBar().setLogo(R.drawable.ic_stat_non4g);
                if (validRSSISignalStrength(mSignalInfo.evdoSigStrength)) {
                    voiceDataSame = false;
                    dataSigStrength = mSignalInfo.evdoSigStrength;
                }
                break;

            default:
//                getSupportActionBar().setLogo(R.drawable.ic_stat_non4g);
                break;
        }

        ArrayList<String> netInfo = new ArrayList<>();

        String opString;

        if(!mSignalInfo.operatorName.isEmpty()) {
            opString = mSignalInfo.operatorName;
        } else if (lteMode && validMcc(mSignalInfo.mcc) && validMnc(mSignalInfo.mnc)) {
            opString = formatPLMN(mSignalInfo.mcc, mSignalInfo.mnc);
        } else if (mSignalInfo.phoneType == TelephonyManager.PHONE_TYPE_GSM &&
                validMcc(mSignalInfo.gsmMcc) && validMnc(mSignalInfo.gsmMnc)) {
            opString = formatPLMN(mSignalInfo.gsmMcc, mSignalInfo.gsmMnc);
        } else if (mSignalInfo.phoneType == TelephonyManager.PHONE_TYPE_GSM &&
                !mSignalInfo.operator.isEmpty()) {
            opString = formatOperator(mSignalInfo.operator);
        } else {
            opString = "";
        }

        if (!opString.isEmpty())
            netInfo.add(opString);

        netInfo.add(mService.networkString(mSignalInfo.networkType));

        if (lteMode && mSignalInfo.lteBand > 0) {
            netInfo.add(String.format(Locale.getDefault(), "B%d", mSignalInfo.lteBand));
        }

        if (validLTESignalStrength(dataSigStrength)) {
            netInfo.add(String.format(Locale.getDefault(), "%d\u202FdBm", dataSigStrength));
            if(mService.validTimingAdvance(mSignalInfo.timingAdvance))
                netInfo.add(formatTimingAdvance(mSignalInfo.timingAdvance, mSignalInfo.isFDD));
        }

        if (mSignalInfo.roaming)
            netInfo.add(getString(R.string.roamingInd));

        network.setText(TextUtils.join(" ", netInfo));

        if (!voiceDataSame && validRSSISignalStrength(voiceSigStrength)) {
            voiceSigStrengthView.setText(String.valueOf(voiceSigStrength) + "\u202FdBm");
            voiceSignalBlock.setVisibility(View.VISIBLE);
        } else {
            voiceSignalBlock.setVisibility(View.GONE);
        }

        ArrayList<String> bsList = new ArrayList<>();

        if (mSignalInfo.phoneType == TelephonyManager.PHONE_TYPE_CDMA && mService.validSID(mSignalInfo.sid)) {
            bsLabel.setText(R.string.cdma_1xrtt_base_station);
            bsList = mService.cdmaCellInfo(mSignalInfo);
        } else if (mSignalInfo.phoneType == TelephonyManager.PHONE_TYPE_GSM &&
                (!lteMode || (mSignalInfo.lac != mSignalInfo.tac && mSignalInfo.fullCid != mSignalInfo.gci))) {
            // Devices seem to put LTE stuff into non-LTE fields...?
            bsLabel.setText(R.string._2g_3g_tower);
            bsList = mService.gsmCellInfo(mSignalInfo);

            if(mSignalInfo.gsmTimingAdvance != Integer.MAX_VALUE)
                bsList.add(formatGsmTimingAdvance(mSignalInfo.gsmTimingAdvance));
        }

        if (!bsList.isEmpty()) {
            cdmaBS.setText(TextUtils.join(", ", bsList));
            preLteBlock.setVisibility(View.VISIBLE);
        } else {
            cdmaBS.setText(R.string.none);
            preLteBlock.setVisibility(View.GONE);
        }

        if (mService.validLocation(mSignalInfo.longitude, mSignalInfo.latitude))
            centerMap(mSignalInfo.latitude, mSignalInfo.longitude, mSignalInfo.accuracy,
                    mSignalInfo.avgSpeed, bearing, mSignalInfo.fixAge);
        addBsMarker();

        setupSharing();
    }

    private String formatOperator(String operator) {
        if(Integer.valueOf(operator) > 0)
            return operator.substring(0,3)+"-"+operator.substring(3);
        else
            return operator;
    }

    private boolean validTAC(int tac) {
        return (tac > 0x0000 && tac < 0xFFFF); // 0, 0xFFFF are reserved values
    }

    private boolean validMcc(int mcc) {
        return (mcc > 0 && mcc <= 999);
    }

    private boolean validMnc(int mnc) {
        return (mnc > 0 && mnc <= 999);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    // Use evaluateJavascript if available (KITKAT+), otherwise hack
    private void execJavascript(String script) {
        Log.d(TAG, script);
        if (!pageAvailable)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            leafletView.evaluateJavascript(script, null);
        else {
            Uri uri = new Uri.Builder().scheme("javascript").opaquePart(script).build();

            leafletView.loadUrl(uri.toString());
        }
    }

    private int zoomForSpeed(float speed) {
        speed = speed*(float)3.6; // Convert to km/h from m/s

        if(speed >= 83)
            return 13;
        else if (speed >= 63)
            return 14;
        else if (speed >= 43)
            return 15;
        else if (speed >= 23)
            return 16;
        else if (speed >= 5)
            return 17;

        return 0; // Don't zoom
    }

    private void centerMap(double latitude, double longitude, float accuracy, float speed,
                           float bearing, long fixAge) {
        boolean staleFix = fixAge > (30 * 1000); // 30 seconds

        if(coverageLayer.equals("provider")) {
            coverageLayer = mTelephonyManager.getSimOperator();
            if (coverageLayer == null)
                coverageLayer = mTelephonyManager.getNetworkOperator();
            if (coverageLayer == null)
                coverageLayer = "";
        }

        double towerRadius = 0.0;

        if(mSignalInfo != null) {
            towerRadius = timingAdvanceToMeters(mSignalInfo.timingAdvance, mSignalInfo.isFDD);
            if (mSignalInfo.gsmTimingAdvance != Integer.MAX_VALUE)
                towerRadius = gsmTimingAdvanceToMeters(mSignalInfo.gsmTimingAdvance);
        }

/*
        mapView.setCenter(new LatLng(latitude, longitude));
        int zoom = zoomForSpeed(speed);
        if(zoom > 0)
            mapView.setZoom(zoom);
        // TODO Add markers here
*/
        execJavascript(String.format(Locale.US, "recenter(%.5f,%.5f,%f,%.0f,%.0f,%s,\"%s\",\"%s\",%.0f);",
                latitude, longitude, accuracy, speed, bearing, staleFix, coverageLayer, baseLayer,
                towerRadius));
    }

//    private Marker baseMarker = null;

    private void addBsMarker() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        bsmarker = sharedPref.getBoolean("show_base_station", false);

/*
        LatLng location = new LatLng(bslat, bslon);
        // TODO Place markers
        if(bsmarker && Math.abs(bslat) <= 90 && Math.abs(bslon) <= 190) {
            if(baseMarker == null) {
                baseMarker = new Marker(mapView, "Base Station", "CDMA base station location.",
                        location);
                baseMarker.addTo(mapView);
            } else {
                baseMarker.setPoint(location);
            }
        } else if (baseMarker != null) {
            mapView.removeMarker(baseMarker);
        }
*/

        if (bsmarker && mService.validLocation(bslon, bslat))
            execJavascript(String.format(Locale.US, "placeMarker(%.5f,%.5f);", bslat, bslon));
        else
            execJavascript("clearMarker();");
    }

    private void updateUnits() {
        if (tradunits) {
            speedFactor = 2.237;
            speedLabel = "mph";
            accuracyFactor = 3.28084;
            accuracyLabel = "ft";
            ta_distance_units = "mi";
        } else {
            speedFactor = 3.6;
            speedLabel = "km/h";
            accuracyFactor = 1.0;
            accuracyLabel = "m";
            ta_distance_units = "km";
        }
    }

    public void launchSettings(MenuItem x) {
        Intent myIntent = new Intent(this, SettingsActivity.class);
        startActivityForResult(myIntent, 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (action != null && action.equals(SignalDetectorService.ACTION_STOP)) {
            Log.d(TAG, "onActivityResult: exit received.");
            unbindSDService();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        unbindSDService();
        reloadPreferences();
        bindSDService();
    }

    private void reloadPreferences() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        bsmarker = sharedPref.getBoolean("show_base_station", false);
        tradunits = sharedPref.getBoolean("traditional_units", false);
        baseLayer = sharedPref.getString("tile_source", "osm");
        taAsDistance = sharedPref.getBoolean("ta_distance", false);

        updateUnits();
        updateGui();
        setMapView(baseLayer);
        addMapOverlays(sharedPref.getString("overlay_tile_source", "provider"));
    }

    public void exitApp(MenuItem x) {
        unbindSDService();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
//        System.gc();
        super.onDestroy();
    }

    /**
     * Dialog to prompt users to enable GPS on the device.
     */
    @SuppressLint("ValidFragment")
    public class EnableGpsDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.enable_gps)
                    .setMessage(R.string.enable_gps_dialog)
                    .setPositiveButton(R.string.enable_gps, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            enableLocationSettings();
                        }
                    })
                    .create();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SIG_INFO_KEY, mSignalInfo);
    }

    private void setupSharing() {
        ArrayList<Uri> logUris = new ArrayList<>();
        File logPath = getExternalFilesDir("");
        List<String> logNames = Arrays.asList("cellinfolte.csv", "ltecells.csv",
                "esmrcells.csv", "cdmacells.csv", "gsmcells.csv");

        for (String fileName : logNames) {
            File logFile = new File(logPath, fileName);

            if (logFile.exists())
                logUris.add(getUriForFile(getApplicationContext(),
                        "com.lordsutch.android.signaldetector.fileprovider", logFile));
        }

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, logUris);
        shareIntent.setType("text/csv");
        shareIntent.setFlags(FLAG_GRANT_READ_URI_PERMISSION);

        if(mShareActionProvider != null)
            mShareActionProvider.setShareIntent(shareIntent);
    }

    protected void addMapOverlays(String layer) {
        String layerName = layer;

        if(layer.equalsIgnoreCase("provider")) {
            layer = mTelephonyManager.getSimOperator();
            if (layer == null)
                layer = mTelephonyManager.getNetworkOperator();
            layerName = mTelephonyManager.getSimOperatorName();
        }

        coverageLayer = layer;
        execJavascript("setOverlayLayer(\""+layer+"\")");
/*
        ITileLayer source = new WebSourceTileLayer("coverage",
                "http://tiles-day.cdn.sensorly.net/tile/any/"+providerFragment+"/{z}/{x}/{x}/{y}/{y}.png?s=256")
                .setName(layerName)
                .setAttribution("© Sensorly")
                .setMinimumZoomLevel(1)
                .setMaximumZoomLevel(18);

//        MapTileLayerBase base = new MapTileLayerBasic(this, source, mapView);
//        Overlay overlay = new TilesOverlay(base);
        mapView.addTileSource(source);
*/
    }

    protected void setMapView(String layer) {
        baseLayer = layer;
        execJavascript("setBaseLayer(\""+layer+"\")");
/*

        ITileLayer source = null;

        if (layer.equalsIgnoreCase("shields")) {
            source = new WebSourceTileLayer("shields",
                    "http://tile.openstreetmap.us/osmus_shields/{z}/{x}/{y}.png")
                    .setName("Shields")
                    .setAttribution("© OpenStreetMap")
                    .setMinimumZoomLevel(1)
                    .setMaximumZoomLevel(18);
        } else if(layer.equalsIgnoreCase("mapquest")) {
            source = new WebSourceTileLayer("mapquest",
                    "http://otile1.mqcdn.com/tiles/1.0.0/map/{z}/{x}/{y}.jpg")
                    .setName("MapQuest Open")
                    .setAttribution("© OpenStreetMap, MapQuest")
                    .setMinimumZoomLevel(1)
                    .setMaximumZoomLevel(18);
        } else if(layer.equalsIgnoreCase("usgs-aerial")) {
            source = new WebSourceTileLayer("usgs-aerial",
                    "http://tile.openstreetmap.us/usgs_large_scale/{z}/{x}/{y}.jpg")
                    .setName("USGS-NAIP")
                    .setAttribution("Courtesy USGS/NAIP")
                    .setMinimumZoomLevel(1)
                    .setMaximumZoomLevel(18);
        } else if(layer.equalsIgnoreCase("topos")) {
            source = new WebSourceTileLayer("topos",
                    "http://tile.openstreetmap.us/usgs_scanned_topos/{z}/{x}/{y}.jpg")
                    .setName("USGS-NAIP")
                    .setAttribution("Courtesy USGS/NAIP")
                    .setMinimumZoomLevel(12)
                    .setMaximumZoomLevel(18);
        }

        if (source != null) {
            mapView.setTileSource(source);
            mapView.setScrollableAreaLimit(source.getBoundingBox());
            mapView.setMinZoomLevel(mapView.getTileProvider().getMinimumZoomLevel());
            mapView.setMaxZoomLevel(mapView.getTileProvider().getMaximumZoomLevel());
//            mapView.setCenter(mapView.getTileProvider().getCenterCoordinate());
//            mapView.setZoom(13);
*/
    }
}

