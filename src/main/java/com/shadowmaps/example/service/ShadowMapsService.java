/*
* Copyright (C) 2008-2013 The Android Open Source Project,
* Sean J. Barbeau
* Daniel P. Iland, ShadowMaps Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.shadowmaps.example.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.shadowmaps.example.data.Cell;
import com.shadowmaps.example.data.DeviceInfo;
import com.shadowmaps.example.data.OutgoingInfo;
import com.shadowmaps.example.data.Satellite;
import com.shadowmaps.example.data.SensorData;
import com.shadowmaps.example.data.WiFi;
import com.shadowmaps.example.data.ServerResponse;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.codehaus.jackson.map.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * ShadowMaps stand-alone service implements listeners for GPS and Sensor data.
 * Also requires data model classes: DeviceInfo, Message, OutgoingInfo, Satellite
 */
public class ShadowMapsService extends IntentService implements LocationListener, SensorEventListener {
    /**
     *  Settings!!
     *  Periodic mode requests GPS updates every X ms (default [1000])
     *  Passive mode does not request GPS updates.
     *  Passive mode receives GPS updates ONLY when other apps are using GPS
     *  Change gps_mode and timeBetweenPeriodicUpdates to your liking.
     *  Defaults to 1000 ms = 1 second interval
     */

    // You can enter your API Key here for testing, if you are sending data directly from your app
    // to ShadowMaps. In production, this should be added to the JSON by your servers instead.
    final String API_KEY = "YOUR_API_KEY";

    private enum UpdateInterval { PERIODIC, PASSIVE }
    // Periodic requests GPS Updates every timeBetweenPeriodicUpdates milliseconds
    private UpdateInterval gps_mode = UpdateInterval.PERIODIC;
    // Passive does not request GPS updates, but receives them when other apps use GPS
    //private UpdateInterval gps_mode = UpdateInterval.PASSIVE;
    int timeBetweenPeriodicUpdates = 1000;


    // Sensor managers and receivers
    private SensorManager mSensorManager;
    private Sensor mPressure;
    private Sensor mLux;
    private Sensor mTemp;

    // Last known sensor readings
    private float lastPressure;
    private float lastLux;
    private float lastTemp;

    // Receivers for Wi-Fi and cellular data
    private TelephonyManager telephonyManager;
    private BroadcastReceiver wifiReceiver;
    private WifiManager wifiManager;
    private PhoneStateListener phoneStateListener;

    // Data regarding Wi-Fi and cellular transmitters
    private int lastCellLocation;
    private int lastCellArea;
    private int lastNetworkType;
    private int lastSignalStrength;
    private long lastSignalStrengthTime;
    private int lastSatCount;
    private List<WiFi> wiFis;
    private List<Cell> cells;

    // Location data
    private LocationManager locMgr;
    private static final String locationProvider = LocationManager.GPS_PROVIDER;

    // Required API fields. id can be a persistent per-user ID or a unique ID per session.
    private String id;
    private final String model = Build.MODEL;


    // The Key GPS Status Listener that receives GPS Signal to Noise ratios
    ShadowStatusListener gpsListener;

    // HTTP + JSON
    // ShadowMaps Public API Endpoint
    public final String URL_TO_POST = "https://api.shadowmaps.com/v1/update/";
    // Content Type
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    // Using Square's excellent OkHttp
    private OkHttpClient client = new OkHttpClient();
    // Using the Jackson JSON mapper
    private ObjectMapper objectMapper = new ObjectMapper();
    // ConnectivityManager used to determine if Internet access is available.
    ConnectivityManager cm;


    // In Logcat, filter by ShadowMapsService to see logs from this service
    private final String TAG = "ShadowMapsService";

    @Override
    public void onLocationChanged(Location location) {
        // This logging is very frequent and gets annoying
        //Log.v(TAG, String.format("Got new Location: %s,%s,%s", location.getLatitude(), location.getLongitude(), location.getAccuracy()));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.v(TAG, "GPS Status Changed from " + provider);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.v(TAG, "GPS enabled from " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.v(TAG, "GPS disabled from " + provider);

    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "OnCreate");
        if(!isShadowServiceRunning(ShadowMapsService.class)) {
            final String STARTUP_EXTRA = "com.shadowmaps.example.start";
            Intent i = new Intent(this, ShadowMapsService.class);
            i.putExtra(STARTUP_EXTRA, true);
            startService(i);
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

    // Call to terminate the service.
    protected BroadcastReceiver stopServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
        }
    };

    // Called when Service is destroyed. Unregister all receivers.
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "OnDestroy");
        this.unregisterReceiver(wifiReceiver);
        // The GPS Status listener is what provides Satellite SNR and Az/El information!
        locMgr.removeGpsStatusListener(gpsListener);
        locMgr.removeUpdates(this);
        mSensorManager.unregisterListener(this);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

    // Construct IntentService with name
    public ShadowMapsService() {
        super("ShadowMapsService");
        Log.v(TAG, "Starting ShadowMapsService");
    }

    public ShadowMapsService(String name) {
        super(name);
        Log.v(TAG, "Starting: " + name);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        try {
            // The ShadowMaps Server requires a unique ID per device, user, or session.
            id = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);

            // Start listening for GPS Status updates!
            gpsListener = new ShadowStatusListener();

            // Initialize listeners for sensors.
            mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            mPressure = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            mLux = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            mTemp = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
            mSensorManager.registerListener(this, mPressure, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mLux , SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mTemp , SensorManager.SENSOR_DELAY_NORMAL);

            // Initialize Location Listener
            locMgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (gps_mode.equals(UpdateInterval.PERIODIC)) {
                requestPeriodicLocationUpdates(timeBetweenPeriodicUpdates);
            } else if (gps_mode.equals(UpdateInterval.PASSIVE)) {
                requestPassiveLocationUpdates();
            }

            // We model cellular base stations and Wi-Fi access points as "Transmitters"
            wiFis = new LinkedList<WiFi>();
            cells = new LinkedList<Cell>();
            // Request cellular location and signal strength data from Telephony Service
            telephonyManager = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
            phoneStateListener = new ShadowPhoneStateListener();
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

            // Request the results of any Wi-Fi scans be delivered to our wifiReceiver
            wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wifiReceiver = new BroadcastReceiver(){
                @Override
                public void onReceive(Context c, Intent intent) {
                    processWifiScan();
                }
            };
            registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

            // ConnectivityManager enables checks for internet connectivity before using network.
            cm =(ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return START_NOT_STICKY;
    }

    // Request periodic GPS updates
    private void requestPeriodicLocationUpdates(int update_interval) {
        Log.v(TAG, String.format("Starting ShadowMaps with Periodic Updates with interval of %sms", update_interval));
        String locationProvider = LocationManager.GPS_PROVIDER;
        // Request GPS location updates
        locMgr.requestLocationUpdates(locationProvider, update_interval, 0, this);
        // Essential component here: A GPS Status Listener to provide satellite SNRs!
        locMgr.addGpsStatusListener(gpsListener);
    }

    // Receive all location updates requested by other apps/services
    public void requestPassiveLocationUpdates() {
        Log.v(TAG, "Starting ShadowMaps with passive (only when otherwise in use) GPS updates");
        String locationProvider = LocationManager.GPS_PROVIDER;
        // minimum 1 second spacing, no distance limits
        locMgr.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000, 0, this);
        locMgr.addGpsStatusListener(gpsListener);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // We don't care, but must override
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void processWifiScan() {
        List<ScanResult> results = wifiManager.getScanResults();
        if (results != null) {
            final int size = results.size();
            if (size != 0) {
                for (ScanResult result : results) {
                    long age;
                    // Signal strength
                    int level = result.level;
                    // Note that we use BSSIDs (AP MAC Address), not human readable SSIDs.
                    String bssid = result.BSSID;
                    int frequency = result.frequency;
                    // We would like to know exactly when this scan took place
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        age = SystemClock.uptimeMillis() - result.timestamp;
                    } else {
                        age = 0;
                    }
                    wiFis.add(new WiFi(bssid, frequency, level, age));
                }
            }
        }
        return;
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        int sensor = event.sensor.getType();
        // This is very chatty, Sensors 5/6 update frequently.
        //Log.v(TAG, "Sensor reading from " + sensor);
        if(sensor == Sensor.TYPE_PRESSURE) {
            lastPressure = event.values[0];
        } else if(sensor == Sensor.TYPE_LIGHT) {
            lastLux = event.values[0];
            // Depricated TYPE_TEMPERATURE returns battery temp on some models, useful to us.
        } else if(sensor == Sensor.TYPE_TEMPERATURE) {
            lastTemp = event.values[0];
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.v("Intent", "Intent handled");
    }

    private boolean isConnected() {
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
        return isConnected;
    }

    private void uploadToShadowMaps(final Location location, final GpsStatus status) {
        if (location != null && status != null) {
            Log.v("Upload", "Uploading at " + System.currentTimeMillis());
            // Device info
            long time = System.currentTimeMillis();
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            double alt = location.getAltitude();
            double acc = location.getAccuracy();
            float speed = location.getSpeed();
            float bearing = location.getBearing();
            String provider = location.getProvider();

            // Grab the most recent sensor readings
            float pressure = lastPressure;
            float lux = lastLux;
            float temp = lastTemp;

            // GNSS Satellite Info
            int mEphemerisMask = 0;
            int mAlmanacMask = 0;
            int mUsedInFixMask = 0;

            // Get/create iterator of visible satellites
            Iterator<GpsSatellite> satellites = status.getSatellites().iterator();
            // Try to intelligently size our ArrayList based on previous observation
            List<Satellite> sats = new ArrayList<Satellite>(lastSatCount);
            lastSatCount = 0;
            while (satellites.hasNext()) {
                GpsSatellite satellite = satellites.next();
                int prn = satellite.getPrn();
                int prnBit = (1 << (prn - 1));
                double snr = satellite.getSnr();
                double el = satellite.getElevation();
                double az = satellite.getAzimuth();
                boolean eph;
                boolean alm;
                boolean use;

                if (satellite.hasEphemeris()) {
                    mEphemerisMask |= prnBit;
                    eph = true;
                } else {
                    eph = false;
                }
                if (satellite.hasAlmanac()) {
                    mAlmanacMask |= prnBit;
                    alm = true;
                } else {
                    alm = false;
                }
                if (satellite.usedInFix()) {
                    mUsedInFixMask |= prnBit;
                    use = true;
                } else {
                    use = false;
                }
                lastSatCount++;
                Satellite s = new Satellite(prn, snr, el, az, eph, alm, use);
                sats.add(s);
            }
            Log.v(TAG, String.format("Done logging GPS info from %s satellites!", lastSatCount));

            // Create other data sources:
            // Location, direction, and device information
            DeviceInfo device = new DeviceInfo(time, lat, lon, alt, acc, speed, bearing, provider, id, model, API_KEY);
            // Sensor readings
            SensorData sensors = new SensorData(pressure, lux, temp);
            try {
                if (isConnected()) {
                    // Dump cellular and Wi-Fi data collected since last upload into this run.
                    List<Cell> cellsToPost = new LinkedList<Cell>();
                    for(Cell t: cells) {
                        cellsToPost.add(t);
                    }
                    cells.clear();
                    List<WiFi> wiFitoPost = new LinkedList<WiFi>();
                    for(WiFi t: wiFis) {
                        wiFitoPost.add(t);
                    }
                    wiFis.clear();
                    // HTTPS POST with device info, satellite info, sensor, wifi and cellular data.
                    Log.v(TAG, "Posting");
                    postUpdate(new OutgoingInfo(device, sats, sensors, wiFitoPost, cellsToPost));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.v(TAG, e.toString());
            }
        }
    }


    // Upload a single measurement to ShadowMaps
    private void postUpdate(final OutgoingInfo packet) {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    String toSend = objectMapper.writeValueAsString(packet);
                    RequestBody body = RequestBody.create(JSON, toSend);
                    Request request = new Request.Builder()
                            .url(URL_TO_POST)
                            .post(body)
                            .build();
                    Response response = client.newCall(request).execute();
                    String response_str = response.body().string();
                    Log.v(TAG, String.format("Received ShadowMapsUpdate: %s", response_str));
                    if (response.code() == 200) {
                        Log.v("HTTP", "200!");
                                                try {
                            Intent intent = new Intent();
                            long time_processed = System.currentTimeMillis();
                            ServerResponse message = objectMapper.readValue(response_str, ServerResponse.class);
                            Log.v("Server JSON", "Got Message");
                            double lon = message.getDeviceInfo().getLon();
                            double lat = message.getDeviceInfo().getLat();
                            double acc = message.getDeviceInfo().getAcc();
                            if(message.getRouteInfo() != null) {
                                double route_lon = message.getRouteInfo().getLongitude();
                                double route_lat = message.getRouteInfo().getLatitude();
                                double route_acc = message.getRouteInfo().getAccuracyHoriz();
                                intent.putExtra("clamp_lat", route_lat);
                                intent.putExtra("clamp_lon", route_lon);
                                intent.putExtra("clamp_radius", route_acc);
                                intent.putExtra("street", message.getRouteInfo().getStreetName());
                            }
                            Log.v("ShadowMaps", "Location Update received.");
                            intent.setAction("shadowmaps.location.update");
                            intent.putExtra("lat", lat);
                            intent.putExtra("lon", lon);
                            intent.putExtra("radius", acc);
                            intent.putExtra("orig_lat", packet.getDev().getLat());
                            intent.putExtra("orig_lon", packet.getDev().getLon());
                            intent.putExtra("orig_acc", packet.getDev().getAcc());

                            Log.v("ShadowMaps", String.format(
                                    "Lat/Lon: %s,%s. Radius:%s",
                                    lat, lon, acc));
                            sendBroadcast(intent);
                            return null;
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.v("Exception", e.getMessage());
                        }
                    } else {
                        Log.v("HTTP", "" + response.code());

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }

    public class ShadowStatusListener implements GpsStatus.Listener {
        // The most essential component of the ShadowMaps service!
        @Override
        public void onGpsStatusChanged(int event) {
            if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                try {
                    GpsStatus status = locMgr.getGpsStatus(null);
                    Location last = locMgr.getLastKnownLocation(locationProvider);
                    if(last != null) {
                        uploadToShadowMaps(last, status);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.v(TAG, "Error collecting GPS status information: " + e.toString());
                }
            }
        }
    }

    public class ShadowPhoneStateListener extends PhoneStateListener {

        public ShadowPhoneStateListener() {}

        @SuppressLint("NewApi")
        @Override
        public void onCellLocationChanged(CellLocation location) {
            long ts = System.currentTimeMillis();
            if (location instanceof GsmCellLocation) {
                GsmCellLocation gcLoc = (GsmCellLocation) location;
                lastCellLocation = gcLoc.getCid() & 0xffff;
                lastCellArea = gcLoc.getLac();
            } else if (location instanceof CdmaCellLocation) {
                CdmaCellLocation ccLoc = (CdmaCellLocation) location;
                lastCellLocation = ccLoc.getBaseStationId();
                lastCellArea = ccLoc.getSystemId();
                Log.d(TAG, "Cell ID: " + lastCellLocation);
            }
            Log.v(TAG, "Adding CellID: " + lastCellLocation + " LAC: " + lastCellArea + "ts:" + ts);
            cells.add(new Cell(String.valueOf(lastCellLocation), lastCellArea, 0, ts));
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType)
        {
            lastNetworkType = networkType;
            Log.d(TAG, "Network Type: "+networkType);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength)
        {
            lastSignalStrengthTime = System.currentTimeMillis();
            if(signalStrength.isGsm()) {
                lastSignalStrength = (signalStrength.getGsmSignalStrength()*2)-113;
            } else {
                lastSignalStrength = signalStrength.getCdmaDbm();
            }
            Log.v(TAG, "Adding Cellular RSSI: " + lastSignalStrength + " CellID: " + lastCellLocation + " LAC: " + lastCellArea  + "ts:" + lastSignalStrengthTime);
            cells.add(new Cell(String.valueOf(lastCellLocation), lastCellArea, lastSignalStrength, lastSignalStrengthTime));
        }
    }
}