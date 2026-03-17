package net.shinytoaster.openpulse.wear;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.wear.ambient.AmbientModeSupport;

import net.shinytoaster.openpulse.wear.service.HeartRateService;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Activity to handle permissions and start/stop the Heart Rate Service.
 */
public class MainActivity extends FragmentActivity implements AmbientModeSupport.AmbientCallbackProvider {
    private static final String TAG = "OpenPulse-Main";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int BACKGROUND_PERMISSION_REQUEST_CODE = 101;
    
    public static final String ACTION_HR_UPDATE = "net.shinytoaster.openpulse.wear.HR_UPDATE";
    public static final String EXTRA_BPM = "bpm";

    private TextView statusText;
    private TextView bpmText;
    private ImageButton toggleButton;
    private HeartRateService heartRateService;
    private boolean isBound = false;

    private final AmbientModeSupport.AmbientCallback ambientCallback = new AmbientModeSupport.AmbientCallback() {
        @Override
        public void onEnterAmbient(Bundle ambientDetails) {
            super.onEnterAmbient(ambientDetails);
            Log.d(TAG, "Entering ambient mode");
        }

        @Override
        public void onExitAmbient() {
            super.onExitAmbient();
            Log.d(TAG, "Exiting ambient mode");
        }

        @Override
        public void onUpdateAmbient() {
            super.onUpdateAmbient();
            Log.d(TAG, "Updating ambient mode");
        }
    };

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return ambientCallback;
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            HeartRateService.LocalBinder binder = (HeartRateService.LocalBinder) service;
            heartRateService = binder.getService();
            isBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver hrReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_HR_UPDATE.equals(intent.getAction())) {
                int bpm = intent.getIntExtra(EXTRA_BPM, 0);
                if (bpmText != null) {
                    bpmText.setText(bpm > 0 ? String.valueOf(bpm) : "--");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AmbientModeSupport.attach(this);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        bpmText = findViewById(R.id.bpm_text);
        toggleButton = findViewById(R.id.toggle_button);

        toggleButton.setOnClickListener(v -> {
            if (isBound && heartRateService != null) {
                if (heartRateService.isTracking()) {
                    heartRateService.stopBroadcast();
                } else {
                    heartRateService.startBroadcast();
                }
                updateUI();
            }
        });

        checkAndRequestPermissions();
        requestIgnoreBatteryOptimizations();
    }

    private void requestIgnoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void updateUI() {
        if (heartRateService != null && toggleButton != null) {
            boolean tracking = heartRateService.isTracking();
            
            // Set icon: Play for Stopped, Stop for Tracking
            toggleButton.setImageResource(tracking ? R.drawable.ic_stop : R.drawable.ic_play);
            
            // Set button color: Green for Start, Red for Stop
            int color = tracking ? Color.parseColor("#E53935") : Color.parseColor("#43A047");
            toggleButton.setBackgroundTintList(ColorStateList.valueOf(color));

            if (statusText != null) {
                statusText.setText(tracking ? "Broadcasting Heart Rate" : "Broadcast Paused");
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, HeartRateService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_HR_UPDATE);
        registerReceiver(hrReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(hrReceiver);
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.BODY_SENSORS);
        permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT);

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            checkBackgroundPermission();
        }
    }

    private void checkBackgroundPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS_BACKGROUND) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.BODY_SENSORS_BACKGROUND}, 
                    BACKGROUND_PERMISSION_REQUEST_CODE);
        } else {
            ensureServiceRunning();
        }
    }

    private void ensureServiceRunning() {
        Intent intent = new Intent(this, HeartRateService.class);
        startForegroundService(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            checkBackgroundPermission();
        } else if (requestCode == BACKGROUND_PERMISSION_REQUEST_CODE) {
            ensureServiceRunning();
        }
    }
}
