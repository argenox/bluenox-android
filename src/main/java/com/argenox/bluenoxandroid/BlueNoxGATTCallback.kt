/*
 * Copyright (c) 2015-2025, Argenox Technologies LLC
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
 * File:    BlueNoxGattCallback.kt
 * Summary: Gatt Callback Mechanism
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log

class BluenoxGATTCallback internal constructor(cb: BlueNoxDeviceCallbacks, name: String?) :
    BluetoothGattCallback(), Handler.Callback {
    private val MODULE_TAG = "AGNX Gatt Callback"

    private val mCallbacks: BlueNoxDeviceCallbacks
    private val bleHandler: Handler


    init {
        // Initialize a new thread
        val handlerThread = HandlerThread(name)
        handlerThread.start()

        // Use the thread's looper for the Handler used
        bleHandler = Handler(handlerThread.looper, this)
        mCallbacks = cb
    }

    @Suppress("unused")
    fun dispose() {
        bleHandler.removeCallbacksAndMessages(null)
        bleHandler.looper.quit()
    }


    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        super.onReadRemoteRssi(gatt, rssi, status)
        bleHandler.obtainMessage(MSG_DEVICE_RSSI, rssi).sendToTarget()
    }

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int, newState: Int
    ) {
        super.onConnectionStateChange(gatt, status, newState)
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(MODULE_TAG, "Connected Gatt Callback Thread; " + Thread.currentThread().name)
            bleHandler.obtainMessage(MSG_DEVICE_CONNECTED, gatt).sendToTarget()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(MODULE_TAG, "Disconnected Gatt Callback Thread; " + Thread.currentThread().name)
            bleHandler.obtainMessage(MSG_DEVICE_DISCONNECTED, gatt).sendToTarget()
        } else if (newState == BluetoothProfile.STATE_CONNECTING) {
            Log.d(MODULE_TAG, "Connecting Gatt Callback Thread; " + Thread.currentThread().name)
            bleHandler.obtainMessage(MSG_DEVICE_CONNECTING, gatt).sendToTarget()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
            Log.d(MODULE_TAG, "Disconnecting Gatt Callback Thread; " + Thread.currentThread().name)
            bleHandler.obtainMessage(MSG_DEVICE_DISCONNECTING, gatt).sendToTarget()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // now, when services discovery is finished, we can call getServices() for Gatt
            //getSupportedServices(gatt);
            bleHandler.obtainMessage(MSG_SERVICES_DISCOVERED, gatt).sendToTarget()
            // Keep services-changed callback path available for lower API stubs by
            // emitting it whenever fresh services are discovered.
            bleHandler.obtainMessage(MSG_SERVICES_CHANGED, gatt).sendToTarget()

            mCallbacks.uiDeviceReady(gatt, gatt.device)
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)

        val data: BluetoothData = BluetoothData()
        data.gatt = gatt
        data.d = descriptor
        data.status = status

        bleHandler.obtainMessage(MSG_DESCRIPTOR_WRITTEN, data)
            .sendToTarget()
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)

        val data: BluetoothData = BluetoothData()
        data.gatt = gatt
        data.status = status
        data.mtu = mtu

        bleHandler.obtainMessage(MSG_MTU_UPDATE, data)
            .sendToTarget()
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, status)


        if (status == BluetoothGatt.GATT_SUCCESS) {
            // now, when services discovery is finished, we can call getServices() for Gatt
            //getSupportedServices(gatt);

            val data: BluetoothData = BluetoothData()
            data.gatt = gatt
            data.c = characteristic
            data.value = characteristic.value

            bleHandler.obtainMessage(MSG_CHAR_READ, data)
                .sendToTarget()
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        super.onCharacteristicChanged(gatt, characteristic, value)

        val data: BluetoothData = BluetoothData()
        data.gatt = gatt
        data.c = characteristic
        data.value = value

        Log.d(MODULE_TAG, "154 Sending MSG_NOTIFY_DATA_CHANGED with ${value.size} bytes" )

        //Log.d(MODULE_TAG, "onCharacteristicChanged Thread; " + Thread.currentThread().getName());

        //Log.d(MODULE_TAG, "onCharacteristicChanged MSG_NOTIFY_DATA_CHANGED: len = " +  data.value.length);
        val msg = bleHandler.obtainMessage(MSG_NOTIFY_DATA_CHANGED, data)
        bleHandler.sendMessage(msg)
    }

    @Suppress("unused")
    fun readData(
        gatt: BluetoothGatt,
        c: BluetoothGattCharacteristic?
    ) {
        gatt.readCharacteristic(c)
    }

    override fun handleMessage(msg: Message): Boolean {
        val gatt: BluetoothGatt
        val data: BluetoothData
        val rssi: Int

        val mDebugLvl = 0
        when (msg.what) {
            MSG_FOUND_DEVICE -> {}
            MSG_DISCOVER_SERVICES -> {
                gatt = msg.obj as BluetoothGatt
                gatt.discoverServices()
            }

            MSG_DEVICE_CONNECTED -> {
                gatt = msg.obj as BluetoothGatt
                handleConnection(gatt)
            }

            MSG_DEVICE_DISCONNECTED -> {
                gatt = msg.obj as BluetoothGatt

                Log.d(MODULE_TAG, "GATT disconnect for gatt: $gatt")

                handleDisconnection(gatt)
            }

            MSG_SERVICES_DISCOVERED -> {
                gatt = msg.obj as BluetoothGatt

                mCallbacks.uiAvailableServices(gatt, gatt.device, gatt.services)
            }

            MSG_NOTIFY_DATA_CHANGED -> {
                data = msg.obj as BluetoothData

                //Log.d(MODULE_TAG, "Data Changed; " + Thread.currentThread().getName());
                /*
                // Discovery successful, fetch services, chars and descs...
                commService = gatt.getService(DATA_SERVICE_ID);
                inputChar = commService.getCharacteristic(INPUT_CHAR_ID);
                outputChar = commService.getCharacteristic(OUTPUT_CHAR_ID);
                */
                if (mDebugLvl > 0) Log.d(
                    MODULE_TAG,
                    "GATT Notify Event for gatt: " + data.gatt.toString()
                )

                //              Log.d(MODULE_TAG, "case MSG_NOTIFY_DATA_CHANGED Thread; " + Thread.currentThread().getName());

//                Log.d(MODULE_TAG, "MSG_NOTIFY_DATA_CHANGED: len = " +  data.c.getValue().length + "   "  +data.c.getUuid() + "  " + data.c.getStringValue(0));

                Log.d(MODULE_TAG, "225 MSG_NOTIFY_DATA_CHANGED with ${data.value.size} bytes" )


                mCallbacks.uiCharacteristicUpdated(data.gatt, data.gatt!!.getDevice(), data.c, data.value)

                Log.d(MODULE_TAG, "230 MSG_NOTIFY_DATA_CHANGED completed processing ${data.value.size} bytes" )
            }

            MSG_DEVICE_DISCONNECTING -> {}
            MSG_DEVICE_CONNECTING -> {}
            MSG_DEVICE_RSSI -> {
                rssi = msg.obj as Int
                mCallbacks.uiNewRssiAvailable(null, null, rssi)
            }

            MSG_CHAR_READ -> {
                data = msg.obj as BluetoothData

                //Log.d(MODULE_TAG, "MSG_CHAR_READ: " + data.c.getUuid() + "  " + data.c.getStringValue(0));
                mCallbacks.uiCharacteristicRead(data.gatt, data.gatt!!.getDevice(), data.c)
            }

            MSG_DESCRIPTOR_WRITTEN -> {
                data = msg.obj as BluetoothData

                //Log.d(MODULE_TAG, "MSG_DESCRIPTOR_WRITTEN: " + data.d.getUuid());
                mCallbacks.uiDescriptorWritten(data.gatt, data.d, data.status)
            }

            MSG_MTU_UPDATE -> {
                data = msg.obj as BluetoothData

                Log.d(MODULE_TAG, "MSG_MTU_UPDATE")
                mCallbacks.uiMtuUpdated(data.gatt, data.mtu)
            }
            MSG_CONNECTION_UPDATED -> {
                data = msg.obj as BluetoothData
                mCallbacks.uiConnectionUpdated(
                    data.gatt,
                    data.interval,
                    data.latency,
                    data.timeout,
                    data.status,
                )
            }
            MSG_SERVICES_CHANGED -> {
                gatt = msg.obj as BluetoothGatt
                mCallbacks.uiServicesChanged(gatt, gatt.device)
            }
        }
        return true
    }


    private fun handleConnection(gatt: BluetoothGatt) {
        Log.d(MODULE_TAG, "handleConnection: " + gatt.device.address)


        //BluetoothDevice connDevice = findExistingDeviceAddress(gatt.getDevice().getAddress());
        //BluetoothGatt connGatt = gatt;
        mCallbacks.uiDeviceConnected(gatt, gatt.device)

        // now we can start talking with the device, e.g.
        //mBluetoothGatt.readRemoteRssi();
        // response will be delivered to callback object!

        // in our case we would also like automatically to call for services discovery
//            startServicesDiscovery(connGatt);
        gatt.discoverServices()

        // and we also want to get RSSI value to be updated periodically
        //startMonitoringRssiValue();
    }

    private fun handleDisconnection(gatt: BluetoothGatt) {
        Log.d(MODULE_TAG, "handleDisconnection: " + gatt.device.address)

        //BluetoothDevice connDevice = findExistingDeviceAddress(gatt.getDevice().getAddress());
        mCallbacks.uiDeviceDisconnected(gatt, gatt.device)
    }

    companion object {
        private const val MSG_FOUND_DEVICE = 9
        private const val MSG_DISCOVER_SERVICES = 10
        private const val MSG_DEVICE_CONNECTED = 11
        private const val MSG_DEVICE_DISCONNECTED = 12
        private const val MSG_SERVICES_DISCOVERED = 13
        private const val MSG_NOTIFY_DATA_CHANGED = 14
        private const val MSG_DEVICE_DISCONNECTING = 15
        private const val MSG_DEVICE_CONNECTING = 16
        private const val MSG_DEVICE_RSSI = 17
        private const val MSG_CHAR_READ = 18
        private const val MSG_DESCRIPTOR_WRITTEN = 19
        private const val MSG_MTU_UPDATE = 20
        private const val MSG_CONNECTION_UPDATED = 21
        private const val MSG_SERVICES_CHANGED = 22
    }
}
