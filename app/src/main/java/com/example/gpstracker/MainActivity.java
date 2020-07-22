package com.example.gpstracker;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.gpstracker.network.RestCall;
import com.example.gpstracker.network.RestClient;
import com.example.gpstracker.networkresponce.CommonResponce;
import com.example.gpstracker.restarter.RestartServiceBroadcastReceiver;
import com.example.gpstracker.utility.ConnectivityListner;
import com.example.gpstracker.utility.InternetConnection;
import com.example.gpstracker.utility.PreferenceManager;
import com.example.gpstracker.utility.Tools;
import com.example.gpstracker.utility.VariableBag;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.patloew.rxlocation.RxLocation;


import java.text.DateFormat;
import java.util.Date;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
public class MainActivity extends AppCompatActivity implements MainView {

    private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance();
    private static final int REQUEST_PERMISSIONS = 100;

    private TextView lastUpdate;
    private TextView locationText;
    private TextView addressText;
    private TextView tvTitle;

    String area = null, locality;

    RestCall restCall;
    Tools tools;
    Button btnIn, btnOut;
    ImageView ivLogout;

    PreferenceManager preferenceManager;

    private RxLocation rxLocation;
    private MainPresenter presenter;

    private double latitude, longitude;
    private String fullAddress = null;
    private boolean boolean_permission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this);
        tools = new Tools(this);

        lastUpdate = findViewById(R.id.tv_last_update);
        locationText = findViewById(R.id.tv_current_location);
        addressText = findViewById(R.id.tv_current_address);
        tvTitle = findViewById(R.id.tvTitle);


        btnIn = findViewById(R.id.btnIn);
        btnOut = findViewById(R.id.btnOut);
        ivLogout = findViewById(R.id.ivLogout);

        fn_permission();
        rxLocation = new RxLocation(this);
        rxLocation.setDefaultTimeout(25, TimeUnit.SECONDS);
        presenter = new MainPresenter(rxLocation);
        presenter.attachView(this);

        if (preferenceManager.getKeyValueString("btnStatus").equals("true")) {
            btnOut.setVisibility(View.VISIBLE);
            btnIn.setVisibility(View.GONE);
        } else if (preferenceManager.getKeyValueString("btnStatus").equals("false")) {
            btnOut.setVisibility(View.GONE);
            btnIn.setVisibility(View.VISIBLE);
        }
        tvTitle.setText("Welcome - " + preferenceManager.getKeyValueString("fullname"));
        ivLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Are you sure you want to logout?")
                        .setCancelable(false)
                        .setPositiveButton(Html.fromHtml("<font color='#000000'>Yes</font>"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                preferenceManager.setKeyValueString("UserStatus", "USERLOGOUT");
                                preferenceManager.clearPrefrence();
                                preferenceManager.setLoginSessionFalse();
                                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                startActivity(intent);
                                finish();
                            }
                        })
                        .setNegativeButton(Html.fromHtml("<font color='#000000'>No</font>"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();

            }
        });
        btnOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Are  you want to out?")
                        .setCancelable(false)
                        .setPositiveButton(Html.fromHtml("<font color='#000000'>Yes</font>"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                btnOut.setVisibility(View.GONE);
                                btnIn.setVisibility(View.VISIBLE);
                                preferenceManager.setKeyValueString("UserStatus", "USEROUT");
                                preferenceManager.setKeyValueString("btnStatus", "false");
                                Toast.makeText(MainActivity.this, "You are successfully out..", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(Html.fromHtml("<font color='#000000'>No</font>"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();

            }
        });
        btnIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Do you want to IN?")
                        .setCancelable(false)
                        .setPositiveButton(Html.fromHtml("<font color='#000000'>Yes</font>"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                btnOut.setVisibility(View.VISIBLE);
                                btnIn.setVisibility(View.GONE);
                                preferenceManager.setKeyValueString("btnStatus", "true");
                                preferenceManager.setKeyValueString("UserStatus", "USERIN");
                                Toast.makeText(MainActivity.this, "You are successfully In..", Toast.LENGTH_SHORT).show();
                               if(boolean_permission) {
                                   UploadData();
                               }else
                               {
                                   Toast.makeText(getApplicationContext(), "Please enable the gps", Toast.LENGTH_SHORT).show();
                               }
                            }
                        })
                        .setNegativeButton(Html.fromHtml("<font color='#000000'>No</font>"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();

            }
        });
    }
    private void fn_permission() {
        if ((ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {

            if ((ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION))) {


            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION

                        },
                        REQUEST_PERMISSIONS);

            }
        } else {
            boolean_permission = true;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_PERMISSIONS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    boolean_permission = true;

                } else {
                    Toast.makeText(getApplicationContext(), "Please allow the permission", Toast.LENGTH_LONG).show();

                }
            }
        }
    }
    private void UploadData() {

        restCall = RestClient.createService(RestCall.class, VariableBag.BASE_URL);
        restCall.getLatLong(
                "user_location",
                preferenceManager.getRegistredUSerID(),
                fullAddress, area, locality, String.valueOf(latitude), String.valueOf(longitude))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<CommonResponce>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d("Error  Upload data", e.getMessage());
                        Toast.makeText(MainActivity.this, e.getMessage() + "", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(CommonResponce commonResponce) {
                        Toast.makeText(MainActivity.this, commonResponce.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPlayServicesAvailable();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RestartServiceBroadcastReceiver.scheduleJob(getApplicationContext());
        } else {
            ProcessMainClass bck = new ProcessMainClass();
            bck.launchService(getApplicationContext());
        }
    }
    private void checkPlayServicesAvailable() {
        final GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int status = apiAvailability.isGooglePlayServicesAvailable(this);

        if (status != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(status)) {
                apiAvailability.getErrorDialog(this, status, 1).show();
            } else {
                Snackbar.make(lastUpdate, "Google Play Services unavailable. This app will not work", Snackbar.LENGTH_INDEFINITE).show();
            }
        }
    }
    @Override
    public void onLocationUpdate(Location location) {
        lastUpdate.setText(DATE_FORMAT.format(new Date()));
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        locationText.setText(location.getLatitude() + ", " + location.getLongitude());
    }

    @Override
    public void onAddressUpdate(Address address) {
        addressText.setText(getAddressText(address));
        fullAddress = getAddressText(address);
        area = address.getSubLocality();
        locality = address.getLocality();
    }


    @Override
    public void onLocationSettingsUnsuccessful() {
        Snackbar.make(lastUpdate, "Location settings requirements not satisfied. Showing last known location if available.", Snackbar.LENGTH_INDEFINITE)
                .setAction("Retry", view -> presenter.startLocationRefresh())
                .show();
    }

    private String getAddressText(Address address) {
        String addressText = "";
        final int maxAddressLineIndex = address.getMaxAddressLineIndex();

        for (int i = 0; i <= maxAddressLineIndex; i++) {
            addressText += address.getAddressLine(i);
            if (i != maxAddressLineIndex) {
                addressText += "\n";
            }
        }

        return addressText;
    }
}
