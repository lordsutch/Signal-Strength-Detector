package com.lordsutch.android.signaldetector;

// Android Packages
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

@TargetApi(17)
public final class HomeActivity extends Activity
{
	public static final String TAG = HomeActivity.class.getSimpleName();
	
	public static final String EMAIL = "lordsutch@gmail.com";
	
	private CellLocation mCellLocation;
	private String mCellInfo = null;
	private SignalStrength mSignalStrength;
	private boolean mDone = false;
	private TextView mText = null;
	private String mTextStr;
	private Button mSubmit, mCancel;
	private TelephonyManager mManager;
	private Object mHTCManager;
	private LocationProvider mLocationProvider = null;
	
	private LocationManager mLocationManager;

    /** Called when the activity is first created. */
    @Override
    @TargetApi(17)
    public void onCreate(Bundle savedInstanceState)
    {
    	List<CellInfo> mInfo;
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mLocationProvider = mLocationManager.getProvider(LocationManager.GPS_PROVIDER);
        
        mHTCManager = getSystemService("htctelephony");
    	mText = (TextView) findViewById(R.id.text);
    	mSubmit = (Button) findViewById(R.id.submit);
    	mCancel = (Button) findViewById(R.id.cancel);
    	
    	// Prevent button press.
    	mSubmit.setEnabled(false);
    	
    	// Handle click events.
    	mSubmit.setOnClickListener(new OnClickListener()
    	{
    		@Override
    		public void onClick(View mView)
    		{
    			sendResults();
    			finish();
    		}
    	});
    	mCancel.setOnClickListener(new OnClickListener()
    	{
    		@Override
    		public void onClick(View mView)
    		{
    			finish();
    		}
    	});
    	
    	// Register the listener with the telephony manager
    	mManager.listen(mListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
    		PhoneStateListener.LISTEN_CELL_LOCATION | PhoneStateListener.LISTEN_CELL_INFO);
    	
    	mInfo = mManager.getAllCellInfo();
    	Log.d(TAG, "getAllCellInfo()");
    	
    	if(mInfo != null) {
    		Log.d(TAG, mInfo.toString());
    		mCellInfo = "";
    		for(CellInfo item : mInfo) {
    			Log.d(TAG, item.toString());
    			if(item != null)
    				mCellInfo = mCellInfo + ReflectionUtils.dumpClass(item.getClass(), item);
    		}
    	}
    	
    	mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2, 2, mLocListener);
    	
    	if(mHTCManager != null) {
    		if(mCellInfo == null)
    			mCellInfo = "";

    		Method m = null;
    		Object x = null;
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
    	}
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
            new EnableGpsDialogFragment().show(getFragmentManager(), "enableGpsDialog");
        }
    }

    private void enableLocationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(settingsIntent);
    }
    
    private final LocationListener mLocListener = new LocationListener()
    {
    	@Override
    	public void onLocationChanged(Location mLocation)
    	{
    		Boolean gotID = false;
    		
    		TextView lat = (TextView) findViewById(R.id.positionLat);
    		TextView lon = (TextView) findViewById(R.id.positionLon);
    		
    		lat.setText(String.valueOf(mLocation.getLatitude())+"\u00b0N");
    		lon.setText(String.valueOf(mLocation.getLongitude())+"\u00b0E");

        	List<CellInfo> mInfo;

			TextView servingid = (TextView) findViewById(R.id.cellid);
			TextView strength = (TextView) findViewById(R.id.sigstrength);

			mInfo = mManager.getAllCellInfo();
        	if(mInfo != null) {
        		for(CellInfo item : mInfo) {
        			if(item != null && item.getClass() == CellInfoLte.class) {
        				CellIdentityLte cellid = ((CellInfoLte) item).getCellIdentity();
        				servingid.setText(String.format("%07x", cellid.getCi()) + " " + String.valueOf(cellid.getPci()));
        				gotID = true;
        				
        				CellSignalStrengthLte cstr = ((CellInfoLte) item).getCellSignalStrength();
        				strength.setText(String.valueOf(cstr.getDbm()) + "\u2009dBm");
        			}
        		}
        	}
    		if(!gotID && mHTCManager != null) {
    			Method m = null;
    			String s = "";
    			Integer ss = -999;
    			
    			try {
					m = mHTCManager.getClass().getMethod("getSectorId");
				} catch (NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    			if(m != null) {
    				try {
    					s = (String) m.invoke(mHTCManager, (Object []) null);
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
    				servingid.setText(s);
    			}
    			
    			try {
					m = mSignalStrength.getClass().getMethod("getLteDbm");
				} catch (NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    			try {
					ss = (Integer) m.invoke(mSignalStrength, (Object []) null);
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
    			
    			strength.setText(ss.toString() + "\u2009dBm");
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
    
    // Listener for signal strength.
    final PhoneStateListener mListener = new PhoneStateListener()
    {
    	@Override
    	public void onCellLocationChanged(CellLocation mLocation)
    	{
    		if (mDone) return;
    		
    		Log.d(TAG, "Cell location obtained.");
    	
    		mCellLocation = mLocation;
    		
    		update();
    	}
    	
    	@Override
    	public void onCellInfoChanged(List<CellInfo> mInfo)
    	{
    		if (mDone) return;
    		
    		Log.d(TAG, "Cell info obtained.");
    	
    		if(mInfo != null) {
        		mCellInfo = "";
    			for(CellInfo item : mInfo) {
    				Log.d(TAG, item.toString());
    				if(item != null)
    					mCellInfo = mCellInfo + ReflectionUtils.dumpClass(item.getClass(), item);
    			}
    		}
    		
    		update();
    	}

    	@Override
    	public void onSignalStrengthsChanged(SignalStrength sStrength)
    	{
    		if (mDone) return;
    		
    		Log.d(TAG, "Signal strength obtained.");
    		
    		mSignalStrength = sStrength;
    		
    		update();
    	}
    };
    
    // AsyncTask to avoid an ANR.
    private class ReflectionTask extends AsyncTask<Void, Void, Void>
    {
		protected Void doInBackground(Void... mVoid)
		{
			mTextStr = 
    			("DEVICE INFO\n\n" + "SDK: `" + Build.VERSION.SDK_INT + "`\nCODENAME: `" +
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
    	mDone = true;
    	
    	try
    	{
    		mText.setText(mTextStr);
    	
			// Stop listening.
			mManager.listen(mListener, PhoneStateListener.LISTEN_NONE);
			Toast.makeText(getApplicationContext(), R.string.done, Toast.LENGTH_SHORT).show();
			
			mSubmit.setEnabled(true);
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

