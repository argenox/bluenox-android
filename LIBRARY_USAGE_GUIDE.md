# BlueNox Android Library Usage Guide

This guide shows how to initialize and use BlueNox with:

- callback-based APIs (classic pattern), and
- coroutine/Flow-based APIs (modern Kotlin pattern).

The examples below use the current library classes in `com.argenox.bluenoxandroid`.

## License

BlueNox is distributed under the **Apache License 2.0**. See `LICENSE` and `NOTICE` in this directory.

---

## 1) Prerequisites

- Android BLE permissions must be granted at runtime.
- You should initialize once (for example in `Application` or in your main entry `Activity` after permissions are granted).
- Keep a single `BluenoxLEManager` instance via `BluenoxLEManager.getInstance()`.

### Dependency

```kotlin
dependencies {
    implementation("com.argenox:bluenox-android:1.0.225")
}
```

---

## 2) Initialize the manager

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.argenox.bluenoxandroid.BluenoxLEManager

class MainActivity : ComponentActivity() {
    private val manager = BluenoxLEManager.getInstance()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        initBlueNox()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = manager.requiredPermissions()
        permissionLauncher.launch(permissions)
    }

    private fun initBlueNox() {
        val initialized = manager.initialize(applicationContext)
        if (!initialized) {
            // Could fail due to missing permissions or unsupported hardware.
            return
        }

        // Ready to scan/connect.
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.uninitialize()
    }
}
```

---

## 3) Callback-based usage

BlueNox has two callback layers:

- **Manager-level events** (`BluenoxEvents`) for scan/connect lifecycle.
- **Device-level callbacks** (`BlueNoxDeviceCallbacks`) for GATT operations.

### 3.1 Manager-level events

```kotlin
import com.argenox.bluenoxandroid.BluenoxLEManager

private val manager = BluenoxLEManager.getInstance()

private val managerEventHandler: (BluenoxLEManager.BluenoxEvents, String, String) -> Unit =
    { evt, name, address ->
        when (evt) {
            BluenoxLEManager.BluenoxEvents.BLUENOX_EVT_SCAN_START -> {
                // Scan started
            }
            BluenoxLEManager.BluenoxEvents.BLUENOX_EVT_DEVICE_FOUND -> {
                // Device found: address
            }
            BluenoxLEManager.BluenoxEvents.BLUENOX_EVT_DEVICE_CONNECTED -> {
                // Connected: address
            }
            BluenoxLEManager.BluenoxEvents.BLUENOX_EVT_DEVICE_DISCONNECTED -> {
                // Disconnected: address
            }
            BluenoxLEManager.BluenoxEvents.BLUENOX_EVT_DEVICE_CONNECTION_FAILED -> {
                // Connect failed: address
            }
            else -> Unit
        }
    }

fun startManagerEvents() {
    manager.registerCallback(managerEventHandler)
}

fun stopManagerEvents() {
    manager.unregisterCallback(managerEventHandler)
}
```

---

## DFU workflow (multi-protocol)

BlueNox Scan now supports protocol-detected DFU flows for Nordic legacy DFU, Nordic SMP (Zephyr), TI OAD/BIM-style flows, and Silicon Labs Gecko OTA/Apploader.

Recommended flow in app code:

1. Connect to a target and complete service discovery.
2. Detect DFU protocol candidates from discovered GATT services.
3. Present protocol-specific options and firmware file picker.
4. Start DFU with a single run request; subscribe to progress and allow cancel.
5. Persist run history (status, protocol, file, details) to diagnostics.

In the bundled app UI, this is exposed from `DeviceScreen` through the `DFU` button and `DFU Update` menu action.

### 3.2 Scanning

```kotlin
import android.os.ParcelUuid
import java.util.UUID

// Scan all devices for 15 seconds
manager.scanForDevices(15_000L)

// Scan by service UUID for 10 seconds
val hrmUuid = ParcelUuid(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"))
manager.scanWithUUID(hrmUuid, 10_000L)

// Scan by manufacturer/company ID for 10 seconds
manager.scanWithManufacturerId(0x004C, 10_000L) // Example: Apple company ID

// Scan by AD type for 10 seconds
manager.scanWithAdvertisingDataType(0x16, 10_000L) // Service Data AD type

// Stop active scan
manager.stopScanning()
```

### 3.2.1 Decode beacons from discovered devices (iBeacon/Eddystone)

```kotlin
import com.argenox.bluenoxandroid.BlueNoxBeaconFrame

fun inspectBeacons(address: String) {
    val device = manager.getDeviceByAddress(address) ?: return
    device.decodedBeaconFrames().forEach { frame ->
        when (frame) {
            is BlueNoxBeaconFrame.IBeacon -> {
                // frame.uuid, frame.major, frame.minor
            }
            is BlueNoxBeaconFrame.EddystoneUid -> {
                // frame.namespaceIdHex, frame.instanceIdHex
            }
            is BlueNoxBeaconFrame.EddystoneUrl -> {
                // frame.url
            }
            is BlueNoxBeaconFrame.EddystoneTlm -> {
                // frame.batteryMilliVolts, frame.temperatureCelsius
            }
            is BlueNoxBeaconFrame.EddystoneEid -> {
                // frame.ephemeralIdHex
            }
        }
    }
}
```

### 3.3 Connect and interact with a device

```kotlin
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.argenox.bluenoxandroid.BlueNoxDevice
import com.argenox.bluenoxandroid.BlueNoxDeviceCallbacks

private val deviceCallbacks = object : BlueNoxDeviceCallbacks.NullBlueNoxDeviceCallbacks() {
    override fun uiDeviceConnected(gatt: BluetoothGatt?, device: BluetoothDevice?) {
        // Connected and ready to discover/read/write
    }

    override fun uiCharacteristicUpdated(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?,
        value: ByteArray
    ) {
        // Notification/indication update
    }

    override fun uiMtuUpdated(gatt: BluetoothGatt?, mtu: Int) {
        // MTU changed
    }

    override fun uiOperationFailure(
        device: BluetoothDevice?,
        reason: BlueNoxDeviceCallbacks.BlueNoxFailureReason,
        detail: String,
        characteristicUuid: String?
    ) {
        // Structured failure path for easier recovery/retry logic
    }
}

fun connectTo(address: String) {
    val started = manager.connectByAddress(address, deviceCallbacks)
    if (!started) {
        // Connect request did not start
    }
}

fun interact(address: String) {
    val device: BlueNoxDevice = manager.getDeviceByAddress(address) ?: return

    // Read a characteristic
    device.requestReadCharacteristic("00002a19-0000-1000-8000-00805f9b34fb")

    // Enable notify
    device.setNotificationForCharacteristic("00002a37-0000-1000-8000-00805f9b34fb", true)

    // Write
    device.writeCharacteristicByUUID(
        uuid = "00002a39-0000-1000-8000-00805f9b34fb",
        data = byteArrayOf(0x01),
        response = true
    )
}
```

---

## 4) Coroutine/Flow usage

BlueNox includes `BlueNoxDeviceFlowAdapter` to bridge callback events into a typed `SharedFlow`.

### 4.1 Register the adapter

```kotlin
import androidx.lifecycle.lifecycleScope
import com.argenox.bluenoxandroid.BlueNoxDeviceFlowAdapter
import kotlinx.coroutines.launch

private var flowAdapter: BlueNoxDeviceFlowAdapter? = null

fun attachFlow(address: String) {
    val device = manager.getDeviceByAddress(address) ?: return
    val adapter = device.registerFlowAdapter() // convenience helper on BlueNoxDevice
    flowAdapter = adapter

    lifecycleScope.launch {
        adapter.events.collect { event ->
            when (event) {
                is BlueNoxDeviceFlowAdapter.BlueNoxDeviceEvent.Connection -> {
                    // connected/disconnected
                }
                is BlueNoxDeviceFlowAdapter.BlueNoxDeviceEvent.CharacteristicValue -> {
                    // notify/read value stream
                }
                is BlueNoxDeviceFlowAdapter.BlueNoxDeviceEvent.Mtu -> {
                    // MTU updates
                }
                is BlueNoxDeviceFlowAdapter.BlueNoxDeviceEvent.ConnectionParameters -> {
                    // interval/latency/timeout/status
                }
                is BlueNoxDeviceFlowAdapter.BlueNoxDeviceEvent.ServicesChanged -> {
                    // service changed indication received
                }
                is BlueNoxDeviceFlowAdapter.BlueNoxDeviceEvent.Bond -> {
                    // bonding update
                }
                is BlueNoxDeviceFlowAdapter.BlueNoxDeviceEvent.Failure -> {
                    // structured failure event
                }
            }
        }
    }
}

fun detachFlow(address: String) {
    val device = manager.getDeviceByAddress(address) ?: return
    val adapter = flowAdapter ?: return
    device.unregisterFlowAdapter(adapter)
    flowAdapter = null
}
```

### Why use Flow here?

- Easier composition with other app streams.
- Lifecycle-aware collection with coroutine scopes.
- Less callback fan-out in larger codebases.

---

## 5) Advanced transport controls (P1 APIs)

```kotlin
val device = manager.getDeviceByAddress(address) ?: return

// MTU (23..517)
device.requestMtu(247)

// Connection priority
device.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

// Refresh GATT cache (reflection-backed)
device.refreshGattCache()

// Reliable write transaction controls
device.beginReliableWriteTransaction()
// ... perform writes ...
device.executeReliableWriteTransaction() // or device.abortReliableWriteTransaction()

// Reliability tuning profiles
device.setConnectionProfile(BlueNoxConnectionProfile.BALANCED)
device.setBondingPolicy(
    BlueNoxBondingPolicy(
        autoRetryEnabled = true,
        maxRetries = 2,
        retryBackoffMs = 800L,
        timeoutMs = 15_000L
    )
)
```

### Split large outgoing writes

```kotlin
val chunks = device.writeCharacteristicSplitByUUID(
    uuid = "0000xxxx-0000-1000-8000-00805f9b34fb",
    data = largePayload,
    response = true,
    maxChunkSize = 100
)
```

---

## 6) Recommended lifecycle pattern

- Initialize once after permissions are granted.
- Register manager callback when screen/session starts.
- Connect with `connectByAddress`.
- Get device object and register:
  - callback listener (`BlueNoxDeviceCallbacks`) or
  - flow adapter (`BlueNoxDeviceFlowAdapter`), or both.
- On shutdown:
  - unregister callbacks/adapters,
  - stop scan if needed,
  - call `uninitialize()`.

---

## 7) Common failure handling tips

- Always implement `uiOperationFailure` and log `reason + detail`.
- Treat `CONNECT_RETRY_EXHAUSTED` as user-visible reconnect failure.
- Treat `OPERATION_TIMEOUT` as recoverable: retry operation or reconnect.
- Treat `BOND_RETRY_EXHAUSTED` and `BOND_TIMEOUT` as user-visible pairing failures.
- Treat `PERMISSION_DENIED` as a hard stop until user re-grants permissions.

---

## 8) Minimal end-to-end sequence

1. Request runtime permissions from `requiredPermissions()`.
2. Call `initialize(applicationContext)`.
3. Start scan with `scanForDevices(timeoutMs)`.
4. Connect with `connectByAddress(address, callbacks)`.
5. Use `BlueNoxDevice` for read/write/notify/MTU/etc.
6. Optionally attach `BlueNoxDeviceFlowAdapter` for stream-based consumption.
7. Cleanup with `unregisterCallback(...)` and `uninitialize()`.

