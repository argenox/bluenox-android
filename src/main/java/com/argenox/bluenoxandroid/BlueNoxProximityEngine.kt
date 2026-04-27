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
 * File:    ProximityEngine.kt
 * Summary: Engine for handling proximity-based device detection and RSSI tracking
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.argenox.bluenoxandroid.DeviceRSSI
import com.argenox.bluenoxandroid.ProximityEngineCallbacks
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

internal class ProximityEngine
    (cback: ProximityEngineCallbacks) {
    private val MODULE_TAG = "ProximityEngine"

    private var mProximityTimeoutTimer: Timer? = null

    @get:Suppress("unused")
    var isRunning: Boolean = false
        private set

    private val mDeviceList: ArrayList<DeviceRSSI>

    private val mCallback: ProximityEngineCallbacks = cback

    fun running(): Boolean {
        return isRunning
    }

    init {
        mDeviceList = ArrayList<DeviceRSSI>()
        mDeviceList.clear()
    }

    fun start() {
        mDeviceList.clear()
        /* Reset settings  */
        /* Hold information about the device with highest rssi */
        val mMaxRssiDevice: BluetoothDevice? = null
        isRunning = true
        val mMaxRSSIDeviceFind = false
        resetTimer()
    }

    private fun stopTimer() {
        if (mProximityTimeoutTimer != null) {
            mProximityTimeoutTimer!!.cancel()
            mProximityTimeoutTimer!!.purge()
            mProximityTimeoutTimer = null
        }
    }

    /* this function will start or restart the timer. */
    private fun resetTimer() {
        Log.d(MODULE_TAG, "resetTimer")

        stopTimer()

        mProximityTimeoutTimer = Timer()

        //Set the schedule function and rate
        val mProximityTimeoutValue = 5000
        mProximityTimeoutTimer!!.schedule(
            object : TimerTask() {
                override fun run() {
                    proximityProcessComplete()
                }
            },
            mProximityTimeoutValue.toLong()
        )
    }

    private fun proximityProcessComplete() {
        var maxRssiDevice: DeviceRSSI? = null

        Log.d(MODULE_TAG, "proximityProcessComplete")
        var maxRssiValue = Int.MIN_VALUE

        if (mDeviceList.size == 0) {
            /*  */
            return
        }

        /* Simple approach, just take the last RSSI measurement and use it to determine */
        for (dev in mDeviceList) {
            if (dev.getRssiList().size > 0) {
                val rssi: Int = dev.getRssiList().get(dev.getRssiList().size - 1)

                if (rssi > maxRssiValue) {
                    maxRssiDevice = dev
                    maxRssiValue = rssi
                }
            }
        }

        if (maxRssiDevice != null) {
            mCallback.nearestDeviceFound(maxRssiDevice.getAddress())
        }

        isRunning = false
        stopTimer()
    }

    fun updateRSSI(device: BluetoothDevice, rssi_val: Int) {
        if (!isRunning) return

        val format = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US)
        val curTimestamp = format.format(Date())
        Log.d(
            MODULE_TAG,
            curTimestamp + " Proximity - Device; " + device.address + "  RSSI: " + rssi_val.toString()
        )

        var exists = false
        var devFound: DeviceRSSI? = null

        /* Check whether it exists */
        for (rsd in mDeviceList) {
            if (rsd.getAddress().equals(device.address, ignoreCase = true)) {
                devFound = rsd
                exists = true
                break
            }
        }

        if (exists) {
            devFound!!.addEntry(rssi_val)
        } else {
            val devNew: DeviceRSSI = DeviceRSSI(device.address)
            mDeviceList.add(devNew)
            resetTimer()
        }
    }
}
