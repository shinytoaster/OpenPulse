package net.shinytoaster.openpulse.wear.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Receiver to start the HeartRateService when the watch boots up.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "OpenPulse-Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Watch restarted, starting HeartRateService...");
            Intent serviceIntent = new Intent(context, HeartRateService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
