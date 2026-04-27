# BlueNox API Reference

This document describes the primary BlueNox library entry points and how to use them.
It is intended as a quick API map for developers integrating the library.

For end-to-end examples, see `LIBRARY_USAGE_GUIDE.md`.

---

## Core manager (`BluenoxLEManager`)

`BluenoxLEManager` is the top-level singleton used to initialize BLE, scan, and connect.

### Initialization and configuration

- `getInstance(): BluenoxLEManager`  
  Returns the singleton manager instance.

- `initialize(context: Context): Boolean`  
  Initializes manager state, validates permissions/hardware support, and prepares BLE internals.

- `uninitialize()`  
  Shuts down scanner/thread resources and unregisters internal receivers.

- `isInitialized(): Boolean`  
  Returns true when manager initialization completed successfully.

- `requiredPermissions(): Array<String>`  
  Returns runtime permissions required for current Android API level.

- `validatePermissions(): Boolean`  
  Checks that required permissions are currently granted.

- `checkHardwareCompatible(ctx: Context): Boolean`  
  Returns whether device reports BLE hardware support.

### Manager event callbacks

- `registerCallback(handler: (evt: BluenoxEvents, String, String) -> Unit)`  
  Registers manager-level events such as scan start/stop, device found, connect/disconnect.

- `unregisterCallback(handler: (evt: BluenoxEvents, String, String) -> Unit): Boolean`  
  Unregisters previously registered manager callback.

### Scanning

- `scanForDevices(timeout: Long): Boolean`  
  Starts generic BLE scan for `timeout` ms.

- `scanWithAddress(addr: String?, timeout: Long)`  
  Starts scan filtered by device MAC address.

- `scanWithUUID(uuid: ParcelUuid, timeout: Long): Boolean`  
  Starts scan filtered by advertised service UUID.

- `scanWithManufacturerId(manufacturerId: Int, timeout: Long): Boolean`  
  Starts scan filtered by manufacturer/company ID.

- `scanWithAdvertisingDataType(adType: Int, timeout: Long): Boolean`  
  Starts scan filtered by BLE advertising data type (AD type byte).

- `stopScanning()`  
  Stops active scan.

- `scanSetLegacy(legacy: Boolean)`  
  Enables/disables legacy scan mode when supported.

- `scanResults(): ArrayList<BlueNoxDevice>`  
  Returns current cached scan/device store results.

- `clearScanResults()`  
  Clears cached scan results.

### Connections

- `connectByAddress(addr: String, callback: BlueNoxDeviceCallbacks): Boolean`  
  Starts connection flow for a discovered device.

- `disconnect(addr: String): Boolean`  
  Disconnects device by address.

- `disconnect(dev: BlueNoxDevice): Boolean`  
  Disconnects provided device object.

- `disconnectAll(): Boolean`  
  Disconnects all devices currently tracked by store.

- `getDeviceByAddress(addr: String): BlueNoxDevice?`  
  Retrieves tracked device by MAC address.

- `getConnectedDevice(): BlueNoxDevice?`  
  Returns first connected device if any.

---

## Device object (`BlueNoxDevice`)

`BlueNoxDevice` provides GATT-level operations and per-device callbacks.

### Listener registration

- `addListener(callback: BlueNoxDeviceCallbacks?): Boolean`  
  Registers a device callback listener.

- `removeListener(callback: BlueNoxDeviceCallbacks?): Boolean`  
  Unregisters a device callback listener.

- `removeAllListeners(): Boolean`  
  Clears all device callback listeners.

- `registerFlowAdapter(adapter: BlueNoxDeviceFlowAdapter = BlueNoxDeviceFlowAdapter()): BlueNoxDeviceFlowAdapter`  
  Registers a flow adapter and returns it for stream collection.

- `unregisterFlowAdapter(adapter: BlueNoxDeviceFlowAdapter): Boolean`  
  Removes a previously registered flow adapter.

### Connection controls

- `connect(callback: BlueNoxDeviceCallbacks?)`  
  Starts connection with bounded reconnect/backoff behavior.

- `disconnect()`  
  Requests disconnection and clears pending queue work.

- `pairAndBond()`  
  Requests Android bonding for connected device.

- `setBondingPolicy(policy: BlueNoxBondingPolicy)`  
  Configures automatic bond retry/timeout behavior.

- `setBondPinProvider(provider: (() -> String?)?)`  
  Optional callback for supplying a PIN when pairing requests require one.

- `setConnectionProfile(profile: BlueNoxConnectionProfile)`  
  Applies reconnect profile (`AGGRESSIVE`, `BALANCED`, `BATTERY_SAVER`).

- `sessionDiagnostics(): BlueNoxSessionDiagnostics`  
  Returns runtime counters for reconnect/bond/timeout reliability telemetry.

### Characteristic operations

- `requestReadCharacteristic(uuid: String): Boolean`  
  Requests characteristic read by UUID string.

- `requestReadCharacteristic(uuid: UUID): Boolean`  
  Requests characteristic read by UUID object.

- `setNotificationForCharacteristic(uuid: String, enabled: Boolean)`  
  Enables/disables notifications via CCC descriptor write.

- `writeCharacteristicByUUID(uuid: String, data: ByteArray?, response: Boolean): Boolean`  
  Writes to characteristic by UUID. Returns true if write request started.

- `writeCharacteristicSplitByUUID(uuid: String, data: ByteArray?, response: Boolean, maxChunkSize: Int): Int`  
  Splits payload and performs chunked writes. Returns number of chunks started.

### Advanced GATT controls

- `requestMtu(mtu: Int): Boolean`  
  Requests MTU update (23..517).

- `requestConnectionPriority(priority: Int): Boolean`  
  Requests connection priority (`BALANCED`, `HIGH`, `LOW_POWER`).

- `refreshGattCache(): Boolean`  
  Attempts GATT cache refresh via reflection.

- `beginReliableWriteTransaction(): Boolean`  
  Begins reliable write transaction.

- `executeReliableWriteTransaction(): Boolean`  
  Executes reliable write transaction.

- `abortReliableWriteTransaction(): Boolean`  
  Aborts reliable write transaction.

### Beacon decoding helpers

- `decodedBeaconFrames(): List<BlueNoxBeaconFrame>`  
  Decodes known beacon frames from the latest scan record attached to this device.

`BlueNoxBeaconFrame` currently supports:

- `IBeacon(uuid, major, minor, txPower, companyId)`
- `EddystoneUid(txPower, namespaceIdHex, instanceIdHex)`
- `EddystoneUrl(txPower, url)`
- `EddystoneTlm(version, batteryMilliVolts, temperatureCelsius, advertisementCount, uptimeSeconds)`
- `EddystoneEid(txPower, ephemeralIdHex)`

---

## Callback contract (`BlueNoxDeviceCallbacks`)

Key callbacks used by most apps:

- `uiDeviceConnected(...)`
- `uiDeviceDisconnected(...)`
- `uiAvailableServices(...)`
- `uiCharacteristicUpdated(...)`
- `uiCharacteristicRead(...)`
- `uiMtuUpdated(...)`
- `uiConnectionUpdated(...)`
- `uiServicesChanged(...)`
- `uiOperationFailure(...)`

### Failure taxonomy

Structured failure reasons are emitted via `uiOperationFailure`:

- `PERMISSION_DENIED`
- `INVALID_ARGUMENT`
- `API_NOT_SUPPORTED`
- `CONNECT_GATT_RETURNED_NULL`
- `CONNECT_RETRY_EXHAUSTED`
- `BOND_REQUEST_REJECTED`
- `BOND_RETRY_EXHAUSTED`
- `BOND_TIMEOUT`
- `BOND_FAILED`
- `OPERATION_START_FAILED`
- `OPERATION_TIMEOUT`
- `CACHE_REFRESH_FAILED`

### Bond-state events

- `uiBondStateEvent(device, state, detail)`  
  Emits lifecycle state updates (`BONDING`, `BONDED`, `UNBONDED`, `RETRYING`, `FAILED`, `PIN_REQUESTED`).

---

## Flow adapter (`BlueNoxDeviceFlowAdapter`)

`BlueNoxDeviceFlowAdapter` bridges callbacks to `SharedFlow`.

- `events: SharedFlow<BlueNoxDeviceEvent>`  
  Stream to collect in coroutine scopes.

Event variants:

- `Connection(address, connected)`
- `CharacteristicValue(address, characteristicUuid, value, source)`
- `Mtu(address, mtu)`
- `ConnectionParameters(address, interval, latency, timeout, status)`
- `ServicesChanged(address)`
- `Bond(address, bonded)`
- `BondState(address, state, detail)`
- `Failure(address, reason, detail, characteristicUuid)`

---

## Mock test harness (`BlueNoxMockBleHarness`)

Use `BlueNoxMockBleHarness` to replay deterministic BLE scenarios without hardware dependencies.

- `runScenario(steps: List<BlueNoxMockStep>)`
- `cancel()`

Each `BlueNoxMockStep` can emit:

- `CONNECTED`
- `DISCONNECTED`
- `MTU_CHANGED`
- `BOND_STATE`
- `FAILURE`

---

## Best-practice usage checklist

- Initialize once after runtime permissions are granted.
- Keep a single manager singleton for process lifetime.
- Use `uiOperationFailure` to centralize retry/fallback policy.
- Prefer `BlueNoxDeviceFlowAdapter` for stream-oriented app architecture.
- Call `uninitialize()` on app shutdown path to release BLE resources.
