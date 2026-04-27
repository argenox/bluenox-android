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
 * File:    BlueNoxDeviceCallbacks.kt
 * Summary: BlueNox Device Callbacks definitions
 *
 **********************************************************************************/

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanRecord

interface BlueNoxDeviceCallbacks {
    enum class BlueNoxBondState {
        BONDING,
        BONDED,
        UNBONDED,
        RETRYING,
        FAILED,
        PIN_REQUESTED,
    }

    enum class BlueNoxFailureReason {
        PERMISSION_DENIED,
        INVALID_ARGUMENT,
        API_NOT_SUPPORTED,
        CONNECT_GATT_RETURNED_NULL,
        CONNECT_RETRY_EXHAUSTED,
        BOND_REQUEST_REJECTED,
        BOND_RETRY_EXHAUSTED,
        BOND_TIMEOUT,
        BOND_FAILED,
        OPERATION_START_FAILED,
        OPERATION_TIMEOUT,
        CACHE_REFRESH_FAILED,
    }

    @Suppress("unused")
    fun uiBluetoothStateChanged(state: Int)

    @Suppress("unused")
    fun uiBluetoothScanStopped()

    @Suppress("unused")
    fun uiDeviceFound(device: BluetoothDevice?, rssi: Int, record: ScanRecord?)

    @Suppress("unused")
    fun uiDeviceConnected(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?
    )

    @Suppress("unused")
    fun uiDeviceReady(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?
    )

    @Suppress("unused")
    fun uiDeviceDisconnected(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?
    )

    @Suppress("unused")
    fun uiAvailableServices(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        services: List<BluetoothGattService?>?
    )

    @Suppress("unused")
    fun uiCharacteristicForService(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        chars: List<BluetoothGattCharacteristic?>?
    )

    @Suppress("unused")
    fun uiCharacteristicsDetails(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        characteristic: BluetoothGattCharacteristic?
    )

    @Suppress("unused")
    fun uiNewValueForCharacteristic(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        ch: BluetoothGattCharacteristic?,
        strValue: String?,
        intValue: Int,
        rawValue: ByteArray?,
        timestamp: String?
    )

    @Suppress("unused")
    fun uiGotNotification(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?
    )

    @Suppress("unused")
    fun uiCharacteristicUpdated(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?,
        value : ByteArray
    )

    @Suppress("unused")
    fun uiSuccessfulWrite(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        ch: BluetoothGattCharacteristic?,
        description: String?
    )

    @Suppress("unused")
    fun uiFailedWrite(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        ch: BluetoothGattCharacteristic?,
        description: String?
    )

    @Suppress("unused")
    fun uiCharacteristicRead(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?
    )


    @Suppress("unused")
    fun uiDescriptorWritten(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    )

    @Suppress("unused")
    fun uiDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int,
        value: ByteArray?,
    ) {
    }

    @Suppress("unused")
    fun uiCccdConfigured(
        device: BlueNoxDevice?,
        result: BlueNoxCccdConfigurationResult,
    )

    @Suppress("unused")
    fun uiMtuUpdated(
        gatt: BluetoothGatt?,
        mtu: Int
    )

    @Suppress("unused")
    fun uiConnectionUpdated(
        gatt: BluetoothGatt?,
        interval: Int,
        latency: Int,
        timeout: Int,
        status: Int,
    )

    @Suppress("unused")
    fun uiServicesChanged(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
    )

    @Suppress("unused")
    fun uiBondingChanged(device: BlueNoxDevice?, bondState: Boolean)

    @Suppress("unused")
    fun uiBondStateEvent(
        device: BlueNoxDevice?,
        state: BlueNoxBondState,
        detail: String,
    )

    @Suppress("unused")
    fun uiNewRssiAvailable(gatt: BluetoothGatt?, device: BluetoothDevice?, rssi: Int)

    @Suppress("unused")
    fun uiNewMaxRssiDeviceFound(device: BluetoothDevice?, rssi: Int)

    @Suppress("unused")
    fun uiOperationFailure(
        device: BluetoothDevice?,
        reason: BlueNoxFailureReason,
        detail: String,
        characteristicUuid: String?,
    )

    /* define Null Adapter class for that interface */
    open class NullBlueNoxDeviceCallbacks : BlueNoxDeviceCallbacks {
        override fun uiBluetoothStateChanged(state: Int) {}

        override fun uiBluetoothScanStopped() {}

        override fun uiDeviceConnected(gatt: BluetoothGatt?, device: BluetoothDevice?) {}

        override fun uiDeviceReady(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?
        ) {
        }

        override fun uiDeviceDisconnected(gatt: BluetoothGatt?, device: BluetoothDevice?) {}
        override fun uiAvailableServices(
            gatt: BluetoothGatt?, device: BluetoothDevice?,
            services: List<BluetoothGattService?>?
        ) {
        }

        override fun uiCharacteristicForService(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?, service: BluetoothGattService?,
            chars: List<BluetoothGattCharacteristic?>?
        ) {
        }

        override fun uiCharacteristicsDetails(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?, service: BluetoothGattService?,
            characteristic: BluetoothGattCharacteristic?
        ) {
        }

        override fun uiNewValueForCharacteristic(
            gatt: BluetoothGatt?,
            device: BluetoothDevice?, service: BluetoothGattService?,
            ch: BluetoothGattCharacteristic?, strValue: String?, intValue: Int,
            rawValue: ByteArray?, timestamp: String?
        ) {
        }

        override fun uiGotNotification(
            gatt: BluetoothGatt?, device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?
        ) {
        }

        override fun uiCharacteristicUpdated(
            gatt: BluetoothGatt?, device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?,
            value : ByteArray
        ) {
        }

        override fun uiDescriptorWritten(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
        }

        override fun uiDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
            value: ByteArray?,
        ) {
        }

        override fun uiCccdConfigured(
            device: BlueNoxDevice?,
            result: BlueNoxCccdConfigurationResult,
        ) {
        }

        override fun uiSuccessfulWrite(
            gatt: BluetoothGatt?, device: BluetoothDevice?,
            service: BluetoothGattService?, ch: BluetoothGattCharacteristic?,
            description: String?
        ) {
        }

        override fun uiFailedWrite(
            gatt: BluetoothGatt?, device: BluetoothDevice?,
            service: BluetoothGattService?, ch: BluetoothGattCharacteristic?,
            description: String?
        ) {
        }

        override fun uiNewRssiAvailable(
            gatt: BluetoothGatt?, device: BluetoothDevice?,
            rssi: Int
        ) {
        }

        override fun uiNewMaxRssiDeviceFound(
            device: BluetoothDevice?,
            rssi: Int
        ) {
        }

        override fun uiOperationFailure(
            device: BluetoothDevice?,
            reason: BlueNoxFailureReason,
            detail: String,
            characteristicUuid: String?,
        ) {
        }

        override fun uiDeviceFound(device: BluetoothDevice?, rssi: Int, record: ScanRecord?) {}

        override fun uiCharacteristicRead(
            gatt: BluetoothGatt?, device: BluetoothDevice?,
            characteristic: BluetoothGattCharacteristic?
        ) {
        }

        override fun uiBondingChanged(device: BlueNoxDevice?, bondState: Boolean) {

        }

        override fun uiBondStateEvent(
            device: BlueNoxDevice?,
            state: BlueNoxBondState,
            detail: String,
        ) {
        }

        override fun uiMtuUpdated(gatt: BluetoothGatt?, mtu: Int) {

        }

        override fun uiConnectionUpdated(
            gatt: BluetoothGatt?,
            interval: Int,
            latency: Int,
            timeout: Int,
            status: Int,
        ) {
        }

        override fun uiServicesChanged(gatt: BluetoothGatt?, device: BluetoothDevice?) {
        }
    }
}
