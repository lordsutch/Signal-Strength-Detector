package com.lordsutch.android.signaldetector

// Android Packages

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView

import com.google.android.material.snackbar.Snackbar
import com.lordsutch.android.signaldetector.SignalDetectorService.LocalBinder

import java.io.File
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ShareActionProvider
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuItemCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider.getUriForFile
import kotlin.collections.ArrayList

class SignalDetector : AppCompatActivity() {
    val TAG = SignalDetector::class.java.simpleName

    private var SIGINFOKEY = "mSignalInfo"

    private var leafletView: WebView? = null
    var pageAvailable = false
    //    private MapView mapView = null;
    private var mTelephonyManager: TelephonyManager? = null
    private var baseLayer: String? = "osm"
    private var coverageLayer: String? = "provider"
    private var receiver: BroadcastReceiver? = null
    private var mShareActionProvider: ShareActionProvider? = null

    private var REQUEST_LOCATION = 0
    private var mService: SignalDetectorService? = null
    private var mBound = false

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private var bslat = Double.NaN
    private var bslon = Double.NaN

    private var mSignalInfo : SignalInfo? = null

    private var speedFactor = 3.6
    private var speedLabel = "km/h"

    private var accuracyFactor = 1.0
    private var accuracyLabel = "m"

    private var bearing = 0.0

    private var tradunits = false
    private var bsmarker = false
    private var taAsDistance = false
    private var taDistanceUnits = "mi"

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // getWindow().requestFeature(Window.FEATURE_PROGRESS);
        if (savedInstanceState != null)
            mSignalInfo = savedInstanceState.getParcelable(SIGINFOKEY)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.main)
        setSupportActionBar(findViewById(R.id.toolbar))

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        mTelephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        /*
        mapView = (MapView) findViewById(R.id.2mapview);
        mapView.setZoom(14);

        UserLocationOverlay userLocationOverlay = new UserLocationOverlay(new GpsLocationProvider(this),
                mapView);
        userLocationOverlay.enableMyLocation();
        userLocationOverlay.setDrawAccuracyEnabled(true);
        mapView.getOverlays().add(userLocationOverlay);
*/

        leafletView = findViewById(R.id.leafletView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val config = resources.configuration

            if (!isSupportedLocale(config.locales.get(0))) {
                val supportedLocales = filterUnsupportedLocales(config.locales)

                if (!supportedLocales.isEmpty) {
                    config.locales = supportedLocales
                    // updateConfiguration() is deprecated in SDK 25, but the alternative
                    // requires restarting the activity, which we don't want to do here.
                    resources.updateConfiguration(config, resources.displayMetrics)
                }
            }
        }

        val webSettings = leafletView!!.settings
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.javaScriptEnabled = true
        webSettings.loadsImagesAutomatically = true

        leafletView!!.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                Log.d(TAG, cm.message() + " -- From line "
                        + cm.lineNumber() + " of "
                        + cm.sourceId())
                return true
            }
        }

        leafletView!!.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                this@SignalDetector.pageAvailable = true

                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this@SignalDetector)
                setMapView(baseLayer!!)
                addMapOverlays(sharedPref.getString("overlay_tile_source", "provider")!!)
                updateGui()
            }
        }

        webSettings.domStorageEnabled = true

        /*
        This next one is crazy. It's the DEFAULT location for your app's cache
    	But it didn't work for me without this line.
    	UPDATE: no hardcoded path. Thanks to Kevin Hawkins */
        val appCachePath = applicationContext.cacheDir.absolutePath
        webSettings.setAppCachePath(appCachePath)
        webSettings.setAppCacheEnabled(true)
        webSettings.builtInZoomControls = false
        webSettings.allowFileAccess = true

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.i(TAG,
                    "Displaying location permission rationale to provide additional context.")
            Snackbar.make(findViewById(R.id.root), R.string.permission_location_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok) {
                        ActivityCompat.requestPermissions(this@SignalDetector,
                                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                                REQUEST_LOCATION)
                    }
                    .show()

        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 0)
        }

        leafletView!!.loadUrl("file:///android_asset/leaflet.html")
        reloadPreferences()

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val s = intent.getParcelableExtra<SignalInfo>(SignalDetectorService.SD_MESSAGE)
                updateSigInfo(this@SignalDetector, s)
                updateGui()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_LOCATION) {
            if (mService != null && grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mService!!.startGPS()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.mainmenu, menu)

        val item = menu.findItem(R.id.menu_item_share)
        mShareActionProvider = MenuItemCompat.getActionProvider(item) as ShareActionProvider

        return true
    }

//    // Define a DialogFragment that displays the error dialog
//    class ErrorDialogFragment : DialogFragment() {
//        // Global field to contain the error dialog
//        private var mDialog: Dialog? = null
//
//        // Default constructor. Sets the dialog field to null
//        init {
//            mDialog = null
//        }
//
//        // Set the dialog to display
//        fun setDialog(dialog: Dialog) {
//            mDialog = dialog
//        }
//
//        // Return a Dialog to the DialogFragment.
//        override fun onCreateDialog(savedInstanceState: Bundle): Dialog? {
//            return mDialog
//        }
//    }

    override fun onStart() {
        super.onStart()

        bindSDService()
        if (receiver != null) {
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver!!,
                    IntentFilter(SignalDetectorService.SD_RESULT)
            )
        }
    }

    override fun onStop() {
        if (receiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver!!)
        }
        super.onStop()
    }

    private fun bindSDService() {
        // Bind cell tracking service
        val intent = Intent(this, SignalDetectorService::class.java)

        startService(intent)
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        mBound = true
    }

    private fun unbindSDService() {
        if (mBound) {
            unbindService(mConnection)
            mBound = false
        }
        val intent = Intent(this, SignalDetectorService::class.java)
        stopService(intent)
    }

    private fun enableLocationSettings() {
        val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(settingsIntent)
    }

    private fun validLTESignalStrength(strength: Int): Boolean {
        return strength in -139..-1
    }

    private fun validRSSISignalStrength(strength: Int): Boolean {
        return strength in -119..-1
    }

    private fun validCellID(eci: Int): Boolean {
        return eci in 0..0x0FFFFFFF
    }

    private fun formatPLMN(mccString: String?, mncString: String?): String {
        return if (mccString.isNullOrEmpty() || mncString.isNullOrEmpty())
            ""
        else
            String.format(Locale.US, "%s-%s", mccString, mncString)
    }

    private fun directionForBearing(bearing: Double): String {
        return if (bearing > 0) {
            val index = Math.ceil((bearing + 11.25) / 22.5).toInt()

            val dir = intArrayOf(0, R.string.bearing_north, R.string.bearing_nne, R.string.bearing_northeast, R.string.bearing_ene, R.string.bearing_east, R.string.bearing_ese, R.string.bearing_southeast, R.string.bearing_sse, R.string.bearing_south, R.string.bearing_ssw, R.string.bearing_southwest, R.string.bearing_wsw, R.string.bearing_west, R.string.bearing_wnw, R.string.bearing_northwest, R.string.bearing_nnw, R.string.bearing_north)

            resources.getString(dir[index])
        } else {
            ""
        }
    }

    private fun validPhysicalCellID(pci: Int): Boolean {
        return pci in 0..503
    }

    private fun validEARFCN(earfcn: Int): Boolean {
        return earfcn != Int.MAX_VALUE
    }

    /* Speed of light in air at sea level is approx. 299,700 km/s according to Wikipedia
     * Android timing advance is in microseconds according to:
     * https://android.googlesource.com/platform/hardware/ril/+/master/include/telephony/ril.h
     *
     * This seems to be round-trip time, so one-way distance is half that.
     */
    private fun timingAdvanceToMeters(timingAdvance: Int, isFDD: Boolean): Double {
        return if (timingAdvance == Int.MAX_VALUE) Double.NaN else (if (isFDD) timingAdvance else timingAdvance - 20) * 149.85
    }

    /* Uses tradunits setting */
    private fun timingAdvanceToDistance(timingAdvance: Int, isFDD: Boolean): Double {
        return timingAdvanceToMeters(timingAdvance, isFDD) / if (tradunits) 1609.334 else 1000.0
    }

    private fun formatTimingAdvance(timingAdvance: Int, isFDD: Boolean): String {
        return if (taAsDistance) {
            /* TA offset for TDD seems to be ~10 microseconds  */
            String.format(Locale.getDefault(), "TA=%.1f\u202f%s",
                    timingAdvanceToDistance(timingAdvance, isFDD), taDistanceUnits)
        } else if (timingAdvance != Int.MAX_VALUE) {
            String.format(Locale.getDefault(), "TA=%d\u202fµs", timingAdvance)
        } else {
            ""
        }
    }

    private fun gsmTimingAdvanceToMeters(timingAdvance: Int): Double {
        return if (timingAdvance == Int.MAX_VALUE) Double.NaN else timingAdvance * 550.0
// See http://www.telecomhall.com/parameter-timing-advance-ta.aspx
    }

    /* Uses tradunits setting */
    private fun gsmTimingAdvanceToDistance(timingAdvance: Int): Double {
        return gsmTimingAdvanceToMeters(timingAdvance) / if (tradunits) 1609.334 else 1000.0
    }

    private fun formatGsmTimingAdvance(timingAdvance: Int): String {
        return if (taAsDistance) {
            String.format(Locale.getDefault(), "\u00a0TA=%.1f\u202f%s",
                    gsmTimingAdvanceToDistance(timingAdvance), taDistanceUnits)
        } else if (timingAdvance != Int.MAX_VALUE) {
            String.format(Locale.getDefault(), "\u00a0TA=%d\u202fµs", timingAdvance)
        } else {
            ""
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus)
            updateGui()
    }

    private fun updateGui() {
//        Log.d(TAG, "updating GUI")
        if (mSignalInfo == null || mService == null)
            return

//        Log.d(TAG, mSignalInfo.toString())

        bslat = mSignalInfo!!.bslat
        bslon = mSignalInfo!!.bslon

        if (mSignalInfo!!.bearing > 0)
            bearing = mSignalInfo!!.bearing

        val latlon = findViewById<TextView>(R.id.positionLatLon)

        latlon.text = String.format(Locale.getDefault(), "%3.5f\u00b0%s %3.5f\u00b0%s (\u00b1%.0f\u202f%s)",
                Math.abs(mSignalInfo!!.latitude), resources.getString(if (mSignalInfo!!.latitude >= 0) R.string.bearing_north else R.string.bearing_south),
                Math.abs(mSignalInfo!!.longitude), resources.getString(if (mSignalInfo!!.longitude >= 0) R.string.bearing_east else R.string.bearing_west),
                mSignalInfo!!.accuracy * accuracyFactor, accuracyLabel)

        val speed = findViewById<TextView>(R.id.speed)

        if (bearing > 0.0)
            speed.text = String.format(Locale.getDefault(), "%3.1f %s %s",
                    mSignalInfo!!.speed * speedFactor, speedLabel,
                    directionForBearing(bearing))
        else
            speed.text = String.format(Locale.getDefault(), "%3.1f %s",
                    mSignalInfo!!.speed * speedFactor, speedLabel)

        val servingid = findViewById<TextView>(R.id.cellid)
        val bsLabel = findViewById<TextView>(R.id.bsLabel)
        val cdmaBS = findViewById<TextView>(R.id.cdma_sysinfo)
        val voiceSigStrengthView = findViewById<TextView>(R.id.cdmaSigStrength)
        val otherSites = findViewById<TextView>(R.id.otherLteSites)

        val voiceSignalBlock = findViewById<LinearLayout>(R.id.voiceSignalBlock)
        val lteBlock = findViewById<LinearLayout>(R.id.lteBlock)
        val lteOtherBlock = findViewById<LinearLayout>(R.id.lteOtherBlock)
        val preLteBlock = findViewById<LinearLayout>(R.id.preLteBlock)

        var networkType = mSignalInfo!!.networkType

        val isIWLAN = (networkType == TelephonyManager.NETWORK_TYPE_IWLAN)

        if (isIWLAN) {
            if (mSignalInfo!!.lteBand > 0 && validLTESignalStrength(mSignalInfo!!.lteSigStrength))
                networkType = TelephonyManager.NETWORK_TYPE_LTE
            else if (validRSSISignalStrength(mSignalInfo!!.evdoSigStrength))
                networkType = TelephonyManager.NETWORK_TYPE_EHRPD
            else if (validRSSISignalStrength(mSignalInfo!!.cdmaSigStrength))
                networkType = TelephonyManager.NETWORK_TYPE_1xRTT
            else if (mSignalInfo!!.lac != Int.MAX_VALUE)
                networkType = TelephonyManager.NETWORK_TYPE_UMTS
            else if (validRSSISignalStrength(mSignalInfo!!.gsmSigStrength))
                networkType = TelephonyManager.NETWORK_TYPE_GPRS
        }

        if (networkType == TelephonyManager.NETWORK_TYPE_LTE) {
            val cellIds = mService!!.lteCellInfo(mSignalInfo!!)

            if (cellIds.isNotEmpty()) {
                servingid.text = TextUtils.join(", ", cellIds)
            } else {
                servingid.setText(R.string.missing)
            }
            lteBlock.visibility = View.VISIBLE
            lteOtherBlock.visibility = View.VISIBLE
        } else {
            servingid.setText(R.string.none)
            lteBlock.visibility = View.GONE
            lteOtherBlock.visibility = View.GONE
        }

        if (mSignalInfo!!.otherCells != null) {
            val otherSitesList = ArrayList<String>()

            mSignalInfo!!.otherCells!!.sortWith( Comparator { lhs, rhs ->
                val c1 = -compareValues(lhs.lteSigStrength, rhs.lteSigStrength)
                val c2 = compareValues(lhs.pci, rhs.pci)

                if (c1 != 0) c1 else if (c2 != 0) c2 else compareValues(lhs.lteBand, rhs.lteBand)
            })

            for (otherCell in mSignalInfo!!.otherCells!!) {
                if (validPhysicalCellID(otherCell.pci)) {
                    val sigList = ArrayList<String>()

                    var sigInfo = String.format(Locale.getDefault(), "%03d", otherCell.pci)

                    if (validLTESignalStrength(otherCell.lteSigStrength))
                        sigList.add(String.format(Locale.getDefault(), "%d\u202FdBm", otherCell.lteSigStrength))

                    if (mService!!.validTimingAdvance(otherCell.timingAdvance))
                        sigList.add(formatTimingAdvance(otherCell.timingAdvance, otherCell.isFDD))

                    if (mService!!.validEARFCN(otherCell.earfcn) && otherCell.earfcn != mSignalInfo!!.earfcn)
                        sigList.add(String.format(Locale.getDefault(), "EARFCN=%d", otherCell.earfcn))

                    if (sigList.isNotEmpty())
                        sigInfo += String.format(Locale.getDefault(), "\u00a0(%s)",
                                TextUtils.join(" ", sigList))

                    otherSitesList.add(sigInfo)
                }
            }
            if (otherSitesList.isEmpty())
                otherSites.setText(R.string.none)
            else
                otherSites.text = TextUtils.join("; ", otherSitesList)
        }

        val network = findViewById<TextView>(R.id.networkString)

        var dataSigStrength: Int
        var voiceDataSame = true
        var lteMode = false

        if (mSignalInfo!!.phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
            dataSigStrength = mSignalInfo!!.cdmaSigStrength
        } else if (mSignalInfo!!.phoneType == TelephonyManager.PHONE_TYPE_GSM) {
            dataSigStrength = mSignalInfo!!.gsmSigStrength
        } else {
            dataSigStrength = if (validRSSISignalStrength(mSignalInfo!!.cdmaSigStrength))
                mSignalInfo!!.cdmaSigStrength
            else
                mSignalInfo!!.gsmSigStrength
        }

        val voiceSigStrength = dataSigStrength

        when (networkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> if (validLTESignalStrength(mSignalInfo!!.lteSigStrength)) {
                //                    getSupportActionBar().setLogo(R.drawable.ic_launcher);
                if (validRSSISignalStrength(voiceSigStrength))
                    voiceDataSame = false

                dataSigStrength = mSignalInfo!!.lteSigStrength
                lteMode = true
                //                } else {
                //                    getSupportActionBar().setLogo(R.drawable.ic_stat_non4g);
            }

            TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B ->
                //                getSupportActionBar().setLogo(R.drawable.ic_stat_non4g);
                if (validRSSISignalStrength(mSignalInfo!!.evdoSigStrength) && validRSSISignalStrength(mSignalInfo!!.cdmaSigStrength)) {
                    voiceDataSame = false
                    dataSigStrength = mSignalInfo!!.evdoSigStrength
                }

            else -> {
            }
        }//                getSupportActionBar().setLogo(R.drawable.ic_stat_non4g);

        val netInfo = ArrayList<String>()

        val opString: String

        if (mSignalInfo!!.operatorName.isNotEmpty()) {
            opString = mSignalInfo!!.operatorName
        } else if (lteMode && validMcc(mSignalInfo!!.mcc) && validMnc(mSignalInfo!!.mnc)) {
            opString = mSignalInfo!!.formatPLMN()
        } else if (mSignalInfo!!.phoneType == TelephonyManager.PHONE_TYPE_GSM &&
                validMcc(mSignalInfo!!.gsmMcc) && validMnc(mSignalInfo!!.gsmMnc)) {
            opString = mSignalInfo!!.formatGsmPLMN()
        } else if (mSignalInfo!!.phoneType == TelephonyManager.PHONE_TYPE_GSM && mSignalInfo!!.operator.isNotEmpty()) {
            opString = formatOperator(mSignalInfo!!.operator)
        } else {
            opString = ""
        }

        if (opString.isNotEmpty())
            netInfo.add(opString)

        netInfo.add(mService!!.networkString(networkType))

        if (lteMode && mSignalInfo!!.lteBand > 0) {
            netInfo.add(String.format(Locale.getDefault(), "B%d", mSignalInfo!!.lteBand))
        }

        if (validLTESignalStrength(dataSigStrength)) {
            netInfo.add(String.format(Locale.getDefault(), "%d\u202FdBm", dataSigStrength))
            if (mService!!.validTimingAdvance(mSignalInfo!!.timingAdvance))
                netInfo.add(formatTimingAdvance(mSignalInfo!!.timingAdvance, mSignalInfo!!.isFDD))
        }

        if (mSignalInfo!!.roaming)
            netInfo.add(getString(R.string.roamingInd))

        if (isIWLAN)
            netInfo.add("WiFi")

        network.text = TextUtils.join(" ", netInfo)

        if (!voiceDataSame && validRSSISignalStrength(voiceSigStrength)) {
            voiceSigStrengthView.text = String.format(Locale.US, "%d dBm", voiceSigStrength)
            voiceSignalBlock.visibility = View.VISIBLE
        } else {
            voiceSignalBlock.visibility = View.GONE
        }

        var bsList = ArrayList<String>()

        if (mSignalInfo!!.phoneType == TelephonyManager.PHONE_TYPE_CDMA && mService!!.validSID(mSignalInfo!!.sid)) {
            bsLabel.setText(R.string.cdma_1xrtt_base_station)
            bsList = mService!!.cdmaCellInfo(mSignalInfo!!)
        } else if (mSignalInfo!!.phoneType == TelephonyManager.PHONE_TYPE_GSM && (!lteMode || mSignalInfo!!.lac != mSignalInfo!!.tac && mSignalInfo!!.fullCid != mSignalInfo!!.gci)) {
            // Devices seem to put LTE stuff into non-LTE fields...?
            bsLabel.setText(R.string._2g_3g_tower)
            bsList = mService!!.gsmCellInfo(mSignalInfo!!)

            if (mSignalInfo!!.gsmTimingAdvance != Int.MAX_VALUE)
                bsList.add(formatGsmTimingAdvance(mSignalInfo!!.gsmTimingAdvance))
        }

        if (bsList.isNotEmpty()) {
            cdmaBS.text = TextUtils.join(", ", bsList)
            preLteBlock.visibility = View.VISIBLE
        } else {
            cdmaBS.setText(R.string.none)
            preLteBlock.visibility = View.GONE
        }

        if (mService!!.validLocation(mSignalInfo!!.longitude, mSignalInfo!!.latitude))
            centerMap(mSignalInfo!!.latitude, mSignalInfo!!.longitude, mSignalInfo!!.accuracy,
                    mSignalInfo!!.avgSpeed, bearing, mSignalInfo!!.fixAge)
        addBsMarker()

        setupSharing()
    }

    private fun formatOperator(operator: String): String {
        return if (operator.toInt() > 0)
            operator.substring(0, 3) + "-" + operator.substring(3)
        else
            operator
    }

    private fun validTAC(tac: Int): Boolean {
        return tac in 0x1..0xFFFE // 0, 0xFFFF are reserved values
    }

    private fun validMcc(mcc: Int): Boolean {
        return mcc in 1..999
    }

    private fun validMnc(mnc: Int): Boolean {
        return mnc in 1..999
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private// Use evaluateJavascript if available (KITKAT+), otherwise hack
    fun execJavascript(script: String) {
        Log.d(TAG, script)
        if (!pageAvailable)
            return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            leafletView!!.evaluateJavascript(script, null)
        else {
            val uri = Uri.Builder().scheme("javascript").opaquePart(script).build()

            leafletView!!.loadUrl(uri.toString())
        }
    }

    private fun zoomForSpeed(speed: Double): Int {
        var speed = speed * 3.6 // Convert to km/h from m/s

        return when {
            speed >= 83 -> 13
            speed >= 63 -> 14
            speed >= 43 -> 15
            speed >= 23 -> 16
            speed >= 5 -> 17
            // Don't zoom
            else -> 0
        }
    }

    private fun centerMap(latitude: Double, longitude: Double, accuracy: Double, speed: Double,
                          bearing: Double, fixAge: Long) {
        val staleFix = fixAge > 30 * 1000 // 30 seconds

        if (coverageLayer == "provider") {
            coverageLayer = mTelephonyManager!!.simOperator
            if (coverageLayer == null)
                coverageLayer = mTelephonyManager!!.networkOperator
            if (coverageLayer == null)
                coverageLayer = ""
        }

        var towerRadius = 0.0

        if (mSignalInfo != null) {
            towerRadius = timingAdvanceToMeters(mSignalInfo!!.timingAdvance, mSignalInfo!!.isFDD)
            if (mSignalInfo!!.gsmTimingAdvance != Int.MAX_VALUE)
                towerRadius = gsmTimingAdvanceToMeters(mSignalInfo!!.gsmTimingAdvance)
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
                towerRadius))
    }

    //    private Marker baseMarker = null;

    private fun addBsMarker() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        bsmarker = sharedPref.getBoolean("show_base_station", false)

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

        if (bsmarker && mService!!.validLocation(bslon, bslat))
            execJavascript(String.format(Locale.US, "placeMarker(%.5f,%.5f);", bslat, bslon))
        else
            execJavascript("clearMarker();")
    }

    private fun updateUnits() {
        if (tradunits) {
            speedFactor = 2.237
            speedLabel = "mph"
            accuracyFactor = 3.28084
            accuracyLabel = "ft"
            taDistanceUnits = "mi"
        } else {
            speedFactor = 3.6
            speedLabel = "km/h"
            accuracyFactor = 1.0
            accuracyLabel = "m"
            taDistanceUnits = "km"
        }
    }

    fun launchSettings(x: MenuItem) {
        val myIntent = Intent(this, SettingsActivity::class.java)
        startActivityForResult(myIntent, 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        if (action != null && action == SignalDetectorService.ACTION_STOP) {
            Log.d(TAG, "onActivityResult: exit received.")
            unbindSDService()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        unbindSDService()
        reloadPreferences()
        bindSDService()
    }

    private fun reloadPreferences() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        bsmarker = sharedPref.getBoolean("show_base_station", false)
        tradunits = sharedPref.getBoolean("traditional_units", false)
        baseLayer = sharedPref.getString("tile_source", "osm")
        taAsDistance = sharedPref.getBoolean("ta_distance", false)

        updateUnits()
        updateGui()
        setMapView(baseLayer!!)
        addMapOverlays(sharedPref.getString("overlay_tile_source", "provider")!!)
    }

    fun exitApp(x: MenuItem) {
        unbindSDService()
        finish()
    }

    override fun onDestroy() {
        if (mBound) {
            unbindService(mConnection)
            mBound = false
        }
        //        System.gc();
        super.onDestroy()
    }

    /**
     * Dialog to prompt users to enable GPS on the device.
     */
    @SuppressLint("ValidFragment")
    inner class EnableGpsDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
            return AlertDialog.Builder(activity)
                    .setTitle(R.string.enable_gps)
                    .setMessage(R.string.enable_gps_dialog)
                    .setPositiveButton(R.string.enable_gps) { dialog, which -> enableLocationSettings() }
                    .create()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SIGINFOKEY, mSignalInfo)
    }

    private fun setupSharing() {
        val logUris = ArrayList<Uri>()
        val logPath = getExternalFilesDir("")
        val logNames = arrayOf("cellinfolte.csv", "ltecells.csv",
                "esmrcells.csv", "cdmacells.csv", "gsmcells.csv")

        for (fileName in logNames) {
            val logFile = File(logPath, fileName)

            if (logFile.exists())
                logUris.add(getUriForFile(applicationContext,
                        "com.lordsutch.android.signaldetector.fileprovider", logFile))
        }

        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND_MULTIPLE
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, logUris)
        shareIntent.type = "text/csv"
        shareIntent.flags = FLAG_GRANT_READ_URI_PERMISSION

        if (mShareActionProvider != null)
            mShareActionProvider!!.setShareIntent(shareIntent)
    }

    protected fun addMapOverlays(layer: String) {
        var layer = layer
        var layerName = layer

        if (layer.equals("provider", ignoreCase = true)) {
            layer = mTelephonyManager!!.simOperator
            if (layer == null)
                layer = mTelephonyManager!!.networkOperator
            layerName = mTelephonyManager!!.simOperatorName
        }

        coverageLayer = layer
        execJavascript("setOverlayLayer(\"$layer\")")
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

    protected fun setMapView(layer: String) {
        baseLayer = layer
        execJavascript("setBaseLayer(\"$layer\")")
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

    fun updateSigInfo(signalDetector: SignalDetector, signal: SignalInfo) {
        signalDetector.mSignalInfo = signal
    }

    override fun attachBaseContext(baseContext: Context) {
        var base = baseContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val currentLocales = base.resources.configuration.locales
            if (!isSupportedLocale(currentLocales.get(0))) {
                val supportedLocales = filterUnsupportedLocales(currentLocales)
                if (!supportedLocales.isEmpty) {
                    val config = Configuration()
                    config.locales = supportedLocales
                    base = base.createConfigurationContext(config)
                }
            }
        }
        super.attachBaseContext(base)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun filterUnsupportedLocales(locales: LocaleList) : LocaleList {
        val filtered = ArrayList<Locale>(locales.size())

        for (i in 0 until locales.size()) {
            val loc = locales[i];
            if (isSupportedLocale(loc)) {
                filtered.add(loc)
            }
        }
        return LocaleList(*filtered.toTypedArray())
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun isSupportedLocale(locale: Locale) : Boolean {
        return locale.language in BuildConfig.LOCALES || locale.toLanguageTag() in BuildConfig.LOCALES
    }
}
