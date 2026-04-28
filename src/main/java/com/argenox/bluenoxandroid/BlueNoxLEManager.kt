package com.argenox.bluenoxandroid
/*
 * Copyright (c) 2015-2026, Argenox Technologies LLC
 *
 * Licensed under the terms in the LICENSE file at the module root.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL ARGENOX TECHNOLOGIES LLC BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * File:    BluenoxLEManager.kt
 * Summary: BlueNox LE Manager
 *
 **********************************************************************************/

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.argenox.bluenoxandroid.BlueNoxDebug.DebugLevels
import com.argenox.bluenoxandroid.BlueNoxDevice
import com.argenox.bluenoxandroid.BlueNoxDeviceCallbacks
import com.argenox.bluenoxandroid.BlueNoxDeviceStore
import com.argenox.bluenoxandroid.BluenoxGATTCallback
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.ParcelUuid
import androidx.core.content.ContextCompat.registerReceiver
import com.argenox.bluenoxandroid.BLEUUIDs
import com.argenox.bluenoxandroid.ProximityEngine
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


/**
 * Singleton entry point for BLE scanning, device discovery, connection orchestration,
 * and manager-level events.
 *
 * Obtain the shared instance with [getInstance]. Call [initialize] after runtime
 * permissions from [requiredPermissions] are granted, then use scan/connect APIs.
 * Subscribe to [readinessState] or [readinessStatus] for permission, adapter, and
 * location-service readiness.
 */
public class BluenoxLEManager
{
    private val mProximityEngine: ProximityEngine? = null

    private var isScanning: Boolean = false
    private var useLegacyScanning = true
    private var mBlueNoxDeviceStore: BlueNoxDeviceStore? = null

    private var mCurFilters: List<ScanFilter>? = null
    private var mCurSettings: ScanSettings? = null
    private var scanManufacturerIdFilter: Int? = null
    private var scanAdvertisingDataTypeFilter: Int? = null

    private var mActiveDevice : BlueNoxDevice? = null
    private val readinessStateFlow = MutableStateFlow(BlueNoxManagerReadinessState.Uninitialized)
    private val readinessStatusFlow = MutableStateFlow(BlueNoxManagerReadinessStatus.UNINITIALIZED)

    /* defines (in milliseconds) how often RSSI should be updated */
    private val RSSI_UPDATE_TIME_INTERVAL = 1500 // 1.5 seconds
    private var SCAN_PERIOD: Long         = 10000 /* Maximum time for scan to le: Device Found:ast */

    private var mdeviceClass: Class<*>? = null

    private val dbgObj = BlueNoxDebug(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR)

    private class BluenoxDebugItem(
        val functionName: String,
        val lineNumber: Int,
        val timestamp: Instant,
        val message: String,
        val lvl: BlueNoxDebug.DebugLevels
    )
    {

        fun intToBytes(i: Int): ByteArray =
            ByteBuffer.allocate(Int.SIZE_BYTES).putInt(i).array()

        fun longToBytes(i: Long): ByteArray =
            ByteBuffer.allocate(Long.SIZE_BYTES).putLong(i).array()

        fun getBytes() :ByteArray
        {
            var array : ByteArray = byteArrayOf(0xaa.toByte(), 0x55)
            array += longToBytes(timestamp.toEpochMilli())
            array += intToBytes(lvl.ordinal)
            array += functionName.toByteArray()
            array += intToBytes(lineNumber)
            array += message.toByteArray()

            return array
        }
    }

    private class BluenoxDebugLog
    {
        val debugLogList = ArrayList<BluenoxDebugItem>();
        var tracingEnabled : Boolean= false

        fun getBytes() : ByteArray
        {
            var array = byteArrayOf(0xaa.toByte(), 0x55)

            for(item in debugLogList)
            {
                array += item.getBytes()
            }

            return array
        }

        fun add(item: BluenoxDebugItem)
        {
            debugLogList.add(item)
        }

        fun setTracing(enabled: Boolean)
        {
            tracingEnabled = enabled
        }
    }

    /**
     * Events handled in Callback
     *
     */
    private enum class BluenoxThreadEvents(val evt: Int)
    {

        /**
         * BlueNox Initialization Complete
         *
         * Called when BlueNox initialization is complete and it is ready to have
         * APIs called
         */
        BLUENOX_THREAD_EVT_TIMER_TICK(0),

        /**
         * BlueNox Initialization Complete
         *
         * Called when BlueNox initialization is complete and it is ready to have
         * APIs called
         */
        BLUENOX_THREAD_EVT_TIMER_FINISH(1),

        /**
         * Device Connected Event
         *
         * Called when a device connects successfully
         */
        BLUENOX_THREAD_EVT_EVT(2),

        /**
         * BlueNox Discovery Done Timer Event
         *
         */
        BLUENOX_THREAD_EVT_DISCOVERY_TIMER_DONE(3),
    }


    /**
     * Events handled in Callback
     *
     */
    public enum class BluenoxEvents(val evt: Int) {

        /**
         * BlueNox Initialization Complete
         *
         * Called when BlueNox initialization is complete and it is ready to have
         * APIs called
         */
        BLUENOX_EVT_INIT_COMPLETE(0),

        /**
         * Scanning Started
         *
         * Called when a device connects successfully
         */
        BLUENOX_EVT_SCAN_START(1),


        /**
         * Scanning Stopped
         *
         * Called when a device connects successfully
         */

        BLUENOX_EVT_SCAN_STOP(2),

        /**
         * Device Connection Failed Event
         *
         * Called when a device connection fails or times out, either the device
         * is unreachable or another error has occurred
         */
        BLUENOX_EVT_DEVICE_FOUND(3),

        /**
         * Device Connected Event
         *
         * Called when a device connects successfully
         */
        BLUENOX_EVT_DEVICE_CONNECTED(4),



        /**
         * Device Connection Failed Event
         *
         * Called when a device connection fails or times out, either the device
         * is unreachable or another error has occurred
         */
        BLUENOX_EVT_DEVICE_CONNECTION_FAILED(5),

        /**
         * Device Disconnected Event
         *
         * Called when a device connection fails or times out, either the device
         * is unreachable or another error has occurred
         */
        BLUENOX_EVT_DEVICE_DISCONNECTED(6),

        /**
         * Device Bonded Event
         *
         * Called when a device has successfully bonded
         */
        BLUENOX_EVT_DEVICE_BONDED(7),

        /**
         * Device Unbonded Event
         *
         * Called when a device bond has been removed
         */
        BLUENOX_EVT_DEVICE_UNBONDED(8),

        /**
         * Device Audio Connected Event
         *
         * Called when a device's audio connection has connected
         */
        BLUENOX_EVT_DEVICE_AUDIO_CONNECTED(9),

        /**
         * Device Audio Disconnected Event
         *
         * Called when a device's audio connection has disconnection
         */

        BLUENOX_EVT_DEVICE_AUDIO_DISCONNECTED(10),

        /**
         * Discovery Completed
         *
         * Called when the device discovery process has completed and devices may be available
         */

        BLUENOX_EVT_DISCOVERY_COMPLETE(11),
    }

    /**
     * BlueNox Device Type
     *
     */
    public enum class BluenoxDeviceType(val type: Int) {
        BLUENOX_TYPE_UNKNOWN(0),
        BLUENOX_TYPE_BT_LE(1),
        BLUENOX_TYPE_BT_CLASSIC(2),
        BLUENOX_TYPE_BT_DUAL(3),
    }

    /**
     * BlueNox Device Audio
     *
     */
    public enum class BluenoxAudioType(val type: Int) {
        BLUENOX_AUDIO_NONE(0),
        BLUENOX_AUDIO_UNKNOWN(1),
        BLUENOX_AUDIO_HEADSET(2),
        BLUENOX_AUDIO_A2DP(3),
    }

    public  class BlueNoxClassicDevice(var n: String?, var addr: String?, var t: BluenoxDeviceType, var a: BluenoxAudioType) {
        var name = n
        var address = addr
        var type = t
        var audioConnected = false
        var audioType = a

        fun setAudioConnectionState(connected: Boolean)
        {
            audioConnected = connected
        }
    }

    /**
     * Library build/version string embedded in the manager singleton.
     */
    fun getVersion(): String {
        return version
    }
    /**
     * Registers an event callback handler to the manager
     *
     * The callback will be notified of the events specified by BluenoxEvents
     *
     * This functions ensures that all necessary permissions have been
     *
     * @param handler is the callback handler function being registered
     *
     */
    public fun registerCallback(handler: (evt: BluenoxEvents, String, String) -> Unit) {

        callbackList.add(handler)
    }

    /**
     * Unregisters an event callback
     *
     * @param handler is the callback handler function being unregistered
     *
     */
    public fun unregisterCallback(handler: (evt: BluenoxEvents, String, String) -> Unit): Boolean {
        return callbackList.remove(handler)
    }

    /**
     * Validates Permissions ensuring all required permissions are enabled
     *
     * If this function returns false, some of the required permissions provided
     * by requiredPermissions() was denied
     *
     * @return true if all required permissions are allowed, false otherwise
     */
    public fun validatePermissions() : Boolean
    {
        initPermissions()

        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
            "Validating Permissions")

        for (perm in permissionList) {
            if (ContextCompat.checkSelfPermission(
                    managerContext,
                    perm) == PackageManager.PERMISSION_DENIED)
            {
                return false
            }
        }

        return true
    }

    /**
     * Optional custom [BlueNoxDevice] subclass used when materializing devices from scan results.
     *
     * @param c Must be a non-abstract [BlueNoxDevice] subclass with a no-arg constructor, or null to use [BlueNoxDevice].
     */
    @Suppress("unused")
    fun setDeviceClass(c: Class<*>?) {
        this.mdeviceClass = c
    }

    /**
     * Provides a list of Required Permissions
     *
     * @return string list of permissions required for the manager to operate
     */
    public fun requiredPermissions() : Array<String>
    {
        initPermissions()
        return permissionList.toTypedArray()
    }

    /**
     * Starts GATT connect for a device already in the scan store (must have been seen in scan or added explicitly).
     *
     * @param addr MAC address of the peripheral.
     * @param callback GATT and device lifecycle callbacks.
     * @return true if [BlueNoxDevice.connect] was started, false if the device is missing or [Manifest.permission.BLUETOOTH_CONNECT] is denied.
     */
    @SuppressLint("MissingPermission")
    public fun connectByAddress(addr: String, callback: BlueNoxDeviceCallbacks): Boolean
    {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {

            if(mBlueNoxDeviceStore != null) {
                var device = mBlueNoxDeviceStore!!.findDeviceByAddress(addr)
                if(device == null) {
                    return false
                }

                device.connect(callback)

                return true
            }
        }

        return false
    }

    /**
     * Connects to a peripheral by MAC, creating a [BlueNoxDevice] in the store if it was not seen in a recent scan.
     *
     * @param addr Bluetooth hardware address (e.g. `"AA:BB:CC:DD:EE:FF"`).
     * @param callback GATT and device lifecycle callbacks.
     * @return true if a connect was started, false if permissions are missing or the address is invalid.
     */
    @SuppressLint("MissingPermission")
    fun connectByMac(addr: String, callback: BlueNoxDeviceCallbacks): Boolean {
        if (addr.isBlank()) {
            return false
        }
        if (connectByAddress(addr, callback)) {
            return true
        }
        if (!checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return false
        }
        val target = runCatching { bluetoothAdapter.getRemoteDevice(addr) }.getOrNull() ?: return false
        mBlueNoxDeviceStore?.addDevice(target, mdeviceClass, 0, null)
        return connectByAddress(addr, callback)
    }

    /**
     * Scans for a specific address then starts [connectByAddress] when the device is found.
     *
     * Registers a temporary manager callback; it is removed when the device is found or when the scan stops.
     *
     * @param addr Target MAC address.
     * @param timeout Scan duration in milliseconds (same semantics as [scanWithAddress]).
     * @param callback GATT callbacks passed through to [connectByAddress].
     * @return false if the address is blank, scan could not start, or the device was already connectable from the store.
     */
    fun scanAndConnect(addr: String, timeout: Long, callback: BlueNoxDeviceCallbacks): Boolean {
        if (addr.isBlank()) {
            return false
        }
        if (connectByAddress(addr, callback)) {
            return true
        }

        var completed = false
        lateinit var scanHandler: (evt: BluenoxEvents, String, String) -> Unit
        scanHandler = { evt, _, eventAddr ->
            if (!completed) {
                if (evt == BluenoxEvents.BLUENOX_EVT_DEVICE_FOUND &&
                    eventAddr.equals(addr, ignoreCase = true)
                ) {
                    completed = true
                    stopScanning()
                    Handler(Looper.getMainLooper()).post { unregisterCallback(scanHandler) }
                    connectByAddress(addr, callback)
                } else if (evt == BluenoxEvents.BLUENOX_EVT_SCAN_STOP) {
                    completed = true
                    Handler(Looper.getMainLooper()).post { unregisterCallback(scanHandler) }
                }
            }
        }
        registerCallback(scanHandler)
        val started = scanWithAddress(addr, timeout)
        if (!started) {
            unregisterCallback(scanHandler)
            return false
        }
        return true
    }


    /**
     * Returns the [BlueNoxDevice] for [addr] if it exists in the current scan/device store.
     *
     * @param addr MAC address of the peripheral.
     */
    @SuppressLint("MissingPermission")
    public fun getDeviceByAddress(addr: String): BlueNoxDevice?
    {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {

            if(mBlueNoxDeviceStore != null) {
                var device = mBlueNoxDeviceStore!!.findDeviceByAddress(addr)
                if(device == null) {
                    return null
                }

                return device
            }
        }
        return null
    }

    /**
     * Returns the first device in the store whose GATT profile reports [BluetoothProfile.STATE_CONNECTED], if any.
     */
    @SuppressLint("MissingPermission")
    public fun getConnectedDevice(): BlueNoxDevice?
    {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {

            if(mBlueNoxDeviceStore != null) {
                var device = mBlueNoxDeviceStore!!.findConnectedDevice()

                return device
            }
        }
        return null
    }

    /**
     * Disconnects the device with the given MAC if it exists in the store.
     *
     * @param addr MAC address of the peripheral to disconnect.
     * @return true if a matching device was found and disconnect was requested.
     */
    @SuppressLint("MissingPermission")
    public fun disconnect(addr: String): Boolean
    {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {

            if(mBlueNoxDeviceStore != null) {
                var device = mBlueNoxDeviceStore!!.findDeviceByAddress(addr)
                if(device == null) {
                    return false
                }

                device.disconnect()
                return true
            }
        }

        return false
    }

    /**
     * Disconnects the given [BlueNoxDevice] if [Manifest.permission.BLUETOOTH_CONNECT] is granted.
     *
     * @param dev Device wrapper returned from scan or [getDeviceByAddress].
     */
    @SuppressLint("MissingPermission")
    public fun disconnect(dev: BlueNoxDevice): Boolean
    {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            dev.disconnect()
            return true
        }

        return false
    }

    /**
     * Requests disconnect on every device currently held in the store.
     */
    @SuppressLint("MissingPermission")
    public fun disconnectAll(): Boolean
    {
        if(mBlueNoxDeviceStore != null) {

            for (i in 0..mBlueNoxDeviceStore!!.count()) {
                var device = mBlueNoxDeviceStore!!.getDeviceFromIndex(i)
                if(device != null) {
                    device.disconnect()
                }
            }

            return true
        }

        return false
    }

    /**
     * Copy of all [BlueNoxDevice] instances currently in the scan/device store.
     */
    @SuppressLint("MissingPermission")
    public fun scanResults() : ArrayList<BlueNoxDevice>
    {
        val leList = ArrayList<BlueNoxDevice>()

        leList.addAll(mBlueNoxDeviceStore?.deviceList!!.toList())

        return leList
    }

    /**
     * Removes all entries from the scan/device store (does not necessarily disconnect).
     */
    public fun clearScanResults()
    {
        mBlueNoxDeviceStore?.removeAll()
    }

    /**
     * Lists paired (bonded) devices from the platform adapter with basic type and audio metadata.
     */
    @SuppressLint("MissingPermission")
    public fun bondedDevices() : ArrayList<BlueNoxClassicDevice>
    {
        val bondedList = ArrayList<BlueNoxClassicDevice>()
        val bluetoothHeadsetObject = bluetoothHeadset

        for(d in bluetoothAdapter.bondedDevices)
        {
            var t : BluenoxDeviceType = BluenoxDeviceType.BLUENOX_TYPE_UNKNOWN

            if(d != null) {

                val type = d.type

                when (type) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> {
                        t = BluenoxDeviceType.BLUENOX_TYPE_BT_CLASSIC
                    }
                    BluetoothDevice.DEVICE_TYPE_LE -> {
                        t = BluenoxDeviceType.BLUENOX_TYPE_BT_LE
                    }
                    BluetoothDevice.DEVICE_TYPE_DUAL -> {
                        t = BluenoxDeviceType.BLUENOX_TYPE_BT_DUAL
                    }
                }
                val audioType = getAudioType(d)

                val device = BlueNoxClassicDevice(d.name, d.address, t, audioType)

                if(bluetoothHeadsetObject != null) {

                    val connected = bluetoothHeadsetObject.isAudioConnected(d)
                    device.setAudioConnectionState(connected)
                }

                bondedList.add(device)
            }
        }
        return bondedList
    }

    /**
     * Provides the type of audio provided by the device
     *
     * @param d is the Bluetooth Device
     *
     * @return BlueNox Audio Type
     */
    @SuppressLint("MissingPermission")
    private fun getAudioType(d: BluetoothDevice) : BluenoxAudioType
    {
        var audioType = BluenoxAudioType.BLUENOX_AUDIO_UNKNOWN

        val bluetoothHeadsetObject = bluetoothHeadset
        val bluetoothA2dpObject = bluetoothA2dp

        val headsetDevices = bluetoothHeadsetObject?.getDevicesMatchingConnectionStates(intArrayOf(BluetoothProfile.STATE_CONNECTED,
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTED,
            BluetoothProfile.STATE_DISCONNECTING))

        if(headsetDevices != null && headsetDevices.size > 0) {
            if (headsetDevices.contains(d)) {
                audioType = BluenoxAudioType.BLUENOX_AUDIO_HEADSET
            }
        }

        if(audioType == BluenoxAudioType.BLUENOX_AUDIO_UNKNOWN)
        {
            val a2dpDevices = bluetoothA2dpObject?.getDevicesMatchingConnectionStates(intArrayOf(BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_DISCONNECTING))

            if(a2dpDevices != null && a2dpDevices.size > 0) {
                if (a2dpDevices.contains(d)) {
                    audioType = BluenoxAudioType.BLUENOX_AUDIO_A2DP
                }
            }
        }

        return audioType
    }

    private fun initPermissions()
    {
        if(!permissionsInit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                /* API below 31  */
                permissionList.add(Manifest.permission.BLUETOOTH)
                permissionList.add(Manifest.permission.BLUETOOTH_ADMIN)
                permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                permissionsInit = true
            } else {
                /* API above 31  */
                permissionList.add(Manifest.permission.BLUETOOTH_SCAN)
                permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
                permissionList.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                permissionsInit = true
            }
        }
    }

    /**
     * Sets the minimum log level for internal [BlueNoxDebug] output from this manager.
     *
     * @param lvl Minimum level to print (errors only vs verbose debug).
     */
    public fun setDebugLevel(lvl: DebugLevels) {
        dbgObj.setDebugLevel(lvl)
    }

    private fun initThread()
    {
        threadTimer = object: CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                queueEventId(BluenoxThreadEvents.BLUENOX_THREAD_EVT_TIMER_TICK)
            }

            override fun onFinish() {
                queueEventId(BluenoxThreadEvents.BLUENOX_THREAD_EVT_TIMER_FINISH)
            }
        }

        discoveryTimer = object: CountDownTimer(7000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                queueEventId(BluenoxThreadEvents.BLUENOX_THREAD_EVT_DISCOVERY_TIMER_DONE)
            }
        }

        thread {
            try {

                threadTimer.start()

                while (runThread) {
                    //consume(queue.take());

                    val evt: BluenoxEventInfo? = bluenoxThreadQueue.take()

                    if (evt != null) {

                        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_INFO, MODULE_TAG,
                            "Thread Event ${evt.id} ${evt.name}")

                        when(evt.id)
                        {
                            BluenoxThreadEvents.BLUENOX_THREAD_EVT_TIMER_TICK -> Unit
                            BluenoxThreadEvents.BLUENOX_THREAD_EVT_TIMER_FINISH ->{

                            }
                            BluenoxThreadEvents.BLUENOX_THREAD_EVT_EVT -> {

                                /* Send event */
                                for(c in callbackList)
                                {
                                    c.invoke(evt.evt, evt.name, evt.addr)
                                }
                            }

                            BluenoxThreadEvents.BLUENOX_THREAD_EVT_DISCOVERY_TIMER_DONE -> {
                                /* Send event */
                                for(c in callbackList)
                                {
                                    c.invoke(BluenoxEvents.BLUENOX_EVT_DISCOVERY_COMPLETE, "", "")
                                }
                            }
                        }
                    }
                }
            }
            catch (ex: InterruptedException )
            {
                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR, MODULE_TAG,
                    "Interrupted exception $ex")

            }
        }
    }

    /**
     * Prepares Bluetooth services, the device store, background thread, and broadcast receivers.
     *
     * Call once per process (typically from `Application` or after permission grant). Requires
     * all [requiredPermissions] to be granted first.
     *
     * @param context Any [Context]; [Context.getApplicationContext] is used internally.
     * @return true if initialization succeeded; false if permissions are missing or setup failed.
     */
    fun initialize(context: Context) : Boolean {

        managerContext = context.applicationContext
        runThread = true

        initPermissions()

        val permissionsReady = validatePermissions()
        if(!permissionsReady) {
            return false
        }

        bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        mBlueNoxDeviceStore = BlueNoxDeviceStore()
        bluenoxThreadQueue.clear()

        initThread()

        // Register the Broadcast Receiver to receive information
        val filter = IntentFilter();

        //filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        //filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        //filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)

//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            filter.addAction(BluetoothDevice.ACTION_ALIAS_CHANGED)
//        }

        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(LocationManager.MODE_CHANGED_ACTION)

//        filter.addAction(BluetoothDevice.ACTION_CLASS_CHANGED)
//        filter.addAction(BluetoothDevice.ACTION_FOUND)
//        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED)
//        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
//        filter.addAction(BluetoothDevice.ACTION_UUID)

        registerReceiver(context, BlueNoxDeviceReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true

        initComplete = true
        refreshReadinessState()
        return true;
    }


    /**
     * Whether [initialize] completed successfully and [uninitialize] has not cleared state.
     */
    fun isInitialized(): Boolean
    {
        return initComplete
    }

    /**
     * Hot stream of detailed readiness flags; updates when Bluetooth, permissions, or location mode change.
     */
    fun readinessState(): StateFlow<BlueNoxManagerReadinessState> {
        return readinessStateFlow.asStateFlow()
    }

    /**
     * Hot stream of coarse readiness status for UI routing (see [BlueNoxManagerReadinessStatus]).
     */
    fun readinessStatus(): StateFlow<BlueNoxManagerReadinessStatus> {
        return readinessStatusFlow.asStateFlow()
    }

    /** Latest [BlueNoxManagerReadinessState] snapshot without subscribing. */
    fun currentReadinessState(): BlueNoxManagerReadinessState {
        return readinessStateFlow.value
    }

    /** Latest [BlueNoxManagerReadinessStatus] snapshot without subscribing. */
    fun currentReadinessStatus(): BlueNoxManagerReadinessStatus {
        return readinessStatusFlow.value
    }

    /**
     * Suggested runtime permission strings to request before BLE scanning for this OS version.
     *
     * @param deriveLocationFromScan If true and API 31+, includes [Manifest.permission.ACCESS_FINE_LOCATION]
     *        alongside [Manifest.permission.BLUETOOTH_SCAN] (when scan is used for user location).
     */
    fun getRecommendedScanRuntimePermissions(deriveLocationFromScan: Boolean = false): Array<String> {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> emptyArray()
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            deriveLocationFromScan -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            else -> arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

    /**
     * Suggested runtime permission strings before GATT connect on API 31+ ([Manifest.permission.BLUETOOTH_CONNECT]).
     */
    fun getRecommendedConnectRuntimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            emptyArray()
        } else {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    /**
     * Whether the process currently holds permissions needed to start a BLE scan for this API level.
     *
     * @param deriveLocationFromScan Must match how you intend to use scan results (see [getRecommendedScanRuntimePermissions]).
     */
    fun isScanRuntimePermissionGranted(deriveLocationFromScan: Boolean = false): Boolean {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            deriveLocationFromScan -> {
                isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN) &&
                    isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

    /** Whether [Manifest.permission.BLUETOOTH_CONNECT] is granted on API 31+ (always true below 31). */
    fun isConnectRuntimePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    /**
     * Subset of [getRecommendedScanRuntimePermissions] that are not currently granted.
     *
     * @param deriveLocationFromScan Same semantics as [isScanRuntimePermissionGranted].
     */
    fun getMissingScanRuntimePermissions(deriveLocationFromScan: Boolean = false): Array<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return emptyArray()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val coarseGranted = isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
            val fineGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            return if (coarseGranted || fineGranted) {
                emptyArray()
            } else {
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        return getRecommendedScanRuntimePermissions(deriveLocationFromScan)
            .filterNot { isPermissionGranted(it) }
            .toTypedArray()
    }

    /** Subset of [getRecommendedConnectRuntimePermissions] that are not currently granted. */
    fun getMissingConnectRuntimePermissions(): Array<String> {
        return getRecommendedConnectRuntimePermissions()
            .filterNot { isPermissionGranted(it) }
            .toTypedArray()
    }

    /**
     * Recomputes readiness from current adapter, permission, and location state and publishes to both flows.
     *
     * @return The new [BlueNoxManagerReadinessState] value.
     */
    fun refreshReadinessState(): BlueNoxManagerReadinessState {
        val state = computeReadinessState()
        readinessStateFlow.value = state
        readinessStatusFlow.value = computeReadinessStatus(state)
        return state
    }

    /** Returns whether [permission] is granted for [managerContext] while the manager is initialized. */
    private fun isPermissionGranted(permission: String): Boolean {
        if (!initComplete) {
            return false
        }
        return ContextCompat.checkSelfPermission(
            managerContext,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Builds a [BlueNoxManagerReadinessState] from hardware, adapter, permissions, and location services. */
    private fun computeReadinessState(): BlueNoxManagerReadinessState {
        if (!initComplete) {
            return BlueNoxManagerReadinessState.Uninitialized
        }
        val hardwareCompatible = runCatching { checkHardwareCompatible(managerContext) }.getOrDefault(false)
        val bluetoothEnabled = runCatching { bluetoothAdapter.isEnabled }.getOrDefault(false)
        val permissionsGranted = isScanRuntimePermissionGranted(deriveLocationFromScan = true) &&
            isConnectRuntimePermissionGranted()
        val locationServicesEnabled = isLocationServicesEnabled(managerContext)
        val ready = hardwareCompatible && bluetoothEnabled && permissionsGranted && locationServicesEnabled
        return BlueNoxManagerReadinessState(
            initialized = true,
            bluetoothEnabled = bluetoothEnabled,
            permissionsGranted = permissionsGranted,
            locationServicesEnabled = locationServicesEnabled,
            hardwareCompatible = hardwareCompatible,
            ready = ready,
        )
    }

    /** Maps [BlueNoxManagerReadinessState] fields to a single [BlueNoxManagerReadinessStatus] label. */
    private fun computeReadinessStatus(state: BlueNoxManagerReadinessState): BlueNoxManagerReadinessStatus {
        return when {
            !state.initialized -> BlueNoxManagerReadinessStatus.UNINITIALIZED
            !state.hardwareCompatible -> BlueNoxManagerReadinessStatus.BLUETOOTH_NOT_AVAILABLE
            !state.bluetoothEnabled -> BlueNoxManagerReadinessStatus.BLUETOOTH_NOT_ENABLED
            !state.permissionsGranted -> BlueNoxManagerReadinessStatus.PERMISSIONS_NOT_GRANTED
            !state.locationServicesEnabled -> BlueNoxManagerReadinessStatus.LOCATION_SERVICES_NOT_ENABLED
            else -> BlueNoxManagerReadinessStatus.READY
        }
    }

    /** Whether system location is enabled (API 23+); scanning often requires this alongside location permission. */
    private fun isLocationServicesEnabled(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            // LOCATION_MODE is deprecated; use provider enablement on older Android releases.
            runCatching {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
        }
    }

    private fun <T : Parcelable> intentParcelableExtra(
        intent: Intent,
        key: String,
        clazz: Class<T>,
    ): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }

    private fun isBluetoothEnabled(): Boolean
    {
        try {
            if (bluetoothAdapter.isEnabled) {

                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_INFO, MODULE_TAG,
                    "Bluetooth is Enabled")
            } else {
                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_INFO, MODULE_TAG,
                    "Bluetooth is Disabled")
            }
            return bluetoothAdapter.isEnabled

        } catch (e: NullPointerException) {

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_INFO, MODULE_TAG,
                "Exception: Null Pointer")
        }

        return false
    }

    /* Stops current scanning */
    private fun stopScanningInternal()
    {
        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_INFO, MODULE_TAG,
            "Stopping Scanning Internal")

        /* Ensure Adapter is still On, otherwise we shouldn't stop */
        if (isBluetoothEnabled() && isScanning) {
            if (checkRequiredPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                bluetoothLeScanner.stopScan(leScanCallback)
            }
            isScanning = false
        }
    }

    /**
     * Stops an active LE scan if running and emits [BluenoxEvents.BLUENOX_EVT_SCAN_STOP] when applicable.
     */
    fun stopScanning()
    {
        stopScanningInternal()

        if (!isScanning)
            queueEvent(BluenoxEvents.BLUENOX_EVT_SCAN_STOP, "", "")
    }

    /**
     * When true, scan settings use legacy advertising path where supported ([ScanSettings.setLegacy]).
     *
     * @param legacy Pass true for legacy-only behavior on compatible stacks.
     */
    fun scanSetLegacy(legacy: Boolean) {
        useLegacyScanning = legacy
    }

    /**
     * Last device marked active via [setActiveDevice], if any (app-defined hint, not OS connection state).
     */
    fun getActiveDevice() : BlueNoxDevice?
    {
        return mActiveDevice
    }

    /**
     * Records which [BlueNoxDevice] the app considers primary for UI or logging.
     *
     * @param d Device to treat as active.
     */
    fun setActiveDevice(d: BlueNoxDevice )
    {
        mActiveDevice = d
    }


    /**
     * \brief Finds a device with the specified address
     *
     * @details Begins a scan that will collect scan results and find the device with the
     * specified address
     *
     * @param addr is the address of the device to scan for
     * @param timeout is the number of seconds to scan
     *
     */
    @Suppress("unused")
    fun scanWithAddress(addr: String?, timeout: Long): Boolean {
        clearCustomScanFilters()
        var settings: ScanSettings? = null
        val filters: MutableList<ScanFilter> = ArrayList()

        filters.add(ScanFilter.Builder().setDeviceAddress(addr).build())

        try {
            settings =
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setLegacy(useLegacyScanning)
                    .setPhy(BluetoothDevice.PHY_LE_1M)
                    .build()
        } catch (e: NullPointerException) {

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR, MODULE_TAG,
                "Null Pointer Error  $e")
        }

        return startScanning(timeout, filters, settings)
    }

    /**
    * \brief Finds a device with the specified address
    *
    * @details Begins a scan that will collect scan results and find the device with the
    * specified address
    *
    * @param addr is the address of the device to scan for
    * @param timeout is the number of seconds to scan
    *
    */
    @Suppress("unused")
    fun scanWithUUID(uuid: ParcelUuid, timeout: Long): Boolean {
        clearCustomScanFilters()
        var settings: ScanSettings? = null
        val filters: MutableList<ScanFilter> = ArrayList()

        filters.add(ScanFilter.Builder().setServiceUuid(uuid).build())

        try {
            settings =
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setLegacy(useLegacyScanning)
                    .setPhy(BluetoothDevice.PHY_LE_1M)
                    .build()
        } catch (e: NullPointerException) {

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR, MODULE_TAG,
                "Null Pointer Error  $e")
        }

        return startScanning(timeout, filters, settings)
    }

    /**
     * \brief Finds all LE devices
     *
     * @details Begins a scan that will collect scan results and find the device with the
     * specified address
     *
     * @param addr is the address of the device to scan for
     * @param timeout is the number of seconds to scan
     *
     * @return true if scan started, false otherwise
     */
    @Suppress("unused")
    public fun scanForDevices(timeout: Long): Boolean {
        clearCustomScanFilters()
        return scanForDevicesInternal(timeout)
    }

    private fun scanForDevicesInternal(timeout: Long): Boolean {
        var settings: ScanSettings? = null
        val filters: MutableList<ScanFilter> = ArrayList()

        try {
            settings =
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .setLegacy(useLegacyScanning)
                    .setPhy(BluetoothDevice.PHY_LE_1M)
                    .build()
        } catch (e: NullPointerException) {

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR, MODULE_TAG,
                "Null Pointer Error  $e")
        }

        return startScanning(timeout, filters, settings)
    }

    /**
     * Starts a scan and only emits devices advertising the given manufacturer/company ID.
     */
    fun scanWithManufacturerId(manufacturerId: Int, timeout: Long): Boolean {
        clearCustomScanFilters()
        scanManufacturerIdFilter = manufacturerId
        return scanForDevicesInternal(timeout)
    }

    /**
     * Starts a scan and only emits devices containing the given advertising data type.
     *
     * The data type should be an AD type value from the BLE advertising specification
     * (for example, 0x09 for complete local name).
     */
    fun scanWithAdvertisingDataType(adType: Int, timeout: Long): Boolean {
        clearCustomScanFilters()
        scanAdvertisingDataTypeFilter = adType and 0xFF
        return scanForDevicesInternal(timeout)
    }

    private fun clearCustomScanFilters() {
        scanManufacturerIdFilter = null
        scanAdvertisingDataTypeFilter = null
    }

    private fun shouldEmitScanResult(result: ScanResult): Boolean {
        val manufacturerFilter = scanManufacturerIdFilter
        if (manufacturerFilter != null) {
            val record = result.scanRecord ?: return false
            val manufacturers = record.manufacturerSpecificData
            if (manufacturers == null || manufacturers.indexOfKey(manufacturerFilter) < 0) {
                return false
            }
        }

        val adTypeFilter = scanAdvertisingDataTypeFilter
        if (adTypeFilter != null) {
            val payload = result.scanRecord?.bytes ?: return false
            if (!containsAdvertisingDataType(payload, adTypeFilter)) {
                return false
            }
        }

        return true
    }

    private fun containsAdvertisingDataType(payload: ByteArray, adType: Int): Boolean {
        var index = 0
        while (index < payload.size) {
            val length = payload[index].toInt() and 0xFF
            if (length == 0) {
                return false
            }
            val typeIndex = index + 1
            if (typeIndex < payload.size) {
                val currentType = payload[typeIndex].toInt() and 0xFF
                if (currentType == adType) {
                    return true
                }
            }
            index += (length + 1)
        }
        return false
    }

    /* before any action check if BT is turned ON and enabled for us
     * call this in onResume to be always sure that BT is ON when Your
     * application is put into the foreground */
    private fun isBtEnabled(): Boolean {
        return bluetoothAdapter.isEnabled
    }
    /**
     * Initiates Scanning for Bluetooth LE Devices
     *
     * Scanning is asynchronous and will generate the BLUENOX_EVT_DISCOVERY_COMPLETE
     * event once complete.
     *
     */
    @Suppress("unused")
    private fun startScanning(
        scanTimeoutMs: Long,
        filters: List<ScanFilter>?,
        settings: ScanSettings?
    ): Boolean {

        var curSettings = settings;

        /* Guard against scanning if not enabled */
        if (!isBtEnabled()) {
            return false
        }

        if (filters != null) mCurFilters = filters
        if (settings != null) mCurSettings = settings

        SCAN_PERIOD = scanTimeoutMs

        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
            "Starting Scanning")

        if (SCAN_PERIOD > 0) {
            Handler(Looper.getMainLooper()).postDelayed({

                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
                    "Scan Period Ended")

                /* Guard against scan being disabled */
                if (isScanning) {
                    stopScanningInternal()
                    isScanning = false

                    /* Send event indicating scan has stopped */
                    queueEvent(BluenoxEvents.BLUENOX_EVT_SCAN_STOP, "", "")
                }
            }, SCAN_PERIOD)
        }

        if (curSettings == null) {

            curSettings = ScanSettings.Builder() //
                //.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) //
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //
                .build()
        }

        if (!checkRequiredPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            dbgObj.debugPrint(
                BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_WARNING,
                MODULE_TAG,
                "Cannot start scan: missing required scan permission",
            )
            return false
        }

        val scanner = runCatching { bluetoothLeScanner }
            .getOrElse {
                val resolved = bluetoothAdapter.bluetoothLeScanner
                bluetoothLeScanner = resolved
                resolved
            }

        scanner.startScan(filters, curSettings, leScanCallback)
        isScanning = true



        return true
    }

    /**
     * Returns platform GATT connection state for [d.device], or `-1` if [Manifest.permission.BLUETOOTH_CONNECT] is missing.
     *
     * @see [BluetoothProfile.getConnectionState]
     */
    @Suppress("unused")
    fun getConnectionState(d: BlueNoxDevice): Int {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return bluetoothManager.getConnectionState(d.device, BluetoothProfile.GATT)
        }

        return -1
    }

    /** True when [getConnectionState] is [BluetoothProfile.STATE_CONNECTED]. */
    @Suppress("unused")
    fun isDeviceConnected(d: BlueNoxDevice): Boolean {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            val state = bluetoothManager.getConnectionState(d.device, BluetoothProfile.GATT)
            return state == BluetoothProfile.STATE_CONNECTED
        }

        return false
    }

    /** True when connection state is [BluetoothProfile.STATE_CONNECTED] or [BluetoothProfile.STATE_CONNECTING]. */
    @Suppress("unused")
    fun isDeviceConnectedOrAttempting(d: BlueNoxDevice): Boolean {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            val state = bluetoothManager.getConnectionState(d.device, BluetoothProfile.GATT)
            return state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING
        }

        return false
    }

    /** True when connection state is [BluetoothProfile.STATE_DISCONNECTED]. */
    @Suppress("unused")
    fun isDeviceDisconnected(d: BlueNoxDevice): Boolean {
        if(checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            val state = bluetoothManager.getConnectionState(d.device, BluetoothProfile.GATT)
            return state == BluetoothProfile.STATE_DISCONNECTED
        }

        return false
    }

    /**
     * Whether a Bluetooth-related runtime permission is granted for the manager [Context].
     *
     * On API below 31, [Manifest.permission.BLUETOOTH_SCAN], [Manifest.permission.BLUETOOTH_CONNECT],
     * and [Manifest.permission.BLUETOOTH_ADVERTISE] are checked via [Manifest.permission.BLUETOOTH].
     *
     * @param perm One of [Manifest.permission] strings used by this library.
     */
    public fun checkRequiredPermission(perm: String) : Boolean
    {
        val permissionToCheck = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            when (perm) {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE -> Manifest.permission.BLUETOOTH
                else -> perm
            }
        } else {
            perm
        }
        when {
            ContextCompat.checkSelfPermission(
                managerContext, permissionToCheck
            ) == PackageManager.PERMISSION_GRANTED -> {
                return true
            }
        }
        return false
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (!shouldEmitScanResult(result)) {
                return
            }

            /* Add device to Device Store */
            if(mBlueNoxDeviceStore != null)
            {
                mBlueNoxDeviceStore!!.addDevice(result.device,
                                                mdeviceClass,
                                                result.rssi,
                                                result.scanRecord)

                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_INFO, MODULE_TAG,
                    "Device Found:    " + result.device.address)

                val d = mBlueNoxDeviceStore!!.getDeviceFromAddress(result.device.address)
                if(d != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        d.primaryPhy = result.primaryPhy
                        d.secondaryPhy = result.secondaryPhy
                    } else {
                        d.primaryPhy = 0
                        d.secondaryPhy = 0
                    }
                    /* Send event indicating scan has stopped */
                    queueEvent(BluenoxEvents.BLUENOX_EVT_DEVICE_FOUND, d.getName(), d.getMacAddress())
                }
            }
        }
    }

    private fun processDeviceRssi(device: BluetoothDevice, rssi: Int) {
        if (mProximityEngine != null && mProximityEngine.running()) mProximityEngine.updateRSSI(device, rssi)
    }

    /**
     * BroadcastReceiver for Bluetooth adapter state, bond changes, and related intents; refreshes readiness where applicable.
     */
    @SuppressLint("MissingPermission")
    private val BlueNoxDeviceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action.toString()
            val device = intentParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

            if(device == null)
            {
                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
                    "device null")



                return
            }

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
                "BluenoxDeviceReceiver $action")


            when (action)
            {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                LocationManager.MODE_CHANGED_ACTION -> {
                    refreshReadinessState()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {

                    dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
                        "Bond State Changed Event")

                    val deviceBondStateChanged: BluetoothDevice?
                    deviceBondStateChanged =
                        intentParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

                    if (deviceBondStateChanged != null) {
                        val bondState =
                            intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                        val previousBondState =
                            intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

                        if (bondState == BluetoothDevice.BOND_BONDING) {
                            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG, "Device still bonding: " + deviceBondStateChanged.name)
                        } else if (bondState == BluetoothDevice.BOND_BONDED) {
                            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG, "Device Bonded: " + deviceBondStateChanged.name)

                            //queueEvent(BluenoxEvents.BLUENOX_EVT_DEVICE_BONDED, deviceBondStateChanged.name, deviceBondStateChanged.address)
                        }
                        val d = mBlueNoxDeviceStore?.getDeviceFromAddress(deviceBondStateChanged.address)
                        d?.handleBondStateChanged(bondState, previousBondState)
                    }
                }
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val pairingDevice =
                        intentParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            ?: return
                    val d = mBlueNoxDeviceStore?.getDeviceFromAddress(pairingDevice.address) ?: return
                    d.getMainCallback().uiBondStateEvent(
                        d,
                        BlueNoxDeviceCallbacks.BlueNoxBondState.PIN_REQUESTED,
                        "Pairing PIN requested by remote device",
                    )
                    val pin = d.provideBondPin() ?: return
                    runCatching {
                        pairingDevice.setPin(pin.toByteArray())
                        pairingDevice.setPairingConfirmation(true)
                    }.onFailure { ex ->
                        d.getMainCallback().uiOperationFailure(
                            pairingDevice,
                            BlueNoxDeviceCallbacks.BlueNoxFailureReason.BOND_FAILED,
                            "Failed to provide pairing PIN: ${ex.message ?: ex.javaClass.simpleName}",
                            null,
                        )
                    }
                }
            }
        }
    }

    /**
     * Tears down scanning, timers, broadcast receiver, and marks the manager uninitialized.
     *
     * Call from application shutdown or when BLE is no longer needed to avoid receiver leaks.
     */
    fun uninitialize()
    {
        stopScanningInternal()
        runCatching { threadTimer.cancel() }
        runCatching { discoveryTimer.cancel() }
        if (receiverRegistered) {
            runCatching { managerContext.unregisterReceiver(BlueNoxDeviceReceiver) }
            receiverRegistered = false
        }
        runThread = false
        queueEventId(BluenoxThreadEvents.BLUENOX_THREAD_EVT_TIMER_FINISH)
        initComplete = false
        readinessStateFlow.value = BlueNoxManagerReadinessState.Uninitialized
        readinessStatusFlow.value = BlueNoxManagerReadinessStatus.UNINITIALIZED
    }


    private class BluenoxEventInfo(
        val id: BluenoxThreadEvents,
        val evt: BluenoxEvents,
        val name: String,
        val addr: String
    )
    {

    }

    private fun queueEventId(id: BluenoxThreadEvents)
    {
        val event = BluenoxEventInfo(id,
            BluenoxEvents.BLUENOX_EVT_INIT_COMPLETE,
            "",
            "")
        var queued = bluenoxThreadQueue.offer(event)
        if (!queued) {
            // Drop oldest item when queue is saturated to avoid crashing on scan bursts.
            bluenoxThreadQueue.poll()
            queued = bluenoxThreadQueue.offer(event)
        }

        if(!queued)
        {
            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR, MODULE_TAG,
                "Unable to Queue Event Init Complete")
        }
    }

    private fun queueEvent(evt: BluenoxEvents, name: String, addr: String)
    {
        val event = BluenoxEventInfo(BluenoxThreadEvents.BLUENOX_THREAD_EVT_EVT, evt, name, addr)
        var queued = bluenoxThreadQueue.offer(event)
        if (!queued) {
            // Drop oldest item when queue is saturated to avoid crashing on scan bursts.
            bluenoxThreadQueue.poll()
            queued = bluenoxThreadQueue.offer(event)
        }

        if(!queued)
        {
            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR, MODULE_TAG,
                "Unable to Queue Event" + evt.name)
        }
    }

    /**
     * Enables or disables tracing capabilities for debug
     *
     */
    public fun setTracing(enabled: Boolean)
    {
        debugLog.setTracing(enabled)
    }

    /**
     * Retrieves tracing data
     *
     */
    public fun getTracingData():ByteArray
    {
        return debugLog.getBytes()
    }

    /**
    * \brief Check whether this hardware supports BLE
    *
    * BLE Manager is a singleton class which means only one is instantiated per application
    *
     * @param ctx is the current context
    *
    * @return true if BLE available, false otherwise
    */
    public fun checkHardwareCompatible(ctx: Context): Boolean {

        /* Check general Bluetooth LE Service availability */
        return ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: BluenoxLEManager? = null

        private const val version = "0.1.003"

        private val permissionList = ArrayList<String>();
        private var permissionsInit = false

        @SuppressLint("StaticFieldLeak")
        private lateinit var managerContext : Context

        private const val MODULE_TAG : String = "BLNXBTMGR"

        val callbackList = arrayListOf<(BluenoxEvents, String, String) -> Unit>()

        private var bluetoothHeadset: BluetoothHeadset? = null
        private var bluetoothA2dp: BluetoothA2dp? = null

        private lateinit var bluetoothManager: BluetoothManager
        private lateinit var bluetoothAdapter: BluetoothAdapter
        private lateinit var bluetoothLeScanner: BluetoothLeScanner


        var initComplete = false
        private val debugLog = BluenoxDebugLog()

        private lateinit var threadTimer : CountDownTimer
        private lateinit var discoveryTimer : CountDownTimer


        private var runThread = true
        private var receiverRegistered = false

        private val bluenoxThreadQueue: ArrayBlockingQueue<BluenoxEventInfo> = ArrayBlockingQueue(10, true)

        /**
         * Returns the process-wide [BluenoxLEManager] singleton, creating it on first use.
         */
        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: BluenoxLEManager().also { instance = it }
            }
    }
}