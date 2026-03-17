package net.shinytoaster.openpulse.wear;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.wear.tiles.TileService;

import net.shinytoaster.openpulse.wear.service.HeartRateService;

/**
 * Trampoline activity to handle start/stop actions from the Tile.
 * It processes the intent and then immediately finishes.
 */
public class TileActionActivity extends Activity {
    private static final String TAG = "OpenPulse-TileAction";
    public static final String ACTION_TOGGLE_BROADCAST = "net.shinytoaster.openpulse.wear.action.TOGGLE_BROADCAST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "TileActionActivity started");
        handleToggle();
        
        finish();
    }

    private void handleToggle() {
        Log.d(TAG, "Tile action: toggling broadcast");
        
        // We start the service first to ensure it exists
        Intent serviceIntent = new Intent(this, HeartRateService.class);
        startForegroundService(serviceIntent);

        // Then we send the toggle command
        // Note: PulseTileService.onTileRequest checks isTracking from HeartRateService.getCurrentBpm()
        // which depends on the instance being active.
        
        // Since we want to toggle, we check current state if possible
        // but it's safer to just send a start/stop based on what the tile thought was happening
        // or just let the service handle it if we add a dedicated toggle action.
        
        // For now, let's use the START/STOP actions already in HeartRateService
        boolean isCurrentlyTracking = HeartRateService.getCurrentBpm() > 0;
        
        Intent actionIntent = new Intent(this, HeartRateService.class);
        if (isCurrentlyTracking) {
            actionIntent.setAction(HeartRateService.ACTION_STOP_BROADCAST);
        } else {
            actionIntent.setAction(HeartRateService.ACTION_START_BROADCAST);
        }
        startForegroundService(actionIntent);

        // Request tile update
        TileService.getUpdater(this).requestUpdate(PulseTileService.class);
    }
}
