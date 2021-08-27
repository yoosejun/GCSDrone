package com.viasofts.mygcs;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.Polyline;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationSource;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import java.util.ArrayList;
import java.util.List;

import static com.o3dr.services.android.lib.drone.attribute.AttributeType.BATTERY;
import static com.o3dr.services.android.lib.drone.attribute.AttributeType.GUIDED_STATE;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback, LocationListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static Context MapsContext;


    private Drone drone;
    Marker droneMarker = new Marker();
    Marker marker = new Marker();
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private FusedLocationSource locationSource;


    private final Handler handler = new Handler();

    int count = 0;
    private GuideMode mGuideMode;

    private LocationSource.OnLocationChangedListener listener;

    private Spinner modeSelector;

    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    Handler mainHandler;
    private NaverMap naverMap;
    private TextView textView;

    private LocationManager locationManager;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    //현재 기체상태 관리하는 변수들
    private int mRecentSatCount = 0;
    private double mRecentYAW = 0;
    private LatLng mRecentDroneCoord;
    private LatLng mRecentCoord;
    double takeoffAltitude = 5.5;
    PathOverlay path = new PathOverlay();
    PolylineOverlay polyline = new PolylineOverlay();
    List<LatLng> coords = new ArrayList<>();


    private RecyclerView mRecyclerView;
    private MyRecyclerAdapter mRecyclerAdapter;
    private ArrayList<MessageItem> mMessageItems;
    Dialog activity_arm;

    private boolean mCheckGuideMode = false;
    private LatLng recentLatLng;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);


        mRecyclerView = (RecyclerView) findViewById(R.id.recylerview);

        /* initiate adapter */
        mRecyclerAdapter = new MyRecyclerAdapter();

        /* initiate recyclerview */

        mRecyclerView.setAdapter(mRecyclerAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));

        /*
        mMessageItems = new ArrayList<>();
        int i = 10;
        switch(i){
            case 1 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"모터가동"));
            case 2 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"이륙완료"));
            case 3 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"기체가 목표지점으로 이동합니다"));
            case 4 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"기체가 목표지점으로 도착했습니다"));
            case 5 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"착륙완료"));
            case 6 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"미션 시작"));
            case 7 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"미션 실패"));
            case 8 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"뭐냐"));
            case 9 :
                mMessageItems.add(new MessageItem(R.drawable.star,i+"번",i+"뭐해"));
            default:
                break;
        }
         */
        mMessageItems = new ArrayList<>();
        for (int i = 1; i < 10; i++) {
            if (i == 1)
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "모터가동"));
            else if (i == 2)
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "이륙완료"));
            else if (i == 3)
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "기체가 목표지점으로 이동합니다"));
            else if (i == 4)
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "기체가 목표지점에 도착했습니다"));
            else if (i == 5)
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "착륙완료"));
            else if (i == 6)
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "미션 시작"));
            else if (i == 7)
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "미션 실패"));
            else if (i == 8)
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "뭐냐"));
            else
                mMessageItems.add(new MessageItem(R.drawable.star, i + "", "뭐해"));
        }
        mRecyclerAdapter.setMessageList(mMessageItems);

        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        this.modeSelector = findViewById(R.id.modeSelector);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((TextView) modeSelector.getChildAt(0)).setTextColor(Color.WHITE);
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        mGuideMode = new GuideMode(this);

//        //시동
//        VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
//            @Override
//            public void onError(int executionError) {
//                alertUser("Unable to arm vehicle.");
//            }
//
//            @Override
//            public void onTimeout() {
//                alertUser("Arming operation timed out.");
//            }
//        });
//
//        //이륙
//        ControlApi.getApi(this.drone).takeoff(takeoffAltitude, new AbstractCommandListener() {
//
//            @Override
//            public void onSuccess() {
//                alertUser("Taking off...");
//            }
//
//            @Override
//            public void onError(int i) {
//                alertUser("Unable to take off.");
//            }
//
//            @Override
//            public void onTimeout() {
//                alertUser("Unable to take off.");
//            }
//        });
//
//        //착륙
//        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
//            @Override
//            public void onError(int executionError) {
//                alertUser("Unable to land the vehicle.");
//            }
//
//            @Override
//            public void onTimeout() {
//                alertUser("Unable to land the vehicle.");
//            }
//        });

    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if(requestCode == LOCATION_PERMISSION_REQUEST_CODE){
//            if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
//                naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
//            }
//        }
//    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) { // 권한 거부됨
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            } else {
                naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
            }
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }


    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
//            ControlApi.getApi(this.drone).takeoff(takeoffAltitude, new AbstractCommandListener() {
//
//                @Override
//                public void onSuccess() {
//                    alertUser("Taking off...");
//                }
//
//                @Override
//                public void onError(int i) {
//                    alertUser("Unable to take off.");
//                }
//
//                @Override
//                public void onTimeout() {
//                    alertUser("Unable to take off.");
//                }
//            });
            takeoff_dialog(view);
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed

//            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
//                @Override
//                public void onError(int executionError) {
//                    alertUser("Unable to arm vehicle.");
//                }
//
//                @Override
//                public void onTimeout() {
//                    alertUser("Arming operation timed out.");
//                }
//            });
            arm_dialog(view);
        }
    }

//    protected void onActivityResult(int requestCode, int resultCode, Intent data){
//        super.onActivityResult(requestCode,resultCode, data);
//
//        if(requestCode==1){
//            if(requestCode==RESULT_OK){
//                VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
//                    @Override
//                    public void onError(int executionError) {
//                        alertUser("Unable to arm vehicle.");
//                    }
//
//                    @Override
//                    public void onTimeout() {
//                        alertUser("Arming operation timed out.");
//                    }
//                });
//            } else{
//                Toast.makeText(MainActivity.this,"ARM Canceled",Toast.LENGTH_SHORT).show();
//            }
//        }
//    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.button);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
        if (hasPermission()) {
            if (locationManager != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, (LocationListener) this);
            }
        } else {
            ActivityCompat.requestPermissions(
                    this, PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE);
        }


    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
        if (locationManager != null) {
            locationManager.removeUpdates((LocationListener) this);
        }
    }

    //모니터링
    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
                mRecentYAW = attitude.getYaw();
                updateYAW();
                break;

            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();

                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.GPS_COUNT:
                Gps gps = this.drone.getAttribute(AttributeType.GPS);
                mRecentSatCount = gps.getSatellitesCount();
                updateSatellite();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateVoltage();
                break;

            case AttributeEvent.HOME_UPDATED:
//                updateDistanceFromHome();
                break;

            case AttributeEvent.GPS_POSITION:
                Gps gps_position = this.drone.getAttribute(AttributeType.GPS);
                mRecentDroneCoord = latLongToLatLng(gps_position.getPosition());
                naverMap.moveCamera(CameraUpdate.scrollTo(latLongToLatLng(gps_position.getPosition())));

                coords.add(mRecentDroneCoord);
                polyline.setCoords(coords);
                polyline.setColor(Color.argb(80, 255, 0, 0));
                //TODO 드론 현재 위치를 마커로 표시하는 메소드 호출
                updateDroneLocation();
                updateGuideMode();


                if (mRecentDroneCoord == marker.getPosition()) {
                    VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {
                        @Override
                        public void onError(int executionError) {
                            alertUser("Unable to land the vehicle.");
                        }

                        @Override
                        public void onTimeout() {
                            alertUser("Unable to land the vehicle.");
                        }
                    });
                }

                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null) {
            alertUser("Unable to retrieve the solo state.");
        } else {
            alertUser("Solo state is up to date.");
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    //드론과 연결 및 해제
    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {

            int selectedConnectionType = ConnectionType.TYPE_UDP;

            ConnectionParameter connectionParams = selectedConnectionType == ConnectionType.TYPE_USB
                    ? ConnectionParameter.newUsbConnection(null)
                    : ConnectionParameter.newUdpConnection(null);

            this.drone.connect(connectionParams);
        }

    }

    public void onLocationChanged(Location location) {
        if (naverMap == null || location == null) {
            return;
        }
        LocationOverlay locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);

        locationOverlay.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
        locationOverlay.setBearing(location.getBearing());

//        locationOverlay.setIcon(OverlayImage.fromResource(R.drawable.dronemarker_icon));
//        locationOverlay.setIconHeight(40);
//        locationOverlay.setIconWidth(40);
//        locationOverlay.setAnchor(new PointF(0.5f, 1));
//        locationOverlay.setSubIcon(
//                OverlayImage.fromResource(R.drawable.drone_sub_icon));
//        locationOverlay.setSubIconWidth(80);
//        locationOverlay.setSubIconHeight(40);
//        locationOverlay.setSubAnchor(new PointF(0.5f, 1));


//        naverMap.moveCamera(CameraUpdate.scrollTo(new LatLng(location.getLatitude(), location.getLongitude())));
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    public void onProviderEnabled(String provider) {
    }

    public void onProviderDisabled(String provider) {
    }

    private boolean hasPermission() {
        return PermissionChecker.checkSelfPermission(this, PERMISSIONS[0])
                == PermissionChecker.PERMISSION_GRANTED
                && PermissionChecker.checkSelfPermission(this, PERMISSIONS[1])
                == PermissionChecker.PERMISSION_GRANTED;
    }


    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {

    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    //기체 속도 가져오기
    protected void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    //기체 고도 가져오기
    protected void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateYAW() {
        TextView yawValueTextView = (TextView) findViewById(R.id.yawValueTextView);
        yawValueTextView.setText(String.format("%3.1f", mRecentYAW) + "deg");


    }

    protected void updateSatellite() {
        TextView satelliteValueTextView = (TextView) findViewById(R.id.satelliteValueTextView);
        satelliteValueTextView.setText(String.valueOf(mRecentSatCount));
    }

    protected void updateVoltage() {
        TextView voltageValueTextView = (TextView) findViewById(R.id.voltageValueTextView);
        Battery droneBattery = this.drone.getAttribute(BATTERY);
        voltageValueTextView.setText(String.format("%3.1f", droneBattery.getBatteryVoltage()) + "V");


    }

    //홈포지션 거리 가져오기
//    protected void updateDistanceFromHome() {
//        TextView distanceTextView = (TextView) findViewById(R.id.distanceValueTextView);
//        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
//        double vehicleAltitude = droneAltitude.getAltitude();
//        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
//        LatLong vehiclePosition = droneGps.getPosition();
//
//        double distanceFromHome = 0;
//
//        if (droneGps.isValid()) {
//            LatLongAlt vehicle3DPosition = new LatLongAlt(vehiclePosition.getLatitude(), vehiclePosition.getLongitude(), vehicleAltitude);
//            Home droneHome = this.drone.getAttribute(AttributeType.HOME);
//            distanceFromHome = distanceBetweenPoints(droneHome.getCoordinate(), vehicle3DPosition);
//        } else {
//            distanceFromHome = 0;
//        }
//
//        distanceTextView.setText(String.format("%3.1f", distanceFromHome) + "m");
//    }
//
//    protected double distanceBetweenPoints(LatLongAlt pointA, LatLongAlt pointB) {
//        if (pointA == null || pointB == null) {
//            return 0;
//        }
//        double dx = pointA.getLatitude() - pointB.getLatitude();
//        double dy = pointA.getLongitude() - pointB.getLongitude();
//        double dz = pointA.getAltitude() - pointB.getAltitude();
//        return Math.sqrt(dx * dx + dy * dy + dz * dz);
//    }

    // UI Events
    // ==========================================================

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText("Disconnect");
        } else {
            connectButton.setText("Connect");
        }
    }

    //비행모드 변경
    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    // Helper methods
    // ==========================================================

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }


    protected void updateGuideMode() {

        if (GuideMode.CheckGoal(drone, recentLatLng)) {
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {

                @Override
                public void onSuccess() {
                    super.onSuccess();
                    alertUser("checkgoal complete.");
                    marker.setMap(null);
                }

                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        }

    }

    @UiThread
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        naverMap.setOnMapLongClickListener((point, coord) -> {
            mCheckGuideMode = true;
            marker.setPosition(new LatLng(coord.latitude, coord.longitude));
            marker.setMap(naverMap);
            marker.setWidth(80);
            marker.setHeight(100);
            marker.setIcon(OverlayImage.fromResource(R.drawable.marker_guide));
            mGuideMode.dialogSimple(drone, point);

        });

        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setCompassEnabled(false);
        uiSettings.setScaleBarEnabled(false);
        uiSettings.setZoomControlEnabled(false);
        uiSettings.setLocationButtonEnabled(false);

        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        ActivityCompat.requestPermissions(this, PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE);

        textView = (TextView) findViewById(R.id.altitudeSetView);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.altitudeSetView:
                        //textview clicked
                        final Button altitudeBtnUp = (Button) findViewById(R.id.altitudeUp);
                        final Button altitudeBtnDown = (Button) findViewById(R.id.altitudeDown);

                        textView.setText(takeoffAltitude + "m");
                        altitudeBtnUp.setVisibility(View.VISIBLE);
                        altitudeBtnDown.setVisibility(View.VISIBLE);
                        altitudeBtnUp.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                double count = 0.5;
                                takeoffAltitude += count;
                                if (takeoffAltitude >= 10) {
                                    takeoffAltitude = 10;
                                }
                                textView.setText(takeoffAltitude + "m");
                            }
                        });
                        altitudeBtnDown.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                double count = 0.5;
                                takeoffAltitude -= count;
                                if (takeoffAltitude <= 3) {
                                    takeoffAltitude = 3;
                                }
                                textView.setText(takeoffAltitude + "m");
                            }
                        });
                        textView.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                Button altitudeBtnUp = (Button) findViewById(R.id.altitudeUp);
                                Button altitudeBtnDown = (Button) findViewById(R.id.altitudeDown);
                                altitudeBtnUp.setVisibility(View.INVISIBLE);
                                altitudeBtnDown.setVisibility(View.INVISIBLE);
                                textView.setText(takeoffAltitude + "m" + "\n이륙고도");
//                                textView.setText(altitude[0] + "m"+ "\n이륙고도");

                                updateAltitude();
                                return true;
                            }
                        });
                        break;
                    //case...
                }
            }
        });

        Button btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClear();
            }
        });

    }

    private LatLng latLongToLatLng(LatLong latLong) {
        return new LatLng(latLong.getLatitude(), latLong.getLongitude());
    }


    private LatLng needToLatLng(LatLng latLng) {
        return new LatLng(latLng.latitude, latLng.longitude);
    }

    protected void updateDroneLocation() {
        //TODO 드론 현재 위치를 마커로 표시하는 메소드 구현
        droneMarker.setPosition(mRecentDroneCoord);
        droneMarker.setMap(naverMap);
        droneMarker.setWidth(50);
        droneMarker.setHeight(50);
        droneMarker.setIcon(OverlayImage.fromResource(R.drawable.drone));
        droneMarker.setAnchor(new PointF(0.5f, 1));
        droneMarker.setAngle((float) mRecentYAW);
    }

    public void arm_dialog(View v) {
        View dialogView = getLayoutInflater().inflate(R.layout.activity_arm, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
        builder.setView(dialogView);

        final AlertDialog alertDialog = builder.create();
        alertDialog.show();

        Button ok_btn = dialogView.findViewById(R.id.ok_btn);
        ok_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Toast.makeText(getApplicationContext(), "OK 버튼을 눌렀습니다.", Toast.LENGTH_LONG).show();
                VehicleApi.getApi(drone).arm(true, false, new SimpleCommandListener() {
                    @Override
                    public void onError(int executionError) {
                        alertUser("Unable to arm vehicle.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("Arming operation timed out.");
                    }
                });
                alertDialog.dismiss();
            }
        });

        Button cancle_btn = dialogView.findViewById(R.id.cancel_btn);
        cancle_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertUser("arm cancelled.");
                alertDialog.dismiss();
            }
        });
    }

    public void takeoff_dialog(View v) {
        View dialogView = getLayoutInflater().inflate(R.layout.activity_takeoff, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
        builder.setView(dialogView);

        final AlertDialog alertDialog = builder.create();
        alertDialog.show();

        Button ok_btn = dialogView.findViewById(R.id.ok_btn);
        ok_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Toast.makeText(getApplicationContext(), "OK 버튼을 눌렀습니다.", Toast.LENGTH_LONG).show();
                ControlApi.getApi(drone).takeoff(takeoffAltitude, new AbstractCommandListener() {

                    @Override
                    public void onSuccess() {
                        alertUser("Taking off...");
                    }

                    @Override
                    public void onError(int i) {
                        alertUser("Unable to take off.");
                    }

                    @Override
                    public void onTimeout() {
                        alertUser("Unable to take off.");
                    }
                });

                alertDialog.dismiss();
            }
        });

        Button cancle_btn = dialogView.findViewById(R.id.cancel_btn);
        cancle_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertUser("take-off cancelled.");
                alertDialog.dismiss();
            }
        });
    }

    private void onMapView() {
        count++;
        Button btnBasicButton = findViewById(R.id.btnBasicMap);
        Button btnSatelliteButton = findViewById(R.id.btnSatelliteMap);
        Button btnContourButton = findViewById(R.id.btnTerrainMap);

        if (count % 2 == 1) {
            btnBasicButton.setVisibility(View.VISIBLE);
            btnSatelliteButton.setVisibility(View.VISIBLE);
            btnContourButton.setVisibility(View.VISIBLE);
        } else if (count % 2 == 0) {
            btnBasicButton.setVisibility(View.INVISIBLE);
            btnSatelliteButton.setVisibility(View.INVISIBLE);
            btnContourButton.setVisibility(View.INVISIBLE);
        }
    }

    public void onMapSelect(View view) {
        Button onbtnMap = findViewById(R.id.btnMap);
        onMapView();
    }

    //Basic Map
    public void onBasicMapSelected(View view) {
        Button btnMap = findViewById(R.id.btnMap);

        Button btnBasicMap = findViewById(R.id.mbtnBasicMap);
        Button btnSatelliteMap = findViewById(R.id.btnSatelliteMap);
        Button btnTerrainMap = findViewById(R.id.btnTerrainMap);

        naverMap.setMapType(NaverMap.MapType.Basic);

        btnBasicMap.setVisibility(View.INVISIBLE);
        btnSatelliteMap.setVisibility(View.INVISIBLE);
        btnTerrainMap.setVisibility(View.INVISIBLE);
        btnMap.setVisibility(View.INVISIBLE);

        onMapView();
    }

    //Terrain Map
    public void onTerrainMapSelected(View view) {
        Button btnMap = findViewById(R.id.btnMap);

        Button btnTerrainMap = findViewById(R.id.btnTerrainMap);
        Button btnBasicMap = findViewById(R.id.mbtnBasicMap);
        Button mbtnTerrainMap = findViewById(R.id.mbtnTerrainMap);
        Button btnSatelliteMap = findViewById(R.id.btnSatelliteMap);

        naverMap.setMapType(NaverMap.MapType.Terrain);

        mbtnTerrainMap.setVisibility(View.VISIBLE);
        btnTerrainMap.setVisibility(View.INVISIBLE);
        btnBasicMap.setVisibility(View.INVISIBLE);
        btnSatelliteMap.setVisibility(View.INVISIBLE);
        btnMap.setVisibility(View.INVISIBLE);

        onMapView();
    }

    //Satellite Map
    public void onSatelliteMapSelected(View view) {
        Button btnMap = findViewById(R.id.btnMap);

        Button btnSatelliteMap = findViewById(R.id.btnSatelliteMap);
        Button btnBasicMap = findViewById(R.id.btnBasicMap);
        Button btnTerrainMap = findViewById(R.id.btnTerrainMap);
        Button mbtnSatelliteMap = findViewById(R.id.mbtnSatelliteMap);

        naverMap.setMapType(NaverMap.MapType.Satellite);

        mbtnSatelliteMap.setVisibility(View.VISIBLE);
        btnBasicMap.setVisibility(View.INVISIBLE);
        btnSatelliteMap.setVisibility(View.INVISIBLE);
        btnTerrainMap.setVisibility(View.INVISIBLE);
        btnMap.setVisibility(View.INVISIBLE);

        onMapView();
    }

    public void onmTerrainMapSelected(View view) {
        onMapView();
    }

    public void onmSatelliteMapSelected(View view) {
        onMapView();
    }

    public void onmBasicMapSelected(View view) {
        onMapView();
    }


    public void onbtnMapLock(View view) {
        count++;

        Button btnMapLock = (Button) findViewById(R.id.btnMapLock);
        Button btnMapMove = (Button) findViewById(R.id.mbtnMapMove);

        if (count % 2 == 1) {
            btnMapLock.setVisibility(View.VISIBLE);
            btnMapMove.setVisibility(View.VISIBLE);
        } else if (count % 2 == 0) {
            btnMapLock.setVisibility(View.INVISIBLE);
            btnMapMove.setVisibility(View.INVISIBLE);
        }
    }

    public void onMapMove(View view) {
        Gps gps_position = this.drone.getAttribute(AttributeType.GPS);
        naverMap.moveCamera(CameraUpdate.scrollTo(latLongToLatLng(gps_position.getPosition())).cancelCallback(() -> {
            Toast.makeText(this, "맵 이동", Toast.LENGTH_SHORT).show();
        }));
    }

    public void onMapLock(View view) {
        Gps gps_position = this.drone.getAttribute(AttributeType.GPS);
        naverMap.moveCamera(CameraUpdate.scrollTo(latLongToLatLng(gps_position.getPosition())));
    }

    public void onLayerOn(View view) {
        Button btnLayerOn = findViewById(R.id.btnLayerOn);
        Button btnLayerOff = findViewById(R.id.btnLayerOff);
        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
        btnLayerOff.setVisibility(View.VISIBLE);
        btnLayerOn.setVisibility(View.INVISIBLE);
    }

    public void onLayerOff(View view) {
        Button btnLayerOn = findViewById(R.id.btnLayerOn);
        Button btnLayerOff = findViewById(R.id.btnLayerOff);
        naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
        btnLayerOff.setVisibility(View.INVISIBLE);
        btnLayerOn.setVisibility(View.VISIBLE);
    }

    public void onClear() {
        marker.setMap(null);
        droneMarker.setMap(null);
        polyline.setMap(null);
        takeoffAltitude = 5.5;
        updateAltitude();
    }

    public void onbtndistance(View view) {
        count++;

        Button onbtnABdistanceplus = (Button) findViewById(R.id.btnABdistanceP);
        Button onbtnABdistanceminus = (Button) findViewById(R.id.btnABdistanceM);

        if (count % 2 == 1) {
            onbtnABdistanceplus.setVisibility(View.VISIBLE);
            onbtnABdistanceminus.setVisibility(View.VISIBLE);
        } else if (count % 2 == 0) {
            onbtnABdistanceplus.setVisibility(View.INVISIBLE);
            onbtnABdistanceminus.setVisibility(View.INVISIBLE);
        }
    }

    public void onbtnflightwidth(View view) {
        count++;

        Button onbtnflightwidthplus = (Button) findViewById(R.id.btnflightwidthP);
        Button onbtnflightwidthminus = (Button) findViewById(R.id.btnflightwidthM);

        if (count % 2 == 1) {
            onbtnflightwidthplus.setVisibility(View.VISIBLE);
            onbtnflightwidthminus.setVisibility(View.VISIBLE);
        } else if (count % 2 == 0) {
            onbtnflightwidthplus.setVisibility(View.INVISIBLE);
            onbtnflightwidthminus.setVisibility(View.INVISIBLE);
        }

    }


    public void onbtnMission(View view) {
        count++;

        Button onbtnMissionAB = (Button) findViewById(R.id.btnMissionAB);
        Button onbtnMissionPolygon = (Button) findViewById(R.id.btnMissionPolygon);
        Button onbtnMissioncancel = (Button) findViewById(R.id.btnMissioncancel);

        if (count % 2 == 1) {
            onbtnMissionAB.setVisibility(View.VISIBLE);
            onbtnMissionPolygon.setVisibility(View.VISIBLE);
            onbtnMissioncancel.setVisibility(View.VISIBLE);
        } else if (count % 2 == 0) {
            onbtnMissionAB.setVisibility(View.INVISIBLE);
            onbtnMissionPolygon.setVisibility(View.INVISIBLE);
            onbtnMissioncancel.setVisibility(View.INVISIBLE);
        }

    }
}