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
 * File:    BlueNoxOpQueue.kt
 * Summary: BlueNox Queue Class
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import com.argenox.bluenoxandroid.BlueNoxDebug
import com.argenox.bluenoxandroid.BluenoxLEManager
import com.argenox.bluenoxandroid.BlueNoxOp.OpCompleteCondition
import com.argenox.bluenoxandroid.BlueNoxDeviceCallbacks.BlueNoxFailureReason
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

internal class BlueNoxOpQueue
    (private val mParent: BlueNoxDevice?, private var mBluetoothGatt: BluetoothGatt?) {
    private val MODULE_TAG = "BlueNoxOpQueue"

    private val _mutex: Lock = ReentrantLock(true)

    private val mOperationQueue: ArrayList<BlueNoxOp>? = ArrayList<BlueNoxOp>()

    private val dbgObj = BlueNoxDebug(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR)

    private var mRunning = false

    private var commandTimeoutTimer: Timer? = null

    private fun cancelTimeout() {
        if (commandTimeoutTimer != null) {
            commandTimeoutTimer!!.cancel()
            commandTimeoutTimer!!.purge()
            commandTimeoutTimer = null
        }
    }

    /* Initialize Timeout for operation if enabled */
    private fun startTimeout(op: BlueNoxOp) {
        if (commandTimeoutTimer != null) {
            cancelTimeout()
        }

        if (op.mTimeoutEnabled) {
            commandTimeoutTimer = Timer()
            commandTimeoutTimer!!.schedule(
                object : TimerTask() {
                    override fun run() {
                        val timedOutOp = removeHeadIfMatching(op)
                        if (timedOutOp != null) {
                            mParent?.queueOperationFailed(
                                timedOutOp,
                                BlueNoxFailureReason.OPERATION_TIMEOUT,
                                "Timed out after ${timedOutOp.mTimeoutVal} ms waiting for ${timedOutOp.mTermCondition.description()}",
                            )
                            runQueue()
                        }
                    }
                },
                op.mTimeoutVal
            )
        }
    }

    fun setGatt(gatt: BluetoothGatt?) {
        mBluetoothGatt = gatt
    }

    @Suppress("unused")
    fun addOperation(op: BlueNoxOp) {
        if (mOperationQueue == null) {
            Log.e(MODULE_TAG, "Operation Queue is invalid")
            return
        }

        if (op.mOperationType == null) {
            Log.e(MODULE_TAG, "Operation Type is invalid")
            return
        }

        if (op.mTermCondition == null) {
            Log.e(MODULE_TAG, "Operation Termination condition is invalid")
            return
        }

        val cnt = mOperationQueue.size


        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Mutex Lock 64")

        _mutex.lock()
        mOperationQueue.add(op)


        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Mutex Unlock 64")
        _mutex.unlock()

        if (cnt == 0) {
            // Start the Operation
            runQueue()
        }
    }

    private fun debugPrint(msg: String) {

        dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_INFO,MODULE_TAG, msg)
    }

    private fun runQueue() {
        var failureToReport: Triple<BlueNoxOp, BlueNoxFailureReason, String>? = null

        _mutex.lock()
        if (mOperationQueue!!.size > 0) {
            mRunning = true

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, Thread.currentThread().name + " - " + "Queue Size: " + mOperationQueue.size)

            val curOp: BlueNoxOp = mOperationQueue[0]

            if (mBluetoothGatt != null) {
                if (curOp.mOperationType == com.argenox.bluenoxandroid.BlueNoxOp.OpType.Read && !curOp.mStarted) {

                    dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Queue Read " + curOp.mCharacteristic.getUuid().toString())

                    if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                        if (!mBluetoothGatt!!.readCharacteristic(curOp.mCharacteristic)) {

                            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Queue Read " + curOp.mCharacteristic.getUuid().toString() + " failed")

                            failureToReport = handleOperationStartFailureLocked(
                                curOp,
                                "Failed to start READ for ${curOp.mCharacteristic.uuid}"
                            )
                        } else {
                            startTimeout(curOp)
                            curOp.mStarted = true
                        }
                    } else {
                        failureToReport = handleOperationStartFailureLocked(
                            curOp,
                            "Missing BLUETOOTH_CONNECT permission for READ ${curOp.mCharacteristic.uuid}",
                            BlueNoxFailureReason.PERMISSION_DENIED,
                        )
                    }
                } else if (curOp.mOperationType == com.argenox.bluenoxandroid.BlueNoxOp.OpType.Write && !curOp.mStarted) {
                    // TODO: Review Write Type
                    curOp.mCharacteristic.setValue(curOp.mWriteData)
                    curOp.mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)

                    val sb = StringBuilder()
                    for (b in curOp.mWriteData) {
                        sb.append(String.format("%02X ", b))
                    }

                    dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Writing to Gatt from Queue " + curOp.mCharacteristic.getUuid()
                        .toString() + "   " + sb.toString())

                    if (!mBluetoothGatt!!.writeCharacteristic(curOp.mCharacteristic)) {
                        Log.e(
                            MODULE_TAG,
                            "Queue Write " + curOp.mCharacteristic.getUuid().toString() + " failed"
                        )
                        failureToReport = handleOperationStartFailureLocked(
                            curOp,
                            "Failed to start WRITE for ${curOp.mCharacteristic.uuid}"
                        )
                    } else {
                        startTimeout(curOp)
                        curOp.mStarted = true
                    }
                } else if (curOp.mOperationType == com.argenox.bluenoxandroid.BlueNoxOp.OpType.WriteNoResponse && !curOp.mStarted) {

                    dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Queue Write no Response " + curOp.mCharacteristic.getUuid().toString())


                    // TODO: Review Write Type
                    curOp.mCharacteristic.setValue(curOp.mWriteData)
                    curOp.mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    if (!mBluetoothGatt!!.writeCharacteristic(curOp.mCharacteristic)) {
                        Log.e(
                            MODULE_TAG,
                            "Queue Write NRSP " + curOp.mCharacteristic.getUuid()
                                .toString() + " failed"
                        )
                        failureToReport = handleOperationStartFailureLocked(
                            curOp,
                            "Failed to start WRITE_NO_RESPONSE for ${curOp.mCharacteristic.uuid}"
                        )
                    } else {
                        startTimeout(curOp)
                        curOp.mStarted = true
                    }
                } else if (curOp.mOperationType == com.argenox.bluenoxandroid.BlueNoxOp.OpType.DescriptorWrite && !curOp.mStarted) {

                    dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Queue Write Descriptor" + curOp.mCharacteristic.getUuid().toString())

                    // TODO: Review Write Type
                    if (!setNotificationForCharacteristic(curOp.mCharacteristic, curOp.mVal)) {
                        failureToReport = handleOperationStartFailureLocked(
                            curOp,
                            "Failed to start DESCRIPTOR_WRITE for ${curOp.mCharacteristic.uuid}"
                        )
                    }
                }

                if (curOp.mTermCondition == OpCompleteCondition.None) {
                    removeCurrentItem()
                }
            }
        } else {

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Queue Empty")
            queueComplete()
        }
        _mutex.unlock()

        if (failureToReport != null) {
            val (op, reason, detail) = failureToReport
            mParent?.queueOperationFailed(op, reason, detail)
            runQueue()
        }
    }

    private fun handleOperationStartFailureLocked(
        op: BlueNoxOp,
        detail: String,
        reason: BlueNoxFailureReason = BlueNoxFailureReason.OPERATION_START_FAILED,
    ): Triple<BlueNoxOp, BlueNoxFailureReason, String>? {
        op.mStartAttempts += 1
        if (op.mStartAttempts >= op.mMaxStartAttempts) {
            if (mOperationQueue!!.isNotEmpty() && mOperationQueue[0] === op) {
                mOperationQueue.removeAt(0)
            }
            val message = "$detail (attempt ${op.mStartAttempts}/${op.mMaxStartAttempts})"
            return Triple(op, reason, message)
        }

        val backoffMs = 100L * op.mStartAttempts
        delayRun(backoffMs)
        return null
    }

    private fun delayRun(delayMs: Long = 100L) {
        /* Schedules running the queue later, when the system is not busy */
        Timer().schedule(object : TimerTask() {
            override fun run() {
                Log.d(MODULE_TAG, "Running delayed Queue")
                runQueue()
            }
        }, delayMs)
    }

    @Suppress("unused")
    fun eventHandler(c: BluetoothGattCharacteristic, evt: OpCompleteCondition) {
        debugPrint("Mutex Lock 210")
        _mutex.lock()

        if (mOperationQueue!!.size > 0) {
            val curOp: BlueNoxOp = mOperationQueue[0]

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG,
                Thread.currentThread().name + " - " + "Event Handler " + c.uuid.toString() + " == " + curOp.mCharacteristic.getUuid()
                    .toString() + "  " + evt.constValue() + "  == " + curOp.mTermCondition!!.constValue())


            if (evt == curOp.mTermCondition && c.uuid == curOp.mTermCharacteristic.getUuid()) {
                cancelTimeout()

                dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, Thread.currentThread().name + " - " + "Operation Complete")

                // Operation complete, so pop off the queue
                if (mOperationQueue.size > 0) mOperationQueue.removeAt(0)
            }
        }
        debugPrint("Mutex Unlock 150")
        _mutex.unlock()

        /* Run next operation if needed */
        if (mOperationQueue.size > 0) {
            runQueue()
        } else {

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG,MODULE_TAG, "Queue Empty")

            queueComplete()
        }
    }

    private fun removeCurrentItem() {
        if (mOperationQueue!!.size > 0) {
            debugPrint("Mutex Lock 248")
            _mutex.lock()
            cancelTimeout()
            mOperationQueue.removeAt(0)
            debugPrint("Mutex Unlock 251")
            _mutex.unlock()
        }
    }

    private fun removeHeadIfMatching(op: BlueNoxOp): BlueNoxOp? {
        _mutex.lock()
        try {
            if (mOperationQueue!!.isNotEmpty() && mOperationQueue[0] === op) {
                cancelTimeout()
                return mOperationQueue.removeAt(0)
            }
        } finally {
            _mutex.unlock()
        }
        return null
    }

    @Suppress("unused")
    fun clearQueue() {
        debugPrint("Mutex Lock 260")
        _mutex.lock()
        cancelTimeout()
        mOperationQueue!!.clear()
        debugPrint("Mutex Unlock 263")
        _mutex.unlock()
    }

    fun queueDepth(): Int {
        _mutex.lock()
        return try {
            mOperationQueue!!.size
        } finally {
            _mutex.unlock()
        }
    }

    fun cancelOperation(operationId: String): Boolean {
        if (operationId.isBlank()) return false
        _mutex.lock()
        try {
            val idx = mOperationQueue!!.indexOfFirst {
                it.mOperationId.equals(operationId, ignoreCase = true) && !it.mStarted
            }
            if (idx < 0) return false
            mOperationQueue.removeAt(idx)
            return true
        } finally {
            _mutex.unlock()
        }
    }

    fun cancelOperationsForCharacteristic(characteristicUuid: UUID): Int {
        _mutex.lock()
        try {
            val before = mOperationQueue!!.size
            mOperationQueue.removeAll {
                !it.mStarted && it.mCharacteristic.uuid == characteristicUuid
            }
            return before - mOperationQueue.size
        } finally {
            _mutex.unlock()
        }
    }

    private fun queueComplete() {
        /* Trigger only when queue was previously running */

        if (mRunning) {
            mRunning = false

            mParent?.queueCompleteCallback() ?: Log.e(MODULE_TAG, "mParent is null")
        }
    }

    interface BlueNoxQueueListener {
        fun queueCompleteCallback()
        fun queueOperationFailed(op: BlueNoxOp, reason: BlueNoxFailureReason, detail: String)
    }

    /* enables/disables notification for characteristic */
    private fun setNotificationForCharacteristic(
        ch: BluetoothGattCharacteristic,
        enabled: Boolean
    ): Boolean {
        if (mBluetoothGatt == null) {
            debugPrint("Bluetooth Gatt Null")
            return false
        }

        var rc = mBluetoothGatt!!.setCharacteristicNotification(ch, enabled)
        if (!rc) {
            debugPrint("Setting proper notification status for characteristic failed!")
        }

        // This is also sometimes required (e.g. for heart rate monitors) to enable notifications/indications
        // see: https://developer.bluetooth.org/gatt/descriptors/Pages/DescriptorViewer.aspx?u=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
        val descriptor = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

        debugPrint("Setting Notification for Characteristic" + ch.uuid.toString())

        if (descriptor != null) {
            debugPrint("Setting Notification")

            if (enabled) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                debugPrint("Enabling Notification ----")
            } else {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                debugPrint("Disabling Notification ----")
            }
            if(BluenoxLEManager.getInstance().checkRequiredPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                mBluetoothGatt!!.writeDescriptor(descriptor)
                debugPrint("Notification Written ----")
            }

            rc = true
        } else {
            debugPrint("Setting Notification - Descriptor Null")
            return false
        }

        return rc
    }
}
