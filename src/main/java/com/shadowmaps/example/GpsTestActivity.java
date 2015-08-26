/*
 * Copyright (C) 2008-2013 The Android Open Source Project,
 * Sean J. Barbeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shadowmaps.example;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.shadowmaps.example.data.Mode;
import com.shadowmaps.example.service.ShadowMapsService;
import com.shadowmaps.example.util.GpsTestUtil;
import com.shadowmaps.example.view.ViewPagerMapBevelScroll;
import com.shadowmaps.shadowgps.R;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.codehaus.jackson.map.ObjectMapper;

import java.util.ArrayList;


public class GpsTestActivity extends SherlockFragmentActivity
        implements LocationListener, GpsStatus.Listener, ActionBar.TabListener,
        SensorEventListener {

    protected void onStart() {
        super.onStart();
    }

    protected void onStop() {
        super.onStop();
    }

    private static final String TAG = "GpsTestActivity";

    private static final int SECONDS_TO_MILLISECONDS = 1000;

    static boolean mIsLargeScreen = false;

    private static GpsTestActivity sInstance;

    private ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient client = new OkHttpClient();

    public final String URL_TO_POST = "https://api.shadowmaps.com/v1/mode/";
    // Holds sensor data
    private static float[] mRotationMatrix = new float[16];

    private static float[] mRemappedMatrix = new float[16];

    private static float[] mValues = new float[3];

    private static float[] mTruncatedRotationVector = new float[4];

    private static boolean mTruncateVector = false;

    boolean mStarted;

    boolean mFaceTrueNorth;

    boolean recording;

    String mTtff;

    private Menu menu;

    SectionsPagerAdapter mSectionsPagerAdapter;

    ViewPagerMapBevelScroll mViewPager;

    private LocationManager mService;

    private LocationProvider mProvider;

    private GpsStatus mStatus;

    private ArrayList<GpsTestListener> mGpsTestListeners = new ArrayList<GpsTestListener>();

    private Location mLastLocation;
    private MediaRecorder mRecorder;

    private GeomagneticField mGeomagneticField;

    private long minTime; // Min Time between location updates, in milliseconds

    private float minDistance; // Min Distance between location updates, in meters

    private SensorManager mSensorManager;

    static GpsTestActivity getInstance() {
        return sInstance;
    }

    void addListener(GpsTestListener listener) {
        mGpsTestListeners.add(listener);
    }


    private synchronized void gpsStart() {

        if (!mStarted) {
            mService.requestLocationUpdates(mProvider.getName(), minTime, minDistance, this);
            mStarted = true;

            // Reset the options menu to trigger updates to action bar menu items
            invalidateOptionsMenu();
        }
        for (GpsTestListener listener : mGpsTestListeners) {
            listener.gpsStart();
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(com.actionbarsherlock.R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);
        sInstance = this;

        // Set the default values from the XML file if this is the first
        // execution of the app
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        recording = false;
        mService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mProvider = mService.getProvider(LocationManager.GPS_PROVIDER);
        if (mProvider == null) {
            Log.e(TAG, "Unable to get GPS_PROVIDER");
            Toast.makeText(this, getString(R.string.gps_not_supported),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mService.addGpsStatusListener(this);


        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Request use of spinner for showing indeterminate progress, to show
        // the user something is going on during long-running operations
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // If we have a large screen, show all the fragments in one layout
        if (GpsTestUtil.isLargeScreen(this)) {
            setContentView(R.layout.activity_main_large_screen);
            mIsLargeScreen = true;
        } else {
            setContentView(R.layout.activity_main);
        }

        initActionBar(savedInstanceState);

        SharedPreferences settings = Application.getPrefs();

        double tempMinTime = Double.valueOf(
                settings.getString(getString(R.string.pref_key_gps_min_time),
                        getString(R.string.pref_gps_min_time_default_sec))
        );
        minTime = (long) (tempMinTime * SECONDS_TO_MILLISECONDS);
        minDistance = Float.valueOf(
                settings.getString(getString(R.string.pref_key_gps_min_distance),
                        getString(R.string.pref_gps_min_distance_default_meters))
        );

        if (settings.getBoolean(getString(R.string.pref_key_auto_start_gps), true)) {
            gpsStart();
        }
        if(!isShadowServiceRunning(ShadowMapsService.class)) {
            final String STARTUP_EXTRA = "com.shadowmaps.example.start";
            Intent i = new Intent(this, ShadowMapsService.class);
            i.putExtra(STARTUP_EXTRA, true);
            startService(i);
            Log.v("SERVICE", "Starting ShadowService in activity onCreate");
        }
    }

    private boolean isShadowServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences settings = Application.getPrefs();
        if (GpsTestUtil.isRotationVectorSensorSupported(this)) {
            // Use the modern rotation vector sensors
            Sensor vectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mSensorManager.registerListener(this, vectorSensor, 16000); // ~60hz
        } else {
            // Use the legacy orientation sensors
            Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
            if (sensor != null) {
                mSensorManager.registerListener(this, sensor,
                        SensorManager.SENSOR_DELAY_GAME);
            }
        }

        if (!mService.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            promptEnableGps();
        }

        /**
         * Check preferences to see how they should be initialized
         */
        checkKeepScreenOn(settings);

        checkTimeAndDistance(settings);

        checkTrueNorth(settings);
    }

    @Override
    protected void onPause() {
        mSensorManager.unregisterListener(this);
        super.onPause();
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

    }

    /**
     * Ask the user if they want to enable GPS
     */
    private void promptEnableGps() {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.enable_gps_message))
                .setPositiveButton(getString(R.string.enable_gps_positive_button),
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(
                                        Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                            }
                        }
                )
                .setNegativeButton(getString(R.string.enable_gps_negative_button),
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                            }
                        }
                )
                .show();
    }

    private void checkTimeAndDistance(SharedPreferences settings) {
        double tempMinTimeDouble = Double
                .valueOf(settings.getString(getString(R.string.pref_key_gps_min_time), "1"));
        long minTimeLong = (long) (tempMinTimeDouble * SECONDS_TO_MILLISECONDS);

        if (minTime != minTimeLong ||
                minDistance != Float.valueOf(
                        settings.getString(getString(R.string.pref_key_gps_min_distance), "0"))) {
            // User changed preference values, get the new ones
            minTime = minTimeLong;
            minDistance = Float.valueOf(
                    settings.getString(getString(R.string.pref_key_gps_min_distance), "0"));
            // If the GPS is started, reset the location listener with the new values
            if (mStarted) {
                mService.requestLocationUpdates(mProvider.getName(), minTime, minDistance, this);
                Toast.makeText(this, String.format(getString(R.string.gps_set_location_listener),
                                String.valueOf(tempMinTimeDouble), String.valueOf(minDistance)),
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }


    private void checkKeepScreenOn(SharedPreferences settings) {
        if (mViewPager != null) {
            if (settings.getBoolean(getString(R.string.pref_key_keep_screen_on), true)) {
                mViewPager.setKeepScreenOn(true);
            } else {
                mViewPager.setKeepScreenOn(false);
            }
        } else {
            View v = findViewById(R.id.large_screen_layout);
            if (v != null && mIsLargeScreen) {
                if (settings.getBoolean(getString(R.string.pref_key_keep_screen_on), true)) {
                    v.setKeepScreenOn(true);
                } else {
                    v.setKeepScreenOn(false);
                }
            }
        }
    }

    private void checkTrueNorth(SharedPreferences settings) {
        mFaceTrueNorth = settings.getBoolean(getString(R.string.pref_key_true_north), true);
    }

    @Override
    protected void onDestroy() {
        mService.removeGpsStatusListener(this);
        mService.removeUpdates(this);
        super.onDestroy();
    }

    public String switch_modes(String mode) {
        String API_KEY = Application.getPrefs().getString("API_KEY", "YOUR_API_KEY");
        String id = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        Mode json_mode_object = new Mode(id, API_KEY, mode);
        try {
            String toSend = objectMapper.writeValueAsString(json_mode_object);
            RequestBody body = RequestBody.create(Application.JSON, toSend);
            Request request = new Request.Builder()
                    .url(URL_TO_POST)
                    .post(body)
                    .build();
            Response response = client.newCall(request).execute();
            Log.v("MODE", "Posting mode change request to " + URL_TO_POST);
            String mode_response = response.body().string();
            String to_ret = "error";
            if (mode_response.contains("pedestrian")) {
                to_ret = "pedestrian";
            } else if(mode_response.contains("vehicular")) {
                to_ret = "vehicular";
            }
            return to_ret;
        } catch (Exception e) {
            Log.v("MODE", "Error switching modes");
            e.printStackTrace();
            return "error";
        }
    }

    private class ChangeModeTask extends AsyncTask<String, Integer, String> {
        protected String doInBackground(String... mode) {
            String mode_str = mode[0];
            return switch_modes(mode_str);
        }

        protected void onProgressUpdate(Integer... progress) {
            Log.v("MODE", " Progress at " + progress[0]);
            return;
        }

        protected void onPostExecute(String result) {
            Log.v("MODE", "Got mode change result: " + result + " from server.");
            if (result.contains("pedestrian") || result.contains("vehicular")) {
                Toast.makeText(getApplicationContext(), "Switched mobility model to " + result + ".",
                        Toast.LENGTH_LONG).show();
            } else {
                Log.v("MODE","Could not switch mobility model.");
            }
            return;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
        getSupportMenuInflater().inflate(R.menu.gps_menu, menu);
        this.menu = menu;
        MenuItem item = menu.getItem(0);
        boolean pedestrian = Application.getPrefs().getBoolean("pedestrian", true);
        if(pedestrian) {
            item.setTitle("Mode: Pedestrian");
            item.setIcon(R.drawable.ic_directions_walk_white_24dp);
        } else {
            item.setTitle("Mode: Vehicular");
            item.setIcon(R.drawable.ic_directions_car_white_24dp);
        }
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(com.actionbarsherlock.view.Menu menu) {
        return true;
    }


    public void onLocationChanged(Location location) {
        //Log.v("GpsTestActivity", "onLocChanged");
        mLastLocation = location;

        updateGeomagneticField();

        // Reset the options menu to trigger updates to action bar menu items
        invalidateOptionsMenu();

        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onLocationChanged(location);
        }
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.v("GpsTestActivity", "onStatusChanged");

        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onStatusChanged(provider, status, extras);
        }
    }

    public void onProviderEnabled(String provider) {
        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onProviderEnabled(provider);
        }
    }

    public void onProviderDisabled(String provider) {
        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onProviderDisabled(provider);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean success;
        // Handle menu item selection
        switch (item.getItemId()) {
            case R.id.mode_switch:
                Log.i("MENU", "Mode Switch");
                Log.i("MENU", item.getTitle().toString());
                if(item.getTitle().equals("Mode: Vehicular")) {
                    item.setTitle("Mode: Pedestrian");
                    item.setIcon(R.drawable.ic_directions_walk_white_24dp);
                    Application.getPrefs().edit().putBoolean("pedestrian", true).commit();
                    new ChangeModeTask().execute("pedestrian");

                } else {
                    item.setTitle("Mode: Vehicular");
                    item.setIcon(R.drawable.ic_directions_car_white_24dp);
                    Application.getPrefs().edit().putBoolean("pedestrian", false).commit();
                    new ChangeModeTask().execute("vehicular");
                }
                return true;
            case R.id.map_circles:
                Log.i("MENU", "Circles");
                if(item.isChecked()) {
                    Toast.makeText(this, "68% Confidence Circles Disabled", Toast.LENGTH_SHORT ).show();
                    Application.getPrefs().edit().putBoolean("circles", false).commit();
                    Application.circles = false;
                    item.setChecked(false);
                } else{
                    Toast.makeText(this, "68% Confidence Circles Enabled", Toast.LENGTH_SHORT ).show();
                    Application.getPrefs().edit().putBoolean("circles", true).commit();
                    Application.circles = true;
                    item.setChecked(true);
                }
                return true;
            case R.id.menu_settings:
                startActivity(new Intent(this, Preferences.class));

            default:
                Log.v("MENU", "Something else clicked!");
                return super.onOptionsItemSelected(item);
        }
    }

    public void onGpsStatusChanged(int event) {
        //Log.v("GpsTestActivity", "onGpsStatusChanged");

        mStatus = mService.getGpsStatus(mStatus);

        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                int ttff = mStatus.getTimeToFirstFix();
                if (ttff == 0) {
                    mTtff = "";
                } else {
                    ttff = (ttff + 500) / 1000;
                    mTtff = Integer.toString(ttff) + " sec";
                }
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                // Stop progress bar after the first status information is obtained
                setSupportProgressBarIndeterminateVisibility(Boolean.FALSE);
                break;
        }

        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onGpsStatusChanged(event, mStatus);
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public void onSensorChanged(SensorEvent event) {

        double orientation = Double.NaN;
        double tilt = Double.NaN;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                // Modern rotation vector sensors
                if (!mTruncateVector) {
                    try {
                        SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                    } catch (IllegalArgumentException e) {
                        // On some Samsung devices, an exception is thrown if this vector > 4 (see #39)
                        // Truncate the array, since we can deal with only the first four values
                        Log.e(TAG, "Samsung device error? Will truncate vectors - " + e);
                        mTruncateVector = true;
                        // Do the truncation here the first time the exception occurs
                        getRotationMatrixFromTruncatedVector(event.values);
                    }
                } else {
                    // Truncate the array to avoid the exception on some devices (see #39)
                    getRotationMatrixFromTruncatedVector(event.values);
                }

                int rot = getWindowManager().getDefaultDisplay().getRotation();
                switch (rot) {
                    case Surface.ROTATION_0:
                        // No orientation change, use default coordinate system
                        SensorManager.getOrientation(mRotationMatrix, mValues);
                        // Log.d(TAG, "Rotation-0");
                        break;
                    case Surface.ROTATION_90:
                        // Log.d(TAG, "Rotation-90");
                        SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_Y,
                                SensorManager.AXIS_MINUS_X, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    case Surface.ROTATION_180:
                        // Log.d(TAG, "Rotation-180");
                        SensorManager
                                .remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_X,
                                        SensorManager.AXIS_MINUS_Y, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    case Surface.ROTATION_270:
                        // Log.d(TAG, "Rotation-270");
                        SensorManager
                                .remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_Y,
                                        SensorManager.AXIS_X, mRemappedMatrix);
                        SensorManager.getOrientation(mRemappedMatrix, mValues);
                        break;
                    default:
                        // This shouldn't happen - assume default orientation
                        SensorManager.getOrientation(mRotationMatrix, mValues);
                        // Log.d(TAG, "Rotation-Unknown");
                        break;
                }
                orientation = Math.toDegrees(mValues[0]);  // azimuth
                tilt = Math.toDegrees(mValues[1]);
                break;
            case Sensor.TYPE_ORIENTATION:
                // Legacy orientation sensors
                orientation = event.values[0];
                break;
            default:
                // A sensor we're not using, so return
                return;
        }

        // Correct for true north, if preference is set
        if (mFaceTrueNorth && mGeomagneticField != null) {
            orientation += mGeomagneticField.getDeclination();
        }

        for (GpsTestListener listener : mGpsTestListeners) {
            listener.onOrientationChanged(orientation, tilt);
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private void getRotationMatrixFromTruncatedVector(float[] vector) {
        System.arraycopy(vector, 0, mTruncatedRotationVector, 0, 4);
        SensorManager.getRotationMatrixFromVector(mRotationMatrix, mTruncatedRotationVector);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateGeomagneticField() {
        mGeomagneticField = new GeomagneticField((float) mLastLocation.getLatitude(),
                (float) mLastLocation.getLongitude(), (float) mLastLocation.getAltitude(),
                mLastLocation.getTime());
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        if (mViewPager != null) {
            mViewPager.setCurrentItem(tab.getPosition());
        }
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
    }

    private void initActionBar(Bundle savedInstanceState) {
        // Set up the action bar.
        final com.actionbarsherlock.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(com.actionbarsherlock.app.ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setTitle(getApplicationContext().getText(R.string.app_name));

        // If we don't have a large screen, set up the tabs using the ViewPager
        if (!mIsLargeScreen) {
            //  page adapter contains all the fragment registrations
            mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

            // Set up the ViewPager with the sections adapter.
            mViewPager = (ViewPagerMapBevelScroll) findViewById(R.id.pager);
            mViewPager.setAdapter(mSectionsPagerAdapter);
            mViewPager.setOffscreenPageLimit(2);

            // When swiping between different sections, select the corresponding
            // tab. We can also use ActionBar.Tab#select() to do this if we have a
            // reference to the Tab.
            mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    actionBar.setSelectedNavigationItem(position);
                }
            });
            // For each of the sections in the app, add a tab to the action bar.
            for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
                // Create a tab with text corresponding to the page title defined by
                // the adapter. Also specify this Activity object, which implements
                // the TabListener interface, as the listener for when this tab is
                // selected.
                actionBar.addTab(actionBar.newTab()
                        .setText(mSectionsPagerAdapter.getPageTitle(i))
                        .setTabListener(this));
            }
        }
    }

    interface GpsTestListener extends LocationListener {

        public void gpsStart();

        public void gpsStop();

        public void onGpsStatusChanged(int event, GpsStatus status);

        public void onOrientationChanged(double x, double y);

    }

    /**
     * A {@link FragmentStatePagerAdapter} that returns a fragment corresponding to
     * one of the primary sections of the app.
     */
    public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

        public static final int NUMBER_OF_TABS = 2; // Used to set up TabListener

        // Constants for the different fragments that will be displayed in tabs, in numeric order
        public static final int GPS_SKY_FRAGMENT = 0;
        public static final int GPS_MAP_FRAGMENT = 1;

        // Maintain handle to Fragments to avoid recreating them if one already
        // exists
        Fragment gpsMap, gpsSky;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case GPS_MAP_FRAGMENT:
                    if (gpsMap == null) {
                        gpsMap = new GpsMapFragment();
                    }
                    return gpsMap;
                case GPS_SKY_FRAGMENT:
                    if (gpsSky == null) {
                        gpsSky = new GpsSkyFragment();
                    }
                    return gpsSky;
            }
            return null; // This should never happen
        }

        @Override
        public int getCount() {
            return NUMBER_OF_TABS;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case GPS_MAP_FRAGMENT:
                    return getString(R.string.gps_map_tab);
                case GPS_SKY_FRAGMENT:
                    return getString(R.string.gps_sky_tab);
            }
            return null; // This should never happen
        }
    }
}
