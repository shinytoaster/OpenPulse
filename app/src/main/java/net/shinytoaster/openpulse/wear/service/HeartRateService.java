package net.shinytoaster.openpulse.wear.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import net.shinytoaster.openpulse.wear.MainActivity;
import net.shinytoaster.openpulse.wear.PulseTileService;
import net.shinytoaster.openpulse.wear.ble.BleManager;
import net.shinytoaster.openpulse.wear.health.HealthServicesManager;
import androidx.wear.tiles.TileService;

/**
 * Foreground Service that maintains the Heart Rate broadcast.
 */
public class HeartRateService extends Service {
    private static final String TAG = "OpenPulse-Service";
    private static final String CHANNEL_ID = "HeartRateBroadcastChannel";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_STOP_SERVICE = "net.shinytoaster.openpulse.wear.STOP_SERVICE";
    public static final String ACTION_START_BROADCAST = "net.shinytoaster.openpulse.wear.START_BROADCAST";
    public static final String ACTION_STOP_BROADCAST = "net.shinytoaster.openpulse.wear.STOP_BROADCAST";

    private static HeartRateService instance;

    private BleManager bleManager;
    private HealthServicesManager healthServicesManager;
    private PowerManager.WakeLock wakeLock;
    private boolean isTracking = false;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public HeartRateService getService() {
            return HeartRateService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "HeartRateService created.");

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification("Service Ready"));

        bleManager = new BleManager(this);
        healthServicesManager = new HealthServicesManager(this, bpm -> {
            Log.d(TAG, "BPM Update: " + bpm);
            lastBpm = bpm;
            bleManager.updateHeartRate(bpm);
            
            Intent intent = new Intent(MainActivity.ACTION_HR_UPDATE);
            intent.putExtra(MainActivity.EXTRA_BPM, bpm);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);

            // Periodically update the tile during broadcast
            // Only update if it's a significant change or every few seconds 
            // to avoid excessive battery drain, but for now let's just do it
            TileService.getUpdater(this).requestUpdate(PulseTileService.class);
        });
    }

    public void startBroadcast() {
        if (isTracking) return;
        Log.i(TAG, "Starting broadcast...");

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenPulse:WakeLock");
            wakeLock.acquire();
        }

        bleManager.start();
        healthServicesManager.startTracking();
        isTracking = true;

        updateNotification("Broadcasting Heart Rate");
        TileService.getUpdater(this).requestUpdate(PulseTileService.class);
    }

    public void stopBroadcast() {
        if (!isTracking) return;
        Log.i(TAG, "Stopping broadcast...");

        healthServicesManager.stopTracking();
        bleManager.stop();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        isTracking = false;

        updateNotification("Broadcast Paused");
        TileService.getUpdater(this).requestUpdate(PulseTileService.class);
        
        // Notify UI that it's stopped
        Intent intent = new Intent(MainActivity.ACTION_HR_UPDATE);
        intent.putExtra(MainActivity.EXTRA_BPM, 0);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    public boolean isTracking() {
        return isTracking;
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, getNotification(text));
        }
    }

    private Notification getNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, HeartRateService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OpenPulse")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setOngoing(isTracking)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        if (isTracking) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent);
        } else {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", stopPendingIntent);
        }

        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP_SERVICE.equals(action)) {
                Log.i(TAG, "Stop action received via intent.");
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_START_BROADCAST.equals(action)) {
                startBroadcast();
            } else if (ACTION_STOP_BROADCAST.equals(action)) {
                stopBroadcast();
            }
        }

        // If the service was already tracking, make sure it stays tracking
        if (isTracking) {
            Log.d(TAG, "onStartCommand: Already tracking, ensuring notification is correct.");
            startForeground(NOTIFICATION_ID, getNotification("Broadcasting Heart Rate"));
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBroadcast();
        if (bleManager != null) {
            bleManager.close();
        }
        instance = null;
    }

    public static int getCurrentBpm() {
        if (instance != null && instance.healthServicesManager != null && instance.isTracking) {
            // We need a way to get the last known BPM from healthServicesManager
            // For now, let's assume it has a getLastBpm method or we store it here
            return instance.lastBpm;
        }
        return 0;
    }

    private int lastBpm = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Heart Rate Broadcast",
                NotificationManager.IMPORTANCE_HIGH
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
