package com.lordsutch.android.signaldetector

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.preference.PreferenceManager
import android.telephony.*
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import android.text.TextUtils
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eu.chainfire.libsuperuser.Shell
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*

class SignalDetectorService : Service() {
    private val mNotificationId = 1

    private var mCellLocation: CellLocation? = null
    private var mSignalStrength: SignalStrength? = null
    private var mManager: TelephonyManager? = null
    private var mHTCManager: Any? = null
    private var mLocation: Location? = null
    private var mNotifyMgr: NotificationManager? = null

    private var mCellInfo: List<CellInfo>? = null

    private var mBinder: IBinder = LocalBinder()      // interface for clients that bind

    private var loggingEnabled = false
    private var mLocationManager: LocationManager? = null
    private var listening = false

    private var mBuilder: NotificationCompat.Builder? = null
    private var mNotification: Notification? = null
    private var broadcaster: LocalBroadcastManager? = null

    private var rootSessionCat: Shell.Interactive? = null
    private var rootSessionEcho: Shell.Interactive? = null
    private var EARFCN = Int.MAX_VALUE

    private var logFilesEnabled: Set<String>? = DEFAULT_LOGS

    private var provider: String? = null
    private val locs = LinkedList<Location>()

    private var lastValidSpeed = Double.NaN
    private var lteLine: String? = null
    private var CdmaLine: String? = null
    private var GSMLine: String? = null

    // Swiped from https://en.wikipedia.org/wiki/Mobile_country_code
    private val threeDigitMNCList = intArrayOf(
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
    )

    private val mLocListener = object : LocationListener {
        override fun onLocationChanged(mLoc: Location) {
            updateLocations(mLoc)
            if (mLocation !== mLoc) {
                mLocation = mLoc
                updatelog(true)
                // updatelog(false); // Only log when there's a signal strength change.
            }
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {

        }

        override fun onProviderEnabled(provider: String) {

        }

        override fun onProviderDisabled(provider: String) {

        }
    }

    // Listener for signal strength.
    internal val mListener: PhoneStateListener = object : PhoneStateListener() {
        override fun onCellLocationChanged(mLocation: CellLocation?) {
            super.onCellLocationChanged(mLocation)
            if (mLocation != null)
                mCellLocation = mLocation

            updatelog(true)
        }

        override fun onCellInfoChanged(cellInfo: List<CellInfo>?) {
            super.onCellInfoChanged(cellInfo)
            if (mCellInfo != null)
                mCellInfo = cellInfo

            updatelog(true)
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            super.onSignalStrengthsChanged(signalStrength)
            if (signalStrength != null)
                mSignalStrength = signalStrength
            //            if (mSignalStrength != null) {
            //                Log.d(TAG, mSignalStrength.toString());
            //            }
            updatelog(true)
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        internal// Return this instance of SignalDetectorService so clients can call public methods
        val service: SignalDetectorService
            get() = this@SignalDetectorService
    }

    /**
     * Start cat on /dev/smd11 and STDOUT line by line, updating EARFCN when a valid one is detected
     */
    private fun sendRootCat() {
        rootSessionCat!!.addCommand(arrayOf("cat /dev/smd11"), 1,
                object : Shell.OnCommandLineListener {
                    override fun onCommandResult(commandCode: Int, exitCode: Int) {
                        if (exitCode != 0) {
                            Log.e(TAG, "Error with root cat shell$exitCode")
                            // And close it because it errored out.
                            rootSessionCat!!.close()
                            // This prevents echo from being sent
                            rootSessionCat = null
                        }
                    }

                    override fun onLine(line: String) {
                        val tmpEARFCN = convertEARFCNtoInt(line)
                        if (validEARFCN(tmpEARFCN)) {
                            EARFCN = tmpEARFCN
                            Log.d(TAG, "EARFCN $tmpEARFCN")
                        }
                    }
                })
    }

    private fun openRootSessionForCat() {
        rootSessionCat = Shell.Builder().useSU().setWantSTDERR(true).setMinimalLogging(false).open { commandCode, exitCode, output ->
            // Callback to report whether the shell was successfully started up
            if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                Log.e(TAG, "Error opening root shell: exitCode $exitCode")
            } else {
                // Shell is up: start processing
                Log.d(TAG, "Root cat shell up")
            }
        }
    }

    private fun openRootSessionForEcho() {
        rootSessionEcho = Shell.Builder().useSU().setWantSTDERR(true).setMinimalLogging(false).setWatchdogTimeout(5).open { commandCode, exitCode, output ->
            // Callback to report whether the shell was successfully started up
            if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                Log.e(TAG, "Error opening root shell: exitCode $exitCode")
            } else {
                // Shell is up: start processing
                Log.d(TAG, "Root echo shell up")
            }
        }
    }

    private fun sendRootEARFCN() {
        if (rootSessionEcho != null && rootSessionCat != null) {
            rootSessionEcho!!.addCommand(arrayOf("echo \"AT\\\$QCRSRP?\\r\\n\" > /dev/smd11"), 0
            ) { commandCode, exitCode, output ->
                if (exitCode < 0) {
                    Log.e(TAG, "Error executing echo: exitCode $exitCode")
                    if (exitCode == -1) {
                        rootSessionEcho!!.close()
                        openRootSessionForEcho()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        broadcaster = LocalBroadcastManager.getInstance(this)

        // Only get earfcn if option is enabled
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.application)
        if (sharedPref.getBoolean("earfcn", false)) {
            openRootSessionForCat()
            if (rootSessionCat != null)
                sendRootCat()
            openRootSessionForEcho()
        }
    }

    private fun convertEARFCNtoInt(rawRootOutput: String): Int {
        // Since we get every line from cat, we only want the one that starts with $QCRSRP
        if (rawRootOutput.matches("\\\$QCRSRP(.*)".toRegex())) {
            // Strip off "$QCRSRP: " from beginning and split into fields, convert to list of strings
            val fields = ArrayList(Arrays.asList(*rawRootOutput.substring(9).split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
            // Return only the EARFCN as Int
            return Integer.parseInt(fields[1])
        }
        // return Int.MAX_VALUE to signify no change
        return Int.MAX_VALUE
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // The user-visible name of the channel.
        val name = getString(R.string.group_1_name)
        // The user-visible description of the channel.
        val description = getString(R.string.group_1_description)

        val mChannel = NotificationChannel(CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_LOW)
        // Configure the notification channel.
        mChannel.description = description
        mChannel.setShowBadge(false)
        mChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        mNotificationManager.createNotificationChannel(mChannel)
    }

    @SuppressLint("WrongConstant")
    override fun onBind(intent: Intent): IBinder? {
        val resultIntent = Intent(this, SignalDetector::class.java)
        val resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0)

        val stopIntent = Intent(this, SignalDetector::class.java).setAction(ACTION_STOP)
        val exitIntent = PendingIntent.getActivity(this, 0, stopIntent, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel()

        mBuilder = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_0g)
                .setContentTitle(getString(R.string.signal_detector_is_running))
                .setContentText(getString(R.string.loading))
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(R.drawable.ic_close_black_24dp, "Exit", exitIntent)
                .setContentIntent(resultPendingIntent)

        mNotification = mBuilder!!.build()

        startForeground(mNotificationId, mNotification)

        mManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        mHTCManager = getSystemService("htctelephony")

        mNotifyMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.application)

        loggingEnabled = sharedPref.getBoolean("logging", true)
        logFilesEnabled = sharedPref.getStringSet("sitesToLog", DEFAULT_LOGS)

        startGPS()

        return mBinder
    }

    fun startGPS() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        mLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Register the listener with the telephony manager
        mManager!!.listen(mListener, PhoneStateListener.LISTEN_CELL_INFO or PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

        /*
        mManager.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
                PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_CELL_INFO);
         */

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.application)
        val lessPower = sharedPref.getBoolean("low_power", false)

        val mCriteria = Criteria()
        mCriteria.isAltitudeRequired = false
        mCriteria.isCostAllowed = false
        mCriteria.powerRequirement = if (lessPower) Criteria.POWER_LOW else Criteria.POWER_HIGH
        mCriteria.accuracy = if (lessPower) Criteria.NO_REQUIREMENT else Criteria.ACCURACY_FINE

        provider = mLocationManager!!.getBestProvider(mCriteria, true)
        Log.d(TAG, "Using GPS provider " + provider!!)

        mLocationManager!!.requestLocationUpdates(provider, 1000, 0f, mLocListener)
        mLocation = mLocationManager!!.getLastKnownLocation(provider)
        listening = true
    }

    private fun appendLog(logfile: String, text: String, header: String) {
        var newfile = false
        val filesdir = getExternalFilesDir(null)

        val logFile = File(filesdir, logfile)
        if (!logFile.exists()) {
            try {
                logFile.createNewFile()
                newfile = true
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }

        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            val buf = BufferedWriter(FileWriter(logFile, true))
            if (newfile) {
                buf.append(header)
                buf.newLine()
            }
            buf.append(text)
            buf.newLine()
            buf.close()
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

    }

    private fun parseSignalStrength(): Int {
        val sstrength = mSignalStrength!!.toString()
        var strength = -999

        val bits = sstrength.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (bits.size >= 10)
            try {
                strength = Integer.parseInt(bits[9])
            } catch (e: NumberFormatException) {
            }

        return strength
    }

    internal fun validTimingAdvance(timingAdvance: Int): Boolean {
        return timingAdvance > 0 && timingAdvance != Int.MAX_VALUE
    }

    internal fun validRSSISignalStrength(strength: Int): Boolean {
        return strength in -119..-1
    }

    internal fun validLTESignalStrength(strength: Int): Boolean {
        return strength in -199..-1
    }

    internal fun validPhysicalCellID(pci: Int): Boolean {
        return pci in 0..503
    }

    internal fun validCellID(eci: Int): Boolean {
        return eci in 0..0x0FFFFFFF
    }

    internal fun validTAC(tac: Int): Boolean {
        return tac in 0x1..0xFFFE // 0, FFFF are reserved values
    }

    internal fun validEARFCN(earfcn: Int): Boolean {
        // Some phones (Samsung S7 Edge) report 0, which is impossible so we'll ignore that too.
        return earfcn != Int.MAX_VALUE && earfcn > 0
    } // Int.MAX_VALUE signifies no change or empty / default EARFCN

    private fun calcAverageSpeed(): Double {
        var totspeed = 0.0
        var weights = 0.0
        val now = System.currentTimeMillis()

        if (locs.isEmpty())
            return 0.0

        for (loc in locs) {
            if (loc.hasSpeed()) {
                val tdiff = Math.max(FIVE_SECONDS - Math.abs(loc.time - now), 0)
                val weight = Math.log1p(tdiff.toDouble()) + 1

                totspeed += (loc.speed * weight).toFloat()
                weights += weight.toFloat()
            }
        }

        return if (weights < 1.0) 0.0 else totspeed / weights

    }

    private fun updateLocations(loc: Location) {
        val now = System.currentTimeMillis()

        val it = locs.iterator()
        var inlist = false

        while (it.hasNext()) {
            val x = it.next()
            if (x == loc) {
                inlist = true
            } else if (Math.abs(now - x.time) > FIVE_SECONDS) {
                it.remove()
            }
        }

        if (!inlist)
            locs.add(loc)
    }

    private fun timingAdvanceToMeters(timingAdvance: Int, isFDD: Boolean): Double {
        return if (!validTimingAdvance(timingAdvance)) Double.NaN else (if (isFDD) timingAdvance else timingAdvance - 20) * 149.85
    }

    private fun isBandFDD(lteBand: Int): Boolean {
        return lteBand !in 33..52
    }

    private fun guessLteBandFromEARFCN(earfcn: Int): Int {
        // Stolen from http://niviuk.free.fr/lte_band.php
        return when {
            earfcn <= 599 -> 1
            earfcn <= 1199 -> 2
            earfcn <= 1949 -> 3
            earfcn <= 2399 -> 4
            earfcn <= 2649 -> 5
            earfcn <= 2749 -> 6
            earfcn <= 2690 -> 7
            earfcn <= 3799 -> 8
            earfcn <= 4149 -> 9
            earfcn <= 4749 -> 10
            earfcn <= 4949 -> 11
            earfcn <= 5179 -> 12
            earfcn <= 5279 -> 13
            earfcn <= 5379 -> 14
            earfcn in 5730..5849 -> 17
            earfcn in 5850..5999 -> 18
            earfcn in 6000..6149 -> 19
            earfcn in 6150..6449 -> 20
            earfcn in 6450..6599 -> 21
            earfcn in 6600..7399 -> 22
            earfcn in 7500..7699 -> 23
            earfcn in 7700..8039 -> 24
            earfcn in 8040..8689 -> 25
            earfcn in 8690..9039 -> 26
            earfcn in 9040..9209 -> 27
            earfcn in 9210..9659 -> 28
            earfcn in 9660..9769 -> 29
            earfcn in 9770..9869 -> 30
            earfcn in 9870..9919 -> 31
            earfcn in 9920..10359 -> 32
            earfcn in 36000..36199 -> 33
            earfcn in 36200..36349 -> 34
            earfcn in 36350..36949 -> 35
            earfcn in 36950..37549 -> 36
            earfcn in 37550..37749 -> 37
            earfcn in 37750..38249 -> 38
            earfcn in 38250..38649 -> 39
            earfcn in 38650..39649 -> 40
            earfcn in 39650..41589 -> 41
            earfcn in 41590..43589 -> 42
            earfcn in 43590..45589 -> 43
            earfcn in 45590..46589 -> 44
            earfcn in 46590..46789 -> 45
            earfcn in 46790..54539 -> 46
            earfcn in 54540..55239 -> 47
            earfcn in 55240..56739 -> 48
            earfcn in 56740..58239 -> 49
            earfcn in 58240..59089 -> 50
            earfcn in 59090..59139 -> 51
            earfcn in 59140..60139 -> 52
            earfcn in 65536..66435 -> 65
            earfcn in 66436..67335 -> 66
            earfcn in 67336..67535 -> 67
            earfcn in 67536..67835 -> 68
            earfcn in 67836..68335 -> 69
            earfcn in 68336..68585 -> 70
            earfcn in 68586..68935 -> 71
            earfcn in 68936..68985 -> 72
            earfcn in 68986..69035 -> 73
            earfcn in 69036..69465 -> 74
            earfcn in 69466..70315 -> 75
            earfcn in 70316..70365 -> 76
            earfcn in 70366..70545 -> 85
            earfcn in 255144..256143 -> 252
            earfcn in 260894..262143 -> 253
            else -> 0
        }// Bands 15, 16 missing
    }

    private fun guessLteBand(mcc: Int, mnc: Int, gci: Int, earfcn: Int): Int {
        if (validEARFCN(earfcn)) {
            val band = guessLteBandFromEARFCN(earfcn)
            if (band > 0)
                return band
        }

        val sector = gci and 0xff
        when {
            mcc == 311 && (mnc == 490 || mnc == 870) -> return 41 // Legacy Clear sites are on band 41
            mcc == 310 && mnc == 120 || mcc == 312 && mnc == 530 -> {
                // Sprint (312-530 is prepaid)
                if (gci and 0x00100000 != 0)
                // 3rd digit is odd if B41
                    return 41

                return when (sector) {
                    in 0x19..0x1b, // Ericsson/ALU
                    in 0x0f..0x10 // Samsung
                    -> 26
                    0x11 -> if (gci in 0x7600000..0xB9FFFFF) { // Samsung
                        26
                    } else {
                        25
                    }
                    // mini macros starts with what looks like B25 but b41 sectors
                    in 0x31..0x43 -> 41
                    else
                        // small cells - thanks Flompholph
                    -> if (gci and 0x0f0000 >= 0x090000 && gci <= 0x0fdfffff && sector == 0x01) 41 else 25
                }
            }
            mcc == 310 && (mnc == 410 || mnc == 150) -> // AT&T
                return when (sector) {
                    in 0x00..0x02 -> 5
                    in 0x08..0x0a -> 2
                    in 0x16..0x19 -> 4
                    in 0x95..0x9a -> 30
                    else -> 17
                }
            mcc == 310 && mnc == 260 -> return when (sector) {
                in 0x01..0x04 -> 4
                in 0x11..0x14 -> 2
                in 0x21..0x23 -> 12
                in 0x05..0x07 -> 12
                in 0x15..0x15 -> 12
                else -> 0
            }
            mcc == 311 && mnc == 480 -> // Verizon
                return when {
                    sector <= 6 -> 13
                    else -> when (sector % 10) {
                        2, 3 -> 4
                        4, 5 -> 2
                        7 -> 5
                        else -> 13
                    }
                }
            mcc == 312 && mnc == 190 -> // nTelos
                return when (sector) {
                    0x0c, 0x16, 0x20 -> 26
                    0x0d, 0x17, 0x21 -> 2
                    in 0x01..0x03 -> 25
                    else -> 13
                }
            // T-Mobile
            else -> return 0
        }
    }

    fun networkString(networkType: Int): String {
        when (networkType) {
            TelephonyManager.NETWORK_TYPE_EHRPD -> return "eHRPD"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> return "EVDO Rel. 0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> return "EVDO Rev. A"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> return "EVDO Rev. B"
            TelephonyManager.NETWORK_TYPE_GPRS -> return "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> return "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> return "UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA -> return "HSPA"
            TelephonyManager.NETWORK_TYPE_HSPAP -> return "HSPA+"
            TelephonyManager.NETWORK_TYPE_CDMA -> return "CDMA"
            TelephonyManager.NETWORK_TYPE_1xRTT -> return "1xRTT"
            TelephonyManager.NETWORK_TYPE_IDEN -> return "iDEN"
            TelephonyManager.NETWORK_TYPE_LTE -> return "LTE"
            TelephonyManager.NETWORK_TYPE_IWLAN -> return "IWLAN"
            else -> return "Unknown"
        }
    }

    private fun networkIcon(networkType: Int): Int {
        val icon: Int

        when (networkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> icon = R.drawable.ic_stat_4g

            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSDPA /* 3.5G? */, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_CDMA -> icon = R.drawable.ic_stat_3g

            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_IDEN -> icon = R.drawable.ic_stat_2g

            else -> icon = R.drawable.ic_stat_0g
        }

        return icon
    }

    private fun locationFixAge(loc: Location): Long {
        return (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / (1000 * 1000)
    }

    protected fun valueString(value: Int): String {
        return if (value == Int.MAX_VALUE) "" else value.toString()
    }

    private fun getLegacyCellLocationData(signal: SignalInfo) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        mCellLocation = mManager!!.cellLocation
        if (mCellLocation is CdmaCellLocation) {
            val x = mCellLocation as CdmaCellLocation?

            signal.bsid = x!!.baseStationId
            signal.nid = x.networkId
            signal.sid = x.systemId

            if (signal.bsid < 0)
                signal.bsid = Int.MAX_VALUE

            if (signal.nid <= 0)
                signal.nid = Int.MAX_VALUE

            if (signal.sid <= 0)
                signal.sid = Int.MAX_VALUE

            signal.bslat = fixCDMAPosition(x.baseStationLatitude)
            signal.bslon = fixCDMAPosition(x.baseStationLongitude)

            /* Deal with possiblity these may be swapped in Android 8 - thanks Mikejeep
             * https://issuetracker.google.com/issues/63130155 */
            if (validLocation(signal.bslat, signal.bslon) && Math.abs(signal.bslat - signal.latitude) > 1.0) {
                val tmp = signal.bslat
                signal.bslat = signal.bslon
                signal.bslon = tmp
            }
        } else if (mCellLocation is GsmCellLocation) {
            val x = mCellLocation as GsmCellLocation?

            signal.lac = x!!.lac
            if (signal.lac < 0 || signal.lac > 0xffff)
                signal.lac = Int.MAX_VALUE

            signal.psc = x.psc
            if (signal.psc < 0)
                signal.psc = Int.MAX_VALUE

            val cid = x.cid

            if (cid >= 0) {
                signal.rnc = cid shr 16
                signal.cid = cid and 0xffff
                signal.fullCid = cid
            } else {
                signal.rnc = Int.MAX_VALUE
                signal.cid = Int.MAX_VALUE
                signal.fullCid = Int.MAX_VALUE
            }
        }
    }

    private fun fixCDMAPosition(coordinate: Int): Double {
        return if (coordinate != 0 && coordinate != Int.MAX_VALUE) coordinate / 14400.0 else java.lang.Double.NaN
    }

    private fun updatelog(log: Boolean) {
        if (mSignalStrength == null)
            return

        var gotID = false

        if (mLocation == null &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocation = mLocationManager!!.getLastKnownLocation(provider)
        }

        if (mLocation == null)
            mLocation = Location(provider)

        var signal = SignalInfo()

        signal.latitude = mLocation!!.latitude
        signal.longitude = mLocation!!.longitude
        if (mLocation!!.hasSpeed()) {
            signal.speed = mLocation!!.speed.toDouble()
            lastValidSpeed = signal.speed
        } else {
            signal.speed = lastValidSpeed
        }
        signal.accuracy = mLocation!!.accuracy.toDouble()
        signal.altitude = mLocation!!.altitude
        signal.bearing = mLocation!!.bearing.toDouble()
        signal.avgSpeed = calcAverageSpeed()

        signal.phoneType = mManager!!.phoneType
        signal.networkType = mManager!!.networkType
        signal.roaming = mManager!!.isNetworkRoaming
        signal.operator = mManager!!.networkOperator
        signal.operatorName = mManager!!.networkOperatorName

        signal.gsmSigStrength = mSignalStrength!!.gsmSignalStrength
        signal.gsmSigStrength = if (signal.gsmSigStrength < 32) -113 + 2 * signal.gsmSigStrength else -9999

        signal.cdmaSigStrength = mSignalStrength!!.cdmaDbm
        signal.evdoSigStrength = mSignalStrength!!.evdoDbm

        //if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        getLegacyCellLocationData(signal)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mCellInfo = mManager!!.allCellInfo
        }

        if (mCellInfo != null) {
            sendRootEARFCN()
            signal.otherCells = ArrayList()

            for (item in mCellInfo!!) {
                //                Log.d(TAG, item.toString());
                if (item is CellInfoLte) {
                    val cstr = item.cellSignalStrength
                    val cellid = item.cellIdentity

                    if (item.isRegistered) {
                        if (cstr != null) {
                            signal.lteSigStrength = cstr.dbm
                            if (signal.lteSigStrength > 0)
                                signal.lteSigStrength = -(signal.lteSigStrength / 10)
                            signal.timingAdvance = cstr.timingAdvance
                        }

                        if (cellid != null) {
                            signal.gci = cellid.ci
                            signal.pci = cellid.pci
                            signal.tac = cellid.tac
                            signal.mcc = cellid.mcc
                            signal.mnc = cellid.mnc
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                signal.mccString = cellid.mccString
                                signal.mncString = cellid.mncString
                            }

                            if (signal.mccString == null)
                                signal.mccString = formatMcc(signal.mcc)

                            if (signal.mncString == null)
                                signal.mncString = formatMnc(signal.mcc, signal.mnc)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                signal.earfcn = cellid.earfcn
                            else
                                signal.earfcn = EARFCN

                            signal.lteBand = guessLteBand(signal.mcc, signal.mnc, signal.gci, signal.earfcn)
                            signal.isFDD = isBandFDD(signal.lteBand)
                            gotID = true
                        }
                    } else {
                        val otherCell = OtherLteCell()

                        if (cstr != null) {
                            otherCell.lteSigStrength = cstr.dbm
                            if (otherCell.lteSigStrength > 0)
                                otherCell.lteSigStrength = -(otherCell.lteSigStrength / 10)
                            otherCell.timingAdvance = cstr.timingAdvance
                        }

                        if (cellid != null) {
                            otherCell.gci = cellid.ci
                            otherCell.pci = cellid.pci
                            otherCell.tac = cellid.tac
                            otherCell.mcc = cellid.mcc
                            otherCell.mnc = cellid.mnc
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                otherCell.mccString = cellid.mccString
                                otherCell.mncString = cellid.mncString
                            } else {
                                otherCell.mccString = formatMcc(otherCell.mcc)
                                otherCell.mncString = formatMnc(otherCell.mcc, otherCell.mnc)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                otherCell.earfcn = cellid.earfcn
                            otherCell.lteBand = guessLteBand(otherCell.mcc, otherCell.mnc, otherCell.gci, otherCell.earfcn)
                            otherCell.isFDD = isBandFDD(otherCell.lteBand)
                        }
                        signal.otherCells!!.add(otherCell)
                    }
                } else if (item is CellInfoCdma) {
                    val cstr = item.cellSignalStrength
                    val cellid = item.cellIdentity

                    signal.bsid = cellid.basestationId
                    signal.bslat = fixCDMAPosition(cellid.latitude)
                    signal.bslon = fixCDMAPosition(cellid.longitude)
                    signal.nid = cellid.networkId
                    signal.sid = cellid.systemId

                    signal.cdmaSigStrength = cstr.cdmaDbm
                    signal.evdoSigStrength = cstr.evdoDbm
                } else if (item is CellInfoGsm) {
                    val cstr = item.cellSignalStrength
                    val cellid = item.cellIdentity

                    signal.lac = cellid.lac
                    signal.gsmMcc = cellid.mcc
                    signal.gsmMnc = cellid.mnc

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        signal.gsmMccString = cellid.mccString
                        signal.gsmMncString = cellid.mncString
                    }

                    if (signal.gsmMccString == null)
                        signal.gsmMccString = formatMcc(signal.gsmMcc)

                    if (signal.gsmMncString == null)
                        signal.gsmMncString = formatMnc(signal.gsmMnc, signal.gsmMcc)

                    val cid = cellid.cid
                    signal.cid = cid
                    signal.fullCid = cid
                    signal.gsmSigStrength = cstr.dbm
                    if (cid != Int.MAX_VALUE) {
                        signal.rnc = cid shr 16
                        signal.cid = cid and 0xffff
                    } else {
                        signal.rnc = Int.MAX_VALUE
                        signal.cid = Int.MAX_VALUE
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        signal.arfcn = cellid.arfcn
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        signal.bsic = cellid.bsic
                        signal.gsmTimingAdvance = cstr.timingAdvance
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    if (item is CellInfoWcdma) {
                        val cstr = item.cellSignalStrength
                        val cellid = item.cellIdentity

                        val cid = cellid.cid
                        signal.fullCid = cid
                        if (cid != Int.MAX_VALUE) {
                            signal.rnc = cid shr 16
                            signal.cid = cid and 0xffff
                        } else {
                            signal.rnc = Int.MAX_VALUE
                            signal.cid = Int.MAX_VALUE
                        }

                        signal.lac = cellid.lac
                        signal.psc = cellid.psc
                        signal.gsmMcc = cellid.mcc
                        signal.gsmMnc = cellid.mnc
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            signal.gsmMccString = cellid.mccString
                            signal.gsmMncString = cellid.mncString
                        } else {
                            signal.gsmMccString = formatMcc(signal.gsmMcc)
                            signal.gsmMncString = formatMnc(signal.gsmMnc, signal.gsmMcc)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            signal.uarfcn = cellid.uarfcn
                        }

                        signal.gsmSigStrength = cstr.dbm
                    }
                }
            }
        }

        if (!validLTESignalStrength(signal.lteSigStrength))
            signal.lteSigStrength = parseSignalStrength()

        if (!gotID && mHTCManager != null) {
            try {
                val cellID: String

                val m = mHTCManager!!.javaClass.getMethod("getSectorId", Int::class.javaPrimitiveType!!)
                cellID = m!!.invoke(mHTCManager, *arrayOf<Any>(Integer.valueOf(1))) as String
                signal.gci = Integer.parseInt(cellID, 16)
            } catch (e: NoSuchMethodException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }

        if (!validLTESignalStrength(signal.lteSigStrength)) {
            try {
                val m: Method = mSignalStrength!!.javaClass.getMethod("getLteRsrp")

                signal.lteSigStrength = m.invoke(mSignalStrength) as Int
            } catch (e: NoSuchMethodException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            }

        }

        var cellIdInfo = getString(R.string.none)
        var basicCellInfo = cellIdInfo

        var networkType = signal.networkType

        val isIWLAN = (networkType == TelephonyManager.NETWORK_TYPE_IWLAN)

        if (isIWLAN) {
            if (signal.lteBand > 0 && validLTESignalStrength(signal.lteSigStrength))
                networkType = TelephonyManager.NETWORK_TYPE_LTE
            else if (validRSSISignalStrength(signal.evdoSigStrength))
                networkType = TelephonyManager.NETWORK_TYPE_EHRPD
            else if (validRSSISignalStrength(signal.cdmaSigStrength))
                networkType = TelephonyManager.NETWORK_TYPE_1xRTT
            else if (signal.lac != Int.MAX_VALUE)
                networkType = TelephonyManager.NETWORK_TYPE_UMTS
            else if (validRSSISignalStrength(signal.gsmSigStrength))
                networkType = TelephonyManager.NETWORK_TYPE_GPRS
        }

        if (networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            basicCellInfo = String.format("%s %s", signal.operatorName,
                    networkString(networkType))
        }

        var cellIds = ArrayList<String>()

        if (networkType == TelephonyManager.NETWORK_TYPE_LTE) {
            if (signal.lteBand != 0)
                basicCellInfo += String.format(Locale.getDefault(), " band\u202f%d", signal.lteBand)

            if (validLTESignalStrength(signal.lteSigStrength))
                basicCellInfo += String.format(Locale.US, " %d\u202fdBm", signal.lteSigStrength)

            if (validTimingAdvance(signal.timingAdvance))
                basicCellInfo += String.format(Locale.US, " TA=%d\u202fµs", signal.timingAdvance)

            cellIds = lteCellInfo(signal)
        } else if (validRSSISignalStrength(signal.cdmaSigStrength) && validSID(signal.sid)) {
            val sigStrength = if (validRSSISignalStrength(signal.evdoSigStrength)) signal.evdoSigStrength else signal.cdmaSigStrength

            basicCellInfo += String.format(Locale.US, " %d\u202fdBm", sigStrength)
            if (validRSSISignalStrength(signal.evdoSigStrength))
                basicCellInfo += String.format(Locale.US, " voice %d\u202fdBm", signal.cdmaSigStrength)

            cellIds = cdmaCellInfo(signal)
        } else if (validRSSISignalStrength(signal.gsmSigStrength) && validCID(signal.cid)) {
            basicCellInfo += String.format(Locale.US, " %d\u202fdBm", signal.gsmSigStrength)
            if (validTimingAdvance(signal.gsmTimingAdvance))
                basicCellInfo += String.format(Locale.US, " TA=%d\u202fµs", signal.gsmTimingAdvance)

            cellIds = gsmCellInfo(signal)
        }

        if (cellIds.isEmpty())
            cellIdInfo = getString(R.string.missing)
        else
            cellIdInfo = TextUtils.join(", ", cellIds)

        if (signal.roaming)
            basicCellInfo += " " + getString(R.string.roamingInd)

        if (isIWLAN)
            basicCellInfo += " WiFi"

        mBuilder = mBuilder!!.setContentTitle(basicCellInfo)
                .setContentText(cellIdInfo)
                .setSmallIcon(networkIcon(networkType))

        mNotification = mBuilder!!.build()
        mNotifyMgr!!.notify(mNotificationId, mNotification)

        signal.fixAge = locationFixAge(mLocation!!)

        if (loggingEnabled && log) {
            Log.d(TAG, signal.toString())

            val tz = TimeZone.getTimeZone("UTC")
            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US) // Quoted "Z" to indicate UTC, no timezone offset
            df.timeZone = tz
            val now = Date()

            val nowAsISO = df.format(now)
            val slat = Location.convert(signal.latitude, Location.FORMAT_DEGREES)
            val slon = Location.convert(signal.longitude, Location.FORMAT_DEGREES)

            if (signal.networkType == TelephonyManager.NETWORK_TYPE_LTE &&
                    logFilesEnabled!!.contains("lte") &&
                    (validLTESignalStrength(signal.lteSigStrength) ||
                            validPhysicalCellID(signal.pci) || validCellID(signal.gci))) {
                val estDistance = timingAdvanceToMeters(signal.timingAdvance, signal.isFDD)

                val newLteLine = slat + "," + slon + "," +
                        (if (validCellID(signal.gci)) String.format(Locale.US, "%08X", signal.gci) else "") + "," +
                        valueString(signal.pci) + "," +
                        (if (validLTESignalStrength(signal.lteSigStrength)) signal.lteSigStrength.toString() else "") + "," +
                        String.format(Locale.US, "%.0f", signal.altitude) + "," +
                        (if (validTAC(signal.tac)) String.format(Locale.US, "%04X", signal.tac) else "") + "," +
                        String.format(Locale.US, "%.0f", signal.accuracy) + "," +
                        (if (validCellID(signal.gci)) String.format(Locale.US, "%06X", signal.gci / 256) else "") + "," +
                        (if (signal.lteBand > 0) valueString(signal.lteBand) else "") + "," +
                        valueString(signal.timingAdvance) + "," +
                        (if (validEARFCN(signal.earfcn)) valueString(signal.earfcn) else "") + "," +
                        nowAsISO + "," + now.time.toString() + "," +
                        (if (isBandFDD(signal.lteBand)) "1" else "0") + "," +
                        (if (java.lang.Double.isNaN(estDistance)) "" else String.format(Locale.US, "%.0f", estDistance)) + "," +
                        signal.mccString + "," +
                        signal.mncString

                if (lteLine == null || newLteLine != lteLine) {
                    //                    Log.d(TAG, "Logging LTE cell.");
                    appendLog("ltecells.csv", newLteLine, "latitude,longitude,cellid,physcellid,dBm,altitude,tac,accuracy,baseGci,band,timingAdvance,earfcn,timestamp,timeSinceEpoch,fdd,estDistance,mcc,mnc")
                    lteLine = newLteLine
                }
            }

            if (mCellInfo != null && logFilesEnabled!!.contains("lte")) {
                for (item in mCellInfo!!) {
                    if (item is CellInfoLte) {
                        val mIdentity = item.cellIdentity
                        val mSS = item.cellSignalStrength

                        val tac = mIdentity.tac
                        val eci = mIdentity.ci
                        val pci = mIdentity.pci
                        val mcc = mIdentity.mcc
                        val mnc = mIdentity.mnc
                        var mccString: String? = null
                        var mncString: String? = null

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            mccString = mIdentity.mccString
                            mncString = mIdentity.mncString
                        }

                        if(mccString == null)
                            mccString = formatMcc(mcc)
                        if(mncString == null)
                            mncString = formatMnc(mcc, mnc)

                        var earfcn = Int.MAX_VALUE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            earfcn = mIdentity.earfcn
                        val rsrp = mSS.dbm
                        val timingAdvance = mSS.timingAdvance
                        val lteBand = guessLteBand(mcc, mnc, eci, earfcn)
                        val isFDD = isBandFDD(lteBand)
                        val estDistance = timingAdvanceToMeters(timingAdvance, isFDD)

                        val cellLine = slat + "," + slon + "," +
                                String.format(Locale.US, "%.0f", signal.accuracy) + "," +
                                String.format(Locale.US, "%.0f", signal.altitude) + "," +
                                mccString + "," + mncString + "," +
                                (if (validTAC(tac)) String.format(Locale.US, "%04X", tac) else "") + "," +
                                (if (validCellID(eci)) String.format(Locale.US, "%08X", eci) else "") + "," +
                                (if (validPhysicalCellID(pci)) pci.toString() else "") + "," +
                                (if (validLTESignalStrength(rsrp)) rsrp.toString() else "") + "," +
                                (if (item.isRegistered()) "1" else "0") + "," +
                                (if (validCellID(eci)) String.format(Locale.US, "%06X", eci / 256) else "") + "," +
                                (if (lteBand > 0) lteBand.toString() else "") + "," +
                                valueString(timingAdvance) + "," +
                                (if (validEARFCN(earfcn)) String.format(Locale.US, "%d", earfcn) else "") + "," +
                                nowAsISO + "," + now.time.toString() + "," +
                                (if (isFDD) "1" else "0") + "," +
                                if (java.lang.Double.isNaN(estDistance)) "" else String.format(Locale.US, "%.0f", estDistance)

                        appendLog("cellinfolte.csv", cellLine,
                                "latitude,longitude,accuracy,altitude,mcc,mnc,tac,gci,pci,rsrp,registered,baseGci,band,timingAdvance,earfcn,timestamp,timeSinceEpoch,fdd,estDistance")

                    }
                }
            }

            if (validRSSISignalStrength(signal.cdmaSigStrength) && validSID(signal.sid) &&
                    logFilesEnabled!!.contains("cdma")) {
                val isValid = validLocation(signal.bslon, signal.bslat)

                val bslatstr = if (isValid) Location.convert(signal.bslat, Location.FORMAT_DEGREES) else ""
                val bslonstr = if (isValid) Location.convert(signal.bslon, Location.FORMAT_DEGREES) else ""

                val newCdmaLine = String.format(Locale.US, "%s,%s,%s,%s,%s,%d,%s,%s,%.0f,%.0f,%s,%d",
                        slat, slon, valueString(signal.sid), valueString(signal.nid), valueString(signal.bsid),
                        signal.cdmaSigStrength,
                        bslatstr, bslonstr, signal.altitude, signal.accuracy, nowAsISO, now.time)
                if (CdmaLine == null || newCdmaLine != CdmaLine) {
                    //                    Log.d(TAG, "Logging CDMA cell.");
                    appendLog(if (signal.sid >= 22404 && signal.sid <= 22451) "esmrcells.csv" else "cdmacells.csv",
                            newCdmaLine, "latitude,longitude,sid,nid,bsid,rssi,bslat,bslon,altitude,accuracy,timestamp,timeSinceEpoch")
                    CdmaLine = newCdmaLine
                }
            } else if (validRSSISignalStrength(signal.gsmSigStrength) && validCID(signal.cid) &&
                    logFilesEnabled!!.contains("gsm")) {
                val newGSMLine = String.format(Locale.US, "%s,%s,%.0f,%.0f,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s", slat, slon,
                        signal.altitude, signal.accuracy,
                        valueString(signal.cid), valueString(signal.rnc), valueString(signal.lac),
                        valueString(signal.psc), valueString(signal.gsmSigStrength),
                        nowAsISO, now.time, valueString(signal.bsic), valueString(signal.uarfcn),
                        valueString(signal.gsmTimingAdvance),
                        if (signal.gsmTimingAdvance != Int.MAX_VALUE) signal.gsmTimingAdvance * 550 else "",
                        signal.gsmMccString, signal.gsmMncString,
                        valueString(signal.arfcn))
                if (GSMLine == null || newGSMLine != GSMLine) {
                    //                    Log.d(TAG, "Logging GSM cell.");
                    appendLog("gsmcells.csv", newGSMLine, "latitude,longitude,altitude,accuracy,cid,rnc,lac,psc,rssi,timestamp,timeSinceEpoch,bsic,uarfcn,timingAdvance,estDistance,mcc,mnc,arfcn")
                    GSMLine = newGSMLine
                }
            }
        }

        sendResult(signal)
    }

    private fun formatMcc(mcc: Int) : String {
        return if (validMcc(mcc)) mcc.toString() else ""
    }

    private fun formatMnc(mcc: Int, mnc: Int): String {
        return if (validMcc(mcc) && validMnc(mnc))
            String.format(Locale.US, if (is3digitMnc(mcc)) "%03s" else "%02s", mnc)
        else
            ""
    }

    fun lteCellInfo(signal: SignalInfo): ArrayList<String> {
        val cellIds = ArrayList<String>()
        val plmnString: String

        if (validMcc(signal.mcc) && validMnc(signal.mnc)) {
            plmnString = signal.formatPLMN()
        } else {
            plmnString = formatOperator(signal.operator)
        }
        cellIds.add("PLMN\u00A0$plmnString")

        if (validTAC(signal.tac))
            cellIds.add(String.format(Locale.US, "TAC\u00a0%04X", signal.tac))

        if (validCellID(signal.gci))
            cellIds.add(String.format(Locale.US, "GCI\u00a0%08X", signal.gci))

        if (validPhysicalCellID(signal.pci))
            cellIds.add(String.format(Locale.US, "PCI\u00a0%03d", signal.pci))

        if (validEARFCN(signal.earfcn)) {
            cellIds.add(String.format(Locale.US, "EARFCN\u00a0%d", signal.earfcn))
        }

        return cellIds
    }

    fun cdmaCellInfo(signal: SignalInfo): ArrayList<String> {
        val cellIds = ArrayList<String>()

        cellIds.add("SID\u00A0" + signal.sid)

        if (validNID(signal.nid))
            cellIds.add("NID\u00A0" + signal.nid)

        if (validBSID(signal.bsid))
            cellIds.add(String.format(Locale.US, "BSID\u00A0%d\u00A0(x%X)",
                    signal.bsid, signal.bsid))

        return cellIds
    }

    private fun is3digitMnc(mcc: Int): Boolean {
        return threeDigitMNCList.contains(mcc)
    }

    private fun formatOperator(operator: String): String {
        try {
            return if (Integer.valueOf(operator) > 0)
                operator.substring(0, 3) + "-" + operator.substring(3)
            else
                operator
        } catch (e: NumberFormatException) {
            return operator
        }

    }

    private fun validMcc(mcc: Int): Boolean {
        return mcc in 1..999
    }

    private fun validMnc(mnc: Int): Boolean {
        return mnc in 1..999
    }

    fun gsmCellInfo(signal: SignalInfo): ArrayList<String> {
        val cellIds = ArrayList<String>()
        val gsmOpString: String

        if (validMcc(signal.gsmMcc) && validMnc(signal.gsmMnc)) {
            gsmOpString = signal.formatGsmPLMN()
        } else {
            gsmOpString = formatOperator(signal.operator)
        }
        cellIds.add("PLMN\u00A0$gsmOpString")

        if (signal.lac != Int.MAX_VALUE)
            cellIds.add("LAC\u00A0" + signal.lac.toString())

        if (signal.rnc != Int.MAX_VALUE && signal.rnc > 0 && signal.rnc != signal.lac)
            cellIds.add("RNC\u00A0" + signal.rnc.toString())

        if (signal.cid != Int.MAX_VALUE)
            cellIds.add("CID\u00A0" + signal.cid.toString())

        if (signal.psc != Int.MAX_VALUE)
            cellIds.add("PSC\u00A0" + signal.psc.toString())

        if (signal.bsic != Int.MAX_VALUE)
            cellIds.add("BSIC\u00A0" + signal.bsic.toString())

        if (signal.uarfcn != Int.MAX_VALUE)
            cellIds.add("UARFCN\u00A0" + signal.uarfcn.toString())

        if (signal.arfcn != Int.MAX_VALUE)
            cellIds.add("ARFCN\u00A0" + signal.arfcn.toString())

        return cellIds
    }

    fun validLocation(lon: Double, lat: Double): Boolean {
        return Math.abs(lon) <= 180 && Math.abs(lat) <= 90
    }

    fun validSID(sid: Int): Boolean { // CDMA System Identifier
        return sid in 1..0x7fff
    }

    protected fun validNID(nid: Int): Boolean { // CDMA System Identifier
        return nid in 0..0xffff
    }

    protected fun validBSID(bsid: Int): Boolean {
        return bsid in 0..0xffff
    }

    protected fun validCID(cid: Int): Boolean { // GSM cell ID
        return cid in 0..0xffff
    }

    override fun onDestroy() {
        // The service is no longer used and is being destroyed
        stopForeground(true)

        if (listening) {
            try {
                mLocationManager!!.removeUpdates(mLocListener)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            mManager!!.listen(mListener, PhoneStateListener.LISTEN_NONE)
            listening = false
        }
        if (rootSessionEcho != null) rootSessionEcho!!.close()
        if (rootSessionCat != null) rootSessionCat!!.kill() // we must kill this Shell instead of close(), since cat never returns. close() waits for an idle shell

        super.onDestroy()
    }

    fun sendResult(message: Parcelable?) {
        val intent = Intent(SD_RESULT)
        if (message != null)
            intent.putExtra(SD_MESSAGE, message)
        broadcaster!!.sendBroadcast(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.cancel(mNotificationId)
    }

    companion object {
        val TAG = SignalDetector::class.java.simpleName

        const val ACTION_STOP = "STOP"
        private const val CHANNEL_ID = "networkInfo"

        private val SET_VALUES = arrayOf("cdma", "esmr", "gsm", "lte")
        private val DEFAULT_LOGS = HashSet(Arrays.asList(*SET_VALUES))

        private const val FIVE_SECONDS = (5 * 1000).toLong()

        const val SD_RESULT = "com.lordsutch.android.signaldetector.SignalServiceBroadcast"
        const val SD_MESSAGE = "com.lordsutch.android.signaldetector.SignalInfo"
    }
}
