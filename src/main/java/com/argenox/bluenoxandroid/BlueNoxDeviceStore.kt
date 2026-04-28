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
 * File:    BlueNoxDeviceStore.kt
 * Summary: Storage for BlueNox Devices
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import com.argenox.bluenoxandroid.BlueNoxDebug
import java.util.UUID
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

internal class BlueNoxDeviceStore
 {
    @get:Suppress("unused")
    var deviceList: ArrayList<BlueNoxDevice>
        private set

    private val MODULE_TAG = "BlueNoxDeviceStore"

    private val DEVICE_FILENAME = "device.data"

    private val _mutex: Lock = ReentrantLock(true)

    private val dbgObj = BlueNoxDebug(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_ERROR)
    init {
        deviceList = ArrayList()
        //loadData()
    }

    @Suppress("unused")
    fun count(): Int {
        return deviceList.size
    }

    @get:Suppress("unused")
    val advertisingDeviceList: ArrayList<BlueNoxDevice>
        get() {
            val advList = ArrayList<BlueNoxDevice>()
            _mutex.lock()
            for (dev in deviceList) {
                if (dev.currentlyAdvertising()) {
                    advList.add(dev)
                }
            }
            _mutex.unlock()
            return advList
        }


    @Suppress("unused")
    fun getDevice(id: UUID): BlueNoxDevice? {
        for (dev in deviceList) {
            if (dev.id == id) {
                return dev
            }
        }
        return null
    }

    @Suppress("unused")
    fun getDeviceFromAddress(mac: String): BlueNoxDevice? {
        for (dev in deviceList) {
            if (dev.mACAddress == mac) {
                return dev
            }
        }
        return null
    }

    @Suppress("unused")
    fun getDeviceIndex(d: BlueNoxDevice): Int {
        var index = 0
        for (dev in deviceList) {
            if (dev.mACAddress == d.mACAddress) {
                return index
            }
            index++
        }
        return -1
    }

     @Suppress("unused")
     fun getDeviceFromIndex(idx: Int): BlueNoxDevice? {
         if(idx < deviceList.count()) {
             return deviceList[idx]
         }

         return null
     }


    fun getDevice(mac: String?): BlueNoxDevice? {
        for (dev in deviceList) {
            if (dev.mACAddress == mac) {
                return dev
            }
        }
        return null
    }

    @Suppress("unused")
    fun addDevice(device: BlueNoxDevice): Boolean {
        _mutex.lock()

        deviceList.add(device)

        _mutex.unlock()

        return true
    }

    @Suppress("unused")
    fun removeDevice(device: BluetoothDevice): Boolean {
        val d = getDeviceFromAddress(device.address)

        if (d != null) {
            _mutex.lock()
            deviceList.remove(d)
            _mutex.unlock()
        }

        return true
    }


    fun addDevice(device: BluetoothDevice,
                    c: Class<*>?,
                    rssi: Int,
                    sr: ScanRecord?): Boolean
    {
        var c = c
        var bledev: BlueNoxDevice? = null

        val exists = false
        var count = 0

        if (c == null) {
            c = BlueNoxDevice::class.java
        }

        try {
            val ctor = c.getDeclaredConstructor()
            ctor.isAccessible = true
            bledev = ctor.newInstance() as BlueNoxDevice
        } catch (e: Exception) {
            e.printStackTrace()
        }


        if (bledev != null) {

            dbgObj.debugPrint(BlueNoxDebug.DebugLevels.BLUENOX_DEBUG_LVL_DEBUG, MODULE_TAG,"Received advertisement for device: " + device.address)

            bledev.setLastAdvertisement()
            bledev.device = device
            bledev.rssi = rssi
            bledev.scanRecord = sr
            bledev.addAdvertisingEvent(rssi, sr)

            /* Ensure we don't already have the device in the list */
            for (d in deviceList) {
                if (d.device!!.address == device.address) {
                    d.device = device
                    d.rssi = rssi
                    d.scanRecord = sr
                    d.addAdvertisingEvent(rssi, sr)
                    d.setLastAdvertisement()

                    _mutex.lock()

                    /* Update Device RSSI and Data */
                    deviceList[count] = d

                    _mutex.unlock()

                    return false
                }
                count++
            }

            deviceList.add(bledev)
        }
        return true
    }

     fun findDeviceByAddress(addr: String): BlueNoxDevice?
     {
         var device : BlueNoxDevice? = null

         _mutex.lock()

         for (dev in deviceList) {
             if(dev.mACAddress.toString() == addr)
             {
                 device = dev
                 break
             }
         }

         _mutex.unlock()

         return device
     }

     fun findConnectedDevice(): BlueNoxDevice?
     {
         var device : BlueNoxDevice? = null

         _mutex.lock()

         for (dev in deviceList) {
             if(dev.isConnected)
             {
                 device = dev
                 break
             }
         }

         _mutex.unlock()

         return device
     }



    @Suppress("unused")
    fun removeAll() {
        _mutex.lock()

        deviceList.clear()

        _mutex.unlock()
    }

    @Suppress("unused")
    fun removeAdvertisingDevices() {
        _mutex.lock()

        for (dev in deviceList) {
            dev.clearLastAdvertisement()
        }

        _mutex.unlock()
    }

     /* Disabled
    fun saveData() {
        _mutex.lock()
        if (deviceList.size > 0) {
            try {
                val fos = mContext!!.openFileOutput(DEVICE_FILENAME, Context.MODE_PRIVATE)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(deviceList)
                oos.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        _mutex.unlock()
    }

    private fun loadData() {
        _mutex.lock()
        try {
            val dataFile = File(DEVICE_FILENAME)
            if (dataFile.exists()) {
                val fis = mContext!!.openFileInput(DEVICE_FILENAME)
                val `is` = ObjectInputStream(fis)
                deviceList = `is`.readObject() as ArrayList<BlueNoxDevice>
                `is`.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
        _mutex.unlock()
    }

      */
}
