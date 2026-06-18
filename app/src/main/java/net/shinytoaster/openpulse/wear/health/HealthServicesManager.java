package net.shinytoaster.openpulse.wear.health;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.health.services.client.ExerciseUpdateCallback;
import androidx.health.services.client.HealthServices;
import androidx.health.services.client.ExerciseClient;
import androidx.health.services.client.data.Availability;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.ExerciseConfig;
import androidx.health.services.client.data.ExerciseType;
import androidx.health.services.client.data.ExerciseUpdate;
import androidx.health.services.client.data.DataPointContainer;
import androidx.health.services.client.data.ExerciseLapSummary;
import androidx.health.services.client.data.SampleDataPoint;
import androidx.health.services.client.data.WarmUpConfig;
import androidx.health.services.client.data.ExerciseState;
import androidx.health.services.client.data.BatchingMode;

import com.google.common.collect.ImmutableSet;

import java.util.List;

/**
 * Manages Health Services using ExerciseClient for robust background tracking.
 */
public class HealthServicesManager {
    private static final String TAG = "OpenPulse-Health";

    public interface HeartRateListener {
        void onHeartRateUpdate(int bpm);
    }

    private final ExerciseClient exerciseClient;
    private final HeartRateListener listener;

    public HealthServicesManager(Context context, HeartRateListener listener) {
        this.exerciseClient = HealthServices.getClient(context).getExerciseClient();
        this.listener = listener;
    }

    public void startTracking() {
        Log.i(TAG, "Starting HR tracking via ExerciseClient.");
        
        WarmUpConfig warmUpConfig = new WarmUpConfig(
                ExerciseType.WALKING,
                ImmutableSet.of(DataType.HEART_RATE_BPM)
        );

        ExerciseConfig config = ExerciseConfig.builder(ExerciseType.WALKING)
                .setDataTypes(ImmutableSet.of(DataType.HEART_RATE_BPM))
                .setIsAutoPauseAndResumeEnabled(false)
                .setBatchingModeOverrides(ImmutableSet.of(BatchingMode.HEART_RATE_5_SECONDS))
                .build();

        exerciseClient.setUpdateCallback(callback);
        
        Log.d(TAG, "Preparing new exercise...");
        exerciseClient.prepareExerciseAsync(warmUpConfig).addListener(() -> {
            Log.d(TAG, "Exercise prepared, starting...");
            exerciseClient.startExerciseAsync(config);
        }, Runnable::run);
    }

    public void stopTracking() {
        Log.i(TAG, "Stopping ExerciseClient.");
        exerciseClient.endExerciseAsync();
    }

    private final ExerciseUpdateCallback callback = new ExerciseUpdateCallback() {
        @Override
        public void onExerciseUpdateReceived(@NonNull ExerciseUpdate update) {
            ExerciseState state = update.getExerciseStateInfo().getState();
            Log.d(TAG, "Exercise state: " + state);
            
            if (state == ExerciseState.ENDED) {
                Log.w(TAG, "Exercise ended unexpectedly. Reason: " + update.getExerciseStateInfo().getEndReason());
            }

            DataPointContainer latestMetrics = update.getLatestMetrics();
            List<SampleDataPoint<Double>> hrSamples = latestMetrics.getData(DataType.HEART_RATE_BPM);
            
            if (!hrSamples.isEmpty()) {
                double bpm = hrSamples.get(hrSamples.size() - 1).getValue();
                Log.d(TAG, "Exercise update HR: " + bpm);
                listener.onHeartRateUpdate((int) bpm);
            }
        }

        @Override
        public void onLapSummaryReceived(@NonNull ExerciseLapSummary lapSummary) {
            Log.d(TAG, "Lap summary received.");
        }

        @Override
        public void onRegistered() {
            Log.d(TAG, "Exercise callback registered.");
        }

        @Override
        public void onRegistrationFailed(@NonNull Throwable throwable) {
            Log.e(TAG, "Exercise callback registration failed", throwable);
        }

        @Override
        public void onAvailabilityChanged(@NonNull DataType<?, ?> dataType, @NonNull Availability availability) {
            Log.d(TAG, "Availability changed for " + dataType.getName() + ": " + availability);
        }
    };
}
