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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

public final class HomeActivity extends Activity
{
	public static final String TAG = HomeActivity.class.getSimpleName();
	
	public static final String EMAIL = "lordsutch@gmail.com";
	
	private CellLocation mCellLocation;
	private String mCellInfo = null;
	private SignalStrength mSignalStrength;
	private TelephonyManager mManager;
	private Object mHTCManager;
	private Notification.Builder mBuilder = null;
	private NotificationManager mNotifyMgr;
	private LocationManager mLocationManager;
	private int mNotificationId = 001;
    private Location mLocation = null;
    
    private WebView leafletView = null;
    
    private boolean bsmarker = false;
	
	/** Called when the activity is first created. */
	@SuppressLint("SetJavaScriptEnabled")
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
                
        mManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
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
    	gpsCriteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
    	
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
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        
        MenuItem x = menu.findItem(R.id.mapbasestation);
        if(bsmarker)
        	x.setTitle(R.string.hide_base_station);
        
        return true;
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
    
	private double bslat = 999;
	private double bslon = 999;
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private void updatelog() {
    	Log.d(TAG, "updatelog() entered");
    	
    	if(mLocation == null || mSignalStrength == null || mCellLocation == null)
    		return;
    	
    	centerMap();
    	
		Boolean gotID = false;
		
		TextView latlon = (TextView) findViewById(R.id.positionLatLon);
		
		double latitude = mLocation.getLatitude();
		double longitude = mLocation.getLongitude();
		
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
			
			bslat = x.getBaseStationLatitude()/14400.0;
			bslon = x.getBaseStationLongitude()/14400.0;
			
			addBsMarker();
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
				servingid.setText(cellID);
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
			int lac = x.getLac();
			int cid = x.getCid();
			
			if(lac >= 0 && cid >= 0) {
				bsLabel.setText("2G/3G Tower");
				cdmaBS.setText(String.format("MNC %s, LAC %d, CID %d", mManager.getNetworkOperator(),
						x.getLac(), x.getCid()));
			}
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
    	if(mLocation == null) return;
    	
		leafletView.loadUrl(String.format("javascript:recenter(%f,%f,%f,%f)",
				mLocation.getLatitude(), mLocation.getLongitude(),
				mLocation.getAccuracy(), mLocation.getSpeed()));
		
    }
    
    private void addBsMarker() {
    	if(bsmarker && Math.abs(bslat) <= 90 && Math.abs(bslon) <= 190)
    		leafletView.loadUrl(String.format("javascript:placeMarker(%f,%f)",
    				bslat, bslon));
    	else
    		leafletView.loadUrl("javascript:clearMarker()");
    }
    
    public void toggleBsMarker(MenuItem x) {
    	bsmarker = !bsmarker;
    	
    	if(!bsmarker) {
    		x.setTitle(R.string.show_base_station);
    		leafletView.loadUrl("javascript:clearMarker()");
    		centerMap();
    	} else {
    		x.setTitle(R.string.hide_base_station);
    		addBsMarker();
    	}
    	invalidateOptionsMenu();
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
    		
    		updatelog();
    	}
    	
    	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
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
    		
    		updatelog();
    	}

    	@Override
    	public void onSignalStrengthsChanged(SignalStrength sStrength)
    	{
    		mSignalStrength = sStrength;
    		
    		updatelog();
    	}
    };
    
    protected void onStop() {
        super.onStop();
        mLocationManager.removeUpdates(mLocListener);
        mManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
        mNotifyMgr.cancelAll();
    }

    private String STATE_SHOWBS ="showBSlocation";
    
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putBoolean(STATE_SHOWBS, bsmarker);
        
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }
    
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);
     
        bsmarker = savedInstanceState.getBoolean(STATE_SHOWBS, false);
        invalidateOptionsMenu();
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