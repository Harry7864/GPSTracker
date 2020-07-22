package com.example.gpstracker.utility;

import android.app.Application;


public class gpstracker extends Application {

    private static gpstracker mInstance;

    public static synchronized gpstracker getInstance() {
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }

    public void setConnectivityListener(ConnectivityReceiver.ConnectivityReceiverListener listener) {
        ConnectivityReceiver.connectivityReceiverListener = listener;
    }

}
