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
 * File:    BlueNoxService.kt
 * Summary: Android service implementation for BlueNox BLE operations
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

/*
class BlueNoxService : Service() {
    interface NotificationInterface {
        fun notificationBlueNoxEvent(evt: Int)
        fun notificationBlueNoxDeviceEvent(dev: BlueNoxDevice?, evt: Int)
        fun notificationDeviceData(
            dev: BlueNoxDevice?,
            c: BluetoothGattCharacteristic?,
            data: ByteArray?
        )

        fun notificationDeviceRssi(dev: BlueNoxDevice?, rssi: Int, phy_pri: Int, phy_sec: Int)
        fun AdvertisingReportNotif(dev: BlueNoxDevice?, rssi: Int, phy: Int, data: ByteArray?)
    }

    private val mIBinder: IBinder = ServiceBinder()
    private val MODULE_TAG = "BlueNoxManager Service"

    /** BlueNox Manager Object  */
    private var mServiceStarted = false
    var isInitComplete: Boolean = false
        private set
    var isScanning: Boolean = false
        private set

    private val mDebugEnabled = 1

    private val mProxEngine: ProximityEngine?

    private var mActiveDeviceIndex = -1

    private val _notificationMutex: Lock = ReentrantLock(true)

    /* defines (in milliseconds) how often RSSI should be updated */
    private val RSSI_UPDATE_TIME_INTERVAL = 1500 // 1.5 seconds

    private var SCAN_PERIOD: Long = 10000 /* Maximum time for scan to le: Device Found:ast */

    private var mdeviceClass: Class<*>? = null

    /**
     * Used for periodically restarting
     */
    private val mScanningResetInterval: Long = 5000

    private val mConnectionInterval: Long = 500


    private var mParent: Context? = null

    @get:Suppress("unused")
    var manager: BluetoothManager? = null
        private set
    var adapter: BluetoothAdapter? = null
        private set
    val device: BluetoothDevice? = null
    private var mScanCallback: ScanCallback? = null
    private var mLEScanner: BluetoothLeScanner? = null
    private var mBlueNoxDeviceStore: BlueNoxDeviceStore? = null

    private var periodicBLERestart: Timer? = null

    /**
     * Used for periodically restarting
     */
    private var reconnectionTimer: Timer? = null

    private val _mutex: Lock = ReentrantLock(true)


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!mServiceStarted) {
            mServiceStarted = true
            startBackgroundTask(intent, startId)
        }
        return START_NOT_STICKY
    }

    @Suppress("unused")
    private fun startBackgroundTask(intent: Intent, startId: Int) {
        // Start a background thread and begin the processing.

        backgroundExecution()
    }

    /**
     * Listing 9-14: Moving processing to a background Thread
     */
    //This method is called on the main GUI thread.
    private fun backgroundExecution() {
        // This moves the time consuming operation to a child thread.
        val thread = Thread(
            null, doBackgroundThreadProcessing,
            "Background"
        )
        thread.start()
    }

    @Suppress("unused")
    fun setDeviceClass(c: Class<*>?) {
        this.mdeviceClass = c
    }

    private val notificationListenerList: MutableList<NotificationInterface> = ArrayList()

    // Runnable that executes the background processing method.
    private val doBackgroundThreadProcessing = Runnable {
        Looper.prepare()
        @Suppress("unused") var doInitialization = true

        if (doInitialization) {
            Log.d(MODULE_TAG, "Device Manager Thread: " + Thread.currentThread().name)


            // Initialize the Bluetooth LE Manager object. Two parameters are passed: the parent activity
            // which is required to access Bluetooth, as well as the callback handlers. Callbacks below will be received for general
            // activities such as scan results and connections. Once a device is connected, it will receive
            // its own callbacks (not here).


            /* Initialize BLE Manager */
            initialize()

            Log.d(MODULE_TAG, "Initialize BLE MGR")

            // Enable Bluetooth if not already enabled
            if (!isBluetoothEnabled) {
                enableBluetooth()
            }

            if (isBluetoothEnabled) {
                isInitComplete = true
                sendNotificationEventAll(DeviceNotificationEvent.InitializationComplete.constValue())
            }

            doInitialization = false
        }
        Looper.loop()
    }

    override fun onBind(intent: Intent): IBinder? {
        // TODO: Return the communication channel to the service.
        return mIBinder
    }


    fun notificationListenerAdd(listener: NotificationInterface) {
        _notificationMutex.lock()
        notificationListenerList.add(listener)
        _notificationMutex.unlock()
    }

    @Suppress("unused")
    fun notificationListenerRemove(listener: NotificationInterface) {
        _notificationMutex.lock()
        for (nif in notificationListenerList) {
            if (nif != null && nif === listener) {
                notificationListenerList.remove(listener)
            }
        }

        _notificationMutex.unlock()
    }

    /* Send Generic notification to all */
    private fun sendNotificationEventAll(evt: Int) {
        _notificationMutex.lock()
        for (nif in notificationListenerList) {
            nif?.notificationBlueNoxEvent(evt)
        }
        _notificationMutex.unlock()
    }

    /* Send Device notification to all */
    private fun sendDeviceNotificationEventAll(dev: BlueNoxDevice?, evt: Int) {
        _notificationMutex.lock()
        for (nif in notificationListenerList) {
            nif?.notificationBlueNoxDeviceEvent(dev, evt)
        }
        _notificationMutex.unlock()
    }

    /* Send Device notification to all */
    @Suppress("unused")
    fun sendDeviceRssiEventAll(dev: BlueNoxDevice?, rssi: Int, phy_pri: Int, phy_sec: Int) {
        _notificationMutex.lock()
        for (nif in notificationListenerList) {
            nif?.notificationDeviceRssi(dev, rssi, phy_pri, phy_sec)
        }
        _notificationMutex.unlock()
    }

    /* Send Device notification to all */
    @Suppress("unused")
    fun sendDeviceDataNotificationEventAll(
        dev: BlueNoxDevice?,
        c: BluetoothGattCharacteristic?,
        data: ByteArray?
    ) {
        _notificationMutex.lock()
        for (nif in notificationListenerList) {
            nif?.notificationDeviceData(dev, c, data)
        }
        _notificationMutex.unlock()
    }

    /* Send Device notification to all */
    @Suppress("unused")
    fun SendAdvertisingReportNotifAll(dev: BlueNoxDevice?, rssi: Int, phy: Int, data: ByteArray?) {
        _notificationMutex.lock()
        for (nif in notificationListenerList) {
            nif?.AdvertisingReportNotif(dev, rssi, phy, data)
        }
        _notificationMutex.unlock()
    }


    enum class DeviceNotificationEvent(
        private val constValue: Int,
        private val description: String?
    ) {
        Unknown(-1, null),
        InitializationComplete(0, "Scan Started"),
        ScanStarted(1, "Scan Started"),
        ScanStopped(2, "Scan Stopped"),
        DeviceFound(3, "Device Found"),
        DeviceConnected(4, "Device Connected"),
        DeviceDisconnected(5, "Device Disconnected"),
        UpdateReceived(6, "Device Connected"),
        BluetoothOff(7, "Bluetooth Off"),
        BluetoothOn(8, "Bluetooth On"),
        NearDeviceFound(8, "Near Device Found"),
        NearDeviceFailed(8, "Near Device Failed"),
        NoEventExists(1000, "No Event Exists");

        fun constValue(): Int {
            return constValue
        }

        fun description(): String? {
            return if (constValue != -1) description else "unknown gatt status $constValue"
        }

        fun description(altStatus: Int): String? {
            return if (constValue != -1) description else "unknown gatt status $altStatus"
        }

        companion object {
            fun parse(status: Int): DeviceNotificationEvent {
                return when (status) {
                    0 -> InitializationComplete
                    1 -> ScanStarted
                    2 -> ScanStopped
                    3 -> DeviceFound
                    4 -> DeviceConnected
                    5 -> DeviceDisconnected
                    6 -> UpdateReceived
                    7 -> BluetoothOff
                    8 -> BluetoothOn

                    else -> Unknown
                }
            }
        }
    }

    inner class ServiceBinder : Binder() {
        val service: BlueNoxService
            get() = this@BlueNoxService
    }

    var activeDevice: BlueNoxDevice?
        get() {
            if (mActiveDeviceIndex == -1) {
                return null
            }


            val sz: Int = mBlueNoxDeviceStore.getDeviceList().size

            Log.d(MODULE_TAG, "sz, $sz  mActiveDeviceIndex  $mActiveDeviceIndex")

            if (sz > 0 && mActiveDeviceIndex < sz) {
                val dev: BlueNoxDevice = mBlueNoxDeviceStore.getDeviceList().get(mActiveDeviceIndex)
                if (dev == null) Log.d(MODULE_TAG, "getActiveDevice - Device is Null ")
                return mBlueNoxDeviceStore.getDeviceList().get(mActiveDeviceIndex)
            }

            return null
        }
        set(d) {
            mActiveDeviceIndex = mBlueNoxDeviceStore!!.getDeviceIndex(d!!)
            if (mActiveDeviceIndex == -1) {
                Log.d(MODULE_TAG, "mActiveDeviceIndex -1")
                mBlueNoxDeviceStore!!.addDevice(d)

                mActiveDeviceIndex = mBlueNoxDeviceStore!!.getDeviceIndex(d)
            }

            Log.d(
                MODULE_TAG,
                "mActiveDeviceIndex set to " + mActiveDeviceIndex + "for " + d.mACAddress
            )
        }

    val isBluetoothEnabled: Boolean
        get() {
            val manager = mParent!!.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

            if (manager != null) {
                try {
                    val adapter = manager.adapter

                    if (adapter != null) {
                        if (mDebugEnabled > 0) {
                            if (adapter.isEnabled) {
                                Log.i("BlueNox Manager", "Bluetooth is Enabled")
                            } else {
                                Log.i("BlueNox Manager", "Bluetooth is Disabled")
                            }
                        }

                        return adapter.isEnabled
                    } else {
                        // SEVERE ERROR: Adapter is NULL
                        Log.e("BlueNox Manager", "Exception: Adapter is NULL")
                    }
                } catch (e: NullPointerException) {
                    Log.e("BlueNox Manager", "Exception: Adapter is NULL")
                }
            }

            return false
        }

    /*!
     * \brief Finds the Nearest device via RSSI
     *
     * Begins a scan that will collect scan results and find the device with the best RSSI
     *
     * @param None.
     *
     * @return true if BLE available, false otherwise
     *
     */
    @Suppress("unused")
    fun scanForDevices(timeout: Int) {
        var settings: ScanSettings? = null

        if (Build.VERSION.SDK_INT >= 21) {
            settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ScanSettings.Builder() //
                    //.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) //
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY or ScanSettings.MATCH_MODE_AGGRESSIVE) //
                    .build()
            } else {
                ScanSettings.Builder() //
                    //.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) //
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //
                    .build()
            }
        }

        /* Scan quickly for all devices for 5 seconds */
        startScanning(timeout.toLong(), null, settings)
    }

    private var useLegacyScanning = true

    fun scanSetLegacy(legacy: Boolean) {
        useLegacyScanning = legacy
    }

    /*!
     * \brief Finds the Nearest device with the specified address
     *
     * Begins a scan that will collect scan results and find the device with the best RSSI
     *
     * @param None.
     *
     * @return true if BLE available, false otherwise
     */
    @Suppress("unused")
    fun scanWithAddress(addr: String?, timeout: Long) {
        var settings: ScanSettings? = null
        val filters: MutableList<ScanFilter> = ArrayList()

        if (Build.VERSION.SDK_INT >= 21) {
            filters.add(ScanFilter.Builder().setDeviceAddress(addr).build())

            try {
                settings = if (Build.VERSION.SDK_INT >= 26) {
                    ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setLegacy(useLegacyScanning)
                        .setPhy(BluetoothDevice.PHY_LE_CODED)
                        .build()
                } else {
                    ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                }
            } catch (e: NullPointerException) {
                Log.e(MODULE_TAG, "Null Pointer Error")
            }
        }

        /* Scan quickly for all devices for 5 seconds */
        startScanning(timeout, filters, settings)
    }

    /*!
     * \brief Finds devices with matching Service UUID
     *
     * Begins a scan that will collect scan results and find all devices with matching service UUID
     *
     * @param None.
     *
     * @return true if BLE available, false otherwise
     */
    @Suppress("unused")
    fun scanWithServiceUUID(srvcUUIDs: List<UUID?>?, timeout: Int) {
        var settings: ScanSettings? = null
        val filters: MutableList<ScanFilter> = ArrayList()

        if (Build.VERSION.SDK_INT >= 21) {
            if (srvcUUIDs != null) {
                for (srvcUUID in srvcUUIDs) {
                    val scanUUID = ParcelUuid(srvcUUID)
                    val addrFilter = ScanFilter.Builder().setServiceUuid(scanUUID).build()
                    filters.add(addrFilter)
                }
            }

            settings = ScanSettings.Builder() //
                //.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) //
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //
                .build()
        }

        /* Scan for devices */
        startScanning(timeout.toLong(), filters, settings)
    }

    /*!
     * \brief Finds devices with matching Service UUID
     *
     * Begins a scan that will collect scan results and find all devices with matching service UUID
     *
     * @param None.
     *
     * @return true if BLE available, false otherwise
     */
    @Suppress("unused")
    fun scan(timeout: Int) {
        var settings: ScanSettings? = null
        val filters: List<ScanFilter> = ArrayList()

        if (Build.VERSION.SDK_INT >= 21) {
            settings = ScanSettings.Builder() //
                //.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) //
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //
                .build()
        }

        /* Scan for devices */
        startScanning(timeout.toLong(), filters, settings)
    }

    val isLeCodedPhySupported: Boolean
        get() = if (Build.VERSION.SDK_INT < 26) {
            false
        } else {
            adapter!!.isLeCodedPhySupported
        }

    val isLe2MPhySupported: Boolean
        get() = if (Build.VERSION.SDK_INT < 26) {
            false
        } else {
            adapter!!.isLe2MPhySupported
        }

    private val isBtEnabled: Boolean
        /* before any action check if BT is turned ON and enabled for us
     * call this in onResume to be always sure that BT is ON when Your
     * application is put into the foreground */
        get() {
            val manager = mParent!!.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                ?: return false

            val adapter = manager.adapter
            return adapter != null && adapter.isEnabled
        }


    private var mCurFilters: List<ScanFilter>? = null
    private var mCurSettings: ScanSettings? = null

    private fun restartScanning() {
        /*if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner.startScan(mScanFilters, mScanSettings, mScanCallback);
        }*/

        stopScanningInternal()
        startScanning(SCAN_PERIOD, mCurFilters, mCurSettings)
    }


    @Suppress("unused")
    fun scanForNearestDevice(baseUUIDStr: String?, timeout: Int): Boolean {
        val sensorUUID: UUID
        var searchUUIDList: List<UUID?>? = null

        if (isScanning) {
            stopScanning()
        }

        if (!isScanning) {
            if (baseUUIDStr != null) {
                sensorUUID = UUID.fromString(baseUUIDStr)
                searchUUIDList = Arrays.asList(sensorUUID)
            }

            mProxEngine!!.start()
            isScanning = true
            removeAdvertisingDevices()

            /* Scan for devices with all UUIDs */
            scanWithServiceUUID(searchUUIDList, timeout)

            return true
        } else {
            return false
        }
    }

    @Suppress("unused")
    fun scan(baseUUIDStr: String?, timeout: Int): Boolean {
        if (!isScanning) {
            val sensorUUID = UUID.fromString(baseUUIDStr)

            isScanning = true
            removeAdvertisingDevices()

            /* Scan for devices with all UUIDs */
            val searchUUIDList = Arrays.asList(sensorUUID)
            scanWithServiceUUID(searchUUIDList, timeout)

            return true
        } else {
            return false
        }
    }

    /* start scanning for BT LE devices around */
    @Suppress("unused")
    private fun startScanning(
        scanTimeoutMs: Long,
        filters: List<ScanFilter>?,
        settings: ScanSettings?
    ): Boolean

    {
        /* Guard against scanning if not enabled */
        var settings = settings
        if (!isBtEnabled) {
            return false
        }

        if (filters != null) mCurFilters = filters

        if (settings != null) mCurSettings = settings

        SCAN_PERIOD = scanTimeoutMs
        Log.v(MODULE_TAG, "Starting Scanning")

        if (mHandler == null) {
            Log.e(MODULE_TAG, "Handler Null")
        }

        if (SCAN_PERIOD > 0) {
            /* Add code to automatically stop scanning after the scan interval */
            mHandler!!.postDelayed({
                /* Guard against scan being disabled */
                if (isScanning) {
                    stopScanningInternal()
                    isScanning = false

                    /* Send event indicating scan has stopped */
                    sendNotificationEventAll(DeviceNotificationEvent.ScanStopped.constValue())
                }
            }, SCAN_PERIOD)
        }

        if (Build.VERSION.SDK_INT < 21) {
            isScanning = true
            Log.v(MODULE_TAG, "Scanning API < 21")
            adapter!!.startLeScan(mLeScanCallback)
        } else {
            isScanning = true
            Log.v(MODULE_TAG, "Scanning API >= 21")
            if (settings == null) {
                settings = ScanSettings.Builder() //
                    //.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) //
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //
                    .build()
            }

            if (mLEScanner != null) {
                scanLeDevice21(true, filters, settings)
                //mLEScanner.startScan(filters, settings, mScanCallback);
            }
        }

        val periodicBLEScanRestart = false
        if (periodicBLEScanRestart) {
            if (periodicBLERestart != null) {
                periodicBLERestart!!.cancel()
                periodicBLERestart = null
            }

            /* Do restarts only below Nougat (Android 7). Android 7 and above
            * have a anti abuse code that will cause issues if restarted more than
            * 5 times in 30 second timeframe */
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                periodicBLERestart = Timer()
                //Set the schedule function and rate
                periodicBLERestart!!.schedule(
                    object : TimerTask() {
                        override fun run() {
                            if (this.isScanning) {
                                /* Restart Scan */
                                stopScanningInternal()
                                restartScanning()
                            }
                        }
                    },
                    mScanningResetInterval, mScanningResetInterval
                )
            }
        }

        return true
    }


    /* Stops current scanning */
    private fun stopScanningInternal() {
        Log.v(MODULE_TAG, "Stopping Scanning Internal")
        /* Ensure Adapter is still On, otherwise we shouldn't stop */
        if (isBluetoothEnabled && isScanning) {
            if (Build.VERSION.SDK_INT < 21) {
                Log.v(MODULE_TAG, "Scanning API < 21")
                adapter!!.stopLeScan(mLeScanCallback)
            } else {
                scanLeDevice21(false, null, null)
                //mLEScanner.stopScan(mScanCallback);
            }
            isScanning = false
        }
    }

    fun stopScanning() {
        stopScanningInternal()
        if (!isScanning) sendNotificationEventAll(DeviceNotificationEvent.ScanStopped.constValue())

        isScanning = false
    }

    /* initialize BLE and get BT Manager & Adapter */
    @Suppress("unused")
    private fun initialize(): Boolean {
        Log.v(MODULE_TAG, "Initializing BLE Manager")

        mParent = applicationContext

        if (mHandler == null) {
            mHandler = Handler()
        }

        if (manager == null) {
            manager = mParent.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            if (manager == null) {
                return false
            }
        }

        if (adapter == null) {
            adapter = manager!!.adapter
        }
        if (adapter == null) {
            Log.e(MODULE_TAG, "Bluetooth Adapter Still null")
        } else {
            Log.e(MODULE_TAG, "Bluetooth Adapter is not null" + adapter.hashCode())
        }

        if (Build.VERSION.SDK_INT >= 21) {
            if (BluetoothAdapter.getDefaultAdapter() != null) mLEScanner =
                BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
            Log.v(MODULE_TAG, "API >= 21: Using BluetoothLEScanner mLEScanner = $mLEScanner")
        }


        mBlueNoxDeviceStore = BlueNoxDeviceStore(mParent)
        Log.d(MODULE_TAG, "mBlueNoxDeviceStore Initialized $mBlueNoxDeviceStore")

        mParent.registerReceiver(mReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        //restart();
        return true
    }

    private fun updateObjects() {
        if (mHandler == null) {
            mHandler = Handler()
        }

        if (manager == null) {
            manager = mParent!!.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        }

        if (adapter == null) {
            try {
                if (manager != null) {
                    adapter = manager!!.adapter
                }
            } catch (e: NullPointerException) {
                Log.d(MODULE_TAG, "Adapter is Null")
            }
        }
        if (adapter == null) {
            Log.e(MODULE_TAG, "Bluetooth Adapter Still null")
        } else {
            Log.e(MODULE_TAG, "Bluetooth Adapter is not null" + adapter.hashCode())
        }

        if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
            Log.v(MODULE_TAG, "API >= 21: Using BluetoothLEScanner mLEScanner = $mLEScanner")
        }
    }

    @Suppress("unused")
    fun restart() {
        Log.d(MODULE_TAG, "Disabling Bluetooth Adapter")
        BluetoothAdapter.getDefaultAdapter().disable()

        val t = Timer()
        //Set the schedule function and rate
        t.schedule(
            object : TimerTask() {
                override fun run() {
                    Log.d(MODULE_TAG, "Enabling Bluetooth Adapter")
                    BluetoothAdapter.getDefaultAdapter().enable()
                }
            },
            300
        )
    }

    private fun enableBluetooth() {
        if (adapter != null) {
            adapter!!.enable()
        } else {
            Log.e(MODULE_TAG, "Bluetooth Adapter is null")
        }
    }


    /* Broadcast receiver to receive notifications if Bluetooth Adapter state changed */
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action != null) {
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> sendNotificationEventAll(
                            DeviceNotificationEvent.BluetoothOff.constValue()
                        )

                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            stopScanningInternal()
                            this.isScanning = false
                            sendNotificationEventAll(DeviceNotificationEvent.BluetoothOff.constValue())
                        }

                        BluetoothAdapter.STATE_ON -> {
                            updateObjects()

                            if (!this.isInitComplete) {
                                this.isInitComplete = true
                                sendNotificationEventAll(DeviceNotificationEvent.InitializationComplete.constValue())
                            }
                        }

                        BluetoothAdapter.STATE_TURNING_ON -> {}
                    }
                }
            }
        }
    }

    private fun startConnectionTimer() {
        if (reconnectionTimer == null) {
            reconnectionTimer = Timer()
            //Set the schedule function and rate
            reconnectionTimer!!.scheduleAtFixedRate(
                object : TimerTask() {
                    override fun run() {
                        var connectedCount = 0

                        for (dev in mBlueNoxDeviceStore.getDeviceList()) {
                            val state =
                                manager!!.getConnectionState(dev.device, BluetoothProfile.GATT)
                            if (state == BluetoothProfile.STATE_DISCONNECTED && dev.attemptConnection()) {
                                dev.reduceConnectionTimeoutCount(500)

                                if (dev.ConnectionTimeoutExpired()) {
                                    // Re-attempt to connect

                                    Log.d(
                                        MODULE_TAG,
                                        "Timeout Expired. Reattempting connection for " + dev.mACAddress + " " + connectedCount
                                    )
                                    dev.resetConnectionTimeoutCount(4000)

                                    dev.gatt!!.connect()
                                }
                            } else if (state == BluetoothProfile.STATE_CONNECTED) {
                                connectedCount++
                            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                                Log.d(MODULE_TAG, "Device " + dev.mACAddress + " Connecting")
                            } else if (state == BluetoothProfile.STATE_DISCONNECTING) {
                                Log.d(MODULE_TAG, "Device " + dev.mACAddress + " Disconnecting")
                            }
                        }

                        Log.d(
                            MODULE_TAG,
                            "Total Devices: " + mBlueNoxDeviceStore.getDeviceList().size + ". Connected: " + connectedCount
                        )

                        if (connectedCount == mBlueNoxDeviceStore.getDeviceList().size) {
                            Log.d(MODULE_TAG, "All Devices connected, stopping reconnection timer")
                            reconnectionTimer!!.cancel()
                            reconnectionTimer!!.purge()
                            reconnectionTimer = null
                        }
                    }
                },
                mConnectionInterval, mConnectionInterval
            )
        }
    }

    fun disconnectDevice(devAddr: String?) {
        val d: BlueNoxDevice = mBlueNoxDeviceStore!!.getDevice(devAddr)

        if (d != null) {
            //d.disconnectDevice(d.getMACAddress());
            disconnect(d)
        }
    }

    /* connect to the device with specified address */
    @Suppress("unused")
    fun connect(deviceAddress: String?, callback: BluenoxGATTCallback?): Boolean {
        var curBluetoothDevice: BluetoothDevice?
        val curBluetoothGatt: BluetoothGatt?

        //BlueNoxDevice dev = new BlueNoxDevice();
        val dev: BlueNoxDevice = mBlueNoxDeviceStore!!.getDevice(deviceAddress)

        if (dev != null) {
            Log.v(MODULE_TAG, "Connecting to device with address: $deviceAddress")

            if (adapter == null) {
                Log.d(MODULE_TAG, "Bluetooth Adapter Invalid ")
                return false
            }

            if (deviceAddress == null) {
                Log.d(MODULE_TAG, "Device Address Invalid")
                return false
            }

            curBluetoothDevice = findExistingDeviceAddress(deviceAddress)

            // Check if we need to connect from scratch or just reconnect to previous device
            if (curBluetoothDevice != null) {
                Log.d(MODULE_TAG, "curBluetoothDevice not null $curBluetoothDevice")

                // Reconnect to device
                curBluetoothDevice.connectGatt(mParent, false, callback)
            } else {
                Log.d(MODULE_TAG, "Connect from Scratch ")

                // connect from scratch
                // get BluetoothDevice object for specified address
                curBluetoothDevice = adapter!!.getRemoteDevice(deviceAddress)


                Log.d(MODULE_TAG, "Connect from Scratch $curBluetoothDevice")

                // connect to remote device
                Log.d(MODULE_TAG, "curBluetoothDevice connectGatt$curBluetoothDevice")
                dev.mCurrentlyConnecting = true
                curBluetoothGatt = curBluetoothDevice.connectGatt(mParent, false, callback)
                if (curBluetoothGatt != null) {
                    Log.d(MODULE_TAG, "curBluetoothGatt Not null")
                    dev.gatt = curBluetoothGatt
                    dev.device = curBluetoothDevice
                    dev.enableReconnection()
                    dev.resetConnectionTimeoutCount(4000)
                    dev.callback = callback
                    //mBluetoothDevices.add(dev);
                }
            }

            Log.d(MODULE_TAG, "mBlueNoxConnectedDeviceStore.addDevic")

            mBlueNoxDeviceStore!!.addDevice(dev)
            startConnectionTimer()
        }

        return true
    }

    @Suppress("unused")
    private fun checkDeviceExists(deviceAddress: String): Boolean {
        return findExistingDeviceGatt(deviceAddress) != null
    }

    private fun findExistingDeviceGatt(deviceAddress: String): BluetoothGatt? {
        for (dev in mBlueNoxDeviceStore.getDeviceList()) {
            if (dev.device!!.address == deviceAddress) {
                // already exists
                return dev.gatt
            }
        }
        return null
    }

    private fun findExistingDeviceAddress(deviceAddress: String): BluetoothDevice? {
        for (dev in mBlueNoxDeviceStore.getDeviceList()) {
            if (dev.device!!.address == deviceAddress) {
                // already exists
                return dev.device
            }
        }

        return null
    }

    @Suppress("unused")
    fun disconnect(device: BluetoothDevice) {
        Log.d(
            MODULE_TAG,
            "Line: " + Thread.currentThread().stackTrace[2].lineNumber + "Disconnecting from device"
        )

        val i: MutableIterator<BlueNoxDevice> = mBlueNoxDeviceStore.getDeviceList().iterator()

        while (i.hasNext()) {
            val dev = i.next()

            if (dev.device == device) {
                dev.stopReconnection()
                dev.disconnect()
                i.remove()
            }
        }
    }


    /* disconnect the device. It is still possible to reconnect to it later with this Gatt client */
    @Suppress("unused")
    private fun disconnect(device: BlueNoxDevice) {
        Log.d(
            MODULE_TAG,
            "Line: " + Thread.currentThread().stackTrace[2].lineNumber + "Disconnecting from device"
        )

        val i: MutableIterator<BlueNoxDevice> = mBlueNoxDeviceStore.getDeviceList().iterator()

        while (i.hasNext()) {
            val dev = i.next()

            if (dev == device) {
                if (dev.gatt != null) {
                    dev.stopReconnection()
                    dev.disconnect()
                }

                i.remove()
            }
        }
    }


    @Suppress("unused")
    fun removeAdvertisingDevices() {
        Log.d(MODULE_TAG, "removeAdvertisingDevices()")
        mBlueNoxDeviceStore!!.removeAdvertisingDevices()
    }

    @Suppress("unused")
    fun removeAllDevices() {
        Log.d(MODULE_TAG, "removeAllDevices()")
        mBlueNoxDeviceStore!!.removeAll()
    }

    @Suppress("unused")
    fun removeAllAdvertisingDevices() {
        Log.d(MODULE_TAG, "removeAllAdvertisingDevices()")
        mBlueNoxDeviceStore!!.removeAdvertisingDevices()
    }

    /* disconnect the device. It is still possible to reconnect to it later with this Gatt client */
    @Suppress("unused")
    fun disconnectAll() {
        _mutex.lock()

        Log.d(
            MODULE_TAG,
            "Disconnect All: " + mBlueNoxDeviceStore.getDeviceList().size + " Devices at list"
        )

        for (dev in mBlueNoxDeviceStore.getDeviceList()) {
            if (dev.isConnected) {
                dev.stopReconnection()

                if (isDeviceConnectedOrAttempting(dev.device)) {
                    Log.d(MODULE_TAG, "Disconnecting " + dev.device!!.address)
                    dev.disconnect()
                }
            }
        }

        _mutex.unlock()
        mBlueNoxDeviceStore!!.removeAdvertisingDevices()
    }

    @Suppress("unused")
    fun disableReconnectionAll() {
        _mutex.lock()

        for (dev in mBlueNoxDeviceStore.getDeviceList()) {
            dev.stopReconnection()
        }

        _mutex.unlock()
    }

    /*
    public boolean isDeviceConnected(String addr)
    {
        if(mBluetoothManager != null)
        {
            BlueNoxDevice dev = mBlueNoxDeviceStore.getDevice(addr);
            if(dev != null)
                if(mBluetoothManager.getConnectionState(dev.getDevice(), BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED)
                    return true;
        }

        return false;
    }*/
    @Suppress("unused")
    fun getConnectionState(d: BluetoothDevice?): Int {
        if (d == null) return 0

        return manager!!.getConnectionState(d, BluetoothProfile.GATT)
    }

    @Suppress("unused")
    fun isDeviceConnected(d: BluetoothDevice?): Boolean {
        if (d == null) return false

        val state = manager!!.getConnectionState(d, BluetoothProfile.GATT)

        return state == BluetoothProfile.STATE_CONNECTED
    }

    @Suppress("unused")
    fun isDeviceConnectedOrAttempting(d: BluetoothDevice?): Boolean {
        if (d == null) {
            return false
        }

        val state = manager!!.getConnectionState(d, BluetoothProfile.GATT)
        return state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING
    }

    @Suppress("unused")
    fun isDeviceDisconnected(d: BluetoothDevice?): Boolean {
        if (d == null) return false

        val state = manager!!.getConnectionState(d, BluetoothProfile.GATT)

        return state == BluetoothProfile.STATE_DISCONNECTED
    }


    /* close GATT client completely */
    @Suppress("unused")
    fun close() {
        for (dev in mBlueNoxDeviceStore.getDeviceList()) {
            dev.gatt!!.close()
            dev.gatt = null
        }
    }

    /* request new RSSi value for the connection*/
    @Suppress("unused")
    private fun readPeriodicalyRssiValue(device: BluetoothDevice?, repeat: Boolean): Int {
        var curDevice: BlueNoxDevice? = null
        // Find BlueNoxDevice corresponding to device
        for (dev in mBlueNoxDeviceStore.getDeviceList()) {
            if (dev.device!!.address == device!!.address) curDevice = dev
        }

        if (curDevice == null) return -1

        // check if we should stop checking RSSI value
        if (curDevice.gatt == null ||
            (manager!!.getConnectionState(
                curDevice.device,
                BluetoothProfile.GATT
            ) != BluetoothProfile.STATE_CONNECTED)
        ) {
            curDevice.mRssiTimerEnabled = false
            curDevice.mRssiTimerRepeat = false
            return -1
        }

        curDevice.mRssiTimerRepeat = repeat
        curDevice.mRssiTimerEnabled = true

        val curDevice2: BlueNoxDevice = curDevice

        if (!repeat) {
            curDevice2.gatt!!.readRemoteRssi()
            return 0
        }
        if (curDevice.mTimerHandler == null) curDevice.mTimerHandler = Handler()

        curDevice.mTimerHandler!!.postDelayed({
            if (curDevice2.gatt == null || adapter == null ||
                !curDevice2.mConnected
            ) {
                curDevice2.mRssiTimerEnabled = false
                return@postDelayed
            }
            // request RSSI value
            curDevice2.gatt!!.readRemoteRssi()

            // add call it once more in the future
            readPeriodicalyRssiValue(curDevice2.device, curDevice2.mRssiTimerEnabled)
        }, RSSI_UPDATE_TIME_INTERVAL.toLong())

        return 0
    }

    /* starts monitoring RSSI value */
    @Suppress("unused")
    fun startMonitoringRssiValue(device: BluetoothDevice?) {
        readPeriodicalyRssiValue(device, true)
    }

    @Suppress("unused")
    fun readRssiValue(device: BluetoothDevice?) {
        readPeriodicalyRssiValue(device, false)
    }

    /* stops monitoring of RSSI value */
    @Suppress("unused")
    fun stopMonitoringRssiValue(device: BluetoothDevice?) {
        readPeriodicalyRssiValue(device, false)
    }

    /* request to discover all services available on the remote devices
     * results are delivered through callback object */
    @Suppress("unused")
    fun startServicesDiscovery(gatt: BluetoothGatt?) {
        gatt?.discoverServices()
    }

    @Suppress("unused") /* get all characteristic for particular service and pass them to the UI callback */ fun getCharacteristicsForService(
        service: BluetoothGattService?,
        device: BluetoothDevice
    ) {
        if (service == null) {
            return
        }

        val chars: List<BluetoothGattCharacteristic>

        val curGatt = findExistingDeviceGatt(device.address)

        if (curGatt != null) {
            chars = service.characteristics

            for (c in chars) {
                val charName = resolveCharacteristicName(c.uuid.toString())
                Log.d(MODULE_TAG, charName)
                //gattList += "Characteristic: " + charName + "\n";
            }


            val dev: BlueNoxDevice = mBlueNoxDeviceStore!!.getDevice(device.address)
            try {
                if (dev != null) dev.serviceCharDiscoveryCount++
            } catch (e: NullPointerException) {
                Log.d(MODULE_TAG, e.message!!)
            }
        }
    }

    /* request to fetch newest value stored on the remote device for particular characteristic */
    @Suppress("unused")
    fun requestCharacteristicValue(ch: BluetoothGattCharacteristic?, device: BluetoothDevice) {
        if (adapter == null) {
            return
        }

        val curGatt = findExistingDeviceGatt(device.address)

        curGatt?.readCharacteristic(ch)
    }

    @get:Suppress("unused")
    val deviceList: ArrayList<BlueNoxDevice>
        get() = mBlueNoxDeviceStore.getDeviceList()

    @get:Suppress("unused")
    val advertisingDeviceList: ArrayList<BlueNoxDevice>
        get() = mBlueNoxDeviceStore.getAdvertisingDeviceList()


    @Suppress("unused")
    fun getDeviceFromAddress(mac: String): BlueNoxDevice? {
        if (mBlueNoxDeviceStore != null) {
            return mBlueNoxDeviceStore.getDeviceFromAddress(mac)
        }

        return null
    }

    /* get characteristic's value (and parse it for some types of characteristics)
     * before calling this You should always update the value by calling requestCharacteristicValue() */
    @Suppress("unused")
    fun getCharacteristicValue(ch: BluetoothGattCharacteristic?, device: BluetoothDevice) {
        if (adapter == null || ch == null) {
            return
        }

        val curGatt = findExistingDeviceGatt(device.address) ?: return

        val rawValue = ch.value
        var strValue: String? = null
        var intValue: Int

        // lets read and do real parsing of some characteristic to get meaningful value from it
        val uuid = ch.uuid

        if (uuid == BLEUUIDs.Characteristic.HEART_RATE_MEASUREMENT) { // heart rate
            // follow https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
            // first check format used by the device - it is specified in bit 0 and tells us if we should ask for index 1 (and uint8) or index 2 (and uint16)
            val index = if (((rawValue[0].toInt() and 0x01) == 1)) 2 else 1
            // also we need to define format
            val format =
                if ((index == 1)) BluetoothGattCharacteristic.FORMAT_UINT8 else BluetoothGattCharacteristic.FORMAT_UINT16
            // now we have everything, get the value
            intValue = ch.getIntValue(format, index)
            strValue = "$intValue bpm" // it is always in bpm units
        } else if (uuid == BLEUUIDs.Characteristic.MODEL_NUMBER_STRING || uuid == BLEUUIDs.Characteristic.FIRMWARE_REVISION_STRING) // firmware revision string
        {
            // follow https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.manufacturer_name_string.xml etc.
            // string value are usually simple utf8s string at index 0
            strValue = ch.getStringValue(0)
        } else if (uuid == BLEUUIDs.Characteristic.APPEARANCE) { // appearance
            // follow: https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.gap.appearance.xml
            intValue = rawValue[1].toInt() shl 8
            intValue += rawValue[0].toInt()
            strValue = resolveAppearance(intValue)
        } else if (uuid == BLEUUIDs.Characteristic.BODY_SENSOR_LOCATION) { // body sensor location
            // follow: https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.body_sensor_location.xml
            intValue = rawValue[0].toInt()
            strValue = resolveHeartRateSensorLocation(intValue)
        } else if (uuid == BLEUUIDs.Characteristic.BATTERY_LEVEL) { // battery level
            // follow: https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.battery_level.xml
            intValue = rawValue[0].toInt()
            strValue = "$intValue% battery level"
        } else {
            // not known type of characteristic, so we need to handle this in "general" way
            // get first four bytes and transform it to integer
            intValue = 0
            if (rawValue.size > 0) {
                intValue = rawValue[0].toInt()
            }
            if (rawValue.size > 1) {
                intValue = intValue + (rawValue[1].toInt() shl 8)
            }
            if (rawValue.size > 2) {
                intValue = intValue + (rawValue[2].toInt() shl 8)
            }
            if (rawValue.size > 3) {
                intValue = intValue + (rawValue[3].toInt() shl 8)
            }

            if (rawValue.size > 0) {
                val stringBuilder = StringBuilder(rawValue.size)
                for (byteChar in rawValue) {
                    stringBuilder.append(String.format("%c", byteChar))
                }
                strValue = stringBuilder.toString()
            }
        }

        @SuppressLint("SimpleDateFormat") val timestamp =
            SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS").format(
                Date()
            )

        /*
        mUiCallback.uiNewValueForCharacteristic(curGatt,
                mBluetoothDevice,
                mBluetoothSelectedService,
                ch,
                strValue,
                intValue,
                rawValue,
                timestamp);*/
    }

    /* reads and return what what FORMAT is indicated by characteristic's properties
     * seems that value makes no sense in most cases */
    @Suppress("unused")
    fun getValueFormat(ch: BluetoothGattCharacteristic): Int {
        val properties = ch.properties

        if ((BluetoothGattCharacteristic.FORMAT_FLOAT and properties) != 0) {
            return BluetoothGattCharacteristic.FORMAT_FLOAT
        }
        if ((BluetoothGattCharacteristic.FORMAT_SFLOAT and properties) != 0) {
            return BluetoothGattCharacteristic.FORMAT_SFLOAT
        }
        if ((BluetoothGattCharacteristic.FORMAT_SINT16 and properties) != 0) {
            return BluetoothGattCharacteristic.FORMAT_SINT16
        }
        if ((BluetoothGattCharacteristic.FORMAT_SINT32 and properties) != 0) {
            return BluetoothGattCharacteristic.FORMAT_SINT32
        }
        if ((BluetoothGattCharacteristic.FORMAT_SINT8 and properties) != 0) {
            return BluetoothGattCharacteristic.FORMAT_SINT8
        }
        if ((BluetoothGattCharacteristic.FORMAT_UINT16 and properties) != 0) {
            return BluetoothGattCharacteristic.FORMAT_UINT16
        }
        if ((BluetoothGattCharacteristic.FORMAT_UINT32 and properties) != 0) {
            return BluetoothGattCharacteristic.FORMAT_UINT32
        }
        if ((BluetoothGattCharacteristic.FORMAT_UINT8 and properties) != 0) {
            return BluetoothGattCharacteristic.FORMAT_UINT8
        }

        return 0
    }

    /* set new value for particular characteristic */
    @Suppress("unused")
    fun writeDataToCharacteristic(
        ch: BluetoothGattCharacteristic?,
        dataToWrite: ByteArray?,
        device: BluetoothDevice
    ) {
        if (adapter == null || ch == null) {
            return
        }

        val curGatt = findExistingDeviceGatt(device.address) ?: return


        // first set it locally....
        ch.setValue(dataToWrite)
        // ... and then "commit" changes to the peripheral
        curGatt.writeCharacteristic(ch)
    }

    /* Scann callback used in API 21 and Below */ /* Callback for Scanning Results */
    private val mLeScanCallback = LeScanCallback { device, rssi, scanRecord ->
        Log.i("onLeScan", device.toString())
        /* Add the device or update its state if it already exists.
                This also now adds an advertising event
              */
        if (mBlueNoxDeviceStore!!.addDevice(
                this@BlueNoxService,
                device,
                mdeviceClass,
                rssi,
                null
            )
        ) {
            if (mDebugEnabled > 0) Log.d(MODULE_TAG, "DeviceFound: " + device.address)

            val d: BlueNoxDevice = mBlueNoxDeviceStore!!.getDevice(device.address)
            sendDeviceNotificationEventAll(d, DeviceNotificationEvent.DeviceFound.constValue())
        }

        val ad: BlueNoxDevice = mBlueNoxDeviceStore!!.getDevice(device.address)
        sendDeviceRssiEventAll(ad, rssi, 0, 0)
        SendAdvertisingReportNotifAll(ad, rssi, 0, scanRecord)
        processDeviceRssi(device, rssi)
    }


    init {
        val mProximityCallbacks: ProximityEngineCallbacks =
            object : NullProximityEngineCallbacks() {
                override fun nearestDeviceFound(addr: String) {
                    super.nearestDeviceFound(addr)
                    sendDeviceNotificationEventAll(
                        getDeviceFromAddress(addr),
                        DeviceNotificationEvent.NearDeviceFound.constValue()
                    )
                }

                override fun nearestDeviceFailed() {
                    super.nearestDeviceFailed()
                    sendNotificationEventAll(DeviceNotificationEvent.NearDeviceFailed.constValue())
                }
            }
        mProxEngine = ProximityEngine(mProximityCallbacks)
    }


    private fun processDeviceRssi(device: BluetoothDevice, rssi: Int) {
        if (mProxEngine != null && mProxEngine.running()) mProxEngine.updateRSSI(device, rssi)
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun scanLeDevice21(
        enable: Boolean,
        filters: List<ScanFilter>?,
        settings: ScanSettings?
    ) {
        Log.v(MODULE_TAG, "scanLeDevice21")
        if (Build.VERSION.SDK_INT >= 21) {
            if (!enable) {
                mLEScanner!!.stopScan(mScanCallback)
            } else {
                Log.v(MODULE_TAG, "scanLeDevice21 Build.VERSION.SDK_INT >= 21")

                if (mScanCallback == null) {
                    /* API 21 and Above */

                    mScanCallback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            /*  Connect to  device  found   */
                            //Log.i("callbackType   " + result.getDevice().getAddress(), String.valueOf(callbackType));

                            if (mBlueNoxDeviceStore!!.addDevice(
                                    this@BlueNoxService,mBlueNoxDeviceStore
                                    result.device,
                                    mdeviceClass,
                                    result.rssi,
                                    result.scanRecord
                                )
                            ) {
                                if (mDebugEnabled > 0) Log.d(
                                    MODULE_TAG,
                                    "Device Found:   " + result.device.address
                                )

                                val d: BlueNoxDevice =
                                    mBlueNoxDeviceStore!!.getDevice(result.device.address)
                                sendDeviceNotificationEventAll(
                                    d,
                                    DeviceNotificationEvent.DeviceFound.constValue()
                                )
                            }

                            //mUiCallback.uiDeviceFound(result.getDevice(), result.getRssi(), result.getScanRecord());
                            val ad: BlueNoxDevice =
                                mBlueNoxDeviceStore!!.getDevice(result.device.address)

                            if (Build.VERSION.SDK_INT >= 26) {
                                sendDeviceRssiEventAll(
                                    ad,
                                    result.rssi,
                                    result.primaryPhy,
                                    result.secondaryPhy
                                )
                                SendAdvertisingReportNotifAll(
                                    ad, result.rssi, result.primaryPhy, result.scanRecord!!
                                        .bytes
                                )
                            } else {
                                sendDeviceRssiEventAll(ad, result.rssi, 0, 0)
                                SendAdvertisingReportNotifAll(
                                    ad, result.rssi, 0, result.scanRecord!!
                                        .bytes
                                )
                            }

                            processDeviceRssi(result.device, result.rssi)
                        }

                        override fun onBatchScanResults(results: List<ScanResult>) {
                            if (mDebugEnabled > 0) Log.d(MODULE_TAG, "Batch Scan Results")


                            /*  Process a   batch   scan    results */
                            for (sr in results) {
                                if (mDebugEnabled > 0) Log.d("Scan Item:   ", sr.toString())


                                processDeviceRssi(sr.device, sr.rssi)
                                mBlueNoxDeviceStore!!.addDevice(
                                    this@BlueNoxService,
                                    sr.device,
                                    mdeviceClass,
                                    sr.rssi,
                                    sr.scanRecord
                                )
                            }
                        }
                    }
                } else {
                    Log.v(MODULE_TAG, "mScanCallback not null")
                }

                if (mLEScanner != null) {
                    if (filters != null || filters!!.size == 0) mLEScanner!!.startScan(
                        filters,
                        settings,
                        mScanCallback
                    )
                    else mLEScanner!!.startScan(mScanCallback)
                } else {
                    Log.e(MODULE_TAG, "Attempting to scan before initialization complete")
                }
            }
        }
    }

    companion object {
        private const val mMajorVer = 1
        private const val mMinVer = 0
        private const val mPatchVer = 225

        private var mHandler: Handler? = null

        @get:Suppress("unused")
        val blueNoxVersion: String
            get() = String.format(Locale.US, "%d.%02d.%03d", mMajorVer, mMinVer, mPatchVer)

        /*!
     * \brief Check whether this hardware supports BLE
     *
     * BLE Manager is a singleton class which means only one is instantiated per application
     *
     * @param None.
     *
     * @return true if BLE available, false otherwise
     */
        fun checkHardwareCompatible(ctx: Context): Boolean {
            /* Check general Bluetooth Service availability */

            val manager = ctx.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                ?: return false
            // .. and then get adapter from manager
            val adapter = manager.adapter ?: return false

            /* Check if BT LE is also available */
            return ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        }
    }
}
*/