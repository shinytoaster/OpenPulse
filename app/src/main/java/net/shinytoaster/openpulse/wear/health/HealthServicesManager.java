package net.shinytoaster.openpulse.wear.health;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.health.services.client.HealthServices;
import androidx.health.services.client.MeasureCallback;
import androidx.health.services.client.MeasureClient;
import androidx.health.services.client.data.Availability;
import androidx.health.services.client.data.DataPointContainer;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.DeltaDataType;
import androidx.health.services.client.data.SampleDataPoint;

import java.util.List;

/**
 * Manages Health Services using MeasureClient for robust background tracking
 * without cancelling other active workouts on the watch.
 *
 * Unlike ExerciseClient which claims exclusive ownership of the exercise session,
 * MeasureClient passively reads sensor data allowing native workout apps to
 * continue running in parallel.
 */
public class HealthServicesManager {
    private static final String TAG = "OpenPulse-Health";

    public interface HeartRateListener {
        void onHeartRateUpdate(int bpm);
    }

    private final MeasureClient measureClient;
    private final HeartRateListener listener;

    public HealthServicesManager(Context context, HeartRateListener listener) {
        this.measureClient = HealthServices.getClient(context).getMeasureClient();
        this.listener = listener;
    }

    public void startTracking() {
        Log.i(TAG, "Starting HR tracking via MeasureClient.");
        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback);
    }

    public void stopTracking() {
        Log.i(TAG, "Stopping MeasureClient.");
        measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback);
    }

    private final MeasureCallback callback = new MeasureCallback() {
        @Override
        public void onDataReceived(@NonNull DataPointContainer data) {
            List<SampleDataPoint<Double>> hrSamples = data.getData(DataType.HEART_RATE_BPM);
            if (!hrSamples.isEmpty()) {
                double bpm = hrSamples.get(hrSamples.size() - 1).getValue();
                Log.d(TAG, "Measure update HR: " + bpm);
                listener.onHeartRateUpdate((int) bpm);
            }
        }

        @Override
        public void onAvailabilityChanged(@NonNull DeltaDataType<?, ?> dataType, @NonNull Availability availability) {
            Log.d(TAG, "Availability changed: " + availability);
        }
    };
}
