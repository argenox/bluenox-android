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
 * File:    BlueNoxDevice.kt
 * Summary: Bluetooth Device Object Class
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanRecord
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.argenox.bluenoxandroid.BlueNoxDebug
import com.argenox.bluenoxandroid.BluenoxLEManager
import java.io.Serializable
import java.io.UnsupportedEncodingException
import java.lang.reflect.Method
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.toString

/**
 * Represents one BLE peripheral: GATT connection, reads/writes, notifications, bonding, and an internal operation queue.
 *
 * Instances are created by [BlueNoxDeviceStore] during scan; connect with [connect]. Register
 * [BlueNoxDeviceCallbacks] or [BlueNoxDeviceFlowAdapter] before or when connecting. Most GATT
 * calls require an active connection and [Manifest.permission.BLUETOOTH_CONNECT] on API 31+.
 */
class BlueNoxDevice protected constructor() : BlueNoxOpQueue.BlueNoxQueueListener, Serializable {

    private val MODULE_TAG : String = "BlueNoxDevice"

    private val dbgObj = BlueNoxDebug(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR)

    /** Unique id for this in-memory device wrapper (not the Bluetooth address). */
    val id: UUID = UUID.randomUUID()
    /** App-side timestamp associated with this device record. */
    var date: Date = Date()
    /** Platform [BluetoothDevice]; null until set by scan or construction. */
    var device: BluetoothDevice? = null

    /** Last observed RSSI from scan or remote read. */
    @get:Suppress("unused")
    var rssi: Int = 0

    /** Latest LE scan record when populated by the scanner. */
    @get:Suppress("unused")
    var scanRecord: ScanRecord? = null
    /** Primary advertising PHY from the last scan callback. */
    var primaryPhy: Int = 0
    /** Secondary advertising PHY from the last scan callback. */
    var secondaryPhy: Int = 0
    /** Active GATT client after [connect]; cleared on disconnect. */
    var mBluetoothGatt: BluetoothGatt? = null
    private var mServices: List<BluetoothGattService>? = null

    /** True while a connect or reconnect attempt is in flight. */
    var mCurrentlyConnecting: Boolean = false
    /** Set by [disconnect] so [uiDeviceDisconnected] can distinguish user vs accidental disconnect. */
    var mDisconnectRequested: Boolean = false

    private val _mutex: Lock = ReentrantLock(true)
    protected val _PropertyListMutex: Lock = ReentrantLock(true)

    private var mConnectionTimestamp: Long = 0

    /** When true, RSSI polling may repeat on [mTimerHandler]. */
    var mRssiTimerRepeat: Boolean = false
    /** Master switch for periodic RSSI reads. */
    var mRssiTimerEnabled: Boolean = false
    /** Legacy flag; connection state is tracked via GATT and [isConnected]. */
    val mConnected: Boolean = false

    /** Optional handler used for RSSI or other timers. */
    var mTimerHandler: Handler? = null



    private var connectionTimeoutCount = 0
    private var mParent: Context? = null

    private var mAttemptConnection = false
    private var reconnectAttempt = 0
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var connectionPolicy: BlueNoxConnectionPolicy = BlueNoxConnectionPolicy.Balanced
    private var bondingPolicy: BlueNoxBondingPolicy = BlueNoxBondingPolicy.Default
    private val bondingHandler = Handler(Looper.getMainLooper())
    private var bondAttempts = 0
    private var bondRetries = 0
    private var bondFailures = 0
    private var reconnectAttemptsTotal = 0
    private var reconnectExhaustions = 0
    private var operationTimeouts = 0
    private var bondInProgress = false
    private var bondRetryCount = 0
    private var bondPinProvider: (() -> String?)? = null
    private var longWriteStrategy: BlueNoxLongWriteStrategy = BlueNoxLongWriteStrategy.Default
    private data class PendingCccdRequest(
        val characteristicUuid: String,
        val enabled: Boolean,
        val indicate: Boolean,
        val expectedValue: ByteArray,
    )
    private val pendingCccdByCharacteristic = HashMap<String, PendingCccdRequest>()

    private lateinit var mOperationQueue: BlueNoxOpQueue

    /** Incremented when service/characteristic discovery completes (diagnostics). */
    var serviceCharDiscoveryCount: Int = 0

    private val deviceCallbacksArrayList: HashSet<BlueNoxDeviceCallbacks?>? =
        HashSet<BlueNoxDeviceCallbacks?>()
    protected val propertyCallbacksArrayList: HashSet<BlueNoxDevicePropertyCallbacks?>? =
        HashSet<BlueNoxDevicePropertyCallbacks?>()

    private var mbleConnCallback: BluenoxGATTCallback? = null
    private var mCallback: BluenoxGATTCallback? = null

    protected val mAdvList: ArrayList<Any?> = ArrayList<Any?>()

    private var mLastAdvertisement: Date? = null


    private var manufacturerName: String? = null
    private var SerialNumber: String? = null
    private var ModelNumber: String? = null
    private var FirmwareVersion: String? = null
    private var SoftwareVersion: String? = null
    private var HarwareVersion: String? = null
    private var batteryLvl: String? = null

    /**
     * Starts Android bonding for this peripheral when already GATT-connected.
     *
     * @return false if not connected, permission denied, or [BluetoothDevice.createBond] failed.
     */
    fun pairAndBond(): Boolean {
        val target = device ?: return false
        if (!BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.PERMISSION_DENIED,
                detail = "BLUETOOTH_CONNECT permission is required before bonding",
                characteristicUuid = null,
            )
            return false
        }
        if (!isConnected) {
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = "Cannot start bonding while device is disconnected",
                characteristicUuid = null,
            )
            return false
        }
        if (target.bondState == BluetoothDevice.BOND_BONDED) {
            dispatchBondStateEvent(
                state = BlueNoxDeviceCallbacks.BlueNoxBondState.BONDED,
                detail = "Device is already bonded",
            )
            return true
        }
        bondAttempts += 1
        bondInProgress = true
        bondRetryCount = 0
        dispatchBondStateEvent(
            state = BlueNoxDeviceCallbacks.BlueNoxBondState.BONDING,
            detail = "Starting bond procedure",
        )
        val started = target.createBond()
        if (!started) {
            bondInProgress = false
            bondFailures += 1
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.BOND_REQUEST_REJECTED,
                detail = "createBond returned false",
                characteristicUuid = null,
            )
            dispatchBondStateEvent(
                state = BlueNoxDeviceCallbacks.BlueNoxBondState.FAILED,
                detail = "Bond request rejected by Android stack",
            )
            return false
        }
        scheduleBondTimeout()
        return true
    }

    /** Replaces reconnect backoff policy with explicit [BlueNoxConnectionPolicy] values. */
    fun setConnectionPolicy(policy: BlueNoxConnectionPolicy) {
        connectionPolicy = policy
    }

    /** Applies a named [BlueNoxConnectionProfile] preset (aggressive / balanced / battery saver). */
    fun setConnectionProfile(profile: BlueNoxConnectionProfile) {
        connectionPolicy = profile.toPolicy()
    }

    /** Configures automatic bond retry and timeout behavior. */
    fun setBondingPolicy(policy: BlueNoxBondingPolicy) {
        bondingPolicy = policy
    }

    /**
     * Optional supplier for pairing PIN when [BlueNoxDeviceCallbacks.BlueNoxBondState.PIN_REQUESTED] occurs.
     *
     * Return null to decline automatic PIN entry.
     */
    fun setBondPinProvider(provider: (() -> String?)?) {
        bondPinProvider = provider
    }

    /** Updates chunking/retry behavior for [writeCharacteristicLongByUUID] and split writes. */
    fun setLongWriteStrategy(strategy: BlueNoxLongWriteStrategy) {
        longWriteStrategy = strategy
    }

    /** Current [BlueNoxLongWriteStrategy] used for long and split writes. */
    fun getLongWriteStrategy(): BlueNoxLongWriteStrategy {
        return longWriteStrategy
    }

    /** Supplies a trimmed PIN string during pairing, if [setBondPinProvider] is configured. */
    internal fun provideBondPin(): String? {
        val value = bondPinProvider?.invoke()?.trim()
        return value?.takeIf { it.isNotEmpty() }
    }

    internal fun handleBondStateChanged(currentState: Int, previousState: Int) {
        when (currentState) {
            BluetoothDevice.BOND_BONDING -> {
                bondInProgress = true
                dispatchBondStateEvent(
                    state = BlueNoxDeviceCallbacks.BlueNoxBondState.BONDING,
                    detail = "Bonding in progress",
                )
                scheduleBondTimeout()
            }

            BluetoothDevice.BOND_BONDED -> {
                bondInProgress = false
                bondRetryCount = 0
                cancelBondTimeout()
                mMainCallback.uiBondingChanged(this, true)
                dispatchBondStateEvent(
                    state = BlueNoxDeviceCallbacks.BlueNoxBondState.BONDED,
                    detail = "Bonded successfully",
                )
            }

            BluetoothDevice.BOND_NONE -> {
                cancelBondTimeout()
                mMainCallback.uiBondingChanged(this, false)
                val failedFromBonding = previousState == BluetoothDevice.BOND_BONDING || bondInProgress
                if (!failedFromBonding) {
                    dispatchBondStateEvent(
                        state = BlueNoxDeviceCallbacks.BlueNoxBondState.UNBONDED,
                        detail = "Bond removed",
                    )
                    return
                }
                bondInProgress = false
                bondFailures += 1
                if (bondingPolicy.autoRetryEnabled && bondRetryCount < bondingPolicy.maxRetries) {
                    bondRetryCount += 1
                    bondRetries += 1
                    val delayMs = bondingPolicy.retryBackoffMs * bondRetryCount
                    dispatchBondStateEvent(
                        state = BlueNoxDeviceCallbacks.BlueNoxBondState.RETRYING,
                        detail = "Retrying bond ($bondRetryCount/${bondingPolicy.maxRetries})",
                    )
                    bondingHandler.postDelayed({
                        pairAndBond()
                    }, delayMs)
                } else {
                    reportOperationFailure(
                        reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.BOND_RETRY_EXHAUSTED,
                        detail = "Bonding failed after $bondRetryCount retries",
                        characteristicUuid = null,
                    )
                    dispatchBondStateEvent(
                        state = BlueNoxDeviceCallbacks.BlueNoxBondState.FAILED,
                        detail = "Bonding failed and retries exhausted",
                    )
                }
            }
        }
    }

    private fun scheduleBondTimeout() {
        cancelBondTimeout()
        if (bondingPolicy.timeoutMs <= 0L) return
        bondingHandler.postDelayed({
            if (!bondInProgress) {
                return@postDelayed
            }
            bondInProgress = false
            bondFailures += 1
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.BOND_TIMEOUT,
                detail = "Bonding timed out after ${bondingPolicy.timeoutMs}ms",
                characteristicUuid = null,
            )
            dispatchBondStateEvent(
                state = BlueNoxDeviceCallbacks.BlueNoxBondState.FAILED,
                detail = "Bond timeout",
            )
        }, bondingPolicy.timeoutMs)
    }

    private fun cancelBondTimeout() {
        bondingHandler.removeCallbacksAndMessages(null)
    }

    private fun dispatchBondStateEvent(
        state: BlueNoxDeviceCallbacks.BlueNoxBondState,
        detail: String,
    ) {
        mMainCallback.uiBondStateEvent(this, state, detail)
    }

    /** Marks this device as recently seen in advertising (used by [currentlyAdvertising]). */
    fun setLastAdvertisement() {
        mLastAdvertisement = Date()
    }

    /** Forces [currentlyAdvertising] to false by backdating the last advertisement timestamp. */
    fun clearLastAdvertisement() {
        val calendar =
            Calendar.getInstance() // gets a calendar using the default time zone and locale.
        calendar.add(Calendar.SECOND, -10)

        mLastAdvertisement = calendar.time
    }

    /** True if the last advertisement timestamp is within the last few seconds. */
    fun currentlyAdvertising(): Boolean {
        val curTime = Date()

        return (curTime.time - mLastAdvertisement!!.time) < 7000
    }

    /** Human-readable elapsed time since the last advertisement update. */
    val lastAdvertisement: String
        get() {
            val curTime = Date()

            return TimeHelper.getTimeString((curTime.time - mLastAdvertisement!!.time) / 1000, true)
        }

    /**
     * Constructs a device bound to a platform [BluetoothDevice] and app [Context] for GATT.
     *
     * @param device Remote device; typically from scan.
     * @param ctx Context used for [BluetoothDevice.connectGatt].
     */
    constructor(device: BluetoothDevice?, ctx: Context?) : this() {
        this.device = device
        mParent = ctx
    }

    /** Records a single RSSI + scan record sample in the rolling advertisement history. */
    fun addAdvertisingEvent(r: Int, sr: ScanRecord?) {
        mAdvList.add(AdvertisementEvent(Date(), r, sr))
    }

    /**
     * Decodes known beacon frames (iBeacon / Eddystone) from the latest scan record.
     */
    fun decodedBeaconFrames(): List<BlueNoxBeaconFrame> {
        return BlueNoxBeaconDecoder.decode(scanRecord)
    }



    /** Allows automatic reconnect scheduling after unexpected GATT disconnects. */
    fun enableReconnection() {
        Log.d(MODULE_TAG, "Begin Connection")
        mAttemptConnection = true
    }

    /** Stops automatic reconnect attempts and clears pending reconnect callbacks. */
    fun stopReconnection() {
        Log.d(MODULE_TAG, "Stop Reconnection")
        mAttemptConnection = false
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectAttempt = 0
        cancelBondTimeout()
    }

    private var mStartupComplete = false

    /** Invoked by [BlueNoxOpQueue] when the startup read queue has drained; emits [BlueNoxDeviceCallbacks.uiDeviceReady]. */
    override fun queueCompleteCallback() {
        if (mStartupComplete) {
            _mutex.withLock {
                if (deviceCallbacksArrayList != null) {
                    for (cback in deviceCallbacksArrayList) {
                        if (cback != null) {
                            cback.uiDeviceReady(mBluetoothGatt, device)
                        }
                    }
                }
            }
            mStartupComplete = false
        }
    }

    /** Invoked by [BlueNoxOpQueue] when a queued operation fails to start or times out. */
    override fun queueOperationFailed(
        op: BlueNoxOp,
        reason: BlueNoxDeviceCallbacks.BlueNoxFailureReason,
        detail: String,
    ) {
        reportOperationFailure(
            reason = reason,
            detail = detail,
            characteristicUuid = op.mCharacteristic.uuid.toString(),
        )
    }

    /**
     * Registers a [BlueNoxDeviceCallbacks] listener for this device.
     *
     * @return true if the callback was added, false otherwise.
     */
    fun addListener(callback: BlueNoxDeviceCallbacks?): Boolean {
        if (deviceCallbacksArrayList == null) {
            /* Severe Issue, should never be null */

            Log.e(MODULE_TAG, "Callback list is null")

            return false
        }
        if (callback != null) {
            _mutex.withLock {
                return deviceCallbacksArrayList.add(callback)
            }
        }

        return false
    }

    /**
     * Convenience API to register a flow adapter for this device.
     */
    fun registerFlowAdapter(adapter: BlueNoxDeviceFlowAdapter = BlueNoxDeviceFlowAdapter()): BlueNoxDeviceFlowAdapter {
        addListener(adapter)
        return adapter
    }

    /**
     * Convenience API to unregister a previously registered flow adapter.
     */
    fun unregisterFlowAdapter(adapter: BlueNoxDeviceFlowAdapter): Boolean {
        return removeListener(adapter)
    }

    /**
     * Unregisters a [BlueNoxDeviceCallbacks] listener from this device.
     *
     * @return true if the callback was removed, false otherwise.
     */
    fun removeListener(callback: BlueNoxDeviceCallbacks?): Boolean {
        if (deviceCallbacksArrayList == null) {
            /* Severe Issue, should never be null */

            Log.e(MODULE_TAG, "Callback list is null")

            return false
        }
        if (callback != null) {
            _mutex.withLock {
                return deviceCallbacksArrayList.remove(callback)
            }
        }

        return false
    }

    /**
     * Clears all registered device callbacks.
     */
    fun removeAllListeners(): Boolean {
        if (deviceCallbacksArrayList == null) {
            /* Severe Issue, should never be null */

            Log.e(MODULE_TAG, "Callback list is null")

            return false
        }

        _mutex.withLock {
            deviceCallbacksArrayList.clear()
        }
        return true
    }

    /**
     * Registers a property-change callback.
     */
    fun addPropertyListener(callback: BlueNoxDevicePropertyCallbacks?): Boolean {
        if (propertyCallbacksArrayList == null) {
            /* Severe Issue, should never be null */

            Log.e(MODULE_TAG, "Callback list is null")

            return false
        }
        if (callback != null) {
            _PropertyListMutex.withLock {
                if (propertyCallbacksArrayList.contains(callback)) {
                    return false
                }
                propertyCallbacksArrayList.add(callback)
                return true
            }
        }

        return false
    }

    /**
     * Removes a property-change callback.
     */
    fun removePropertyListener(callback: BlueNoxDevicePropertyCallbacks?): Boolean {
        if (propertyCallbacksArrayList == null) {
            /* Severe Issue, should never be null */

            Log.e(MODULE_TAG, "Callback list is null")

            return false
        }
        if (callback != null) {
            _PropertyListMutex.withLock {
                return propertyCallbacksArrayList.remove(callback)
            }
        }

        return false
    }

    /**
     * Clears all registered property callbacks.
     */
    fun removeAllPropertyListeners(): Boolean {
        if (propertyCallbacksArrayList == null) {
            /* Severe Issue, should never be null */

            Log.e(MODULE_TAG, "Callback list is null")

            return false
        }

        _PropertyListMutex.withLock {
            propertyCallbacksArrayList.clear()
        }
        return true
    }

    /**
     * Opens a GATT connection to [device] and registers an optional callback.
     *
     * Enables reconnection with backoff per [setConnectionPolicy]. Service discovery runs
     * automatically after connect in [BluenoxGATTCallback].
     *
     * @param callback Optional listener added via [addListener] before connect starts.
     */
    fun connect(callback: BlueNoxDeviceCallbacks?) {
        Log.v(MODULE_TAG, "Connecting to device with address: " + mACAddress)

        if (callback != null) {
            addListener(callback)
        }

        if (mbleConnCallback == null) {
            Log.d(MODULE_TAG, "Setting new mbleConnCallback")
            mbleConnCallback = BluenoxGATTCallback(mMainCallback, "connectionCallback")
        }

        enableReconnection()
        mCurrentlyConnecting = true
        reconnectAttempt = 0
        if (!attemptGattConnection()) {
            scheduleReconnectAttempt("initial connect failed")
        }
        resetConnectionTimeoutCount(4000)

        BluenoxLEManager.getInstance().setActiveDevice(this)

        //startConnectionTimer();
    }

    /** Calls [BluetoothDevice.connectGatt] with [mbleConnCallback]; reports failures via [reportOperationFailure]. */
    private fun attemptGattConnection(): Boolean {
        if (!BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.PERMISSION_DENIED,
                detail = "BLUETOOTH_CONNECT permission is required before connecting",
                characteristicUuid = null,
            )
            return false
        }

        val targetDevice = device
        if (targetDevice == null) {
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.CONNECT_GATT_RETURNED_NULL,
                detail = "Cannot connect because device reference is null",
                characteristicUuid = null,
            )
            return false
        }

        val gatt = targetDevice.connectGatt(mParent, false, mbleConnCallback)
        if (gatt == null) {
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.CONNECT_GATT_RETURNED_NULL,
                detail = "connectGatt returned null for ${targetDevice.address}",
                characteristicUuid = null,
            )
            return false
        }

        mBluetoothGatt = gatt
        return true
    }

    /** Schedules the next GATT connect retry with exponential backoff per [connectionPolicy]. */
    private fun scheduleReconnectAttempt(trigger: String) {
        if (!mAttemptConnection) {
            return
        }

        reconnectAttempt += 1
        reconnectAttemptsTotal += 1
        if (reconnectAttempt > connectionPolicy.maxReconnectAttempts) {
            mCurrentlyConnecting = false
            reconnectExhaustions += 1
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.CONNECT_RETRY_EXHAUSTED,
                detail = "Reconnect attempts exhausted after ${connectionPolicy.maxReconnectAttempts} retries ($trigger)",
                characteristicUuid = null,
            )
            return
        }

        val backoffMs = minOf(
            connectionPolicy.maxBackoffMs,
            connectionPolicy.baseBackoffMs * (1L shl (reconnectAttempt - 1)),
        )
        reconnectHandler.postDelayed({
            if (!mAttemptConnection || !mCurrentlyConnecting) {
                return@postDelayed
            }
            Log.d(
                MODULE_TAG,
                "Reconnect attempt $reconnectAttempt/${connectionPolicy.maxReconnectAttempts} after $backoffMs ms",
            )
            if (!attemptGattConnection()) {
                scheduleReconnectAttempt("connectGatt retry failed")
            }
        }, backoffMs)
    }

    private fun reportOperationFailure(
        reason: BlueNoxDeviceCallbacks.BlueNoxFailureReason,
        detail: String,
        characteristicUuid: String?,
    ) {
        if (reason == BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_TIMEOUT) {
            operationTimeouts += 1
        }
        _mutex.withLock {
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    cback?.uiOperationFailure(device, reason, detail, characteristicUuid)
                }
            }
        }
    }

    private fun getConnectedGattOrReport(opName: String): BluetoothGatt? {
        if (!BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.PERMISSION_DENIED,
                detail = "$opName requires BLUETOOTH_CONNECT permission",
                characteristicUuid = null,
            )
            return null
        }
        val gatt = mBluetoothGatt
        if (gatt == null) {
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = "$opName failed because GATT is not connected",
                characteristicUuid = null,
            )
            return null
        }
        return gatt
    }

    private fun operationSuccess(
        operation: String,
        detail: String,
        characteristicUuid: String? = null,
        bytes: Int? = null,
        chunksStarted: Int? = null,
    ): BlueNoxGattOperationResult {
        return BlueNoxGattOperationResult(
            operation = operation,
            success = true,
            detail = detail,
            characteristicUuid = characteristicUuid,
            bytes = bytes,
            chunksStarted = chunksStarted,
        )
    }

    private fun operationFailure(
        operation: String,
        detail: String,
        characteristicUuid: String? = null,
        bytes: Int? = null,
        chunksStarted: Int? = null,
    ): BlueNoxGattOperationResult {
        return BlueNoxGattOperationResult(
            operation = operation,
            success = false,
            detail = detail,
            characteristicUuid = characteristicUuid,
            bytes = bytes,
            chunksStarted = chunksStarted,
        )
    }

    /** Requests ATT MTU negotiation; result delivered via [BlueNoxDeviceCallbacks.uiMtuUpdated]. */
    fun requestMtu(mtu: Int): Boolean = requestMtuResult(mtu).success

    /** Same as [requestMtu] but returns structured [BlueNoxGattOperationResult]. */
    fun requestMtuResult(mtu: Int): BlueNoxGattOperationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val detail = "requestMtu is not supported on API < 21"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.API_NOT_SUPPORTED,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "request-mtu", detail = detail)
        }
        if (mtu !in 23..517) {
            val detail = "Invalid MTU value $mtu. Expected range is 23..517"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "request-mtu", detail = detail)
        }
        val gatt = getConnectedGattOrReport("requestMtu")
            ?: return operationFailure(operation = "request-mtu", detail = "GATT is not connected")
        val started = gatt.requestMtu(mtu)
        if (!started) {
            val detail = "requestMtu($mtu) was rejected by Android stack"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "request-mtu", detail = detail)
        }
        return operationSuccess(operation = "request-mtu", detail = "Requested MTU $mtu")
    }

    /** Requests connection interval/latency priority (balanced / high / low power). */
    fun requestConnectionPriority(priority: Int): Boolean = requestConnectionPriorityResult(priority).success

    /** Same as [requestConnectionPriority] with structured result. */
    fun requestConnectionPriorityResult(priority: Int): BlueNoxGattOperationResult {
        val allowed = setOf(
            BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
            BluetoothGatt.CONNECTION_PRIORITY_HIGH,
            BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER,
        )
        if (!allowed.contains(priority)) {
            val detail = "Invalid connection priority $priority"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "request-connection-priority", detail = detail)
        }
        val gatt = getConnectedGattOrReport("requestConnectionPriority")
            ?: return operationFailure(operation = "request-connection-priority", detail = "GATT is not connected")
        val started = gatt.requestConnectionPriority(priority)
        if (!started) {
            val detail = "requestConnectionPriority($priority) was rejected by Android stack"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "request-connection-priority", detail = detail)
        }
        return operationSuccess(operation = "request-connection-priority", detail = "Requested connection priority $priority")
    }

    /** Attempts hidden `BluetoothGatt.refresh()` to clear cached services (stack-dependent). */
    fun refreshGattCache(): Boolean = refreshGattCacheResult().success

    /** Same as [refreshGattCache] with structured result and failure reasons. */
    fun refreshGattCacheResult(): BlueNoxGattOperationResult {
        val gatt = getConnectedGattOrReport("refreshGattCache")
            ?: return operationFailure(operation = "refresh-gatt-cache", detail = "GATT is not connected")
        val refreshed = runCatching {
            val refreshMethod: Method = gatt.javaClass.getMethod("refresh")
            (refreshMethod.invoke(gatt) as? Boolean) == true
        }.onFailure { ex ->
            val detail = "refreshGattCache reflection failed: ${ex.message ?: ex.javaClass.simpleName}"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.CACHE_REFRESH_FAILED,
                detail = detail,
                characteristicUuid = null,
            )
        }.getOrElse { false }
        if (!refreshed) {
            val detail = "refreshGattCache returned false"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.CACHE_REFRESH_FAILED,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "refresh-gatt-cache", detail = detail)
        }
        return operationSuccess(operation = "refresh-gatt-cache", detail = "Refresh cache requested")
    }

    /** Starts a remote RSSI read; value arrives on [BlueNoxDeviceCallbacks.uiNewRssiAvailable]. */
    fun readRssi(): Boolean = readRssiResult().success

    /** Same as [readRssi] with structured result. */
    fun readRssiResult(): BlueNoxGattOperationResult {
        val gatt = getConnectedGattOrReport("readRssi")
            ?: return operationFailure(operation = "read-rssi", detail = "GATT is not connected")
        val started = gatt.readRemoteRssi()
        if (!started) {
            val detail = "readRemoteRssi returned false"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "read-rssi", detail = detail)
        }
        return operationSuccess(operation = "read-rssi", detail = "RSSI read requested")
    }

    private fun parseUuidOrFail(uuid: String, label: String): UUID? {
        return runCatching { UUID.fromString(uuid) }.getOrElse {
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = "Invalid $label UUID: $uuid",
                characteristicUuid = uuid,
            )
            return null
        }
    }

    private fun getDescriptor(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        descriptorUuid: UUID,
    ): BluetoothGattDescriptor? {
        val services = mServices ?: return null
        val service = services.firstOrNull { it.uuid == serviceUuid } ?: return null
        val characteristic = service.characteristics.firstOrNull { it.uuid == characteristicUuid } ?: return null
        return characteristic.getDescriptor(descriptorUuid)
    }

    /**
     * Reads a GATT descriptor identified by UUIDs after services are discovered.
     *
     * @param serviceUuid Service UUID string.
     * @param characteristicUuid Parent characteristic UUID string.
     * @param descriptorUuid Descriptor UUID string.
     */
    fun readDescriptorByUuid(
        serviceUuid: String,
        characteristicUuid: String,
        descriptorUuid: String,
    ): Boolean {
        return readDescriptorByUuidResult(serviceUuid, characteristicUuid, descriptorUuid).success
    }

    /** Same as [readDescriptorByUuid] with structured [BlueNoxGattOperationResult]. */
    @Suppress("DEPRECATION")
    fun readDescriptorByUuidResult(
        serviceUuid: String,
        characteristicUuid: String,
        descriptorUuid: String,
    ): BlueNoxGattOperationResult {
        val serviceId = parseUuidOrFail(serviceUuid, "service")
            ?: return operationFailure(operation = "read-descriptor", detail = "Invalid service UUID", characteristicUuid = characteristicUuid)
        val characteristicId = parseUuidOrFail(characteristicUuid, "characteristic")
            ?: return operationFailure(operation = "read-descriptor", detail = "Invalid characteristic UUID", characteristicUuid = characteristicUuid)
        val descriptorId = parseUuidOrFail(descriptorUuid, "descriptor")
            ?: return operationFailure(operation = "read-descriptor", detail = "Invalid descriptor UUID", characteristicUuid = characteristicUuid)
        val gatt = getConnectedGattOrReport("readDescriptorByUuid")
            ?: return operationFailure(operation = "read-descriptor", detail = "GATT is not connected", characteristicUuid = characteristicUuid)
        val descriptor = getDescriptor(serviceId, characteristicId, descriptorId)
        if (descriptor == null) {
            val detail = "Descriptor not found: $descriptorId on characteristic $characteristicId"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = characteristicId.toString(),
            )
            return operationFailure(
                operation = "read-descriptor",
                detail = detail,
                characteristicUuid = characteristicId.toString(),
            )
        }
        val started = gatt.readDescriptor(descriptor)
        if (!started) {
            val detail = "readDescriptor failed to start for $descriptorId"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = characteristicId.toString(),
            )
            return operationFailure(
                operation = "read-descriptor",
                detail = detail,
                characteristicUuid = characteristicId.toString(),
            )
        }
        return operationSuccess(
            operation = "read-descriptor",
            detail = "Descriptor read requested",
            characteristicUuid = characteristicId.toString(),
        )
    }

    /**
     * Writes payload bytes to a GATT descriptor (e.g. CCCD or vendor descriptor).
     *
     * @param data Descriptor value; must not be null.
     */
    fun writeDescriptorByUuid(
        serviceUuid: String,
        characteristicUuid: String,
        descriptorUuid: String,
        data: ByteArray?,
    ): Boolean {
        return writeDescriptorByUuidResult(serviceUuid, characteristicUuid, descriptorUuid, data).success
    }

    /** Same as [writeDescriptorByUuid] with structured result. */
    @Suppress("DEPRECATION")
    fun writeDescriptorByUuidResult(
        serviceUuid: String,
        characteristicUuid: String,
        descriptorUuid: String,
        data: ByteArray?,
    ): BlueNoxGattOperationResult {
        if (data == null) {
            val detail = "writeDescriptorByUuid received null payload"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = characteristicUuid,
            )
            return operationFailure(operation = "write-descriptor", detail = detail, characteristicUuid = characteristicUuid)
        }
        val serviceId = parseUuidOrFail(serviceUuid, "service")
            ?: return operationFailure(operation = "write-descriptor", detail = "Invalid service UUID", characteristicUuid = characteristicUuid, bytes = data.size)
        val characteristicId = parseUuidOrFail(characteristicUuid, "characteristic")
            ?: return operationFailure(operation = "write-descriptor", detail = "Invalid characteristic UUID", characteristicUuid = characteristicUuid, bytes = data.size)
        val descriptorId = parseUuidOrFail(descriptorUuid, "descriptor")
            ?: return operationFailure(operation = "write-descriptor", detail = "Invalid descriptor UUID", characteristicUuid = characteristicUuid, bytes = data.size)
        val gatt = getConnectedGattOrReport("writeDescriptorByUuid")
            ?: return operationFailure(
                operation = "write-descriptor",
                detail = "GATT is not connected",
                characteristicUuid = characteristicId.toString(),
                bytes = data.size,
            )
        val descriptor = getDescriptor(serviceId, characteristicId, descriptorId)
        if (descriptor == null) {
            val detail = "Descriptor not found: $descriptorId on characteristic $characteristicId"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = characteristicId.toString(),
            )
            return operationFailure(
                operation = "write-descriptor",
                detail = detail,
                characteristicUuid = characteristicId.toString(),
                bytes = data.size,
            )
        }
        descriptor.value = data
        val started = gatt.writeDescriptor(descriptor)
        if (!started) {
            val detail = "writeDescriptor failed to start for $descriptorId"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = characteristicId.toString(),
            )
            return operationFailure(
                operation = "write-descriptor",
                detail = detail,
                characteristicUuid = characteristicId.toString(),
                bytes = data.size,
            )
        }
        return operationSuccess(
            operation = "write-descriptor",
            detail = "Descriptor write requested (${data.size} byte(s))",
            characteristicUuid = characteristicId.toString(),
            bytes = data.size,
        )
    }

    /** Internal fan-out callback that dispatches to all registered [BlueNoxDeviceCallbacks]. */
    fun getMainCallback() : BlueNoxDeviceCallbacks
    {
        return mMainCallback
    }


    /**
     * Bridges [BluenoxGATTCallback] into every registered [BlueNoxDeviceCallbacks]; also drives
     * [mOperationQueue] completion and standard characteristic property updates.
     */
    private val mMainCallback: BlueNoxDeviceCallbacks = object : BlueNoxDeviceCallbacks.NullBlueNoxDeviceCallbacks() {
        /** Forwards global scan-stopped events to all listeners. */
        override fun uiBluetoothScanStopped() {
            super.uiBluetoothScanStopped()

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,
                MODULE_TAG,
                "uiBluetoothScanStopped")

            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    if (cback != null) {
                        cback.uiBluetoothScanStopped()
                    }
                }
            }
            _mutex.unlock()
        }

        /** Binds the operation queue to GATT, resets reconnect state, and notifies listeners. */
        override fun uiDeviceConnected(gatt: BluetoothGatt?, device: BluetoothDevice?) {
            super.uiDeviceConnected(gatt, device)

            mConnectionTimestamp = System.currentTimeMillis() / 1000
            reconnectAttempt = 0
            reconnectHandler.removeCallbacksAndMessages(null)
            cancelBondTimeout()

            /* Initialize Operation Queue */
            mOperationQueue.setGatt(gatt)
            mStartupComplete = true
            Log.d(MODULE_TAG, "uiDeviceConnected")
            mCurrentlyConnecting = false

            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    if (cback != null) {
                        cback.uiDeviceConnected(mBluetoothGatt, device)
                    }
                }
            }
            _mutex.unlock()
        }


        /** Clears queue, closes GATT, notifies listeners, or schedules reconnect on transient failure. */
        override fun uiDeviceDisconnected(gatt: BluetoothGatt?, device: BluetoothDevice?) {
            Log.d(MODULE_TAG, "uiDeviceDisconnected")

            super.uiDeviceDisconnected(gatt, device)
            mOperationQueue.clearQueue()
            mStartupComplete = false
            reconnectHandler.removeCallbacksAndMessages(null)
            val requestedDisconnect = mDisconnectRequested

            if (mBluetoothGatt == null) {
                Log.d(MODULE_TAG, "uiDeviceDisconnected Gatt Null")
            }
            Log.d(MODULE_TAG, "Gatt CLOSING")
            if (mBluetoothGatt != null) {

                if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                mBluetoothGatt!!.close()
                    }
            }

            Log.d(MODULE_TAG, "uiDeviceDisconnected")

            mDisconnectRequested = false
            if (!mCurrentlyConnecting || requestedDisconnect) {
                _mutex.lock()
                if (deviceCallbacksArrayList != null) {
                    for (cback in deviceCallbacksArrayList) {
                        if (cback != null) {
                            cback.uiDeviceDisconnected(mBluetoothGatt, device)
                        }
                    }
                }
                _mutex.unlock()
            } else {
                Log.d(
                    MODULE_TAG,
                    "Currently trying to connect but disconneted. Attempting again to connect"
                )

                /* Got accidental disconnect due to Android issues, so reconnect with bounded backoff. */
                scheduleReconnectAttempt("disconnect during connect")
            }
        }

        /** Caches discovered services, logs the tree, queues initial reads, and refreshes characteristics. */
        override fun uiAvailableServices(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            services: List<BluetoothGattService?>?
        ) {
            super.uiAvailableServices(gatt, device, services)

            Log.d(MODULE_TAG, "Setting GATT to: $gatt")
            mBluetoothGatt = gatt
            mServices = services?.filterNotNull()

            printServiceTree()

            if (mServices != null) {
                updateAllCharacteristics(gatt, mServices!!)
                updateCharacteristics()
            }
        }


        /** Debug log of services, characteristics (with property flags), and descriptors. */
        fun printServiceTree() {
            for (serv in mServices!!) {
                Log.d(MODULE_TAG, "Service: " + serv.uuid.toString())
                for (characteristic in serv.characteristics) {
                    val properties = characteristic.properties
                    val propertiesStr = getCharacteristicPropertiesString(properties)
                    Log.d(MODULE_TAG, "Characteristic: " + characteristic.uuid.toString() + " Properties: $propertiesStr (0x${properties.toString(16)})")

                    for (desc in characteristic.descriptors) {
                        Log.d(MODULE_TAG, "Descriptor: " + desc.uuid.toString())
                    }
                }
            }
        }
        
        /** Human-readable GATT property bitmask for logging. */
        private fun getCharacteristicPropertiesString(properties: Int): String {
            val props = mutableListOf<String>()
            
            if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                props.add("READ")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                props.add("WRITE")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                props.add("WRITE_NO_RESPONSE")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                props.add("NOTIFY")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                props.add("INDICATE")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) {
                props.add("SIGNED_WRITE")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) {
                props.add("EXTENDED_PROPS")
            }
            
            return if (props.isEmpty()) "NONE" else props.joinToString(" | ")
        }

        /** Decodes characteristic bytes as UTF-8 text when possible. */
        private fun getCharacteristicValueString(data: ByteArray?): String? {
            val str: String

            if (data == null) return null

            if (data.size == 0) return null

            try {
                str = String(data, charset("UTF-8"))
                return str
            } catch (e: UnsupportedEncodingException) {
                Log.e(MODULE_TAG, "Error " + e.message)
                return null
            }
        }


        /** Enqueues a [BlueNoxOp] read for every readable characteristic after discovery. */
        fun updateAllCharacteristics(gatt: BluetoothGatt?, services: List<BluetoothGattService>) {
            for (serv in services) {
                for (characteristic in serv.characteristics) {
                    val props = characteristic.properties
                    if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        Log.d(
                            MODULE_TAG,
                            "Add Read Characteristic " + characteristic.uuid.toString()
                        )

                        //gatt.readCharacteristic(characteristic);
                        val op: BlueNoxOp = BlueNoxOp(
                            BlueNoxOp.OpType.Read,
                            BlueNoxOp.OpCompleteCondition.Read,
                            characteristic
                        )

                        addQueueOperation(op)
                    }
                }
            }
        }


        /** Updates cached DIS/battery fields and property callbacks; otherwise delegates to [characteristicNotification]. */
        private fun refreshUpdatedCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
            val data = characteristicValueOrNull(characteristic) ?: return false

            //Log.d(MODULE_TAG, "Characteristic " + characteristic.getUuid().toString() + "  " + characteristic.getStringValue(0));

            if (characteristic.uuid == BLEUUIDs.Characteristic.MANUFACTURER_STRING) {
                manufacturerName = getCharacteristicValueString(data)

                _PropertyListMutex.lock()
                if (propertyCallbacksArrayList != null) {
                    for (cback in propertyCallbacksArrayList) {
                        if (cback != null) {
                            cback.updatedManufacturer()
                        }
                    }
                }
                _PropertyListMutex.unlock()
                return true
            } else if (characteristic.uuid == BLEUUIDs.Characteristic.MODEL_NUMBER_STRING) {
                ModelNumber = getCharacteristicValueString(data)

                _PropertyListMutex.lock()
                if (propertyCallbacksArrayList != null) {
                    for (cback in propertyCallbacksArrayList) {
                        if (cback != null) {
                            cback.updatedModelNumber()
                        }
                    }
                }
                _PropertyListMutex.unlock()
                return true
            } else if (characteristic.uuid == BLEUUIDs.Characteristic.SERIAL_NUMBER_STRING) {
                _PropertyListMutex.lock()
                if (propertyCallbacksArrayList != null) {
                    for (cback in propertyCallbacksArrayList) {
                        if (cback != null) {
                            cback.updatedSerialNumber()
                        }
                    }
                }
                _PropertyListMutex.unlock()
                SerialNumber = getCharacteristicValueString(data)
                return true
            } else if (characteristic.uuid == BLEUUIDs.Characteristic.HARDWARE_REVISION_STRING) {
                _PropertyListMutex.lock()
                if (propertyCallbacksArrayList != null) {
                    for (cback in propertyCallbacksArrayList) {
                        if (cback != null) {
                            cback.updatedHardwareRevision()
                        }
                    }
                }
                _PropertyListMutex.unlock()
                HarwareVersion = getCharacteristicValueString(data)
                return true
            } else if (characteristic.uuid == BLEUUIDs.Characteristic.SOFTWARE_REVISION_STRING) {
                _PropertyListMutex.lock()
                if (propertyCallbacksArrayList != null) {
                    for (cback in propertyCallbacksArrayList) {
                        if (cback != null) {
                            cback.updatedSoftwareRevision()
                        }
                    }
                }
                _PropertyListMutex.unlock()
                SoftwareVersion = getCharacteristicValueString(data)
                return true
            } else if (characteristic.uuid == BLEUUIDs.Characteristic.FIRMWARE_REVISION_STRING) {
                _PropertyListMutex.lock()
                if (propertyCallbacksArrayList != null) {
                    for (cback in propertyCallbacksArrayList) {
                        if (cback != null) {
                            cback.updatedFirmwareRevision()
                        }
                    }
                }
                _PropertyListMutex.unlock()
                FirmwareVersion = getCharacteristicValueString(data)
                return true
            } else if (characteristic.uuid == BLEUUIDs.Characteristic.APPEARANCE) {
                return true
            } else if (characteristic.uuid == BLEUUIDs.Characteristic.BODY_SENSOR_LOCATION) {
                return true
            } else if (characteristic.uuid == BLEUUIDs.Characteristic.BATTERY_LEVEL) {
                batteryLvl = data[0].toString()

                _PropertyListMutex.lock()
                if (propertyCallbacksArrayList != null) {
                    for (cback in propertyCallbacksArrayList) {
                        if (cback != null) {
                            cback.updatedBatteryLevel()
                        }
                    }
                }
                _PropertyListMutex.unlock()
                return true
            }

            return characteristicNotification(characteristic)
        }

        /** Unused helper to refresh standard characteristics from an in-memory service list. */
        @Suppress("unused")
        private fun refreshUpdatedServices(services: List<BluetoothGattService>) {
            for (serv in services) {
                for (characteristic in serv.characteristics) {
                    Log.d(MODULE_TAG, "Characteristic ${characteristic.uuid}")
                    val data = characteristicValueOrNull(characteristic) ?: continue

                    if (characteristic.uuid == BLEUUIDs.Characteristic.MANUFACTURER_STRING) {
                        manufacturerName = getCharacteristicValueString(data)
                    } else if (characteristic.uuid == BLEUUIDs.Characteristic.MODEL_NUMBER_STRING) {
                        ModelNumber = getCharacteristicValueString(data)
                    } else if (characteristic.uuid == BLEUUIDs.Characteristic.SERIAL_NUMBER_STRING) {
                        SerialNumber = getCharacteristicValueString(data)
                    } else if (characteristic.uuid == BLEUUIDs.Characteristic.HARDWARE_REVISION_STRING) {
                        HarwareVersion = getCharacteristicValueString(data)
                    } else if (characteristic.uuid == BLEUUIDs.Characteristic.FIRMWARE_REVISION_STRING) {
                        FirmwareVersion = getCharacteristicValueString(data)
                    } else if (characteristic.uuid == BLEUUIDs.Characteristic.APPEARANCE) {
                    } else if (characteristic.uuid == BLEUUIDs.Characteristic.BODY_SENSOR_LOCATION) {
                    } else if (characteristic.uuid == BLEUUIDs.Characteristic.BATTERY_LEVEL) {
                    }
                }
            }
        }

        /** Legacy stub; resolves characteristics for [service] when [gatt] is non-null. */
        fun getCharacteristicsForService(service: BluetoothGattService?, gatt: BluetoothGatt?) {
            if (service == null) {
                return
            }

            var chars: List<BluetoothGattCharacteristic?>? = null

            //BluetoothGatt curGatt = findExistingDeviceGatt(device.getAddress());
            if (gatt != null) {
                chars = service.characteristics

                //mUiCallback.uiCharacteristicForService(curGatt, device, service, chars);
            }
        }

        /** Forwards per-service characteristic lists to registered listeners. */
        override fun uiCharacteristicForService(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            service: BluetoothGattService?,
            chars: List<BluetoothGattCharacteristic?>?
        ) {
            super.uiCharacteristicForService(gatt, device, service, chars)

            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    if (cback != null) {
                        cback.uiCharacteristicForService(gatt, device, service, chars)
                    }
                }
            }
            _mutex.unlock()


            /*
            for (BluetoothGattCharacteristic c : chars) {

            }*/
        }

        /** Notifies listeners and completes a queued write operation on [ch]. */
        override fun uiSuccessfulWrite(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            service: BluetoothGattService?,
            ch: BluetoothGattCharacteristic?,
            description: String?
        ) {
            super.uiSuccessfulWrite(gatt, device, service, ch, description)

            Log.d(MODULE_TAG, "uiSuccessfulWrite")

            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    if (cback != null) {
                        cback.uiSuccessfulWrite(gatt, device, service, ch, description)
                    }
                }
            }
            _mutex.unlock()

            if (ch != null) {
                mOperationQueue.eventHandler(ch, BlueNoxOp.OpCompleteCondition.Write)
            }
        }

        /** Notifies listeners and signals write failure to the operation queue for [ch]. */
        override fun uiFailedWrite(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            service: BluetoothGattService?,
            ch: BluetoothGattCharacteristic?,
            description: String?
        ) {
            super.uiFailedWrite(gatt, device, service, ch, description)
            Log.d(MODULE_TAG, "uiFailedWrite")

            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    if (cback != null) {
                        cback.uiFailedWrite(gatt, device, service, ch, description)
                    }
                }
            }
            _mutex.unlock()

            if (ch != null) {
                mOperationQueue.eventHandler(ch, BlueNoxOp.OpCompleteCondition.Write)
            }
        }

        /** Forwards remote RSSI reads to all listeners. */
        override fun uiNewRssiAvailable(gatt: BluetoothGatt?, device: BluetoothDevice?, rssi: Int) {
            super.uiNewRssiAvailable(gatt, device, rssi)


            //AdvertisementEvent(Date t, int r, ScanRecord s)

            //mAdvList.add(new AdvertisementEvent(new Date(), rssi, ))
            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    if (cback != null) {
                        cback.uiNewRssiAvailable(gatt, device, rssi)
                    }
                }
            }
            _mutex.unlock()
        }


        /** Legacy notification path: forwards to listeners and completes notify ops on the queue. */
        override fun uiGotNotification(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.uiGotNotification(gatt, device, characteristic)

            if (characteristic != null) {

                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
                    "uiGotNotification : " + characteristic.uuid.toString())


                val sb = StringBuilder()
                val payload: ByteArray = characteristicValueOrNull(characteristic) ?: byteArrayOf()

                for (b in payload) {
                    sb.append(String.format("%02X ", b))
                }


                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
                    "Notification Data: $sb")

                _mutex.lock()
                if (deviceCallbacksArrayList != null) {
                    for (cback in deviceCallbacksArrayList) {
                        if (cback != null) {
                            cback.uiGotNotification(gatt, device, characteristic)
                        }
                    }
                }
                _mutex.unlock()


                mOperationQueue.eventHandler(characteristic, BlueNoxOp.OpCompleteCondition.Notification)

            }
        }


        /** API 33+ value callback: updates standard characteristics, forwards [value], may complete queue ops. */
        override fun uiCharacteristicUpdated(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?,
            value : ByteArray
        ) {
            super.uiCharacteristicUpdated(gatt, device, characteristic, value)

            Log.d(MODULE_TAG,
                "837 Received Notification for ${characteristic.toString()} with ${value.size}" )

            if(characteristic != null) {

                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,
                    "uiCharacteristicUpdated with len: ${value.size}")

                val success = refreshUpdatedCharacteristic(characteristic)

                _mutex.lock()
                if (deviceCallbacksArrayList != null) {
                    Log.d(MODULE_TAG, "837 Not Null" )
                    for (cback in deviceCallbacksArrayList) {
                        if (cback != null) {
                            Log.d(MODULE_TAG, "837 Calling uiCharacteristicUpdated" )
                            cback.uiCharacteristicUpdated(gatt, device, characteristic, value)
                        }
                    }
                }
                _mutex.unlock()

                if (success) mOperationQueue.eventHandler(
                    characteristic,
                    BlueNoxOp.OpCompleteCondition.Notification
                )
            }
        }

        /** Dispatches read results, refreshes cached properties, and completes read queue ops. */
        override fun uiCharacteristicRead(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.uiCharacteristicRead(gatt, device, characteristic)

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,"uiDescriptorWritten")

            if(characteristic != null) {
                refreshUpdatedCharacteristic(characteristic)

                _mutex.lock()
                if (deviceCallbacksArrayList != null) {
                    for (cback in deviceCallbacksArrayList) {
                        if (cback != null) {
                            cback.uiCharacteristicRead(gatt, device, characteristic)
                        }
                    }
                }
                _mutex.unlock()

                mOperationQueue.eventHandler(characteristic, BlueNoxOp.OpCompleteCondition.Read)
            }
        }

        /** Forwards descriptor writes, emits CCCD confirmation when applicable, and advances the queue. */
        override fun uiDescriptorWritten(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.uiDescriptorWritten(gatt, descriptor, status)

            if (descriptor != null) {
                val cccdResult = if (descriptor.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) {
                    val key = descriptor.characteristic.uuid.toString()
                    _mutex.withLock { pendingCccdByCharacteristic.remove(key) }?.let { pending ->
                        val confirmed = status == BluetoothGatt.GATT_SUCCESS &&
                            descriptor.value?.contentEquals(pending.expectedValue) == true
                        BlueNoxCccdConfigurationResult(
                            characteristicUuid = pending.characteristicUuid,
                            enabled = pending.enabled,
                            indicate = pending.indicate,
                            gattStatus = status,
                            confirmedValueMatches = confirmed,
                        )
                    }
                } else {
                    null
                }

                _mutex.lock()
                if (deviceCallbacksArrayList != null) {
                    for (cback in deviceCallbacksArrayList) {
                        if (cback != null) {
                            cback.uiDescriptorWritten(gatt, descriptor, status)
                            if (cccdResult != null) {
                                cback.uiCccdConfigured(this@BlueNoxDevice, cccdResult)
                            }
                        }
                    }
                }
                _mutex.unlock()

                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,"uiDescriptorWritten")

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    reportOperationFailure(
                        reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                        detail = "Descriptor write failed with status $status for ${descriptor.characteristic.uuid}",
                        characteristicUuid = descriptor.characteristic.uuid.toString(),
                    )
                }

                mOperationQueue.eventHandler(
                    descriptor.characteristic,
                    BlueNoxOp.OpCompleteCondition.DescriptorWrite
                )

            }
        }

        /** Forwards descriptor read results to listeners. */
        override fun uiDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
            value: ByteArray?,
        ) {
            super.uiDescriptorRead(gatt, descriptor, status, value)
            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    cback?.uiDescriptorRead(gatt, descriptor, status, value)
                }
            }
            _mutex.unlock()
        }

        /** Forwards simplified bonded / not bonded state. */
        override fun uiBondingChanged(device: BlueNoxDevice?, bondState: Boolean) {
            super.uiBondingChanged(device, bondState)

            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    if (cback != null) {
                        cback.uiBondingChanged(device, bondState)
                    }
                }
            }
            _mutex.unlock()

        }

        /** Forwards detailed bond state machine updates from the GATT layer. */
        override fun uiBondStateEvent(
            device: BlueNoxDevice?,
            state: BlueNoxDeviceCallbacks.BlueNoxBondState,
            detail: String,
        ) {
            super.uiBondStateEvent(device, state, detail)
            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    cback?.uiBondStateEvent(device, state, detail)
                }
            }
            _mutex.unlock()
        }

        /** Forwards negotiated ATT MTU to listeners. */
        override fun uiMtuUpdated(gatt: BluetoothGatt?, mtu: Int) {
            super.uiMtuUpdated(gatt, mtu)

            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    if (cback != null) {
                        cback.uiMtuUpdated(gatt, mtu)
                    }
                }
            }
            _mutex.unlock()
        }

        /** Forwards connection parameter update (interval, latency, supervision timeout). */
        override fun uiConnectionUpdated(
            gatt: BluetoothGatt?,
            interval: Int,
            latency: Int,
            timeout: Int,
            status: Int,
        ) {
            super.uiConnectionUpdated(gatt, interval, latency, timeout, status)
            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    cback?.uiConnectionUpdated(gatt, interval, latency, timeout, status)
                }
            }
            _mutex.unlock()
        }

        /** Indicates remote service database changed; does not auto-rediscover to avoid stack loops. */
        override fun uiServicesChanged(gatt: BluetoothGatt?, device: BluetoothDevice?) {
            super.uiServicesChanged(gatt, device)
            // Do not call discoverServices() here; this callback can be emitted as part of
            // service discovery flow on some builds, and rediscovering here causes a loop.
            _mutex.lock()
            if (deviceCallbacksArrayList != null) {
                for (cback in deviceCallbacksArrayList) {
                    cback?.uiServicesChanged(gatt, device)
                }
            }
            _mutex.unlock()
        }
    }

    init
    {
        mOperationQueue = BlueNoxOpQueue(this, mBluetoothGatt)
        mCurrentlyConnecting = false
        mDisconnectRequested = false
    }

    /**
     * Requests GATT disconnect if connected (legacy helper; [devAddr] is unused in current implementation).
     */
    @Suppress("unused")
    fun disconnectDevice(devAddr: String?) {

        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,"BlueNoxDevice - disconnectDevice")

        if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            mBluetoothGatt?.disconnect()
        }
    }

    /** Hook for subclasses when a non-standard characteristic updates; default returns false. */
    protected fun characteristicNotification(characteristic: BluetoothGattCharacteristic?): Boolean {

        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,"updateCharacteristics")

        return false
    }

    /** Called after service discovery; override to refresh app-specific characteristic state. */
    protected fun updateCharacteristics() {
        //Log.d(MODULE_TAG, "updateCharacteristics");
    }

    /**
     * User-requested disconnect: stops reconnection, clears the operation queue, and closes GATT when the stack delivers disconnect.
     */
    fun disconnect() {
        Log.d(MODULE_TAG, "Requesting Disconnection")
        mDisconnectRequested = true
        stopReconnection()
        mCurrentlyConnecting = false
        bondInProgress = false
        if (mBluetoothGatt != null) {
            if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                mBluetoothGatt?.disconnect()
            }
        } else {
            Log.d(MODULE_TAG, "mBluetoothGatt null")
        }
        mOperationQueue.clearQueue()
    }

    /** Returns cumulative counters for reconnect, bond, and operation-timeout diagnostics. */
    fun sessionDiagnostics(): BlueNoxSessionDiagnostics {
        return BlueNoxSessionDiagnostics(
            reconnectAttempts = reconnectAttemptsTotal,
            reconnectExhaustions = reconnectExhaustions,
            bondAttempts = bondAttempts,
            bondRetries = bondRetries,
            bondFailures = bondFailures,
            operationTimeouts = operationTimeouts,
        )
    }

    /** Whether automatic reconnection is currently enabled ([enableReconnection]). */
    fun attemptConnection(): Boolean {
        return mAttemptConnection
    }

    /** Seeds the internal connection-timeout counter used by legacy connection timers. */
    fun resetConnectionTimeoutCount(cnt: Int) {
        connectionTimeoutCount = cnt
    }

    /** Optional alternate GATT callback reference (most flows use [mbleConnCallback]). */
    @get:Suppress("unused")
    var callback: BluenoxGATTCallback?
        get() = mCallback
        set(cback) {
            mCallback = cback
        }

    /** Decrements the internal connection-timeout counter by [cnt], floored at zero. */
    fun reduceConnectionTimeoutCount(cnt: Int) {
        if (connectionTimeoutCount - cnt >= 0) connectionTimeoutCount -= cnt
        else connectionTimeoutCount = 0
    }

    /** True when the legacy connection-timeout counter has reached zero. */
    fun ConnectionTimeoutExpired(): Boolean {
        return connectionTimeoutCount == 0
    }

    /** Bluetooth address from [device], or empty string if device is null. */
    val mACAddress: String
        get() = if (device == null) {
            ""
        } else {
            device!!.address
        }

    /** Returns the peripheral friendly name when [Manifest.permission.BLUETOOTH_CONNECT] allows, or `"N/A"`. */
    fun getName(): String {
        if (device == null) {
            return "N/A"
        } else
                {
            if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                if (device!!.name == null) {
                    return "N/A"
                } else {
                    return device!!.name
                }
            }
        }
        return "N/A"
    }

    /** Returns the Bluetooth MAC address when permitted, or empty string / `"N/A"`. */
    fun getMacAddress(): String {
        if (device == null) {
            return ""
        } else
        {
            if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                if (device!!.address == null) {
                    return "N/A"
                } else {
                    return device!!.address
                }
            }
        }
        return ""
    }


    /** True when [mBluetoothGatt] exists and profile state is [BluetoothProfile.STATE_CONNECTED]. */
    @get:Suppress("unused")
    val isConnected: Boolean
        get() {
            if (mBluetoothGatt != null) {

                val state: Int = getConnectionState()
                return state == BluetoothProfile.STATE_CONNECTED
            } else {
                return false
            }
        }

    /** Platform GATT connection state for this device (see [BluetoothProfile]). */
    fun getConnectionState(): Int
    {
        return BluenoxLEManager.getInstance().getConnectionState(this)
    }


    /** Queues a read of the standard firmware revision characteristic (API 31+). */
    @RequiresApi(Build.VERSION_CODES.S)
    protected fun fetchFirmwareVersion() {
        if (mBluetoothGatt != null) {
            Log.d(MODULE_TAG, "Device Reading Firmware")
            if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                readCharacteristic(BLEUUIDs.Characteristic.FIRMWARE_REVISION_STRING)
            }
        }
    }

    /** Seconds since [uiDeviceConnected] set [mConnectionTimestamp], or since epoch if unset. */
    val connectionTime: Long
        get() {
            val curTimestamp = System.currentTimeMillis() / 1000

            return curTimestamp - mConnectionTimestamp
        }

    /** Queues a read of the manufacturer name string (API 31+). */
    @RequiresApi(Build.VERSION_CODES.S)
    protected fun fetchManufacturer() {
        if (mBluetoothGatt != null) {
            Log.d(MODULE_TAG, "Device Reading Manufacturer")
            if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                readCharacteristic(BLEUUIDs.Characteristic.MANUFACTURER_STRING)
            }
        }
    }

    /** Queues a read of the serial number string (API 31+). */
    @RequiresApi(Build.VERSION_CODES.S)
    protected fun fetchSerialNumber() {
        if (mBluetoothGatt != null) {
            Log.d(MODULE_TAG, "Device Reading Serial Num")
            if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                readCharacteristic(BLEUUIDs.Characteristic.SERIAL_NUMBER_STRING)
            }
        }
    }

    /** Queues a read of the hardware revision string (API 31+). */
    @RequiresApi(Build.VERSION_CODES.S)
    protected fun fetchHardwareRevision() {
        if (mBluetoothGatt != null) {
            Log.d(MODULE_TAG, "Device Reading Hardware Rev")

            if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                readCharacteristic(BLEUUIDs.Characteristic.HARDWARE_REVISION_STRING)
            }
        }
    }

    /** True if [uuid] resolves to a characteristic in the cached service list. */
    protected fun characteristicExists(uuid: String?): Boolean {
        return getCharacteristicByUUID(UUID.fromString(uuid)) != null
    }

    /** Looks up a characteristic by UUID string in discovered services. */
    protected fun getCharacteristicByUUID(uuid: String?): BluetoothGattCharacteristic? {
        return getCharacteristicByUUID(UUID.fromString(uuid))
    }

    /** Looks up a characteristic by [u] in discovered services. */
    protected fun getCharacteristicByUUID(u: UUID): BluetoothGattCharacteristic? {
        if (mServices != null && mServices!!.size > 0) {
            for (serv in mServices!!) {
                for (characteristic in serv.characteristics) {
                    if (characteristic.uuid == u) {
                        return characteristic
                    }
                }
            }
        }

        return null
    }

    /** Enqueues [op] on [mOperationQueue] when non-null. */
    protected fun addQueueOperation(op: BlueNoxOp?) {
        if (op != null) {
            mOperationQueue.addOperation(op)
        } else {
            Log.d(MODULE_TAG, "Queue Characteristic Error")
        }
    }

    /** Removes a not-yet-started queued operation by [BlueNoxOp.mOperationId]. */
    fun cancelQueuedOperation(operationId: String): Boolean {
        return mOperationQueue.cancelOperation(operationId)
    }

    /** Cancels all queued operations targeting [uuid] that have not started yet; returns count removed. */
    fun cancelQueuedOperationsForCharacteristic(uuid: String): Int {
        val parsed = runCatching { UUID.fromString(uuid) }.getOrNull() ?: return 0
        return mOperationQueue.cancelOperationsForCharacteristic(parsed)
    }

    /** Current depth of the internal [BlueNoxOpQueue]. */
    fun queuedOperationCount(): Int {
        return mOperationQueue.queueDepth()
    }

    /** Alias for [stopReconnection]. */
    @Suppress("unused")
    fun disableReconnection() {
        stopReconnection()
    }

    /** Enables or disables notifications (not indications) via CCCD for [uuid]. */
    fun setNotificationForCharacteristic(uuid: String, enabled: Boolean) {
        configureCccdByUuidResult(uuid = uuid, enabled = enabled, indicate = false)
    }

    /** Enables/disables notify or indicate on the CCCD for [uuid] per [indicate] when [enabled] is true. */
    fun configureCccdByUuid(uuid: String, enabled: Boolean, indicate: Boolean): Boolean {
        return configureCccdByUuidResult(uuid = uuid, enabled = enabled, indicate = indicate).success
    }

    /** Same as [configureCccdByUuid] with structured result and validation of characteristic properties. */
    fun configureCccdByUuidResult(uuid: String, enabled: Boolean, indicate: Boolean): BlueNoxGattOperationResult {
        val normalizedUuid = runCatching { UUID.fromString(uuid).toString() }.getOrElse {
            val detail = "Invalid characteristic UUID: $uuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = uuid,
            )
            return operationFailure(operation = "configure-cccd", detail = detail, characteristicUuid = uuid)
        }
        val gatt = getConnectedGattOrReport("configureCccdByUuid")
            ?: return operationFailure(
                operation = "configure-cccd",
                detail = "GATT is not connected",
                characteristicUuid = normalizedUuid,
            )
        val characteristic = getCharacteristicByUUID(normalizedUuid)
        if (characteristic == null) {
            val detail = "Characteristic not found for CCCD config: $normalizedUuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = normalizedUuid,
            )
            return operationFailure(operation = "configure-cccd", detail = detail, characteristicUuid = normalizedUuid)
        }

        val supportsNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val supportsIndicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        if (enabled && indicate && !supportsIndicate) {
            val detail = "Characteristic does not support indications: $normalizedUuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = normalizedUuid,
            )
            return operationFailure(operation = "configure-cccd", detail = detail, characteristicUuid = normalizedUuid)
        }
        if (enabled && !indicate && !supportsNotify) {
            val detail = "Characteristic does not support notifications: $normalizedUuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = normalizedUuid,
            )
            return operationFailure(operation = "configure-cccd", detail = detail, characteristicUuid = normalizedUuid)
        }

        val cccd = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (cccd == null) {
            val detail = "CCCD descriptor missing for $normalizedUuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = normalizedUuid,
            )
            return operationFailure(operation = "configure-cccd", detail = detail, characteristicUuid = normalizedUuid)
        }

        val expected = when {
            !enabled -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            indicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }.copyOf()

        if (!gatt.setCharacteristicNotification(characteristic, enabled)) {
            val detail = "setCharacteristicNotification failed for $normalizedUuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = normalizedUuid,
            )
            return operationFailure(operation = "configure-cccd", detail = detail, characteristicUuid = normalizedUuid)
        }
        cccd.value = expected
        @Suppress("DEPRECATION")
        val started = gatt.writeDescriptor(cccd)
        if (!started) {
            val detail = "writeDescriptor failed for CCCD on $normalizedUuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = normalizedUuid,
            )
            return operationFailure(operation = "configure-cccd", detail = detail, characteristicUuid = normalizedUuid)
        }

        _mutex.withLock {
            pendingCccdByCharacteristic[normalizedUuid] = PendingCccdRequest(
                characteristicUuid = normalizedUuid,
                enabled = enabled,
                indicate = indicate,
                expectedValue = expected,
            )
        }
        val mode = if (!enabled) "disabled" else if (indicate) "indicate" else "notify"
        return operationSuccess(
            operation = "configure-cccd",
            detail = "CCCD write requested ($mode)",
            characteristicUuid = normalizedUuid,
        )
    }

    fun writeCharacteristicByUUID(uuid: String, data: ByteArray?, response: Boolean): Boolean {
        return writeCharacteristicByUUIDResult(uuid, data, response).success
    }

    fun writeCharacteristicByUUIDResult(uuid: String, data: ByteArray?, response: Boolean): BlueNoxGattOperationResult {
        if (data == null) {
            val detail = "writeCharacteristicByUUID received null payload"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = uuid,
            )
            return operationFailure(operation = "write", detail = detail, characteristicUuid = uuid)
        }

        val normalizedUuid = runCatching { UUID.fromString(uuid).toString() }.getOrElse {
            val detail = "Invalid characteristic UUID: $uuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = uuid,
            )
            return operationFailure(operation = "write", detail = detail, characteristicUuid = uuid, bytes = data.size)
        }

        val gatt = getConnectedGattOrReport("writeCharacteristicByUUID")
            ?: return operationFailure(
                operation = "write",
                detail = "GATT is not connected",
                characteristicUuid = normalizedUuid,
                bytes = data.size,
            )
        val ch = getCharacteristicByUUID(normalizedUuid)
        if (ch == null) {
            val detail = "Characteristic not found: $normalizedUuid"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = normalizedUuid,
            )
            return operationFailure(operation = "write", detail = detail, characteristicUuid = normalizedUuid, bytes = data.size)
        }

        val writeType = if (response) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        ch.writeType = writeType

        Log.d(MODULE_TAG, "Writing to $ch data: $data")
        var requestStatus = gatt.writeCharacteristic(ch, data, writeType)

        if (requestStatus == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY) {
            Handler(Looper.getMainLooper()).postDelayed({
                gatt.writeCharacteristic(ch, data, writeType)
            }, 100)
        }

        val started = requestStatus == BluetoothStatusCodes.SUCCESS ||
            requestStatus == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY

        if (!started) {
            val detail = "writeCharacteristicByUUID failed with status $requestStatus"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = normalizedUuid,
            )
            return operationFailure(operation = "write", detail = detail, characteristicUuid = normalizedUuid, bytes = data.size)
        }
        return operationSuccess(
            operation = "write",
            detail = "Write requested (${data.size} byte(s))",
            characteristicUuid = normalizedUuid,
            bytes = data.size,
        )
    }

    fun writeCharacteristicSplitByUUID(
        uuid: String,
        data: ByteArray?,
        response: Boolean,
        maxChunkSize: Int,
    ): Int = writeCharacteristicSplitByUUIDResult(uuid, data, response, maxChunkSize).chunksStarted ?: 0

    fun writeCharacteristicLongByUUID(
        uuid: String,
        data: ByteArray?,
        response: Boolean,
    ): BlueNoxGattOperationResult {
        val strategy = longWriteStrategy
        return writeCharacteristicSplitInternal(
            uuid = uuid,
            data = data,
            response = response,
            maxChunkSize = strategy.batchSize,
            strategy = strategy,
        )
    }

    fun writeCharacteristicSplitByUUIDResult(
        uuid: String,
        data: ByteArray?,
        response: Boolean,
        maxChunkSize: Int,
    ): BlueNoxGattOperationResult {
        return writeCharacteristicSplitInternal(
            uuid = uuid,
            data = data,
            response = response,
            maxChunkSize = maxChunkSize,
            strategy = longWriteStrategy.copy(batchSize = maxChunkSize),
        )
    }

    private fun writeCharacteristicSplitInternal(
        uuid: String,
        data: ByteArray?,
        response: Boolean,
        maxChunkSize: Int,
        strategy: BlueNoxLongWriteStrategy,
    ): BlueNoxGattOperationResult {
        if (data == null) {
            val detail = "writeCharacteristicSplitByUUID received null payload"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = uuid,
            )
            return operationFailure(operation = "write-split", detail = detail, characteristicUuid = uuid)
        }
        if (maxChunkSize <= 0) {
            val detail = "Invalid maxChunkSize=$maxChunkSize for split write"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.INVALID_ARGUMENT,
                detail = detail,
                characteristicUuid = uuid,
            )
            return operationFailure(operation = "write-split", detail = detail, characteristicUuid = uuid, bytes = data.size)
        }

        var failedChunks = 0
        var lastFailureDetail: String? = null
        var chunksStarted = 0
        var offset = 0
        val retryBackoffMs = strategy.retryBackoffMs
        while (offset < data.size) {
            val next = minOf(offset + maxChunkSize, data.size)
            val chunk = data.copyOfRange(offset, next)
            var attempts = 0
            var chunkResult = writeCharacteristicByUUIDResult(uuid, chunk, response)
            while (!chunkResult.success && attempts < strategy.maxRetriesPerChunk) {
                attempts += 1
                if (retryBackoffMs > 0L) {
                    runCatching { Thread.sleep(retryBackoffMs) }
                }
                chunkResult = writeCharacteristicByUUIDResult(uuid, chunk, response)
            }
            if (!chunkResult.success) {
                failedChunks += 1
                lastFailureDetail = chunkResult.detail
                if (!strategy.continueOnChunkFailure) {
                    return operationFailure(
                        operation = "write-split",
                        detail = chunkResult.detail,
                        characteristicUuid = chunkResult.characteristicUuid,
                        bytes = data.size,
                        chunksStarted = chunksStarted,
                    )
                }
            } else {
                chunksStarted++
            }
            offset = next
            if (strategy.interChunkDelayMs > 0L && offset < data.size) {
                runCatching { Thread.sleep(strategy.interChunkDelayMs) }
            }
        }
        if (failedChunks > 0) {
            return operationFailure(
                operation = "write-split",
                detail = "Split write finished with $failedChunks failed chunk(s). Last failure: ${lastFailureDetail ?: "unknown"}",
                characteristicUuid = runCatching { UUID.fromString(uuid).toString() }.getOrNull() ?: uuid,
                bytes = data.size,
                chunksStarted = chunksStarted,
            )
        }
        return operationSuccess(
            operation = "write-split",
            detail = "Split write requested in $chunksStarted chunk(s)",
            characteristicUuid = runCatching { UUID.fromString(uuid).toString() }.getOrNull() ?: uuid,
            bytes = data.size,
            chunksStarted = chunksStarted,
        )
    }

    fun beginReliableWriteTransaction(): Boolean = beginReliableWriteTransactionResult().success

    fun beginReliableWriteTransactionResult(): BlueNoxGattOperationResult {
        val gatt = getConnectedGattOrReport("beginReliableWriteTransaction")
            ?: return operationFailure(operation = "reliable-begin", detail = "GATT is not connected")
        val started = gatt.beginReliableWrite()
        if (!started) {
            val detail = "beginReliableWrite returned false"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "reliable-begin", detail = detail)
        }
        return operationSuccess(operation = "reliable-begin", detail = "Reliable write begun")
    }

    fun executeReliableWriteTransaction(): Boolean = executeReliableWriteTransactionResult().success

    fun executeReliableWriteTransactionResult(): BlueNoxGattOperationResult {
        val gatt = getConnectedGattOrReport("executeReliableWriteTransaction")
            ?: return operationFailure(operation = "reliable-execute", detail = "GATT is not connected")
        val started = gatt.executeReliableWrite()
        if (!started) {
            val detail = "executeReliableWrite returned false"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = null,
            )
            return operationFailure(operation = "reliable-execute", detail = detail)
        }
        return operationSuccess(operation = "reliable-execute", detail = "Reliable write execute requested")
    }

    fun abortReliableWriteTransaction(): Boolean = abortReliableWriteTransactionResult().success

    fun abortReliableWriteTransactionResult(): BlueNoxGattOperationResult {
        val gatt = getConnectedGattOrReport("abortReliableWriteTransaction")
            ?: return operationFailure(operation = "reliable-abort", detail = "GATT is not connected")
        val aborted = runCatching {
            gatt.abortReliableWrite()
            true
        }.getOrElse {
            val detail = "abortReliableWrite failed: ${it.message ?: it.javaClass.simpleName}"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = null,
            )
            false
        }
        if (!aborted) {
            return operationFailure(operation = "reliable-abort", detail = "abortReliableWrite returned false")
        }
        return operationSuccess(operation = "reliable-abort", detail = "Reliable write abort requested")
    }

    fun writeCharacteristic(uuid: UUID, `val`: ByteArray?, response: Boolean) {
        val payload = `val` ?: return
        if (mServices != null) {
            for (srvc in mServices!!) {
                for (characteristic in srvc.characteristics) {
                    if (characteristic.uuid == uuid) {
                        val writeType = if (!response) {
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        } else {
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        }

                        if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT))
                        {
                            val gatt = mBluetoothGatt ?: return
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeCharacteristic(characteristic, payload, writeType)
                            } else {
                                @Suppress("DEPRECATION")
                                run {
                                    characteristic.setValue(payload)
                                    characteristic.writeType = writeType
                                    gatt.writeCharacteristic(characteristic)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun characteristicValueOrNull(characteristic: BluetoothGattCharacteristic?): ByteArray? {
        return characteristic?.value
    }

    fun requestReadCharacteristic(uuid: String): Boolean = requestReadCharacteristicResult(uuid).success

    fun requestReadCharacteristicResult(uuid: String): BlueNoxGattOperationResult {
        val characteristic = getCharacteristicByUUID(uuid) ?: run {
            val detail = "Characteristic not found: $uuid"
            return operationFailure(operation = "read", detail = detail, characteristicUuid = uuid)
        }
        return requestReadCharacteristicResult(characteristic.uuid)
    }

    fun requestReadCharacteristic(uuid: UUID): Boolean = requestReadCharacteristicResult(uuid).success

    fun requestReadCharacteristicResult(uuid: UUID): BlueNoxGattOperationResult {
        val characteristic = getCharacteristicByUUID(uuid) ?: run {
            val normalized = uuid.toString()
            return operationFailure(operation = "read", detail = "Characteristic not found: $normalized", characteristicUuid = normalized)
        }
        if (!BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            val detail = "BLUETOOTH_CONNECT permission is required before read"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.PERMISSION_DENIED,
                detail = detail,
                characteristicUuid = uuid.toString(),
            )
            return operationFailure(operation = "read", detail = detail, characteristicUuid = uuid.toString())
        }
        val started = mBluetoothGatt?.readCharacteristic(characteristic) ?: false
        if (!started) {
            val detail = "readCharacteristic failed to start for ${uuid}"
            reportOperationFailure(
                reason = BlueNoxDeviceCallbacks.BlueNoxFailureReason.OPERATION_START_FAILED,
                detail = detail,
                characteristicUuid = uuid.toString(),
            )
            return operationFailure(operation = "read", detail = detail, characteristicUuid = uuid.toString())
        }
        return operationSuccess(operation = "read", detail = "Read requested", characteristicUuid = uuid.toString())
    }

    private fun readCharacteristic(uuid: UUID) {
        if (mServices != null) {
            for (s in mServices!!) {
                for (characteristic in s.characteristics) {
                    if (characteristic.uuid == uuid) {

                        if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT))
                        {
                            mBluetoothGatt?.readCharacteristic(characteristic)
                        }
                    }
                }
            }
        }
    }

    interface BlueNoxDeviceEventListener {
        fun manufacturerNameUpdated()
    }
}
