package com.lordsutch.android.signaldetector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import eu.chainfire.libsuperuser.Shell;

public class SignalDetectorService extends Service {
    public static final String TAG = SignalDetector.class.getSimpleName();

    private int mNotificationId = 1;

    private CellLocation mCellLocation;
    private SignalStrength mSignalStrength;
    private TelephonyManager mManager;
    private Object mHTCManager;
    private Location mLocation = null;
    private NotificationManager mNotifyMgr;

    private List<CellInfo> mCellInfo = null;

    IBinder mBinder = new LocalBinder();      // interface for clients that bind

    private boolean loggingEnabled = false;
    private LocationManager mLocationManager;
    private boolean listening = false;

    private NotificationCompat.Builder mBuilder;
    private Notification mNotification;
    private LocalBroadcastManager broadcaster;

    private Shell.Interactive rootSessionCat;
    private Shell.Interactive rootSessionEcho;
    private int EARFCN = Integer.MAX_VALUE;

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        SignalDetectorService getService() {
            // Return this instance of SignalDetectorService so clients can call public methods
            return SignalDetectorService.this;
        }
    }

    /**
     * Start cat on /dev/smd11 and STDOUT line by line, updating EARFCN when a valid one is detected
     */
    private void sendRootCat() {
        rootSessionCat.addCommand(new String[]{"cat /dev/smd11"}, 1,
                new Shell.OnCommandLineListener() {
                    @Override
                    public void onCommandResult(int commandCode, int exitCode) {
                        if (exitCode != 0) {
                            Log.e(TAG, "Error with root cat shell" + exitCode);
                            // And close it because it errored out.
                            rootSessionCat.close();
                            // This prevents echo from being sent
                            rootSessionCat = null;
                        }
                    }

                    @Override
                    public void onLine(String line) {
                        int tmpEARFCN = convertEARFCNtoInt(line);
                        if (validEARFCN(tmpEARFCN)) {
                            EARFCN = tmpEARFCN;
                            Log.d(TAG, "EARFCN " + tmpEARFCN);
                        }
                    }
                });
    }

    private void openRootSessionForCat() {
        rootSessionCat = new Shell.Builder().
                useSU().
                setWantSTDERR(true).
                setMinimalLogging(false).
                open(new Shell.OnCommandResultListener() {
                    // Callback to report whether the shell was successfully started up
                    @Override
                    public void onCommandResult(int commandCode, int exitCode, List<String> output) {

                        if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                            Log.e(TAG, "Error opening root shell: exitCode " + exitCode);
                        } else {
                            // Shell is up: start processing
                            Log.d(TAG, "Root cat shell up");
                        }
                    }
                });
    }

    private void openRootSessionForEcho() {
        rootSessionEcho = new Shell.Builder().
                useSU().
                setWantSTDERR(true).
                setMinimalLogging(false).
                setWatchdogTimeout(5).
                open(new Shell.OnCommandResultListener() {
                    // Callback to report whether the shell was successfully started up
                    @Override
                    public void onCommandResult(int commandCode, int exitCode, List<String> output) {

                        if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                            Log.e(TAG, "Error opening root shell: exitCode " + exitCode);
                        } else {
                            // Shell is up: start processing
                            Log.d(TAG, "Root echo shell up");
                        }
                    }
                });
    }

    private void sendRootEARFCN() {
        if (rootSessionEcho != null && rootSessionCat != null) {
            rootSessionEcho.addCommand(new String[]{"echo \"AT\\$QCRSRP?\\r\\n\" > /dev/smd11"}, 0,
                    new Shell.OnCommandResultListener() {
                        public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                            if (exitCode < 0) {
                                Log.e(TAG, "Error executing echo: exitCode " + exitCode);
                                if (exitCode == -1) {
                                    rootSessionEcho.close();
                                    openRootSessionForEcho();
                                }
                            }
                        }
                    }
            );
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        broadcaster = LocalBroadcastManager.getInstance(this);

        // Only get earfcn if option is enabled
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.getApplication());
        if (sharedPref.getBoolean("earfcn", false)) {
            openRootSessionForCat();
            if (rootSessionCat != null)
                sendRootCat();
            openRootSessionForEcho();
        }
    }

    private int convertEARFCNtoInt(String rawRootOutput) {
        // Since we get every line from cat, we only want the one that starts with $QCRSRP
        if (rawRootOutput.matches("\\$QCRSRP(.*)")) {
            // Strip off "$QCRSRP: " from beginning and split into fields, convert to list of strings
            List<String> fields = new ArrayList<String>(Arrays.asList(rawRootOutput.substring(9).split(",")));
            // Return only the EARFCN as Int
            return Integer.parseInt(fields.get(1));
        }
        // return Integer.MAX_VALUE to signify no change
        return Integer.MAX_VALUE;
    }

    public static final String ACTION_STOP = "STOP";
    private static String CHANNEL_ID = "networkInfo";

    @TargetApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // The user-visible name of the channel.
        CharSequence name = getString(R.string.group_1_name);
        // The user-visible description of the channel.
        String description = getString(R.string.group_1_description);

        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_LOW);
        // Configure the notification channel.
        mChannel.setDescription(description);
        mChannel.setShowBadge(false);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    private static final String[] SET_VALUES = new String[] { "cdma", "esmr", "gsm", "lte" };
    private static final Set<String> DEFAULT_LOGS = new HashSet<>(Arrays.asList(SET_VALUES));

    private Set<String> logFilesEnabled = DEFAULT_LOGS;

    @SuppressLint("WrongConstant")
    public IBinder onBind(Intent intent) {
        Intent resultIntent = new Intent(this, SignalDetector.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);

        Intent stopIntent = new Intent(this, SignalDetector.class).setAction(ACTION_STOP);
        PendingIntent exitIntent = PendingIntent.getActivity(this, 0, stopIntent, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel();

        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_0g)
                .setContentTitle(getString(R.string.signal_detector_is_running))
                .setContentText(getString(R.string.loading))
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(R.drawable.ic_close_black_24dp, "Exit", exitIntent)
                .setContentIntent(resultPendingIntent);

        mNotification = mBuilder.build();

        startForeground(mNotificationId, mNotification);

        mManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //noinspection ResourceType
        mHTCManager = getSystemService("htctelephony");

        mNotifyMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.getApplication());

        loggingEnabled = sharedPref.getBoolean("logging", true);
        logFilesEnabled = sharedPref.getStringSet("sitesToLog", DEFAULT_LOGS);

        startGPS();

        return mBinder;
    }

    public void startGPS() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Register the listener with the telephony manager
        mManager.listen(mListener, PhoneStateListener.LISTEN_CELL_INFO | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        /*
        mManager.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
                PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_CELL_INFO);
         */

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.getApplication());
        boolean lessPower = sharedPref.getBoolean("low_power", false);

        Criteria mCriteria = new Criteria();
        mCriteria.setAltitudeRequired(false);
        mCriteria.setCostAllowed(false);
        mCriteria.setPowerRequirement(lessPower ? Criteria.POWER_LOW : Criteria.POWER_HIGH);
        mCriteria.setAccuracy(lessPower ? Criteria.NO_REQUIREMENT : Criteria.ACCURACY_FINE);

        provider = mLocationManager.getBestProvider(mCriteria, true);
        Log.d(TAG, "Using GPS provider " + provider);

        mLocationManager.requestLocationUpdates(provider, 1000, 0, mLocListener);
        mLocation = mLocationManager.getLastKnownLocation(provider);
        listening = true;
    }

    private String provider;

    private void appendLog(String logfile, String text, String header) {
        boolean newfile = false;
        File filesdir = getExternalFilesDir(null);

        File logFile = new File(filesdir, logfile);
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
                newfile = true;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            if (newfile) {
                buf.append(header);
                buf.newLine();
            }
            buf.append(text);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private int parseSignalStrength() {
        String sstrength = mSignalStrength.toString();
        int strength = -999;

        String[] bits = sstrength.split("\\s+");
        if (bits.length >= 10)
            try {
                strength = Integer.parseInt(bits[9]);
            } catch (NumberFormatException e) {
            }

        return strength;
    }

    boolean validTimingAdvance(int timingAdvance) {
        return (timingAdvance > 0 && timingAdvance != Integer.MAX_VALUE);
    }

    boolean validRSSISignalStrength(int strength) {
        return (strength > -120 && strength < 0);
    }

    boolean validLTESignalStrength(int strength) {
        return (strength > -200 && strength < 0);
    }

    boolean validPhysicalCellID(int pci) {
        return (pci >= 0 && pci <= 503);
    }

    boolean validCellID(int eci) {
        return (eci >= 0 && eci <= 0x0FFFFFFF);
    }

    boolean validTAC(int tac) {
        return (tac > 0x0000 && tac < 0xFFFF); // 0, FFFF are reserved values
    }

    boolean validEARFCN(int earfcn) {
        // Some phones (Samsung S7 Edge) report 0, which is impossible so we'll ignore that too.
        return (earfcn != Integer.MAX_VALUE && earfcn > 0);
    } // Integer.MAX_VALUE signifies no change or empty / default EARFCN

    private static long FIVE_SECONDS = 5 * 1000;
    private LinkedList<Location> locs = new LinkedList<>();

    private float calcAverageSpeed() {
        float totspeed = 0;
        float weights = 0;
        long now = System.currentTimeMillis();

        if (locs.size() < 1)
            return 0;

        for (Location loc : locs) {
            if (loc.hasSpeed()) {
                long tdiff = Math.max(FIVE_SECONDS - Math.abs(loc.getTime() - now), 0);
                double weight = Math.log1p(tdiff) + 1;

                totspeed += loc.getSpeed() * weight;
                weights += weight;
            }
        }

        if (weights < 1.0)
            return 0;

        return totspeed / weights;
    }

    private void updateLocations(Location loc) {
        long now = System.currentTimeMillis();

        Iterator<Location> it = locs.iterator();
        boolean inlist = false;

        while (it.hasNext()) {
            Location x = it.next();
            if (x.equals(loc)) {
                inlist = true;
            } else if (Math.abs(now - x.getTime()) > FIVE_SECONDS) {
                it.remove();
            }
        }

        if (!inlist)
            locs.add(loc);
    }

    private double lastValidSpeed = Float.NaN;
    private String lteLine = null;
    private String CdmaLine = null;
    private String GSMLine = null;

    private double timingAdvanceToMeters(int timingAdvance, boolean isFDD) {
        if (!validTimingAdvance(timingAdvance))
            return Double.NaN;
        return (isFDD ? timingAdvance : timingAdvance - 20) * 149.85;
    }

    private boolean isBandFDD(int lteBand) {
        return !(lteBand >= 33 && lteBand <= 48);
    }

    private int guessLteBandFromEARFCN(int earfcn) {
        // Stolen from http://niviuk.free.fr/lte_band.php
        if (earfcn <= 599)
            return 1;
        else if (earfcn <= 1199)
            return 2;
        else if (earfcn <= 1949)
            return 3;
        else if (earfcn <= 2399)
            return 4;
        else if (earfcn <= 2649)
            return 5;
        else if (earfcn <= 2749)
            return 6;
        else if (earfcn <= 2690)
            return 7;
        else if (earfcn <= 3799)
            return 8;
        else if (earfcn <= 4149)
            return 9;
        else if (earfcn <= 4749)
            return 10;
        else if (earfcn <= 4949)
            return 11;
        else if (earfcn <= 5179)
            return 12;
        else if (earfcn <= 5279)
            return 13;
        else if (earfcn <= 5379)
            return 14;
            // Bands 15, 16 missing
        else if (earfcn >= 5730 && earfcn <= 5849)
            return 17;
        else if (earfcn >= 5850 && earfcn <= 5999)
            return 18;
        else if (earfcn >= 6000 && earfcn <= 6149)
            return 19;
        else if (earfcn >= 6150 && earfcn <= 6449)
            return 20;
        else if (earfcn >= 6450 && earfcn <= 6599)
            return 21;
        else if (earfcn >= 6600 && earfcn <= 7399)
            return 22;
        else if (earfcn >= 7500 && earfcn <= 7699)
            return 23;
        else if (earfcn >= 7700 && earfcn <= 8039)
            return 24;
        else if (earfcn >= 8040 && earfcn <= 8689)
            return 25;
        else if (earfcn >= 8690 && earfcn <= 9039)
            return 26;
        else if (earfcn >= 9040 && earfcn <= 9209)
            return 27;
        else if (earfcn >= 9210 && earfcn <= 9659)
            return 28;
        else if (earfcn >= 9660 && earfcn <= 9769)
            return 29;
        else if (earfcn >= 9770 && earfcn <= 9869)
            return 30;
        else if (earfcn >= 9870 && earfcn <= 9919)
            return 31;
        else if (earfcn >= 9920 && earfcn <= 10359)
            return 32;
        else if (earfcn >= 36000 && earfcn <= 36199)
            return 33;
        else if (earfcn >= 36200 && earfcn <= 36349)
            return 34;
        else if (earfcn >= 36350 && earfcn <= 36949)
            return 35;
        else if (earfcn >= 36950 && earfcn <= 37549)
            return 36;
        else if (earfcn >= 37550 && earfcn <= 37749)
            return 37;
        else if (earfcn >= 37750 && earfcn <= 38249)
            return 38;
        else if (earfcn >= 38250 && earfcn <= 38649)
            return 39;
        else if (earfcn >= 38650 && earfcn <= 39649)
            return 40;
        else if (earfcn >= 39650 && earfcn <= 41589)
            return 41;
        else if (earfcn >= 41590 && earfcn <= 43589)
            return 42;
        else if (earfcn >= 43590 && earfcn <= 45589)
            return 43;
        else if (earfcn >= 45590 && earfcn <= 46589)
            return 44;
        else if (earfcn >= 46590 && earfcn <= 46789)
            return 45;
        else if (earfcn >= 46790 && earfcn <= 54539)
            return 46;
        else if (earfcn >= 54540 && earfcn <= 55239)
            return 47;
        else if (earfcn >= 55240 && earfcn <= 56739)
            return 48;
        else if (earfcn >= 65536 && earfcn <= 66435)
            return 65;
        else if (earfcn >= 66436 && earfcn <= 67335)
            return 66;
        else if (earfcn >= 67336 && earfcn <= 67535)
            return 67;
        else if (earfcn >= 67536 && earfcn <= 67835)
            return 68;
        else if (earfcn >= 67836 && earfcn <= 68335)
            return 69;
        else if (earfcn >= 68336 && earfcn <= 68585)
            return 70;
        else if (earfcn >= 255144 && earfcn <= 256143)
            return 252;
        else if (earfcn >= 260894 && earfcn <= 262143)
            return 253;
        else
            return 0;
    }

    private int guessLteBand(int mcc, int mnc, int gci, int earfcn) {
        int sector = gci & 0xff;

        if (validEARFCN(earfcn)) {
            int band = guessLteBandFromEARFCN(earfcn);
            if (band > 0)
                return band;
        }

        if (mcc == 311 && (mnc == 490 || mnc == 870))
            return 41; // Legacy Clear sites are on band 41
        else if ((mcc == 310 && mnc == 120) ||
                (mcc == 312 && mnc == 530)) {
            // Sprint (312-530 is prepaid)
            if ((gci & 0x00100000) != 0) // 3rd digit is odd if B41
                return 41;

            if ((sector >= 0x19 && sector <= 0x1b) || // Ericsson/ALU
                    (sector >= 0x0f && sector <= 0x10)) // Samsung
                return 26;

            if (sector == 0x11) {
                if (gci >= 0x7600000 && gci < 0xBA00000) { // Samsung
                    return 26;
                } else {
                    return 25;
                }
            }

            // mini macros starts with what looks like B25 but b41 sectors
            if (sector >= 0x31 && sector <= 0x43)
                return 41;

            // small cells - thanks Flompholph
            if ((gci & 0x0f0000) >= 0x090000 && gci < 0x0fe00000 && sector == 0x01)
                return 41;

            return 25;
        } else if (mcc == 310 && (mnc == 410 || mnc == 150)) {
            // AT&T
            if (sector >= 0x00 && sector <= 0x02)
                return 5;
            else if (sector >= 0x08 && sector <= 0x0a)
                return 2;
            else if (sector >= 0x16 && sector <= 0x19)
                return 4;
            else if (sector >= 0x95 && sector <= 0x9a)
                return 30;
            return 17;
        } else if (mcc == 310 && mnc == 260) {
            // T-Mobile
            if (sector >= 0x01 && sector <= 0x04)
                return 4;
            else if (sector >= 0x11 && sector <= 0x14)
                return 2;
            else if (sector >= 0x21 && sector <= 0x23)
                return 12;
            else if (sector >= 0x05 && sector <= 0x07)
                return 12;
            else if (sector >= 0x15 && sector <= 0x15)
                return 12;
            return 0;
        } else if (mcc == 311 && mnc == 480) {
            // Verizon
            if (sector <= 6)
                return 13;
            else if ((sector % 10) == 2 || (sector % 10) == 3)
                return 4;
            else if ((sector % 10) == 4 || (sector % 10) == 5)
                return 2;
            else if ((sector % 10) == 7)
                return 5;
            return 13;
        } else if (mcc == 312 && mnc == 190) {
            // nTelos
            if (sector == 0x0c || sector == 0x16 || sector == 0x20)
                return 26;
            else if (sector == 0x0d || sector == 0x17 || sector == 0x21)
                return 2;
            else if (sector >= 0x01 && sector <= 0x03)
                return 25;
            return 13;
        }
        return 0;
    }

    public String networkString(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return "eHRPD";
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                return "EVDO Rel. 0";
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                return "EVDO Rev. A";
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                return "EVDO Rev. B";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPA+";
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                return "1xRTT";
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "iDEN";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            default:
                return "Unknown";
        }
    }

    private int networkIcon(int networkType) {
        int icon;

        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_LTE:
                icon = R.drawable.ic_stat_4g;
                break;

            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSDPA: /* 3.5G? */
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_CDMA:
                icon = R.drawable.ic_stat_3g;
                break;

            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                icon = R.drawable.ic_stat_2g;
                break;

            default:
                icon = R.drawable.ic_stat_0g;
                break;
        }

        return icon;
    }

    private long locationFixAge(Location loc) {
        return (SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()) / (1000 * 1000);
    }

    protected String valueString(int value) {
        return value == Integer.MAX_VALUE ? "" : String.valueOf(value);
    }

    private void getLegacyCellLocationData(signalInfo signal) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mCellLocation = mManager.getCellLocation();
        if (mCellLocation instanceof CdmaCellLocation) {
            CdmaCellLocation x = (CdmaCellLocation) mCellLocation;

            signal.setBsid(x.getBaseStationId());
            signal.setNid(x.getNetworkId());
            signal.setSid(x.getSystemId());

            if (signal.getBsid() < 0)
                signal.setBsid(Integer.MAX_VALUE);

            if (signal.getNid() <= 0)
                signal.setNid(Integer.MAX_VALUE);

            if (signal.getSid() <= 0)
                signal.setSid(Integer.MAX_VALUE);

            signal.setBslat(fixCDMAPosition(x.getBaseStationLatitude()));
            signal.setBslon(fixCDMAPosition(x.getBaseStationLongitude()));

            /* Deal with possiblity these may be swapped in Android 8 - thanks Mikejeep
             * https://issuetracker.google.com/issues/63130155 */
            if (validLocation(signal.getBslat(), signal.getBslon()) && Math.abs(signal.getBslat() - signal.getLatitude()) > 1.0) {
                double tmp = signal.getBslat();
                signal.setBslat(signal.getBslon());
                signal.setBslon(tmp);
            }
        } else if (mCellLocation instanceof GsmCellLocation) {
            GsmCellLocation x = (GsmCellLocation) mCellLocation;

            signal.setLac(x.getLac());
            if(signal.getLac() < 0 || signal.getLac() > 0xffff)
                signal.setLac(Integer.MAX_VALUE);

            signal.setPsc(x.getPsc());
            if (signal.getPsc() < 0)
                signal.setPsc(Integer.MAX_VALUE);

            Integer cid = x.getCid();

            if (cid >= 0) {
                signal.setRnc(cid >> 16);
                signal.setCid(cid & 0xffff);
                signal.setFullCid(cid);
            } else {
                signal.setRnc(Integer.MAX_VALUE);
                signal.setCid(Integer.MAX_VALUE);
                signal.setFullCid(Integer.MAX_VALUE);
            }
        }
    }

    private double fixCDMAPosition(int coordinate) {
        return (coordinate != 0 && coordinate != Integer.MAX_VALUE) ? coordinate / 14400.0 : Double.NaN;
    }

    private void updatelog(boolean log) {
        if (mSignalStrength == null)
            return;

        boolean gotID = false;

        if (mLocation == null &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocation = mLocationManager.getLastKnownLocation(provider);
        }

        if (mLocation == null)
            mLocation = new Location(provider);

        signalInfo signal = new signalInfo();

        signal.setLatitude(mLocation.getLatitude());
        signal.setLongitude(mLocation.getLongitude());
        if (mLocation.hasSpeed()) {
            signal.setSpeed(mLocation.getSpeed());
            lastValidSpeed = signal.getSpeed();
        } else {
            signal.setSpeed(lastValidSpeed);
        }
        signal.setAccuracy(mLocation.getAccuracy());
        signal.setAltitude(mLocation.getAltitude());
        signal.setBearing(mLocation.getBearing());
        signal.setAvgSpeed(calcAverageSpeed());

        signal.setPhoneType(mManager.getPhoneType());
        signal.setNetworkType(mManager.getNetworkType());
        signal.setRoaming(mManager.isNetworkRoaming());
        signal.setOperator(mManager.getNetworkOperator());
        signal.setOperatorName(mManager.getNetworkOperatorName());

        signal.setGsmSigStrength(mSignalStrength.getGsmSignalStrength());
        signal.setGsmSigStrength((signal.getGsmSigStrength() < 32 ? -113 + 2 * signal.getGsmSigStrength() : -9999));

        signal.setCdmaSigStrength(mSignalStrength.getCdmaDbm());
        signal.setEvdoSigStrength(mSignalStrength.getEvdoDbm());

        //if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        getLegacyCellLocationData(signal);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mCellInfo = mManager.getAllCellInfo();
        }

        if (mCellInfo != null) {
            sendRootEARFCN();
            signal.setOtherCells(new ArrayList<>());

            for (CellInfo item : mCellInfo) {
                if (item == null)
                    continue;

//                Log.d(TAG, item.toString());

                if (item instanceof CellInfoLte) {
                    CellSignalStrengthLte cstr = ((CellInfoLte) item).getCellSignalStrength();
                    CellIdentityLte cellid = ((CellInfoLte) item).getCellIdentity();

                    if (item.isRegistered()) {
                        if (cstr != null) {
                            signal.setLteSigStrength(cstr.getDbm());
                            if (signal.getLteSigStrength() > 0)
                                signal.setLteSigStrength(-(signal.getLteSigStrength() / 10));
                            signal.setTimingAdvance(cstr.getTimingAdvance());
                        }

                        if (cellid != null) {
                            signal.setGci(cellid.getCi());
                            signal.setPci(cellid.getPci());
                            signal.setTac(cellid.getTac());
                            signal.setMnc(cellid.getMnc());
                            signal.setMcc(cellid.getMcc());
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                signal.setEarfcn(cellid.getEarfcn());
                            else
                                signal.setEarfcn(EARFCN);
                            signal.setLteBand(guessLteBand(signal.getMcc(), signal.getMnc(), signal.getGci(), signal.getEarfcn()));
                            signal.setFDD(isBandFDD(signal.getLteBand()));
                            gotID = true;
                        }
                    } else {
                        otherLteCell otherCell = new otherLteCell();

                        if (cstr != null) {
                            otherCell.setLteSigStrength(cstr.getDbm());
                            if (otherCell.getLteSigStrength() > 0)
                                otherCell.setLteSigStrength(-(otherCell.getLteSigStrength() / 10));
                            otherCell.setTimingAdvance(cstr.getTimingAdvance());
                        }

                        if (cellid != null) {
                            otherCell.setGci(cellid.getCi());
                            otherCell.setPci(cellid.getPci());
                            otherCell.setTac(cellid.getTac());
                            otherCell.setMnc(cellid.getMnc());
                            otherCell.setMcc(cellid.getMcc());
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                otherCell.setEarfcn(cellid.getEarfcn());
                            otherCell.setLteBand(guessLteBand(otherCell.getMcc(), otherCell.getMnc(), otherCell.getGci(), otherCell.getEarfcn()));
                            otherCell.setFDD(isBandFDD(otherCell.getLteBand()));
                        }
                        signal.getOtherCells().add(otherCell);
                    }
                } else if (item instanceof CellInfoCdma) {
                    CellSignalStrengthCdma cstr = ((CellInfoCdma) item).getCellSignalStrength();
                    CellIdentityCdma cellid = ((CellInfoCdma) item).getCellIdentity();

                    signal.setBsid(cellid.getBasestationId());
                    signal.setBslat(fixCDMAPosition(cellid.getLatitude()));
                    signal.setBslon(fixCDMAPosition(cellid.getLongitude()));
                    signal.setNid(cellid.getNetworkId());
                    signal.setSid(cellid.getSystemId());

                    signal.setCdmaSigStrength(cstr.getCdmaDbm());
                    signal.setEvdoSigStrength(cstr.getEvdoDbm());
                } else if (item instanceof CellInfoGsm) {
                    CellSignalStrengthGsm cstr = ((CellInfoGsm) item).getCellSignalStrength();
                    CellIdentityGsm cellid = ((CellInfoGsm) item).getCellIdentity();

                    signal.setLac(cellid.getLac());
                    signal.setGsmMcc(cellid.getMcc());
                    signal.setGsmMnc(cellid.getMnc());

                    Integer cid = cellid.getCid();
                    signal.setCid(cid);
                    signal.setFullCid(cid);
                    signal.setGsmSigStrength(cstr.getDbm());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        signal.setArfcn(cellid.getArfcn());
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        signal.setBsic(cellid.getBsic());
                        signal.setGsmTimingAdvance(cstr.getTimingAdvance());
                    }
                } else if ((item instanceof CellInfoWcdma) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    CellSignalStrengthWcdma cstr = ((CellInfoWcdma) item).getCellSignalStrength();
                    CellIdentityWcdma cellid = ((CellInfoWcdma) item).getCellIdentity();

                    Integer cid = cellid.getCid();
                    signal.setFullCid(cid);
                    if (cid != Integer.MAX_VALUE) {
                        signal.setRnc(cid >> 16);
                        signal.setCid(cid & 0xffff);
                    }

                    signal.setLac(cellid.getLac());
                    signal.setGsmMcc(cellid.getMcc());
                    signal.setGsmMnc(cellid.getMnc());
                    signal.setPsc(cellid.getPsc());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        signal.setUarfcn(cellid.getUarfcn());
                    }

                    signal.setGsmSigStrength(cstr.getDbm());
                }
            }
        }

        if (!validLTESignalStrength(signal.getLteSigStrength()))
            signal.setLteSigStrength(parseSignalStrength());

        if (!gotID && mHTCManager != null) {
            Method m = null;

            try {
                String cellID;

                m = mHTCManager.getClass().getMethod("getSectorId", int.class);
                cellID = (String) m.invoke(mHTCManager, new Object[]{Integer.valueOf(1)});
                signal.setGci(Integer.parseInt(cellID, 16));
            } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        if (!validLTESignalStrength(signal.getLteSigStrength())) {
            Method m;

            try {
                m = mSignalStrength.getClass().getMethod("getLteRsrp");
                signal.setLteSigStrength((Integer) m.invoke(mSignalStrength, (Object[]) null));
            } catch (NoSuchMethodException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        String cellIdInfo = getString(R.string.none);
        String basicCellInfo = cellIdInfo;

        if (signal.getNetworkType() != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            basicCellInfo = String.format("%s %s", signal.getOperatorName(),
                    networkString(signal.getNetworkType()));
        }

        ArrayList<String> cellIds = new ArrayList<>();

        if (signal.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE) {
            if (signal.getLteBand() != 0)
                basicCellInfo += String.format(Locale.getDefault(), " band\u202f%d", signal.getLteBand());

            if (validLTESignalStrength(signal.getLteSigStrength()))
                basicCellInfo += String.format(Locale.US, " %d\u202fdBm", signal.getLteSigStrength());

            if(validTimingAdvance(signal.getTimingAdvance()))
                basicCellInfo += String.format(Locale.US, " TA=%d\u202fµs", signal.getTimingAdvance());

            cellIds = lteCellInfo(signal);
        } else if (validRSSISignalStrength(signal.getCdmaSigStrength()) && validSID(signal.getSid())) {
            int sigStrength = validRSSISignalStrength(signal.getEvdoSigStrength()) ? signal.getEvdoSigStrength() : signal.getCdmaSigStrength();

            basicCellInfo += String.format(Locale.US, " %d\u202fdBm", sigStrength);
            if(validRSSISignalStrength(signal.getEvdoSigStrength()))
                basicCellInfo += String.format(Locale.US, " voice %d\u202fdBm", signal.getCdmaSigStrength());

            cellIds = cdmaCellInfo(signal);
        } else if (validRSSISignalStrength(signal.getGsmSigStrength()) && validCID(signal.getCid())) {
            basicCellInfo += String.format(Locale.US, " %d\u202fdBm", signal.getGsmSigStrength());
            if(validTimingAdvance(signal.getGsmTimingAdvance()))
                basicCellInfo += String.format(Locale.US, " TA=%d\u202fµs", signal.getGsmTimingAdvance());

            cellIds = gsmCellInfo(signal);
        }

        if (cellIds.isEmpty())
            cellIdInfo = getString(R.string.missing);
        else
            cellIdInfo = TextUtils.join(", ", cellIds);

        if (signal.getRoaming())
            basicCellInfo += " " + getString(R.string.roamingInd);

        mBuilder = mBuilder.setContentTitle(basicCellInfo)
                .setContentText(cellIdInfo)
                .setSmallIcon(networkIcon(signal.getNetworkType()));

        mNotification = mBuilder.build();
        mNotifyMgr.notify(mNotificationId, mNotification);

        signal.setFixAge(locationFixAge(mLocation));

        if (loggingEnabled && log) {
            Log.d(TAG, signal.toString());

            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); // Quoted "Z" to indicate UTC, no timezone offset
            df.setTimeZone(tz);
            Date now = new Date();

            String nowAsISO = df.format(now);
            String slat = Location.convert(signal.getLatitude(), Location.FORMAT_DEGREES);
            String slon = Location.convert(signal.getLongitude(), Location.FORMAT_DEGREES);

            if (signal.getNetworkType() == TelephonyManager.NETWORK_TYPE_LTE &&
                    logFilesEnabled.contains("lte") &&
                    (validLTESignalStrength(signal.getLteSigStrength()) ||
                            validPhysicalCellID(signal.getPci()) || validCellID(signal.getGci()))) {
                double estDistance = timingAdvanceToMeters(signal.getTimingAdvance(), signal.isFDD());

                String newLteLine = slat + "," + slon + "," +
                        (validCellID(signal.getGci()) ? String.format(Locale.US, "%08X", signal.getGci()) : "") + "," +
                        valueString(signal.getPci()) + "," +
                        (validLTESignalStrength(signal.getLteSigStrength()) ? String.valueOf(signal.getLteSigStrength()) : "") + "," +
                        String.format(Locale.US, "%.0f", signal.getAltitude()) + "," +
                        (validTAC(signal.getTac()) ? String.format(Locale.US, "%04X", signal.getTac()) : "") + "," +
                        String.format(Locale.US, "%.0f", signal.getAccuracy()) + "," +
                        (validCellID(signal.getGci()) ? String.format(Locale.US, "%06X", signal.getGci() /256) : "") + "," +
                        (signal.getLteBand() > 0 ? valueString(signal.getLteBand()) : "") + "," +
                        valueString(signal.getTimingAdvance()) + ","+
                        (validEARFCN(signal.getEarfcn()) ? valueString(signal.getEarfcn()) : "") + "," +
                        nowAsISO + "," + String.valueOf(now.getTime()) + "," +
                        (isBandFDD(signal.getLteBand()) ? "1" : "0") + ","+
                        (Double.isNaN(estDistance) ? "" : String.format(Locale.US, "%.0f", estDistance)) + "," +
                        valueString(signal.getMcc()) + "," +
                        valueString(signal.getMnc());

                if (lteLine == null || !newLteLine.equals(lteLine)) {
//                    Log.d(TAG, "Logging LTE cell.");
                    appendLog("ltecells.csv", newLteLine, "latitude,longitude,cellid,physcellid,dBm,altitude,tac,accuracy,baseGci,band,timingAdvance,earfcn,timestamp,timeSinceEpoch,fdd,estDistance,mcc,mnc");
                    lteLine = newLteLine;
                }
            }

            if (mCellInfo != null && logFilesEnabled.contains("lte")) {
                for (CellInfo item : mCellInfo) {
                    if (item instanceof CellInfoLte) {
                        CellIdentityLte mIdentity = ((CellInfoLte) item).getCellIdentity();
                        CellSignalStrengthLte mSS = ((CellInfoLte) item).getCellSignalStrength();

                        int tac = mIdentity.getTac();
                        int eci = mIdentity.getCi();
                        int pci = mIdentity.getPci();
                        int mcc = mIdentity.getMcc();
                        int mnc = mIdentity.getMnc();
                        int earfcn = Integer.MAX_VALUE;
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            earfcn = mIdentity.getEarfcn();
                        int rsrp = mSS.getDbm();
                        int timingAdvance = mSS.getTimingAdvance();
                        int lteBand = guessLteBand(mcc, mnc, eci, earfcn);
                        boolean isFDD = isBandFDD(lteBand);
                        double estDistance = timingAdvanceToMeters(timingAdvance, isFDD);

                        String cellLine = slat + "," + slon + "," +
                                String.format(Locale.US, "%.0f", signal.getAccuracy()) + "," +
                                String.format(Locale.US, "%.0f", signal.getAltitude()) + "," +
                                valueString(mcc) + "," + valueString(mnc) + "," +
                                (validTAC(tac) ? String.format(Locale.US, "%04X", tac) : "") + "," +
                                (validCellID(eci) ? String.format(Locale.US, "%08X", eci) : "") + "," +
                                (validPhysicalCellID(pci) ? String.valueOf(pci) : "") + "," +
                                (validLTESignalStrength(rsrp) ? String.valueOf(rsrp) : "") + "," +
                                (item.isRegistered() ? "1" : "0") + "," +
                                (validCellID(eci) ? String.format(Locale.US, "%06X", eci /256) : "") + "," +
                                (lteBand > 0 ? String.valueOf(lteBand) : "") + "," +
                                valueString(timingAdvance) + "," +
                                (validEARFCN(earfcn) ? String.format(Locale.US, "%d", earfcn) : "") + "," +
                                nowAsISO + "," + String.valueOf(now.getTime()) + "," +
                                (isFDD ? "1" : "0") + ","+
                                (Double.isNaN(estDistance) ? "" : String.format(Locale.US, "%.0f", estDistance));

                        appendLog("cellinfolte.csv", cellLine,
                                "latitude,longitude,accuracy,altitude,mcc,mnc,tac,gci,pci,rsrp,registered,baseGci,band,timingAdvance,earfcn,timestamp,timeSinceEpoch,fdd,estDistance");

                    }
                }
            }

            if (validRSSISignalStrength(signal.getCdmaSigStrength()) && validSID(signal.getSid()) &&
                    logFilesEnabled.contains("cdma")) {
                boolean isValid = validLocation(signal.getBslon(), signal.getBslat());

                String bslatstr = (isValid ? Location.convert(signal.getBslat(), Location.FORMAT_DEGREES) : "");
                String bslonstr = (isValid ? Location.convert(signal.getBslon(), Location.FORMAT_DEGREES) : "");

                String newCdmaLine = String.format(Locale.US, "%s,%s,%s,%s,%s,%d,%s,%s,%.0f,%.0f,%s,%d",
                        slat, slon, valueString(signal.getSid()), valueString(signal.getNid()), valueString(signal.getBsid()),
                        signal.getCdmaSigStrength(),
                        bslatstr, bslonstr, signal.getAltitude(), signal.getAccuracy(), nowAsISO, now.getTime());
                if (CdmaLine == null || !newCdmaLine.equals(CdmaLine)) {
//                    Log.d(TAG, "Logging CDMA cell.");
                    appendLog(((signal.getSid() >= 22404) && (signal.getSid() <= 22451)) ? "esmrcells.csv" : "cdmacells.csv",
                            newCdmaLine, "latitude,longitude,sid,nid,bsid,rssi,bslat,bslon,altitude,accuracy,timestamp,timeSinceEpoch");
                    CdmaLine = newCdmaLine;
                }
            } else if (validRSSISignalStrength(signal.getGsmSigStrength()) && validCID(signal.getCid()) &&
                    logFilesEnabled.contains("gsm")) {
                String newGSMLine = String.format(Locale.US, "%s,%s,%.0f,%.0f,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s", slat, slon,
                        signal.getAltitude(), signal.getAccuracy(),
                        valueString(signal.getCid()), valueString(signal.getRnc()), valueString(signal.getLac()),
                        valueString(signal.getPsc()), valueString(signal.getGsmSigStrength()),
                        nowAsISO, now.getTime(), valueString(signal.getBsic()), valueString(signal.getUarfcn()),
                        valueString(signal.getGsmTimingAdvance()),
                        (signal.getGsmTimingAdvance() != Integer.MAX_VALUE ? signal.getGsmTimingAdvance() * 550 : ""),
                        valueString(signal.getGsmMcc()), valueString(signal.getGsmMnc()),
                        valueString(signal.getArfcn()));
                if (GSMLine == null || !newGSMLine.equals(GSMLine)) {
//                    Log.d(TAG, "Logging GSM cell.");
                    appendLog("gsmcells.csv", newGSMLine, "latitude,longitude,altitude,accuracy,cid,rnc,lac,psc,rssi,timestamp,timeSinceEpoch,bsic,uarfcn,timingAdvance,estDistance,mcc,mnc,arfcn");
                    GSMLine = newGSMLine;
                }
            }
        }

        sendResult(signal);
    }

    public ArrayList<String> lteCellInfo(signalInfo signal) {
        ArrayList<String> cellIds = new ArrayList<>();
        String plmnString;

        if (validMcc(signal.getMcc()) && validMnc(signal.getMnc())) {
            plmnString = formatPLMN(signal.getMcc(), signal.getMnc());
        } else {
            plmnString = formatOperator(signal.getOperator());
        }
        cellIds.add("PLMN\u00A0" + plmnString);

        if (validTAC(signal.getTac()))
            cellIds.add(String.format(Locale.US, "TAC\u00a0%04X", signal.getTac()));

        if (validCellID(signal.getGci()))
            cellIds.add(String.format(Locale.US, "GCI\u00a0%08X", signal.getGci()));

        if (validPhysicalCellID(signal.getPci()))
            cellIds.add(String.format(Locale.US, "PCI\u00a0%03d", signal.getPci()));

        if(validEARFCN(signal.getEarfcn())) {
            cellIds.add(String.format(Locale.US, "EARFCN\u00a0%d", signal.getEarfcn()));
        }

        return cellIds;
    }

    public ArrayList<String> cdmaCellInfo(signalInfo signal) {
        ArrayList<String> cellIds = new ArrayList<>();

        cellIds.add("SID\u00A0" + signal.getSid());

        if(validNID(signal.getNid()))
            cellIds.add("NID\u00A0" + signal.getNid());

        if(validBSID(signal.getBsid()))
            cellIds.add(String.format(Locale.US, "BSID\u00A0%d\u00A0(x%X)",
                    signal.getBsid(), signal.getBsid()));

        return cellIds;
    }

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

    private String formatOperator(String operator) {
        try {
            if (Integer.valueOf(operator) > 0)
                return operator.substring(0, 3) + "-" + operator.substring(3);
            else
                return operator;
        } catch (NumberFormatException e) {
            return operator;
        }
    }

    private boolean validMcc(int mcc) {
        return (mcc > 0 && mcc <= 999);
    }

    private boolean validMnc(int mnc) {
        return (mnc > 0 && mnc <= 999);
    }

    public ArrayList<String> gsmCellInfo(signalInfo signal) {
        ArrayList<String> cellIds = new ArrayList<>();

        String gsmOpString = "";

        if (validMcc(signal.getGsmMcc()) && validMnc(signal.getGsmMnc())) {
            gsmOpString = formatPLMN(signal.getGsmMcc(), signal.getGsmMnc());
        } else {
            gsmOpString = formatOperator(signal.getOperator());
        }
        cellIds.add("PLMN\u00A0" + gsmOpString);

        if (signal.getLac() != Integer.MAX_VALUE)
            cellIds.add("LAC\u00A0" + String.valueOf(signal.getLac()));

        if (signal.getRnc() != Integer.MAX_VALUE && signal.getRnc() > 0 && signal.getRnc() != signal.getLac())
            cellIds.add("RNC\u00A0" + String.valueOf(signal.getRnc()));

        if (signal.getCid() != Integer.MAX_VALUE)
            cellIds.add("CID\u00A0" + String.valueOf(signal.getCid()));

        if (signal.getPsc() != Integer.MAX_VALUE)
            cellIds.add("PSC\u00A0" + String.valueOf(signal.getPsc()));

        if (signal.getBsic() != Integer.MAX_VALUE)
            cellIds.add("BSIC\u00A0" + String.valueOf(signal.getBsic()));

        if (signal.getUarfcn() != Integer.MAX_VALUE)
            cellIds.add("UARFCN\u00A0" + String.valueOf(signal.getUarfcn()));

        if (signal.getArfcn() != Integer.MAX_VALUE)
            cellIds.add("ARFCN\u00A0" + String.valueOf(signal.getArfcn()));

        return cellIds;
    }

    protected boolean validLocation(double lon, double lat) {
        return (Math.abs(lon) <= 180 && Math.abs(lat) <= 90);
    }

    protected boolean validSID(int sid) { // CDMA System Identifier
        return sid > 0 && sid <= 0x7fff;
    }

    protected boolean validNID(int nid) { // CDMA System Identifier
        return nid >= 0 && nid <= 0xffff;
    }

    protected boolean validBSID(int bsid) { return bsid >= 0 && bsid <= 0xffff; }

    protected boolean validCID(int cid) { // GSM cell ID
        return cid >= 0 && cid <= 0xffff;
    }

    private final LocationListener mLocListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location mLoc) {
            updateLocations(mLoc);
            if (mLocation != mLoc) {
                mLocation = mLoc;
                updatelog(true);
                // updatelog(false); // Only log when there's a signal strength change.
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    // Listener for signal strength.
    final PhoneStateListener mListener = new PhoneStateListener() {
        @Override
        public void onCellLocationChanged(CellLocation mLocation) {
            mCellLocation = mLocation;
            updatelog(true);
        }

        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            mCellInfo = cellInfo;
            updatelog(true);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength sStrength) {
            mSignalStrength = sStrength;
//            if (mSignalStrength != null) {
//                Log.d(TAG, mSignalStrength.toString());
//            }
            updatelog(true);
        }
    };

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
        stopForeground(true);

        if(listening) {
            try {
                mLocationManager.removeUpdates(mLocListener);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            mManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
            listening = false;
        }
        if (rootSessionEcho != null) rootSessionEcho.close();
        if (rootSessionCat != null) rootSessionCat.kill(); // we must kill this Shell instead of close(), since cat never returns. close() waits for an idle shell

        super.onDestroy();
    }

    static final public String SD_RESULT = "com.lordsutch.android.signaldetector.SignalServiceBroadcast";
    static final public String SD_MESSAGE = "com.lordsutch.android.signaldetector.SignalInfo";

    public void sendResult(Parcelable message) {
        Intent intent = new Intent(SD_RESULT);
        if(message != null)
            intent.putExtra(SD_MESSAGE, message);
        broadcaster.sendBroadcast(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null) {
            mNotificationManager.cancel(mNotificationId);
        }
    }
}
