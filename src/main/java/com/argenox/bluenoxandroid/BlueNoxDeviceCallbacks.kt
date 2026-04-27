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

/**
 * Application callbacks for GATT, bonding, and peripheral events delivered by [BlueNoxDevice].
 *
 * Subclass [NullBlueNoxDeviceCallbacks] to override only the methods you need.
 */
interface BlueNoxDeviceCallbacks {
    /** Bond lifecycle states surfaced by [BlueNoxDevice] and pairing broadcasts. */
    enum class BlueNoxBondState {
        /** Android reports [BluetoothDevice.BOND_BONDING]. */
        BONDING,
        /** Bond completed successfully. */
        BONDED,
        /** Bond removed or not bonded. */
        UNBONDED,
        /** Library is retrying bond per [BlueNoxBondingPolicy]. */
        RETRYING,
        /** Bond attempt failed after retries or timeout. */
        FAILED,
        /** Passkey/PIN entry may be required; see [BlueNoxDevice.setBondPinProvider]. */
        PIN_REQUESTED,
    }

    /** Structured reasons passed to [uiOperationFailure] for app-level handling. */
    enum class BlueNoxFailureReason {
        /** Missing Bluetooth runtime permission. */
        PERMISSION_DENIED,
        /** Invalid argument to a device API (UUID, MTU range, etc.). */
        INVALID_ARGUMENT,
        /** Requested API not supported on this OS level. */
        API_NOT_SUPPORTED,
        /** [BluetoothDevice.connectGatt] returned null. */
        CONNECT_GATT_RETURNED_NULL,
        /** Reconnect attempts exceeded [BlueNoxConnectionPolicy]. */
        CONNECT_RETRY_EXHAUSTED,
        /** [BluetoothDevice.createBond] returned false. */
        BOND_REQUEST_REJECTED,
        /** Bond retries exhausted per [BlueNoxBondingPolicy]. */
        BOND_RETRY_EXHAUSTED,
        /** Bond did not complete within [BlueNoxBondingPolicy.timeoutMs]. */
        BOND_TIMEOUT,
        /** Bond or pairing failed in a generic way. */
        BOND_FAILED,
        /** GATT operation could not be queued or started. */
        OPERATION_START_FAILED,
        /** Queued operation timed out waiting for completion. */
        OPERATION_TIMEOUT,
        /** [BlueNoxDevice.refreshGattCache] failed. */
        CACHE_REFRESH_FAILED,
    }

    /** @param state Platform Bluetooth adapter state constant from [BluetoothAdapter]. */
    @Suppress("unused")
    fun uiBluetoothStateChanged(state: Int)

    /** Invoked when a manager-level scan stops (see [BluenoxLEManager.stopScanning]). */
    @Suppress("unused")
    fun uiBluetoothScanStopped()

    /** @param device Peripheral from scan; @param rssi Last RSSI; @param record Parsed advertisement if present. */
    @Suppress("unused")
    fun uiDeviceFound(device: BluetoothDevice?, rssi: Int, record: ScanRecord?)

    /** GATT connection established; service discovery may still be in progress. */
    @Suppress("unused")
    fun uiDeviceConnected(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?
    )

    /** GATT is connected and service discovery completed successfully. */
    @Suppress("unused")
    fun uiDeviceReady(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?
    )

    /** GATT link dropped or [BlueNoxDevice.disconnect] completed. */
    @Suppress("unused")
    fun uiDeviceDisconnected(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?
    )

    /** @param services Discovered GATT services after [BluetoothGatt.discoverServices]. */
    @Suppress("unused")
    fun uiAvailableServices(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        services: List<BluetoothGattService?>?
    )

    /** Characteristics discovered under a single service (legacy path). */
    @Suppress("unused")
    fun uiCharacteristicForService(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        chars: List<BluetoothGattCharacteristic?>?
    )

    /** Detailed metadata for a characteristic (legacy path). */
    @Suppress("unused")
    fun uiCharacteristicsDetails(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        characteristic: BluetoothGattCharacteristic?
    )

    /** Parsed presentation of a characteristic value (legacy path). */
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

    /** Legacy notification callback; prefer [uiCharacteristicUpdated] for new code. */
    @Suppress("unused")
    fun uiGotNotification(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?
    )

    /** Notify/indicate payload or other characteristic value updates delivered as raw bytes. */
    @Suppress("unused")
    fun uiCharacteristicUpdated(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?,
        value : ByteArray
    )

    /** Successful characteristic write completed on the connection. */
    @Suppress("unused")
    fun uiSuccessfulWrite(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        ch: BluetoothGattCharacteristic?,
        description: String?
    )

    /** Characteristic write failed at the GATT layer. */
    @Suppress("unused")
    fun uiFailedWrite(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        service: BluetoothGattService?,
        ch: BluetoothGattCharacteristic?,
        description: String?
    )

    /** Read completed; value is on [BluetoothGattCharacteristic.getValue] until API changes. */
    @Suppress("unused")
    fun uiCharacteristicRead(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?
    )


    /** Descriptor write completed; @param status [BluetoothGatt] GATT status code. */
    @Suppress("unused")
    fun uiDescriptorWritten(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    )

    /**
     * Descriptor read completed.
     *
     * @param value Descriptor bytes on success; may be null or empty on failure.
     */
    @Suppress("unused")
    fun uiDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int,
        value: ByteArray?,
    ) {
    }

    /** Result of enabling/disabling notify or indicate via CCCD write. */
    @Suppress("unused")
    fun uiCccdConfigured(
        device: BlueNoxDevice?,
        result: BlueNoxCccdConfigurationResult,
    )

    /** Negotiated ATT MTU after [BlueNoxDevice.requestMtu]. */
    @Suppress("unused")
    fun uiMtuUpdated(
        gatt: BluetoothGatt?,
        mtu: Int
    )

    /** Connection interval/latency/timeout update from [BluetoothGattCallback.onConnectionUpdated]. */
    @Suppress("unused")
    fun uiConnectionUpdated(
        gatt: BluetoothGatt?,
        interval: Int,
        latency: Int,
        timeout: Int,
        status: Int,
    )

    /** Remote service database changed; app may need to rediscover services. */
    @Suppress("unused")
    fun uiServicesChanged(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
    )

    /** Simplified bond boolean (bonded vs not). Prefer [uiBondStateEvent] for detail. */
    @Suppress("unused")
    fun uiBondingChanged(device: BlueNoxDevice?, bondState: Boolean)

    /** Fine-grained bond state machine updates from the library or platform. */
    @Suppress("unused")
    fun uiBondStateEvent(
        device: BlueNoxDevice?,
        state: BlueNoxBondState,
        detail: String,
    )

    /** RSSI read completed via [BlueNoxDevice.readRssi] or periodic updates when enabled. */
    @Suppress("unused")
    fun uiNewRssiAvailable(gatt: BluetoothGatt?, device: BluetoothDevice?, rssi: Int)

    /** Optional scan/ranking hook when strongest signal device is tracked. */
    @Suppress("unused")
    fun uiNewMaxRssiDeviceFound(device: BluetoothDevice?, rssi: Int)

    /**
     * Structured failure for connect, bond, or GATT operations.
     *
     * @param characteristicUuid Related characteristic when applicable.
     */
    @Suppress("unused")
    fun uiOperationFailure(
        device: BluetoothDevice?,
        reason: BlueNoxFailureReason,
        detail: String,
        characteristicUuid: String?,
    )

    /**
     * No-op default implementation of [BlueNoxDeviceCallbacks] for selective overrides.
     */
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
