/*
 * Copyright (C) 2008-2013 The Android Open Source Project,
 * Sean J. Barbeau
 * Danny Iland
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

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockMapFragment;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.shadowmaps.shadowgps.R;

import java.util.ArrayList;

public class GpsMapFragment extends SherlockMapFragment
        implements View.OnClickListener, LocationSource,
        GoogleMap.OnCameraChangeListener {

    private CameraPosition cp = null;
    private final static String TAG = "GpsStatusActivity";

    private GoogleMap mMap;

    private LatLng mLatLng;

    private boolean last_circle_setting;
    private boolean last_pins_setting;


    // Pin drop control
    private long mLastMapTouchTime = 0;
    private String previous_street;

    private ArrayList<LatLng> points = new ArrayList<LatLng>();
    private PolylineOptions polyLineOptions = new PolylineOptions();

    private ArrayList<LatLng> orig_points = new ArrayList<LatLng>();
    private PolylineOptions orig_polyLineOptions = new PolylineOptions();

    private Pair<Circle, Marker> original = null;
    private Pair<Circle, Marker> corrected = null;
    private Pair<Circle, Marker> clamped = null;

    @Override
    public void onCameraChange(CameraPosition position)
    {
        Log.v("Camera", "Camera Changed, zoom level " + position.zoom);
        if(Application.currentZoomLevel != position.zoom)
        {
            Application.currentZoomLevel = position.zoom;
        }
    }

    GoogleMap.OnMyLocationButtonClickListener onMyLocationButtonClick = new GoogleMap.OnMyLocationButtonClickListener() {
        public boolean onMyLocationButtonClick() {
            Application.following = !Application.following;
            return true;
        }
    };


    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Extract data included in the Intent
            double lat = intent.getDoubleExtra("lat", 0);
            double lon = intent.getDoubleExtra("lon", 0);
            boolean following = Application.getPrefs().getBoolean("Following", true);
            if(Application.following) {
                CameraUpdate center =
                        CameraUpdateFactory.newLatLng(new LatLng(lat, lon));
                mMap.moveCamera(center);
            }

            double radius = intent.getDoubleExtra("radius", 0);
            double orig_lat = intent.getDoubleExtra("orig_lat",0);
            double orig_lon = intent.getDoubleExtra("orig_lon",0);
            double orig_acc = intent.getDoubleExtra("orig_acc",0);
            Log.v("PINS", lat + "," + lon  + "," + radius  + "Original:" + orig_lat + "," + orig_lon + "," + orig_acc);
            if(Application.circles != last_circle_setting || Application.pins != last_pins_setting)  {
                corrected = null;
                original = null;
                clamped = null;
                mMap.clear();
            }

            last_circle_setting = Application.circles;
            last_pins_setting = Application.pins;
            if(last_pins_setting) {
                corrected = addMarkerAtPositionWithCircle(corrected, new LatLng(lat, lon), radius, "ShadowMaps Estimate", BitmapDescriptorFactory.HUE_CYAN, 0xA00000FF);
                original = addMarkerAtPositionWithCircle(original, new LatLng(orig_lat, orig_lon), orig_acc, "Original Estimate", BitmapDescriptorFactory.HUE_RED, 0xA0FF0000);
            }
            try {
                double clamp_lat = intent.getDoubleExtra("clamp_lat", 0);
                double clamp_lon = intent.getDoubleExtra("clamp_lon", 0);
                double clamp_acc = intent.getDoubleExtra("clamp_acc", 0);
                String street = intent.getStringExtra("street");
                if(previous_street == null || !previous_street.equals(street)) {
                    if(street != null) {
                        Toast.makeText(getActivity(), "Now on " + street, Toast.LENGTH_SHORT).show();
                    }
                }
                if(street != null) {
                    if(last_pins_setting) {
                        clamped = addMarkerAtPositionWithCircle(clamped, new LatLng(clamp_lat, clamp_lon), clamp_acc, "On " + street, BitmapDescriptorFactory.HUE_GREEN, 0xA000FF00);
                    }
                    previous_street = street;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    public void updateOrigLine(LatLng point) {
        if(orig_points.size() > 60) {
            orig_points.clear();
        }
        orig_points.add(point);
        orig_polyLineOptions.addAll(orig_points);
        orig_polyLineOptions.width(2);
        orig_polyLineOptions.color(Color.BLUE);
        mMap.addPolyline(orig_polyLineOptions);
    }


    public void updateSMLine(LatLng point) {
        if(points.size() > 60) {
            points.clear();
        }
        points.add(point);
        polyLineOptions.addAll(points);
        polyLineOptions.width(2);
        polyLineOptions.color(Color.RED);
        mMap.addPolyline(polyLineOptions);
    }



    public Pair<Circle, Marker> addMarkerAtPositionWithCircle(Pair<Circle, Marker> toUpdate, LatLng position, double radius, String title, float color, int circleFill) {
        if (radius < 0) {
            radius = 1;
        }

        // Five cases:
        // 1. toUpdate is null. Initialize the marker and optionally the circle..
        // 2. There is a circle and a marker. Update both.
        // 3. There is a circle and a marker. Remove the circle.
        // 4. There is a marker and no circle. Add a circle;
        // 5. There is a marker and no circle. Update only the marker.

        // Cases 3 and 4 are handled by making toUpdate null, giving us case 1.
        // We implement cases 1 2 and 5.

        // Case 1
        // First update: Create marker and optionally circle and add to map
        if (toUpdate == null) {
            Circle addedCircle = null;
            Marker addedMarker = null;
            // Marker
            MarkerOptions marker = new MarkerOptions();
            marker.position(position).snippet("Accuracy: " + radius + " meters").title(title).icon(BitmapDescriptorFactory
                    .defaultMarker(color));
            addedMarker = mMap.addMarker(marker);
            // Circle
            if (Application.circles) {
                CircleOptions circle = new CircleOptions();
                circle.center(position).radius(radius).fillColor(circleFill).strokeColor(circleFill);
                addedCircle = mMap.addCircle(circle);
            }
            return new Pair<Circle, Marker>(addedCircle, addedMarker);
        } else {
            // Update marker if it exists. It should always exist.
            if (toUpdate.second != null) {
                toUpdate.second.setPosition(position);
                toUpdate.second.setSnippet("Accuracy: " + radius + " meters");
            }
            // Update circle. If it exists, it is enabled
            if (toUpdate.first != null) {
                toUpdate.first.setCenter(position);
                toUpdate.first.setRadius(radius);
            }
            return toUpdate;
        }
    }

    /**
     * Clamps a value b[etween the given positive min and max.  If abs(value) is less than
     * min, then min is returned.  If abs(value) is greater than max, then max is returned.
     * If abs(value) is between min and max, then abs(value) is returned.
     *
     * @param min   minimum allowed value
     * @param value value to be evaluated
     * @param max   maximum allowed value
     * @return clamped value between the min and max
     */
    private static double clamp(double min, double value, double max) {
        value = Math.abs(value);
        if (value >= min && value <= max) {
            return value;
        } else {
            return (value < min ? value : max);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);
        mMap = getMap();
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        mMap.setOnMyLocationButtonClickListener(onMyLocationButtonClick);
        mMap.setOnCameraChangeListener(this);

        CameraUpdate zoom=
                CameraUpdateFactory.zoomTo(Application.currentZoomLevel);
        mMap.moveCamera(zoom);

        if (isGoogleMapsInstalled()) {
            if (mMap != null) {
                //Show the location on the map
                mMap.setMyLocationEnabled(true);
                //Set location source
                mMap.setLocationSource(this);
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(getString(R.string.please_install_google_maps));
            builder.setCancelable(false);
            builder.setPositiveButton(getString(R.string.install),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.google.android.apps.maps"));
                            startActivity(intent);

                            getActivity().finish();
                        }
                    }
            );
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        return v;
    }

    @Override
    public void onResume() {
        getActivity().registerReceiver(mMessageReceiver,
                new IntentFilter("shadowmaps.location.update"));

        // Draw bounding box for service.

        double lon_min = -122.46419754274331;
        double lon_max = -122.38283106334671;
        double lat_max = 37.811379814950136;
        double lat_min = 37.746798761406474;


        if (mMap != null && cp != null) {
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cp));
            cp = null;
        }
        SharedPreferences settings = Application.getPrefs();
        if (mMap != null && settings != null) {
            if (mMap.getMapType() != Integer.valueOf(
                    settings.getString(getString(R.string.pref_key_map_type),
                            String.valueOf(GoogleMap.MAP_TYPE_NORMAL))
            )) {
                mMap.setMapType(Integer.valueOf(
                        settings.getString(getString(R.string.pref_key_map_type),
                                String.valueOf(GoogleMap.MAP_TYPE_NORMAL))
                ));
            }
        }

        super.onResume();

    }


    public void onClick(View v) {
    }

    @Override
    public void onPause() {
        // Unregister since the activity is not visible
        getActivity().unregisterReceiver(mMessageReceiver);
        cp = mMap.getCameraPosition();

        super.onPause();
    }


    /**
     * Maps V2 Location updates
     */
    @Override
    public void activate(OnLocationChangedListener listener) {

    }

    /**
     * Maps V2 Location updates
     */
    @Override
    public void deactivate() {

    }

    /**
     * Returns true if Google Maps is installed, false if it is not
     */
    @SuppressWarnings("unused")
    public boolean isGoogleMapsInstalled() {
        try {
            ApplicationInfo info = getActivity().getPackageManager()
                    .getApplicationInfo("com.google.android.apps.maps", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

}