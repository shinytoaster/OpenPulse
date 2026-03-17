package net.shinytoaster.openpulse.wear;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DeviceParametersBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.protolayout.material.CompactChip;
import androidx.wear.protolayout.material.ChipColors;
import androidx.wear.protolayout.material.Text;
import androidx.wear.protolayout.material.Typography;
import androidx.wear.protolayout.material.layouts.PrimaryLayout;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;
import androidx.wear.tiles.RequestBuilders;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import net.shinytoaster.openpulse.wear.service.HeartRateService;

/**
 * Tile Service for OpenPulse to allow quick start/stop and BPM viewing.
 */
public class PulseTileService extends TileService {
    private static final String RESOURCES_VERSION = "1.0";
    private static final String ID_CLICK_START = "click_start";
    private static final String ID_CLICK_STOP = "click_stop";

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest requestParams) {
        int bpm = HeartRateService.getCurrentBpm();
        boolean isTracking = bpm > 0;

        Log.d("OpenPulse-Tile", "onTileRequest: bpm=" + bpm + ", isTracking=" + isTracking);

        // Layout from Protolayout
        LayoutElementBuilders.Layout layout = new LayoutElementBuilders.Layout.Builder()
                .setRoot(getLayout(requestParams.getDeviceConfiguration(), isTracking, bpm))
                .build();

        // Timeline from Protolayout (Envelope)
        TimelineBuilders.Timeline timeline = new TimelineBuilders.Timeline.Builder()
                .addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder()
                        .setLayout(layout)
                        .build())
                .build();

        // Tile from Tiles (Outer Envelope)
        return Futures.immediateFuture(new TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(5000)
                .setTileTimeline(timeline)
                .build());
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(@NonNull RequestBuilders.ResourcesRequest requestParams) {
        Log.d("OpenPulse-Tile", "onTileResourcesRequest: version=" + RESOURCES_VERSION);
        
        // Resources from Protolayout (Envelope)
        return Futures.immediateFuture(new ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping("heart_icon", new ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(new ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_pulse_tile)
                                .build())
                        .build())
                .build());
    }

    private LayoutElementBuilders.LayoutElement getLayout(
            @NonNull DeviceParametersBuilders.DeviceParameters deviceParameters,
            boolean isTracking,
            int bpm) {
        
        String bpmDisplay = bpm > 0 ? String.valueOf(bpm) : "--";
        String buttonText = isTracking ? "Stop" : "Start";
        int buttonColor = isTracking ? 0xFFE53935 : 0xFF43A047;

        // PrimaryLayout from Protolayout
        return new PrimaryLayout.Builder(deviceParameters)
                .setPrimaryLabelTextContent(new Text.Builder(this, "OpenPulse")
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setColor(ColorBuilders.argb(0xFFBBBBBB))
                        .build())
                .setContent(new LayoutElementBuilders.Column.Builder()
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                        .addContent(new LayoutElementBuilders.Image.Builder()
                                .setResourceId("heart_icon")
                                .setWidth(DimensionBuilders.dp(32))
                                .setHeight(DimensionBuilders.dp(32))
                                .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                                        .setClickable(getClickAction(isTracking ? ID_CLICK_STOP : ID_CLICK_START))
                                        .build())
                                .build())
                        .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(2)).build())
                        .addContent(new Text.Builder(this, bpmDisplay)
                                .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                                .setColor(ColorBuilders.argb(0xFFFFFFFF))
                                .build())
                        .addContent(new Text.Builder(this, "BPM")
                                .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                                .setColor(ColorBuilders.argb(0xFFBBBBBB))
                                .build())
                        .build())
                .setPrimaryChipContent(new CompactChip.Builder(this, buttonText, getClickAction(isTracking ? ID_CLICK_STOP : ID_CLICK_START), deviceParameters)
                        .setChipColors(new ChipColors(ColorBuilders.argb(buttonColor), ColorBuilders.argb(0xFFFFFFFF)))
                        .build())
                .build();
    }

    @NonNull
    private ModifiersBuilders.Clickable getClickAction(String actionId) {
        return new ModifiersBuilders.Clickable.Builder()
                .setId(actionId)
                .setOnClick(new ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(new ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(getPackageName())
                                .setClassName(TileActionActivity.class.getName())
                                .build())
                        .build())
                .build();
    }
}
