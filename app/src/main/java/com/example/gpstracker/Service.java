

package com.example.gpstracker;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.gpstracker.network.RestCall;
import com.example.gpstracker.network.RestClient;
import com.example.gpstracker.networkresponce.CommonResponce;
import com.example.gpstracker.utilities.Notification;
import com.example.gpstracker.utility.Permissions;
import com.example.gpstracker.utility.PreferenceManager;
import com.example.gpstracker.utility.VariableBag;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;


public class Service extends android.app.Service {
    protected static final int NOTIFICATION_ID = 1337;
    private static String TAG = "Service";
    private static Service mCurrentService;
    private int counter = 0;
    private RestCall restCall;
    Double latitude = 0.0, longitude = 0.0;
    String fullAddress, area, locality;
    String duration;
    PreferenceManager preferenceManager;
    private String address;
    private long durations=0;
    public Service() {
        super();
    }


    @Override
    public void onCreate() {
        super.onCreate();
        preferenceManager = new PreferenceManager(this);
        duration=preferenceManager.getKeyValueString("duration");
        if(duration!=null)
        {
            durations= Long.parseLong(duration) * 60000;
        }
        else
        {
            durations=10000;
        }
        requestLocationUpdates();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            restartForeground();
        }
        mCurrentService = this;
    }

    private void requestLocationUpdates() {
        LocationRequest request = new LocationRequest();
        request.setInterval(10000);
        request.setFastestInterval(5000);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        FusedLocationProviderClient client = getFusedLocationProviderClient(this);

        int permissions = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissions == PackageManager.PERMISSION_GRANTED) {
            client.requestLocationUpdates(request, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();

                        Log.d("Location Service", "location update $location");

                    }
                }
            }, null);
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        android.util.Log.d(TAG, "restarting Service !!");
        counter = 0;

        // it has been killed by Android and now it is restarted. We must make sure to have reinitialised everything
        if (intent == null) {
            ProcessMainClass bck = new ProcessMainClass();
            bck.launchService(this);
        }

        // make sure you call the startForeground on onStartCommand because otherwise
        // when we hide the notification on onScreen it will nto restart in Android 6 and 7
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            restartForeground();
        }

        startTimer();

        // return start sticky so if it is killed by android, it will be restarted with Intent null
        return START_STICKY;
    }


    @androidx.annotation.Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    /**
     * it starts the process in foreground. Normally this is done when screen goes off
     * THIS IS REQUIRED IN ANDROID 8 :
     * "The system allows apps to call Context.startForegroundService()
     * even while the app is in the background.
     * However, the app must call that service's startForeground() method within five seconds
     * after the service is created."
     */
    public void restartForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.util.Log.i(TAG, "restarting foreground");
            try {
                Notification notification = new Notification();
                startForeground(NOTIFICATION_ID, notification.setNotification(this, "Service notification", "This is the GPS tracker notification", R.drawable.ic_sleep));
                android.util.Log.i(TAG, "restarting foreground successful");
                startTimer();
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error in notification " + e.getMessage());
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        android.util.Log.i(TAG, "onDestroy called");
        // restart the never ending service
        Intent broadcastIntent = new Intent(Globals.RESTART_INTENT);
        sendBroadcast(broadcastIntent);
        stoptimertask();
    }


    /**
     * this is called when the process is killed by Android
     *
     * @param rootIntent
     */

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        android.util.Log.i(TAG, "onTaskRemoved called");
        // restart the never ending service
        Intent broadcastIntent = new Intent(Globals.RESTART_INTENT);
        sendBroadcast(broadcastIntent);
        // do not call stoptimertask because on some phones it is called asynchronously
        // after you swipe out the app and therefore sometimes
        // it will stop the timer after it was restarted
        // stoptimertask();
    }


    /**
     * static to avoid multiple timers to be created when the service is called several times
     */
    private static Timer timer;
    private static TimerTask timerTask;
    long oldTime = 0;

    public void startTimer() {
        android.util.Log.i(TAG, "Starting timer");

        //set a new Timer - if one is already running, cancel it to avoid two running at the same time
        stoptimertask();
        timer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask();

        android.util.Log.i(TAG, "Scheduling...");
        //schedule the timer, to wake up every 1 second
        timer.schedule(timerTask, 1000, durations); //
    }

    /**
     * it sets the timer to print the counter every x seconds
     */
    public void initializeTimerTask() {
        android.util.Log.i(TAG, "initialising TimerTask");
        timerTask = new TimerTask() {
            public void run() {
                android.util.Log.i("in timer", "in timer ++++  " + (counter++));
                if (latitude != 0.0 && longitude != 0.0) {
                    Log.d(
                            "Location::",
                            latitude.toString() + ":::" + longitude.toString() + "Count" + counter);
                    address = getAddress(latitude, longitude);
                    uploadData();
                }
            }
        };
    }

    private void uploadData() {

        restCall = RestClient.createService(RestCall.class, VariableBag.BASE_URL);
        if (preferenceManager.getKeyValueString("UserStatus").equalsIgnoreCase("USERIN")) {
            restCall.getLatLong(
                    "user_location",
                    preferenceManager.getRegistredUSerID(),
                    address, area, locality, String.valueOf(latitude), String.valueOf(longitude))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Subscriber<CommonResponce>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {
                            Log.d("Error  Upload data", e.getMessage());
                            Toast.makeText(Service.this, e.getMessage() + "", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onNext(CommonResponce commonResponce) {
                            Toast.makeText(Service.this, commonResponce.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
        else
        {
            Log.d("Eror in restcall","Restcall Null");
        }
    }

    /**
     * not needed
     */
    public void stoptimertask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public static Service getmCurrentService() {
        return mCurrentService;
    }

    public static void setmCurrentService(Service mCurrentService) {
        Service.mCurrentService = mCurrentService;
    }

    private String getAddress(Double latitude, Double longitude) {
        StringBuilder result = new StringBuilder();
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            area = addresses.get(0).getSubAdminArea();
            locality = addresses.get(0).getLocality();
            if (addresses.size() > 0) {
                Address address = addresses.get(0);
                result.append(addresses.get(0).getFeatureName() + ", " + addresses.get(0).getLocality() + ", " + addresses.get(0).getAdminArea() + ", " + addresses.get(0).getCountryName());
//                result.append(address.countryName)
            }
        } catch (IOException e) {
            Log.e("tag", e.getMessage());
        }
        return result.toString();
    }

}
