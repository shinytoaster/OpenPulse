package net.shinytoaster.openpulse.wear.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the BLE GATT Server and Heart Rate Advertising.
 */
public class BleManager {
    private static final String TAG = "OpenPulse-BleManager";

    // Standard Bluetooth SIG UUIDs
    public static final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    public static final UUID HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    // Device Information Service UUIDs
    public static final UUID DEVICE_INFO_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    public static final UUID MANUFACTURER_NAME_CHAR_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    public static final UUID MODEL_NUMBER_CHAR_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattCharacteristic hrCharacteristic;
    private final Set<BluetoothDevice> connectedDevices = new HashSet<>();
    private boolean isAdvertising = false;
    private boolean areServicesAdded = false;

    private BluetoothGattService hrService;
    private BluetoothGattService disService;

    public BleManager(Context context) {
        this.context = context;
    }

    public void start() {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not available or disabled.");
            return;
        }

        if (areServicesAdded) {
            Log.d(TAG, "Services already added, just starting advertising...");
            startAdvertising(adapter);
        } else {
            setupGattServer(bluetoothManager, adapter);
        }
    }

    private void setupGattServer(BluetoothManager bluetoothManager, BluetoothAdapter adapter) {
        if (gattServer == null) {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT Server.");
                return;
            }
        }

        // 1. Prepare Heart Rate Service
        hrService = new BluetoothGattService(HEART_RATE_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        hrCharacteristic = new BluetoothGattCharacteristic(
                HEART_RATE_MEASUREMENT_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG_UUID,
                BluetoothGattDescriptor.PERMISSION_WRITE);
        hrCharacteristic.addDescriptor(descriptor);
        hrService.addCharacteristic(hrCharacteristic);

        // 2. Prepare Device Information Service
        disService = new BluetoothGattService(DEVICE_INFO_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic manufacturerChar = new BluetoothGattCharacteristic(
                MANUFACTURER_NAME_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        manufacturerChar.setValue("ShinyToaster".getBytes());

        BluetoothGattCharacteristic modelChar = new BluetoothGattCharacteristic(
                MODEL_NUMBER_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        modelChar.setValue("OpenPulse Wear".getBytes());

        disService.addCharacteristic(manufacturerChar);
        disService.addCharacteristic(modelChar);

        // 3. Start sequential addition: Add HR Service first
        Log.d(TAG, "Starting sequential service addition...");
        gattServer.addService(hrService);
    }

    private void startAdvertising(BluetoothAdapter adapter) {
        if (isAdvertising) return;
        
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            Log.e(TAG, "BLE Advertising not supported.");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        // Standard HR sensors usually include the Service UUID and the Device Name.
        // We'll put both in the primary packet if they fit (they should).
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build();

        // Scan response can be empty or include additional details
        AdvertiseData scanResponse = new AdvertiseData.Builder().build();

        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback);
    }

    public void updateHeartRate(int bpm) {
        if (hrCharacteristic == null || gattServer == null) return;

        // Heart Rate Measurement format:
        // Byte 0: Flags (0x06 = UINT8 BPM, Sensor Contact supported and detected)
        // Byte 1: BPM value
        byte[] value = new byte[]{(byte) 0x06, (byte) (bpm & 0xFF)};
        hrCharacteristic.setValue(value);

        // Notify all connected devices that have subscribed
        for (BluetoothDevice device : connectedDevices) {
            try {
                gattServer.notifyCharacteristicChanged(device, hrCharacteristic, false);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying device: " + device.getAddress(), e);
            }
        }
    }

    public void stop() {
        if (advertiser != null && isAdvertising) {
            advertiser.stopAdvertising(advertiseCallback);
            isAdvertising = false;
        }
        // Send 0 HR to signify it's paused.
        updateHeartRate(0);
    }

    public void close() {
        if (advertiser != null) {
            advertiser.stopAdvertising(advertiseCallback);
        }
        if (gattServer != null) {
            gattServer.clearServices();
            gattServer.close();
            gattServer = null;
        }
        connectedDevices.clear();
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.d(TAG, "Connection state changed: " + device.getAddress() + " status: " + status + " newState: " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                                            boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (CLIENT_CHARACTERISTIC_CONFIG_UUID.equals(descriptor.getUuid())) {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                }
                Log.d(TAG, "Notification subscription updated for " + device.getAddress());
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.d(TAG, "Service added: " + service.getUuid() + " status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (HEART_RATE_SERVICE_UUID.equals(service.getUuid())) {
                    // HR Service added, now add DIS
                    Log.d(TAG, "HR Service added, adding DIS...");
                    gattServer.addService(disService);
                } else if (DEVICE_INFO_SERVICE_UUID.equals(service.getUuid())) {
                    // All services added, start advertising
                    areServicesAdded = true;
                    Log.d(TAG, "All services added, starting advertising...");
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter != null) {
                        startAdvertising(adapter);
                    }
                }
            }
        }
    };

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "BLE Advertising started successfully.");
            isAdvertising = true;
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "BLE Advertising failed: " + errorCode);
        }
    };
}
