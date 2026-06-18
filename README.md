# <img src="assets/logo.png" width="40" height="40" align="center"> OpenPulse

**OpenPulse** is a standalone application designed for **Wear OS 5.0+** cyclists and athletes. It broadcasts real-time heart rate data from your watch's sensors to bicycle head units (like a Hammerhead Karoo, Garmin Edge, or Wahoo ELEMNT) using the standard Bluetooth SIG Heart Rate Profile (0x180D).



## Features

- **Standard BLE Heart Rate Profile**: Works as a standard "Heart Rate Strap" for any compatible head unit or fitness app.
- **Standalone Operation**: No phone required. All tracking and broadcasting happen directly on your Wear OS watch.
- **Persistent Background Broadcast**: Uses a High-Priority Foreground Service with a WakeLock to ensure the broadcast remains active even when the watch screen is off.
- **Ambient Mode Support**: Fully supports Wear OS Ambient Mode to maintain activity priority and keep the UI (optionally) updated without killing the service.
- **Modern Health Services API**: Uses the `MeasureClient` for continuous background sensor access that allows you to run other workout apps on your watch in parallel!
- **Standalone Wear OS Tile**: View your real-time heart rate and control the broadcast (Start/Stop) directly from your watch's tiles without opening the full app.
- **Battery Efficient**: Optimized BLE settings (Balanced mode) to provide a reliable signal without excessive drain.

## Usage Instructions

1.  **Open the App**: Launch **OpenPulse** on your Wear OS watch.
2.  **Grant Permissions**: 
    - When prompted, grant permissions for **Body Sensors**, **Physical Activity**, and **Bluetooth**.
    - **CRITICAL**: For background tracking, when prompted for sensor access, you **must** select **"Allow all the time"** in the system settings.
3.  **Disable Battery Optimizations**:
    - The app will prompt you to ignore battery optimizations. **Select "Allow"** (if prompted by your watch).
    - *Note*: Since OpenPulse registers as the active exercise session on the watch (it pretends to be a workout), it naturally receives maximum system-level background execution priority. There is no need for advanced battery whitelisting or ADB commands.
4.  **Use the Wear OS Tile**:
    - Swipe left on your watch face to access your tiles.
    - Long-press and tap **"Add Tile"**.
    - Select **"OpenPulse"**.
    - The tile shows your live BPM and a quick Start/Stop button. Tapping the button or the icon will toggle the tracking state instantly.
5.  **Connect Your Headunit**: 
    - On your cycling computer, go to the **Sensors** menu and "Search for New Sensor."
    - Look for **"OpenPulse Watch"** (Heart Rate sensor type).
    - Pair and connect. Your live BPM will now appear on your headunit.
6.  **Pause/Stop**: Tap the **"Stop"** (Red) button to save battery.
    - **Stop**: Immediately shuts down the heart rate sensor and releases the CPU WakeLock.
    - **Smart Standby**: The app maintains the Bluetooth connection in a low-power "Standby" state so you can resume instantly.

### Exercise Coexistence (Exclusive Session)

To maintain continuous broadcasting when the watch screen turns off, OpenPulse starts a background exercise session via Health Services. 

Because Wear OS only permits one active exercise tracking session at a time:
- **No parallel workout tracking on the watch**: You cannot run a native workout tracker (like Samsung Health, Google Fit, or Strava) on the watch in parallel with OpenPulse. Starting another workout session on the watch will immediately preempt and stop the OpenPulse broadcast.
- **Normal Usage**: This is optimized for users who track their ride metrics (distance, GPS, maps) on their dedicated cycling computer (Karoo, Garmin, Wahoo, etc.) and only use the watch as a heart rate sensor.

> [!IMPORTANT]
> **Upgrading OpenPulse**: After upgrading the app to a new version, your headunit may lose the Bluetooth pairing. If the sensor stops connecting, please **unpair/forget** "OpenPulse Watch" on your headunit and **re-pair** it as a new sensor.

## Battery Usage Information

OpenPulse is designed to be as efficient as possible, but continuous heart rate broadcasting is hardware-intensive.
- **Expected Battery Drain**: Approximately **12% to 20% per hour**, depending on your watch model.
- **Typical Runtime**: 5 to 8 hours on a full charge.
- **Power Saving**: The app uses `ADVERTISE_MODE_BALANCED` and `ADVERTISE_TX_POWER_MEDIUM`. **Always tap "Stop" when not in use** to kill the sensor and WakeLock.

## How to Sideload (For Developers/Beta Testers)

Since this app is a standalone Wear OS utility, you can sideload the APK using ADB (Android Debug Bridge):

1.  **Enable Developer Options on Watch**:
    - Go to **Settings > System > About > Versions**.
    - Tap **Build Number** 7 times.
2.  **Enable ADB Debugging**:
    - Go to **Settings > Developer Options**.
    - Enable **ADB Debugging** and **Debug over Wi-Fi**.
3.  **Connect via ADB**:
    - Note the IP address shown on the watch.
    - Run: `adb connect <your-watch-ip>:5555`
    - Accept the pairing prompt on the watch.
4.  **Install the APK**:
    - Run: `adb install -r OpenPulse.apk`

## Permissions & Requirements

OpenPulse requires several permissions to reliably track your heart rate and broadcast it over BLE, especially while running in the background.

### System Permissions
- **Body Sensors**: Required to access the heart rate sensor on your watch.
- **Body Sensors (Always Allow)**: **CRITICAL.** For the broadcast to continue when the screen is off or you are in another app, you must select **"Allow all the time"** in the system permission settings.
- **Physical Activity**: Used to optimize sensor batching and delivery based on your movement.
- **Bluetooth (Advertise & Connect)**: Direct BLE access is required to broadcast the Heart Rate Profile to your headunit.
- **Notifications**: Required to display the persistent Foreground Service notification, allowing you to stop the broadcast easily.
- **Battery Optimization (Ignore)**: The app will request to stay active in the background. This prevents Wear OS from killing the broadcast to save power.

### Technical Architecture
- **Minimum Version**: Wear OS 5.0 (API 34).
- **GATT Server**: Implements the standard Heart Rate Service (0x180D) and characteristic (0x2A37).
- **Service Type**: Uses `FOREGROUND_SERVICE_HEALTH` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
- **Background Persistence**: Uses a `PARTIAL_WAKE_LOCK` and `AmbientModeSupport`.
- **Exercise Priority**: Uses `ExerciseClient` to start an active workout tracking session, giving the app system-level priority for continuous background sensor reading and BLE broadcasting.

## License

OpenPulse is released under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for the full license text.

---
*Created with ❤️ by the OpenPulse contributors.*
