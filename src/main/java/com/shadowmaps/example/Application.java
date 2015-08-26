/*
 * Copyright (C) 2013 Sean J. Barbeau
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

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.squareup.okhttp.MediaType;

/**
 * Holds application-wide state
 *
 * @author Sean J. Barbeau
 */
public class Application extends android.app.Application {

    private static Application mApp;
    public static boolean circles = true;
    public static boolean pins = true;
    public static boolean following = true;
    public static float currentZoomLevel = 17;

    private SharedPreferences mPrefs;

    public static Application get() {
        return mApp;
    }

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    public static SharedPreferences getPrefs() {
        return get().mPrefs;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mApp = this;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        circles = mPrefs.getBoolean("circles", true);

    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        mApp = null;
    }
}
