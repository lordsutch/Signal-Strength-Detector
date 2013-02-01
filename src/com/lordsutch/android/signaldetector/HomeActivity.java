package com.lordsutch.android.signaldetector;

// Android Packages
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthLte;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public final class HomeActivity extends Activity
{
	public static final String TAG = HomeActivity.class.getSimpleName();
	
	public static final String EMAIL = "lordsutch@gmail.com";
	
	private CellLocation mCellLocation;
	private String mCellInfo = null;
	private SignalStrength mSignalStrength;
	private String mTextStr;
	private TelephonyManager mManager;
	private Object mHTCManager;
	private Notification.Builder mBuilder = null;
	private NotificationManager mNotifyMgr;
	private LocationManager mLocationManager;
	private int mNotificationId = 001;
    private Location mLocation = null;
    
    private WebView leafletView = null;
	
	/** Called when the activity is first created. */
	@SuppressLint("SetJavaScriptEnabled")
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
    	List<CellInfo> mInfo;
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
                
        mManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // mLocationManager.getProvider(LocationManager.GPS_PROVIDER);
        
        mHTCManager = getSystemService("htctelephony");
    	
    	leafletView = (WebView) findViewById(R.id.leafletView);
    	leafletView.loadUrl("file:///android_asset/leaflet.html");
    	WebSettings webSettings = leafletView.getSettings();
    	// webSettings.setAllowFileAccessFromFileURLs(true);
    	webSettings.setJavaScriptEnabled(true);
    	    	
    	Intent resultIntent = new Intent(this, HomeActivity.class);
    	PendingIntent resultPendingIntent =
    		    PendingIntent.getActivity(this, 0,
    		    resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    	mBuilder = new Notification.Builder(this)
    		    .setSmallIcon(R.drawable.icon)
    		    .setContentTitle(getString(R.string.signal_detector_is_running))
    		    .setContentText("Hello World!")
    		    .setContentIntent(resultPendingIntent);

    	mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    	// Builds the notification and issues it.
    	mNotifyMgr.notify(mNotificationId, mBuilder.build());
    	
    	// Register the listener with the telephony manager
    	mManager.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
    		PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_CELL_INFO);
    	
    	Criteria gpsCriteria = new Criteria();
    	gpsCriteria.setCostAllowed(false);
    	gpsCriteria.setHorizontalAccuracy(Criteria.ACCURACY_MEDIUM);
    	
    	List<String> providers = mLocationManager.getProviders(gpsCriteria, true);
    	
    	for(String provider : providers) {
    		Log.d(TAG, "Registering "+provider);
    		mLocationManager.requestLocationUpdates(provider, 1000, 10, mLocListener);
    		Location mLoc = mLocationManager.getLastKnownLocation(provider);

    		if(mLoc != null) {
    			mLocation = getBetterLocation(mLoc, mLocation);
    			if(mLocation != null) {
    				centerMap();
    			}
    		}
    	}
    		
    	mInfo = mManager.getAllCellInfo();
    	Log.d(TAG, "getAllCellInfo()");
    	
    	if(mInfo != null) {
    		Log.d(TAG, mInfo.toString());
    		mCellInfo = "getAllCellInfo():\n";
    		for(CellInfo item : mInfo) {
    			Log.d(TAG, item.toString());
    			if(item != null)
    				mCellInfo = mCellInfo + ReflectionUtils.dumpClass(item.getClass(), item);
    		}
    	}
    	
    	/*    	
    	if(mHTCManager != null) {
    		if(mCellInfo == null)
    			mCellInfo = "";

    		Method m = null;
    		Object x = null;
    		String s = null;
    		
			try {
				m = mHTCManager.getClass().getMethod("getSectorId", int.class);
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(m != null) {
				try {
					s = (String) m.invoke(mHTCManager, new Object[] {Integer.valueOf(1)} );
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
    		if(s != null)
    			mCellInfo += "getSectorId = '" + s + "'\n";
			
    		try {
    			m = mHTCManager.getClass().getMethod("requestGetLTERFBandInfo");
    		} catch (NoSuchMethodException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		try {
    			x = (Object) m.invoke(mHTCManager, (Object[]) null);
    		} catch (IllegalArgumentException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IllegalAccessException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (InvocationTargetException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		if(x != null)
    			mCellInfo += "requestGetLTERFBandInfo\n" + ReflectionUtils.dumpClass(x.getClass(), x);
    		else
    			mCellInfo += "requestGetLTERFBandInfo returns " + m.getReturnType().toString() + "\n";

    		try {
    			m = mHTCManager.getClass().getMethod("requestGetLTETxRxInfo");
    		} catch (NoSuchMethodException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		try {
    			x = (Object) m.invoke(mHTCManager, (Object[]) null);
    		} catch (IllegalArgumentException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IllegalAccessException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (InvocationTargetException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}

    		if(x != null)
    			mCellInfo += "requestGetLTETxRxInfo\n" + ReflectionUtils.dumpClass(x.getClass(), x);
    		else
    			mCellInfo += "requestGetLTETxRxInfo returns " + m.getReturnType().toString() + "\n";
    	} */
    }
    
    @Override
    protected void onStart() {
        super.onStart();

        // This verification should be done during onStart() because the system calls
        // this method when the user returns to the activity, which ensures the desired
        // location provider is enabled each time the activity resumes from the stopped state.
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsEnabled) {
            // Build an alert dialog here that requests that the user enable
            // the location services, then when the user clicks the "OK" button,
            // call enableLocationSettings()
            new EnableGpsDialogFragment().show(getFragmentManager(), "enableLocationSettings");
        }
    }

    private void enableLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(settingsIntent);
    }
    
    private void appendLog(String logfile, String text, String header)
    {       
    	Boolean newfile = false;
    	File logFile = new File(getExternalFilesDir(null), logfile);
    	if (!logFile.exists())
    	{
    		try
    		{
    			logFile.createNewFile();
    			newfile = true;
    		} 
    		catch (IOException e)
    		{
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    	try
    	{
    		//BufferedWriter for performance, true to set append to file flag
    		BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true)); 
    		if (newfile) {
    			buf.append(header);
    			buf.newLine();
    		}
    		buf.append(text);
    		buf.newLine();
    		buf.close();
    	}
    	catch (IOException e)
    	{
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	}
    }

    private int parseSignalStrength() {
    	String sstrength = mSignalStrength.toString();
    	int strength = -999;
    	
    	String[] bits = sstrength.split("\\s+");
    	if(bits.length >= 10)
    		try {
    			strength = Integer.parseInt(bits[9]);
    		}
    		catch (NumberFormatException e) {}
    	
    	return strength;
    }
    
    final private Boolean validSignalStrength(int strength)
    {
    	return (strength > -900 && strength < 900);
    }
    
    private void updatelog() {
    	Log.d(TAG, "updatelog() entered");
    	
    	if(mLocation == null || mSignalStrength == null || mCellLocation == null)
    		return;
    	
    	centerMap();
    	
		Boolean gotID = false;
		
		TextView latlon = (TextView) findViewById(R.id.positionLatLon);
		
		double latitude = mLocation.getLatitude();
		double longitude = mLocation.getLongitude();
		
		double bslat = 999;
		double bslon = 999;
		
		latlon.setText(String.format("%3.6f", Math.abs(latitude))+"\u00b0"+(latitude >= 0 ? "N" : "S") + " " +
			String.format("%3.6f", Math.abs(longitude))+"\u00b0"+(longitude >= 0 ? "E" : "W"));

    	List<CellInfo> mInfo;

		TextView servingid = (TextView) findViewById(R.id.cellid);
		TextView strength = (TextView) findViewById(R.id.sigstrength);

		TextView strengthLabel = (TextView) findViewById(R.id.sigStrengthLabel);
		TextView bsLabel = (TextView) findViewById(R.id.bsLabel);
		
		TextView cdmaBS = (TextView) findViewById(R.id.cdma_sysinfo);
		TextView cdmaStrength = (TextView) findViewById(R.id.cdmaSigStrength);

		String cellID = "";
		int physCellID = -1;
		int lteSigStrength = -9999;
		
		int bsid = -1;
		int nid = -1;
		int sid = -1;
		
		if(mCellLocation.getClass() == CdmaCellLocation.class) {
			CdmaCellLocation x = (CdmaCellLocation) mCellLocation;
			bsid = x.getBaseStationId();
			nid = x.getNetworkId();
			sid = x.getSystemId();
			
			bslat = x.getBaseStationLatitude()/14400;
			bslon = x.getBaseStationLongitude()/14400;
		}
		
		int cdmaSigStrength = mSignalStrength.getCdmaDbm();
		int gsmSigStrength = mSignalStrength.getGsmSignalStrength();
		gsmSigStrength = (gsmSigStrength < 32 ? -113+2*gsmSigStrength : -9999);
		
		mInfo = mManager.getAllCellInfo();
    	if(mInfo != null) {
    		for(CellInfo item : mInfo) {
    			if(item != null && item.getClass() == CellInfoLte.class) {
    				CellInfoLte x = (CellInfoLte) item;
    				CellSignalStrengthLte cstr = x.getCellSignalStrength();
    				if(cstr != null)
    					lteSigStrength = cstr.getDbm();

    				CellIdentityLte cellid = x.getCellIdentity();
    				if(cellid != null) {
    					cellID = String.format("%08x", cellid.getCi());
    					physCellID = cellid.getPci();
    					gotID = true;
    				}
    			}
    		}
    	}
    	
    	if(!validSignalStrength(lteSigStrength))
    		lteSigStrength = parseSignalStrength();
    	
		if(!gotID && mHTCManager != null) {
			Method m = null;
			
			try {
				m = mHTCManager.getClass().getMethod("getSectorId", int.class);
				cellID = (String) m.invoke(mHTCManager, new Object[] {Integer.valueOf(1)} );
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if(!validSignalStrength(lteSigStrength)) {
			Method m;

			try {
				m = mSignalStrength.getClass().getMethod("getLteRsrp");
				lteSigStrength = (Integer) m.invoke(mSignalStrength, (Object []) null);
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if(!cellID.isEmpty() || physCellID >= 0) {
			if(physCellID >= 0) {
				servingid.setText(cellID + " " + String.valueOf(physCellID));
			} else {
				servingid.setText("'"+cellID+"'");
			}
		} else {
			servingid.setText(R.string.none);
		}
		
		if(validSignalStrength(lteSigStrength)) {
			strength.setText(String.valueOf(lteSigStrength) + "\u2009dBm");
		} else {
			strength.setText(R.string.no_signal);
		}

		if(validSignalStrength(cdmaSigStrength) && mManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
			strengthLabel.setText(R.string._1xrtt_signal_strength);
			cdmaStrength.setText(String.valueOf(cdmaSigStrength) + "\u2009dBm");
		} else if (validSignalStrength(gsmSigStrength)) {
			strengthLabel.setText(R.string._2g_3g_signal);
			cdmaStrength.setText(String.valueOf(gsmSigStrength) + "\u2009dBm");
    	} else {
			cdmaStrength.setText(R.string.no_signal);
		}
		
		if(sid >= 0 && nid >= 0 && bsid >= 0 && (mManager.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA)) {
			bsLabel.setText(R.string.cdma_1xrtt_base_station);
			cdmaBS.setText(String.format("SID %d, NID %d, BSID %d", sid, nid, bsid));
		} else if(mManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
			GsmCellLocation x = (GsmCellLocation) mCellLocation;
			
			bsLabel.setText("2G/3G Tower");
			cdmaBS.setText(String.format("MNC %s, LAC %d, CID %d", mManager.getNetworkOperator(),
					x.getLac(), x.getCid()));
		} else {
			cdmaBS.setText(R.string.none);
		}
		
		if(!cellID.isEmpty())
			mBuilder.setContentText(getString(R.string.serving_lte_cell_id) + ": " + cellID);
		else
			mBuilder.setContentText(getString(R.string.serving_lte_cell_id) + ": " + getString(R.string.none));

    	mNotifyMgr.notify(mNotificationId, mBuilder.build());

    	String slat = Location.convert(latitude, Location.FORMAT_DEGREES);
    	String slon = Location.convert(longitude, Location.FORMAT_DEGREES);

    	if(lteSigStrength > -900 || !cellID.isEmpty()) {
    		Log.d(TAG, "Logging LTE cell.");
    		appendLog("ltecells.csv", slat+","+slon+","+cellID+","+
    				(physCellID >= 0 ? String.valueOf(physCellID) : "")+","+
    				(validSignalStrength(lteSigStrength) ? String.valueOf(lteSigStrength) : ""),
    				"latitude,longitude,cellid,physcellid,dBm");
    	}
    	if(sid >= 22404 && sid <= 22451)
    	{
    		String bslatstr = (bslat <= 200 ? Location.convert(bslat, Location.FORMAT_DEGREES) : "");
    		String bslonstr = (bslat <= 200 ? Location.convert(bslon, Location.FORMAT_DEGREES) : "");

    		Log.d(TAG, "Logging ESMR cell.");
    		appendLog("esmrcells.csv", 
    				String.format("%s,%s,%d,%d,%d,%s,%s,%s", slat, slon, sid, nid, bsid,
    						(validSignalStrength(cdmaSigStrength) ? String.valueOf(cdmaSigStrength) : ""),
    						bslatstr, bslonstr), "latitude,longitude,sid,nid,bsid,rssi,bslat,bslon");
    	}
    }
    
    private void centerMap() {
		leafletView.loadUrl(String.format("javascript:recenter(%f,%f,%f,%f)",
				mLocation.getLatitude(), mLocation.getLongitude(),
				mLocation.getAccuracy(), mLocation.getSpeed()));
		
    }
    
    private final LocationListener mLocListener = new LocationListener()
    {
    	@Override
    	public void onLocationChanged(Location mLoc)
    	{
    		Log.d(TAG, mLoc.toString());
    		mLocation = getBetterLocation(mLoc, mLocation);
    		
    		if(mLocation != null) {
    			centerMap();
    			updatelog();
    		}
    		
    	}

		@Override
		public void onProviderDisabled(String provider) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onProviderEnabled(String provider) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			// TODO Auto-generated method stub
			
		}
    };

    private static final int TWO_MINUTES = 1000 * 60 * 2;

    /** Determines whether one Location reading is better than the current Location fix.
     * Code taken from
     * http://developer.android.com/guide/topics/location/obtaining-user-location.html
     *
     * @param newLocation  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new
     *        one
     * @return The better Location object based on recency and accuracy.
     */
   protected Location getBetterLocation(Location newLocation, Location currentBestLocation) {
       if (currentBestLocation == null) {
           // A new location is always better than no location
           return newLocation;
       }

       // Check whether the new location fix is newer or older
       long timeDelta = newLocation.getTime() - currentBestLocation.getTime();
       boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
       boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
       boolean isNewer = timeDelta > 0;

       // If it's been more than two minutes since the current location, use the new location
       // because the user has likely moved.
       if (isSignificantlyNewer) {
           return newLocation;
       // If the new location is more than two minutes older, it must be worse
       } else if (isSignificantlyOlder) {
           return currentBestLocation;
       }

       // Check whether the new location fix is more or less accurate
       int accuracyDelta = (int) (newLocation.getAccuracy() - currentBestLocation.getAccuracy());
       boolean isLessAccurate = accuracyDelta > 0;
       boolean isMoreAccurate = accuracyDelta < 0;
       boolean isSignificantlyLessAccurate = accuracyDelta > 200;

       // Check if the old and new location are from the same provider
       boolean isFromSameProvider = isSameProvider(newLocation.getProvider(),
               currentBestLocation.getProvider());

       // Determine location quality using a combination of timeliness and accuracy
       if (isMoreAccurate) {
           return newLocation;
       } else if (isNewer && !isLessAccurate) {
           return newLocation;
       } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
           return newLocation;
       }
       return currentBestLocation;
   }

   /** Checks whether two providers are the same */
   private boolean isSameProvider(String provider1, String provider2) {
       if (provider1 == null) {
         return provider2 == null;
       }
       return provider1.equals(provider2);
   }

    // Listener for signal strength.
    final PhoneStateListener mListener = new PhoneStateListener()
    {
    	@Override
    	public void onCellLocationChanged(CellLocation mLocation)
    	{
    		mCellLocation = mLocation;
    		
    		update();
    		updatelog();
    	}
    	
    	@Override
    	public void onCellInfoChanged(List<CellInfo> mInfo)
    	{
    		if(mInfo != null) {
        		mCellInfo = "";
    			for(CellInfo item : mInfo) {
    				Log.d(TAG, item.toString());
    				if(item != null)
    					mCellInfo = mCellInfo + ReflectionUtils.dumpClass(item.getClass(), item);
    			}
    		}
    		
    		update();
    		updatelog();
    	}

    	@Override
    	public void onSignalStrengthsChanged(SignalStrength sStrength)
    	{
    		mSignalStrength = sStrength;
    		
    		update();
    		updatelog();
    	}
    };
    
    // AsyncTask to avoid an ANR.
    private class ReflectionTask extends AsyncTask<Void, Void, Void>
    {
		protected Void doInBackground(Void... mVoid)
		{
			mTextStr = 
    			("\n\nDEVICE INFO\n\n" + "SDK: `" + Build.VERSION.SDK_INT + "`\nCODENAME: `" +
    			Build.VERSION.CODENAME + "`\nRELEASE: `" + Build.VERSION.RELEASE +
    			"`\nDevice: `" + Build.DEVICE + "`\nHARDWARE: `" + Build.HARDWARE +
    			"`\nMANUFACTURER: `" + Build.MANUFACTURER + "`\nMODEL: `" + Build.MODEL +
    			"`\nPRODUCT: `" + Build.PRODUCT + ((getRadio() == null) ? "" : ("`\nRADIO: `" + getRadio())) +
    			"`\nBRAND: `" + Build.BRAND + ((Build.VERSION.SDK_INT >= 8) ? ("`\nBOOTLOADER: `" + Build.BOOTLOADER) : "") +
    			"`\nBOARD: `" + Build.BOARD + "`\nID: `"+ Build.ID + "`\n\n" +
    			// ReflectionUtils.dumpClass(SignalStrength.class, mSignalStrength) + "\n" +
    			// ReflectionUtils.dumpClass(mCellLocation.getClass(), mCellLocation) + "\n" + // getWimaxDump() +
    			// ReflectionUtils.dumpClass(TelephonyManager.class, mManager) + "\n" +
    			// ReflectionUtils.dumpClass(mHTCManager.getClass(), mHTCManager) + "\n" +
    			mCellInfo /* +
    			ReflectionUtils.dumpStaticFields(Context.class, getApplicationContext()) */
    			);
    			
			return null;
		}
		
		protected void onProgressUpdate(Void... progress)
		{
			// Do nothing...
		}
		
		protected void onPostExecute(Void result)
		{
			complete();
		}
	}

    private final void complete()
    {
    	try
    	{
    		// mText.setText(mTextStr);
    	
			// Stop listening.
			// mManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
			// Toast.makeText(getApplicationContext(), R.string.done, Toast.LENGTH_SHORT).show();
			
			// mSubmit.setEnabled(true);
		}
		catch (Exception e)
		{
			Log.e(TAG, "ERROR!!!", e);
		}
    }
    
    private final void update()
    {
    	if (mSignalStrength == null || mCellLocation == null /* || mCellInfo == null */) return;
    	
    	final ReflectionTask mTask = new ReflectionTask();
    	mTask.execute();
    }
    
    /**
     * @return The Radio of the {@link Build} if available.
     */
    public static final String getRadio()
    {
    	if (Build.VERSION.SDK_INT >= 14)
    		return Build.getRadioVersion();
    	else
    		return null;
    }
    
    private static final String[] mServices =
	{
		"WiMax", "wimax", "wimax", "WIMAX", "WiMAX"
	};
    
    /**
     * @return A String containing a dump of any/ all WiMax
     * classes/ services loaded via {@link Context}.
     */
    public final String getWimaxDump()
    {
    	String mStr = "";
    	
    	for (final String mService : mServices)
    	{
    		final Object mServiceObj = getApplicationContext()
    			.getSystemService(mService);
    		if (mServiceObj != null)
    		{
    			mStr += "getSystemService(" + mService + ")\n\n";
    			mStr += ReflectionUtils.dumpClass(mServiceObj.getClass(), mServiceObj);
			}
    	}
    	
    	return mStr;
    }
    
    /**
     * Start an {@link Intent} chooser for the user to submit the results.
     */
    public final void sendResults()
    {
    	final Intent mIntent = new Intent(Intent.ACTION_SEND);
		mIntent.setType("plain/text");
		mIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { EMAIL });
		mIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.results));
		mIntent.putExtra(Intent.EXTRA_TEXT, mTextStr);
		HomeActivity.this.startActivity(Intent.createChooser(mIntent, "Send results."));
    }
    
    protected void onStop() {
        super.onStop();
        mLocationManager.removeUpdates(mLocListener);
        mManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        mNotifyMgr.cancelAll();
    }

    /**
     * Dialog to prompt users to enable GPS on the device.
     */
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
}

