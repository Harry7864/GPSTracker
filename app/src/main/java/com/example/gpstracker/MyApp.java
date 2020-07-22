package com.example.gpstracker;

import android.app.Application;

import com.example.gpstracker.networkmanager.DroidNet;


public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DroidNet.init(this);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        DroidNet.getInstance().removeAllInternetConnectivityChangeListeners();
    }
}
