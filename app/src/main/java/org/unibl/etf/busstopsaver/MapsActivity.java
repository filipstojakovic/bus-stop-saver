package org.unibl.etf.busstopsaver;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener
{
    public static final String BUS_STOPS_SER = "busstops.ser";
    public static final String BUS_STOPS_TXT = "busstops_json.txt";

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

        MobileAds.initialize(this);
        AdView adView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        textView = findViewById(R.id.textView);
        findViewById(R.id.save_btn).setOnClickListener(l -> onSaveBtnClicked());
        findViewById(R.id.save_file_btn).setOnClickListener(l -> saveJsonToFile());
        editText = findViewById(R.id.edit_text);
        editText.setOnEditorActionListener((v, actionId, event) ->
        {
            editText.clearFocus();
            Util.hideKeyboard(v);
            return true;
        });
        editText.clearFocus();

        findViewById(R.id.delete_all).setOnClickListener(l -> deleteAll());
        findViewById(R.id.delete_last).setOnClickListener(l -> deleteLast());
        findViewById(R.id.share_fab).setOnClickListener(l -> shareConrent(this));

        busStopMarkers = new ArrayList<>();

        //TODO: implement method to check if there was an update
        //TODO: if(needUpdate)
        //        update("https://github.com/filipstojakovic/bus-stop-saver/releases/download/0.0.0.1/app-debug.apk");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    public void shareConrent(Context context)
    {
        List<BusStop> busStopList = getBusStopList();
        if (busStopList == null || busStopList.isEmpty())
            return;

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        String busStopJson = gson.toJson(busStopList);
        shareIntent(busStopJson, context);
    }

    public void shareIntent(String content, Context context)
    {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, content);
        context.startActivity(Intent.createChooser(shareIntent, "Share using:"));
    }

    //need WRITE_EXTERNAL_STORAGE permission
    public void update(String apkurl)
    {
        String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator;
        String fileName = "AppName.apk";
        destination += fileName;
        final Uri uri = Uri.parse("file://" + destination);

        //Delete update file if exists
        File file = new File(destination);
        if (file.exists())
            //file.delete() - test this, I think sometimes it doesnt work
            file.delete();

        //set downloadmanager
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkurl));
        request.setDescription("Description");
        request.setTitle("Title");

        //set destination
        request.setDestinationUri(uri);

        // get download service and enqueue file
        final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        final long downloadId = manager.enqueue(request);

        //set BroadcastReceiver to install app when .apk is downloaded
        BroadcastReceiver onComplete = new BroadcastReceiver()
        {
            public void onReceive(Context ctxt, Intent intent)
            {
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                install.setDataAndType(uri,
                        manager.getMimeTypeForDownloadedFile(downloadId));
                startActivity(install);

                unregisterReceiver(this);
                finish();
                //TODO: maybe delete downloaded .apk after finish
            }
        };
        //register receiver for when .apk download is compete
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }


    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        map = googleMap;
        currentPositionIcon = Util.bitmapDescriptorFromVector(this, R.drawable.ic_baseline_location_on_24);
        deserializeMarkerState();

        LatLng trgBL = new LatLng(44.7691468567798, 17.18835644423962);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(trgBL, 16.0f));
        map.setOnMarkerClickListener(marker ->
        {
            marker.showInfoWindow();
            return false;
        });
        map.setOnMapClickListener(latLng -> Util.hideKeyboard(this));
        map.setOnMapLongClickListener(latLng ->
        {
            String name = editText.getText().toString();
            addMarker(name, latLng);
            editText.getText().clear();
        });
    }

    private void addMarker(String title, LatLng latLng)
    {
        if (map != null)
        {
            if (title == null)
                title = "";
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

    private void serializeMarkerState()
    {
        if (busStopMarkers != null)
            try (FileOutputStream fileOut = openFileOutput(BUS_STOPS_SER, Context.MODE_PRIVATE);
                 ObjectOutputStream out = new ObjectOutputStream(fileOut))
            {
                List<BusStop> busStopList = getBusStopList();
                out.writeObject(busStopList);

            } catch (Exception ex)
            {
                ex.printStackTrace();
            }
    }

    private void deserializeMarkerState()
    {

        try (FileInputStream fileOut = openFileInput(BUS_STOPS_SER);
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
            ex.printStackTrace();
        }
    }

    private void saveJsonToFile()
    {
        if (busStopMarkers != null)
        {
            try (PrintWriter pw = new PrintWriter(openFileOutput(BUS_STOPS_TXT, Context.MODE_PRIVATE)))
            {
                List<BusStop> busStopList = getBusStopList();
                Gson gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .create();
                gson.toJson(busStopList, pw);

            } catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    }


    private List<BusStop> getBusStopList()
    {
        return busStopMarkers.stream()
                .map(x -> new BusStop(x.getTitle(), x.getPosition()))
                .collect(Collectors.toList());
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
        serializeMarkerState();
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