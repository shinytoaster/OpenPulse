# Specification: OpenPulse for Wear OS

## Core Objective
OpenPulse is a **standalone Wear OS 5.0+** application designed for athletes and cyclists. Its primary purpose is to transform a Wear OS watch into a standard Bluetooth Low Energy (BLE) Heart Rate monitor, broadcasting real-time HR data directly from the watch's internal sensors to compatible cycling computers (head units) and fitness applications.

## Technical Architecture
The application follows a **Service-Oriented Architecture** designed for high reliability and persistence on Wear OS.

### Architectural Patterns
- **Foreground Service Orchestration**: The `HeartRateService` acts as the central hub, managing the lifecycle of the Heart Rate sensor and the BLE transmission server.
- **Component-Based Separation**:
    - `net.shinytoaster.openpulse.wear.ble`: Handles BLE GATT server management and advertising logic.
    - `net.shinytoaster.openpulse.wear.health`: Manages interactions with the Android Health Services API.
    - `net.shinytoaster.openpulse.wear.service`: Contains the core foreground service and system receivers.
- **Event-Driven Communication**: Uses standard Android Broadcast Intents for updating the UI and internal service triggers for Tile updates.

### Core Libraries & Dependencies
- **Android Health Services (`MeasureClient`)**: Used for passive, real-time sensor data access that operates alongside other workout apps without conflict.
- **Bluetooth LE GATT API**: Implements the standard Heart Rate Profile.
- **Wear OS Protolayout & Tiles**: Enables the interactive Wear OS Tile.
- **Ambient Mode Support**: Standard Wear OS transitions to maintain activity visibility.
- **Guava**: Used for managing `ListenableFuture` results from the Tile and Health Services APIs.

## Feature Breakdown

### 1. BLE Heart Rate Broadcasting
- **Implementation**: `BleManager.java` manages a GATT server implementing the **Bluetooth SIG Heart Rate Service (0x180D)** and the **Heart Rate Measurement Characteristic (0x2A37)**.
- **Advertising**: Uses `ADVERTISE_MODE_BALANCED` and `ADVERTISE_TX_POWER_MEDIUM` to balance signal reliability with battery life.
- **GATT Server**: Broadcasts notifications to all connected and subscribed devices whenever a new BPM reading is received from the sensor.

### 2. Sensor Integration via Health Services
- **Implementation**: `HealthServicesManager.java` uses the `MeasureClient` to passively read heart rate sensor data without claiming an exercise session.
- **Parallel Workout Support**: Unlike `ExerciseClient`, `MeasureClient` does not take exclusive ownership of the exercise slot, allowing users to run native workout apps (e.g., tracking a run or gym session) on the watch simultaneously while OpenPulse broadcasts HR to an external device.

### 3. Persistent Background Operation
- **Foreground Service**: `HeartRateService` runs with `health` and `connectedDevice` foreground types.
- **Power Management**: Utilizes a `PARTIAL_WAKE_LOCK` to keep the CPU active during the broadcast.
- **Ambient Mode**: `MainActivity` implements `AmbientModeSupport` to maintain UI state without triggering system power-saving kills.

### 4. Interactive Wear OS Tile
- **Implementation**: `PulseTileService.java` provides a quick-access interface using Protolayout.
- **Functionality**: Displays the current BPM and provides a "Start/Stop" toggle that interacts with the `HeartRateService` via a translucent proxy activity (`TileActionActivity`).

## Data Models

### Heart Rate State
- `bpm`: Integer representing beats per minute. Pushed via `HeartRateListener` callback from Health Services.
- `isTracking`: Boolean flag representing the active state of both the BLE Advertiser and the Health Sensor.

### Persistence & Flow
- **Flow**: Sensor (Health Services) -> `HealthServicesManager` -> `HeartRateService` (Central Hub) -> `BleManager` (GATT Notify) -> [External Head Unit].
- **Internal Sync**: `HeartRateService` triggers `TileService.getUpdater().requestUpdate()` on every BPM change to keep the Watch face Tile synchronized.

## Constraints & "Constitution"

1. **Standalone Requirement**: The app MUST NOT depend on a companion phone app for core functionality.
2. **Minimalist Library Usage**: Avoid heavy frameworks (e.g., Jetpack Compose for UI) in favor of lightweight standard Android views and Protolayout for Tiles to maximize performance on low-power Wear OS hardware.
3. **Hardware Directness**: Favor direct interaction with standard Android/Wear OS APIs over "sugar" or abstraction layers that might interfere with background persistence.
4. **Mandatory Permissions**:
    - `BODY_SENSORS_BACKGROUND` is a "Hard Constraint"—the app cannot function reliably without "Allow all the time" sensor access.
    - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is requested during initial setup to ensure long-ride stability.
5. **No Logic in UI**: UI components (`MainActivity`, `PulseTileService`) are purely for display and control; all business logic resides within the `HeartRateService`.
