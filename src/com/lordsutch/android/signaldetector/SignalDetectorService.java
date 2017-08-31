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
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import eu.chainfire.libsuperuser.Shell;

class otherLteCell implements Parcelable {
    int gci = Integer.MAX_VALUE;
    int pci = Integer.MAX_VALUE;
    int tac = Integer.MAX_VALUE;
    int mcc = Integer.MAX_VALUE;
    int mnc = Integer.MAX_VALUE;
    int earfcn = Integer.MAX_VALUE;
    int lteBand = 0;
    boolean isFDD = true;

    int lteSigStrength = Integer.MAX_VALUE;
    int timingAdvance = Integer.MAX_VALUE;

    otherLteCell() {
    }

    protected otherLteCell(Parcel in) {
        gci = in.readInt();
        pci = in.readInt();
        tac = in.readInt();
        mcc = in.readInt();
        mnc = in.readInt();
        earfcn = in.readInt();
        lteBand = in.readInt();
        isFDD = in.readByte() != 0;
        lteSigStrength = in.readInt();
        timingAdvance = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(gci);
        dest.writeInt(pci);
        dest.writeInt(tac);
        dest.writeInt(mcc);
        dest.writeInt(mnc);
        dest.writeInt(earfcn);
        dest.writeInt(lteBand);
        dest.writeByte((byte) (isFDD ? 1 : 0));
        dest.writeInt(lteSigStrength);
        dest.writeInt(timingAdvance);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<otherLteCell> CREATOR = new Creator<otherLteCell>() {
        @Override
        public otherLteCell createFromParcel(Parcel in) {
            return new otherLteCell(in);
        }

        @Override
        public otherLteCell[] newArray(int size) {
            return new otherLteCell[size];
        }
    };
}

class signalInfo implements Parcelable {
    // Location location = null;

    double longitude;
    double latitude;
    double altitude;
    float accuracy;
    float speed;
    float avgSpeed;
    float bearing;
    long fixAge; // in milliseconds

    // LTE
    int gci = Integer.MAX_VALUE;
    int pci = Integer.MAX_VALUE;
    int tac = Integer.MAX_VALUE;
    int mcc = Integer.MAX_VALUE;
    int mnc = Integer.MAX_VALUE;
    int earfcn = Integer.MAX_VALUE;
    int lteSigStrength = Integer.MAX_VALUE;
    int timingAdvance = Integer.MAX_VALUE;

    int gsmTimingAdvance = Integer.MAX_VALUE;

    int lteBand = 0;
    boolean isFDD = false;

    // CDMA2000
    int bsid = Integer.MAX_VALUE;
    int nid = Integer.MAX_VALUE;
    int sid = Integer.MAX_VALUE;
    double bslat = Double.NaN;
    double bslon = Double.NaN;
    int cdmaSigStrength = Integer.MAX_VALUE;
    int evdoSigStrength = Integer.MAX_VALUE;

    // GSM/UMTS/W-CDMA
    String operator = "";
    int lac = Integer.MAX_VALUE;
    int cid = Integer.MAX_VALUE;
    int psc = Integer.MAX_VALUE;
    int rnc = Integer.MAX_VALUE;
    int fullCid = Integer.MAX_VALUE;
    int gsmSigStrength = Integer.MAX_VALUE;
    int bsic = Integer.MAX_VALUE;
    int uarfcn = Integer.MAX_VALUE;

    int gsmMcc = Integer.MAX_VALUE;
    int gsmMnc = Integer.MAX_VALUE;

    int phoneType = TelephonyManager.PHONE_TYPE_NONE;
    int networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;

    boolean roaming = false;

    List<otherLteCell> otherCells = null;

    protected signalInfo(Parcel in) {
        longitude = in.readDouble();
        latitude = in.readDouble();
        altitude = in.readDouble();
        accuracy = in.readFloat();
        speed = in.readFloat();
        avgSpeed = in.readFloat();
        bearing = in.readFloat();
        fixAge = in.readLong();
        gci = in.readInt();
        pci = in.readInt();
        tac = in.readInt();
        mcc = in.readInt();
        mnc = in.readInt();
        earfcn = in.readInt();
        lteSigStrength = in.readInt();
        timingAdvance = in.readInt();
        gsmTimingAdvance = in.readInt();
        lteBand = in.readInt();
        isFDD = in.readByte() != 0;
        bsid = in.readInt();
        nid = in.readInt();
        sid = in.readInt();
        bslat = in.readDouble();
        bslon = in.readDouble();
        cdmaSigStrength = in.readInt();
        evdoSigStrength = in.readInt();
        operator = in.readString();
        lac = in.readInt();
        cid = in.readInt();
        psc = in.readInt();
        rnc = in.readInt();
        fullCid = in.readInt();
        gsmSigStrength = in.readInt();
        bsic = in.readInt();
        uarfcn = in.readInt();
        gsmMcc = in.readInt();
        gsmMnc = in.readInt();
        phoneType = in.readInt();
        networkType = in.readInt();
        roaming = in.readByte() != 0;
        otherCells = in.createTypedArrayList(otherLteCell.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(longitude);
        dest.writeDouble(latitude);
        dest.writeDouble(altitude);
        dest.writeFloat(accuracy);
        dest.writeFloat(speed);
        dest.writeFloat(avgSpeed);
        dest.writeFloat(bearing);
        dest.writeLong(fixAge);
        dest.writeInt(gci);
        dest.writeInt(pci);
        dest.writeInt(tac);
        dest.writeInt(mcc);
        dest.writeInt(mnc);
        dest.writeInt(earfcn);
        dest.writeInt(lteSigStrength);
        dest.writeInt(timingAdvance);
        dest.writeInt(gsmTimingAdvance);
        dest.writeInt(lteBand);
        dest.writeByte((byte) (isFDD ? 1 : 0));
        dest.writeInt(bsid);
        dest.writeInt(nid);
        dest.writeInt(sid);
        dest.writeDouble(bslat);
        dest.writeDouble(bslon);
        dest.writeInt(cdmaSigStrength);
        dest.writeInt(evdoSigStrength);
        dest.writeString(operator);
        dest.writeInt(lac);
        dest.writeInt(cid);
        dest.writeInt(psc);
        dest.writeInt(rnc);
        dest.writeInt(fullCid);
        dest.writeInt(gsmSigStrength);
        dest.writeInt(bsic);
        dest.writeInt(uarfcn);
        dest.writeInt(gsmMcc);
        dest.writeInt(gsmMnc);
        dest.writeInt(phoneType);
        dest.writeInt(networkType);
        dest.writeByte((byte) (roaming ? 1 : 0));
        dest.writeTypedList(otherCells);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<signalInfo> CREATOR = new Creator<signalInfo>() {
        @Override
        public signalInfo createFromParcel(Parcel in) {
            return new signalInfo(in);
        }

        @Override
        public signalInfo[] newArray(int size) {
            return new signalInfo[size];
        }
    };

    @Override
    public String toString() {
        return "signalInfo{" +
                "longitude=" + longitude +
                ", latitude=" + latitude +
                ", altitude=" + altitude +
                ", accuracy=" + accuracy +
                ", speed=" + speed +
                ", avgSpeed=" + avgSpeed +
                ", bearing=" + bearing +
                ", fixAge=" + fixAge +
                ", gci=" + gci +
                ", pci=" + pci +
                ", tac=" + tac +
                ", mcc=" + mcc +
                ", mnc=" + mnc +
                ", earfcn=" + earfcn +
                ", lteSigStrength=" + lteSigStrength +
                ", timingAdvance=" + timingAdvance +
                ", gsmTimingAdvance=" + gsmTimingAdvance +
                ", lteBand=" + lteBand +
                ", isFDD=" + isFDD +
                ", bsid=" + bsid +
                ", nid=" + nid +
                ", sid=" + sid +
                ", bslat=" + bslat +
                ", bslon=" + bslon +
                ", cdmaSigStrength=" + cdmaSigStrength +
                ", evdoSigStrength=" + evdoSigStrength +
                ", operator='" + operator + '\'' +
                ", lac=" + lac +
                ", cid=" + cid +
                ", psc=" + psc +
                ", rnc=" + rnc +
                ", fullCid=" + fullCid +
                ", gsmSigStrength=" + gsmSigStrength +
                ", bsic=" + bsic +
                ", uarfcn=" + uarfcn +
                ", gsmMcc=" + gsmMcc +
                ", gsmMnc=" + gsmMnc +
                ", phoneType=" + phoneType +
                ", networkType=" + networkType +
                ", roaming=" + roaming +
                ", otherCells=" + otherCells +
                '}';
    }

    public signalInfo() {
    }

}

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

        int importance = NotificationManager.IMPORTANCE_MIN;
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
        // Configure the notification channel.
        mChannel.setDescription(description);
        mChannel.setShowBadge(false);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

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
                .setContentText("Loading…")
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
        return (timingAdvance != Integer.MAX_VALUE);
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

    private float lastValidSpeed = Float.NaN;
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

            signal.bsid = x.getBaseStationId();
            signal.nid = x.getNetworkId();
            signal.sid = x.getSystemId();

            if (signal.bsid < 0)
                signal.bsid = Integer.MAX_VALUE;

            if (signal.nid <= 0)
                signal.nid = Integer.MAX_VALUE;

            if (signal.sid <= 0)
                signal.sid = Integer.MAX_VALUE;

            signal.bslat = x.getBaseStationLatitude() != 0 ? x.getBaseStationLatitude() / 14400.0 : Double.NaN;
            signal.bslon = x.getBaseStationLongitude() != 0 ? x.getBaseStationLongitude() / 14400.0 : Double.NaN;

            /* Deal with possiblity these may be swapped in Android 8 - thanks Mikejeep
             * https://issuetracker.google.com/issues/63130155 */
            if (validLocation(signal.bslat, signal.bslon) && Math.abs(signal.bslat - signal.latitude) > 1.0) {
                double tmp = signal.bslat;
                signal.bslat = signal.bslon;
                signal.bslon = tmp;
            }
        } else if (mCellLocation instanceof GsmCellLocation) {
            GsmCellLocation x = (GsmCellLocation) mCellLocation;

            signal.lac = x.getLac();
            if(signal.lac < 0 || signal.lac > 0xffff)
                signal.lac = Integer.MAX_VALUE;

            signal.psc = x.getPsc();
            if (signal.psc < 0)
                signal.psc = Integer.MAX_VALUE;

            signal.fullCid = signal.cid = x.getCid();
            if (signal.cid >= 0) {
                signal.rnc = signal.cid >> 16;
                signal.cid = signal.cid & 0xffff;
            } else {
                signal.rnc = Integer.MAX_VALUE;
                signal.cid = Integer.MAX_VALUE;
                signal.fullCid = Integer.MAX_VALUE;
            }
        }
    }

    private void updatelog(boolean log) {
        if (mSignalStrength == null)
            return;

        boolean gotID = false;

        if (mLocation == null &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)  == PackageManager.PERMISSION_GRANTED) {
            mLocation = mLocationManager.getLastKnownLocation(provider);
        }

        if (mLocation == null)
            mLocation = new Location(provider);

        signalInfo signal = new signalInfo();

        signal.latitude = mLocation.getLatitude();
        signal.longitude = mLocation.getLongitude();
        if (mLocation.hasSpeed()) {
            signal.speed = mLocation.getSpeed();
            lastValidSpeed = signal.speed;
        } else {
            signal.speed = lastValidSpeed;
        }
        signal.accuracy = mLocation.getAccuracy();
        signal.altitude = mLocation.getAltitude();
        signal.bearing = mLocation.getBearing();
        signal.avgSpeed = calcAverageSpeed();

        signal.phoneType = mManager.getPhoneType();
        signal.networkType = mManager.getNetworkType();
        signal.roaming = mManager.isNetworkRoaming();
        signal.operator = mManager.getNetworkOperator();

        signal.gsmSigStrength = mSignalStrength.getGsmSignalStrength();
        signal.gsmSigStrength = (signal.gsmSigStrength < 32 ? -113 + 2 * signal.gsmSigStrength : -9999);

        signal.cdmaSigStrength = mSignalStrength.getCdmaDbm();
        signal.evdoSigStrength = mSignalStrength.getEvdoDbm();

        //if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        getLegacyCellLocationData(signal);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mCellInfo = mManager.getAllCellInfo();
        }

        if (mCellInfo != null) {
            sendRootEARFCN();
            signal.otherCells = new ArrayList<>();

            for (CellInfo item : mCellInfo) {
                if(item == null)
                    continue;

                if (item instanceof CellInfoLte) {
                    CellSignalStrengthLte cstr = ((CellInfoLte) item).getCellSignalStrength();
                    CellIdentityLte cellid = ((CellInfoLte) item).getCellIdentity();

                    if (item.isRegistered()) {
                        if (cstr != null) {
                            signal.lteSigStrength = cstr.getDbm();
                            signal.timingAdvance = cstr.getTimingAdvance();
                        }

                        if (cellid != null) {
                            signal.gci = cellid.getCi();
                            signal.pci = cellid.getPci();
                            signal.tac = cellid.getTac();
                            signal.mnc = cellid.getMnc();
                            signal.mcc = cellid.getMcc();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                signal.earfcn = cellid.getEarfcn();
                            else
                                signal.earfcn = EARFCN;
                            signal.lteBand = guessLteBand(signal.mcc, signal.mnc, signal.gci, signal.earfcn);
                            signal.isFDD = isBandFDD(signal.lteBand);
                            gotID = true;
                        }
                    } else {
                        otherLteCell otherCell = new otherLteCell();

                        if (cstr != null) {
                            otherCell.lteSigStrength = cstr.getDbm();
                            otherCell.timingAdvance = cstr.getTimingAdvance();
                        }

                        if (cellid != null) {
                            otherCell.gci = cellid.getCi();
                            otherCell.pci = cellid.getPci();
                            otherCell.tac = cellid.getTac();
                            otherCell.mnc = cellid.getMnc();
                            otherCell.mcc = cellid.getMcc();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                otherCell.earfcn = cellid.getEarfcn();
                            otherCell.lteBand = guessLteBand(otherCell.mcc, otherCell.mnc, otherCell.gci, otherCell.earfcn);
                            otherCell.isFDD = isBandFDD(otherCell.lteBand);
                        }
                        signal.otherCells.add(otherCell);
                    }
                } else if (item instanceof CellInfoCdma) {
                    CellSignalStrengthCdma cstr = ((CellInfoCdma) item).getCellSignalStrength();
                    CellIdentityCdma cellid = ((CellInfoCdma) item).getCellIdentity();

                    signal.bsid = cellid.getBasestationId();
                    signal.bslat = cellid.getLatitude();
                    signal.bslon = cellid.getLongitude();
                    signal.nid = cellid.getNetworkId();
                    signal.sid = cellid.getSystemId();

                    signal.cdmaSigStrength = cstr.getCdmaDbm();
                    signal.evdoSigStrength = cstr.getEvdoDbm();
                } else if (item instanceof CellInfoGsm) {
                    CellSignalStrengthGsm cstr = ((CellInfoGsm) item).getCellSignalStrength();
                    CellIdentityGsm cellid = ((CellInfoGsm) item).getCellIdentity();

                    signal.lac = cellid.getLac();
                    signal.gsmMcc = cellid.getMcc();
                    signal.gsmMnc = cellid.getMnc();
                    signal.fullCid = signal.cid = cellid.getCid();

                    signal.gsmSigStrength = cstr.getDbm();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        signal.bsic = cellid.getBsic();
                        signal.gsmTimingAdvance = cstr.getTimingAdvance();
                    }
                } else if ((item instanceof CellInfoWcdma) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    CellSignalStrengthWcdma cstr = ((CellInfoWcdma) item).getCellSignalStrength();
                    CellIdentityWcdma cellid = ((CellInfoWcdma) item).getCellIdentity();

                    signal.fullCid = signal.cid = cellid.getCid();
                    if(signal.cid != Integer.MAX_VALUE) {
                        signal.rnc = signal.cid >> 16;
                        signal.cid = signal.cid & 0xffff;
                    }

                    signal.lac = cellid.getLac();
                    signal.gsmMcc = cellid.getMcc();
                    signal.gsmMnc = cellid.getMnc();
                    signal.psc = cellid.getPsc();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        signal.uarfcn = cellid.getUarfcn();
                    }

                    signal.gsmSigStrength = cstr.getDbm();
                }
            }
        }

        if (!validLTESignalStrength(signal.lteSigStrength))
            signal.lteSigStrength = parseSignalStrength();

        if (!gotID && mHTCManager != null) {
            Method m = null;

            try {
                String cellID;

                m = mHTCManager.getClass().getMethod("getSectorId", int.class);
                cellID = (String) m.invoke(mHTCManager, new Object[]{Integer.valueOf(1)});
                signal.gci = Integer.parseInt(cellID, 16);
            } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        if (!validLTESignalStrength(signal.lteSigStrength)) {
            Method m;

            try {
                m = mSignalStrength.getClass().getMethod("getLteRsrp");
                signal.lteSigStrength = (Integer) m.invoke(mSignalStrength, (Object[]) null);
            } catch (NoSuchMethodException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        String cellIdInfo = getString(R.string.none);
        if (signal.networkType == TelephonyManager.NETWORK_TYPE_LTE &&
                ((signal.gci != Integer.MAX_VALUE || signal.pci != Integer.MAX_VALUE))) {
            ArrayList<String> cellIds = new ArrayList<>();

            if (validTAC(signal.tac))
                cellIds.add(String.format(Locale.US, "TAC\u00a0%04X", signal.tac));

            if (validCellID(signal.gci))
                cellIds.add(String.format(Locale.US, "GCI\u00a0%08X", signal.gci));

            if (validPhysicalCellID(signal.pci))
                cellIds.add(String.format(Locale.US, "PCI\u00a0%03d", signal.pci));

            if(validEARFCN(signal.earfcn)) {
                cellIds.add(String.format(Locale.US, "EARFCN\u00a0%d", signal.earfcn));
            }

            if (cellIds.isEmpty())
                cellIdInfo = getString(R.string.missing);
            else
                cellIdInfo = TextUtils.join(", ", cellIds);
        }
        mBuilder = mBuilder.setContentText(getString(R.string.serving_lte_cell_id) + ": " + cellIdInfo)
                .setSmallIcon(networkIcon(signal.networkType));

        mNotification = mBuilder.build();
        mNotifyMgr.notify(mNotificationId, mNotification);

        signal.fixAge = locationFixAge(mLocation);

        if (loggingEnabled && log) {
            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); // Quoted "Z" to indicate UTC, no timezone offset
            df.setTimeZone(tz);
            Date now = new Date();

            String nowAsISO = df.format(now);
            String slat = Location.convert(signal.latitude, Location.FORMAT_DEGREES);
            String slon = Location.convert(signal.longitude, Location.FORMAT_DEGREES);

            if (signal.networkType == TelephonyManager.NETWORK_TYPE_LTE &&
                    (validLTESignalStrength(signal.lteSigStrength) ||
                            validPhysicalCellID(signal.pci) || validCellID(signal.gci))) {
                double estDistance = timingAdvanceToMeters(signal.timingAdvance, signal.isFDD);

                String newLteLine = slat + "," + slon + "," +
                        (validCellID(signal.gci) ? String.format(Locale.US, "%08X", signal.gci) : "") + "," +
                        valueString(signal.pci) + "," +
                        (validLTESignalStrength(signal.lteSigStrength) ? String.valueOf(signal.lteSigStrength) : "") + "," +
                        String.format(Locale.US, "%.0f", signal.altitude) + "," +
                        (validTAC(signal.tac) ? String.format(Locale.US, "%04X", signal.tac) : "") + "," +
                        String.format(Locale.US, "%.0f", signal.accuracy) + "," +
                        (validCellID(signal.gci) ? String.format(Locale.US, "%06X", signal.gci /256) : "") + "," +
                        (signal.lteBand > 0 ? valueString(signal.lteBand) : "") + "," +
                        valueString(signal.timingAdvance) + ","+
                        (validEARFCN(signal.earfcn) ? valueString(signal.earfcn) : "") + "," +
                        nowAsISO + "," + String.valueOf(now.getTime()) + "," +
                        (isBandFDD(signal.lteBand) ? "1" : "0") + ","+
                        (Double.isNaN(estDistance) ? "" : String.format(Locale.US, "%.0f", estDistance)) + "," +
                        valueString(signal.mcc) + "," +
                        valueString(signal.mnc);

                if (lteLine == null || !newLteLine.equals(lteLine)) {
//                    Log.d(TAG, "Logging LTE cell.");
                    appendLog("ltecells.csv", newLteLine, "latitude,longitude,cellid,physcellid,dBm,altitude,tac,accuracy,baseGci,band,timingAdvance,earfcn,timestamp,timeSinceEpoch,fdd,estDistance,mcc,mnc");
                    lteLine = newLteLine;
                }
            }

            if (mCellInfo != null) {
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
                                String.format(Locale.US, "%.0f", signal.accuracy) + "," +
                                String.format(Locale.US, "%.0f", signal.altitude) + "," +
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

            if (validRSSISignalStrength(signal.cdmaSigStrength) && validSID(signal.sid)) {
                boolean isValid = validLocation(signal.bslon, signal.bslat);

                String bslatstr = (isValid ? Location.convert(signal.bslat, Location.FORMAT_DEGREES) : "");
                String bslonstr = (isValid ? Location.convert(signal.bslon, Location.FORMAT_DEGREES) : "");

                String newCdmaLine = String.format(Locale.US, "%s,%s,%s,%s,%s,%d,%s,%s,%.0f,%.0f,%s,%d",
                        slat, slon, valueString(signal.sid), valueString(signal.nid), valueString(signal.bsid),
                        signal.cdmaSigStrength,
                        bslatstr, bslonstr, signal.altitude, signal.accuracy, nowAsISO, now.getTime());
                if (CdmaLine == null || !newCdmaLine.equals(CdmaLine)) {
//                    Log.d(TAG, "Logging CDMA cell.");
                    appendLog(((signal.sid >= 22404) && (signal.sid <= 22451)) ? "esmrcells.csv" : "cdmacells.csv",
                            newCdmaLine, "latitude,longitude,sid,nid,bsid,rssi,bslat,bslon,altitude,accuracy,timestamp,timeSinceEpoch");
                    CdmaLine = newCdmaLine;
                }
            } else if (validRSSISignalStrength(signal.gsmSigStrength) && validCID(signal.cid)) {
                String newGSMLine = String.format(Locale.US, "%s,%s,%.0f,%.0f,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s", slat, slon,
                        signal.altitude, signal.accuracy,
                        valueString(signal.cid), valueString(signal.rnc), valueString(signal.lac),
                        valueString(signal.psc), valueString(signal.gsmSigStrength),
                        nowAsISO, now.getTime(), valueString(signal.bsic), valueString(signal.uarfcn),
                        valueString(signal.gsmTimingAdvance),
                        (signal.gsmTimingAdvance != Integer.MAX_VALUE ? signal.gsmTimingAdvance * 550 : ""),
                        valueString(signal.gsmMcc), valueString(signal.gsmMnc));
                if (GSMLine == null || !newGSMLine.equals(GSMLine)) {
//                    Log.d(TAG, "Logging GSM cell.");
                    appendLog("gsmcells.csv", newGSMLine, "latitude,longitude,altitude,accuracy,cid,rnc,lac,psc,rssi,timestamp,timeSinceEpoch,bsic,uarfcn,timingAdvance,estDistance,mcc,mnc");
                    GSMLine = newGSMLine;
                }
            }
        }

        sendResult(signal);
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
