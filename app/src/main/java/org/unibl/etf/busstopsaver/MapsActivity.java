package org.unibl.etf.busstopsaver;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.EditText;
import android.widget.TextView;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.Display;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.loveplusplus.update.UpdateChecker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener
{
    public static final String BUS_STOPS_TXT = "busstops.txt";

    private TextView textView;
    private EditText editText;

    private GoogleMap map;
    private BitmapDescriptor currentPositionIcon;

    private LatLng currentLocation;
    private Marker currentPositionMarker;

    private List<Marker> busStopMarkers;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        textView = findViewById(R.id.textView);
        findViewById(R.id.save_btn).setOnClickListener(l -> onSaveBtnClicked());
        findViewById(R.id.refresh_btn).setOnClickListener(l -> initLocationListener());
        editText = findViewById(R.id.edit_text);
        editText.clearFocus();

        initAppUpdate(this);

        findViewById(R.id.delete_all).setOnClickListener(l -> deleteAll());
        findViewById(R.id.delete_last).setOnClickListener(l -> deleteLast());

        busStopMarkers = new ArrayList<>();

        currentPositionIcon = Util.bitmapDescriptorFromVector(this, R.drawable.ic_baseline_location_on_24);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void initAppUpdate(Activity activity)
    {

        //        UpdateChecker.checkForDialog(MapsActivity.this);

        AppUpdater appUpdater = new AppUpdater(this)
                .setDisplay(Display.DIALOG)
                .setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON("https://raw.githubusercontent.com/filipstojakovic/bus-stop-saver/master/updateinfo.json");
        appUpdater.start();
    }

    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        map = googleMap;
        restoreMarkerState();

        LatLng trgBL = new LatLng(44.7691468567798, 17.18835644423962);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(trgBL, 16.0f));
        map.setOnMarkerClickListener(marker ->
        {
            marker.showInfoWindow();
            return false;
        });
        map.setOnMapClickListener(latLng -> Util.hideKeyboard(this));
    }

    private void addMarker(String title, LatLng latLng)
    {
        if (map != null)
        {
            busStopMarkers.add(map.addMarker(new MarkerOptions().position(latLng).title(title)));
        }
    }

    private void onSaveBtnClicked()
    {
        if (currentLocation == null)
            return;

        String name = editText.getText().toString();
        addMarker(name, currentLocation);
        editText.setText("");
        Util.hideKeyboard(this);
    }

    private void deleteLast()
    {
        if (!busStopMarkers.isEmpty())
        {
            Marker current = busStopMarkers.get(busStopMarkers.size() - 1);
            busStopMarkers.remove(current);
            current.remove();
        }
    }

    private void deleteAll()
    {
        map.clear();
        busStopMarkers = new ArrayList<>();
        setCurrentLocationOnMap();

    }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        setCurrentLocationOnMap();

        textView.setText(location.getLatitude() + "\n" + location.getLongitude());
    }

    private void setCurrentLocationOnMap()
    {
        if (map != null && currentLocation != null)
        {
            if (currentPositionMarker != null)
                currentPositionMarker.remove();
            else
                map.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));

            currentPositionMarker = map.addMarker(new MarkerOptions().position(currentLocation));
            currentPositionMarker.setIcon(currentPositionIcon);
        }
    }

    private void initLocationListener()
    {
        LocationManager locationManager = null;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
        {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            {
                Util.buildAlertMessageNoGps(this);
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, this);
        } else
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    1);
        }

    }

    private void restoreMarkerState()
    {

        try (FileInputStream fileOut = openFileInput(BUS_STOPS_TXT);
             //                    FileInputStream fileOut = new FileInputStream(file);
             ObjectInputStream out = new ObjectInputStream(fileOut))
        {
            List<BusStop> busStopList = (ArrayList) out.readObject();

            if (busStopList != null && !busStopList.isEmpty())
                busStopMarkers = busStopList.stream()
                        .map(x -> map.addMarker(new MarkerOptions()
                                .position(new LatLng(x.getLat(), x.getLng()))
                                .title(x.getName())))
                        .collect(Collectors.toList());

        } catch (Exception ex)
        {
            //            ex.printStackTrace();
        }
    }

    private void saveMarkerState()
    {
        if (busStopMarkers != null)
            try (FileOutputStream fileOut = openFileOutput(BUS_STOPS_TXT, Context.MODE_PRIVATE);
                 //                    FileOutputStream fileOut = new FileOutputStream(file);
                 ObjectOutputStream out = new ObjectOutputStream(fileOut))
            {
                List<BusStop> busStopList = busStopMarkers.stream()
                        .map(x -> new BusStop(x.getTitle(), x.getPosition()))
                        .collect(Collectors.toList());
                out.writeObject(busStopList);

            } catch (Exception ex)
            {
                ex.printStackTrace();
            }

    }

    @Override
    protected void onResume()
    {
        initLocationListener();
        super.onResume();
    }

    @Override
    protected void onStop()
    {
        saveMarkerState();
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
    }

    @Override
    public void onProviderEnabled(@NonNull String provider)
    {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider)
    {
    }
}