package com.argenox.bluenoxandroid

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Flow-first adapter for BlueNox callbacks.
 *
 * Consumers can register this adapter as a device callback listener and collect [events]
 * to observe BLE events using coroutines/Flow APIs.
 */
class BlueNoxDeviceFlowAdapter : BlueNoxDeviceCallbacks.NullBlueNoxDeviceCallbacks() {

    sealed interface BlueNoxDeviceEvent {
        data class Connection(
            val address: String,
            val connected: Boolean,
        ) : BlueNoxDeviceEvent

        data class CharacteristicValue(
            val address: String,
            val characteristicUuid: String,
            val value: ByteArray,
            val source: String,
        ) : BlueNoxDeviceEvent

        data class Mtu(
            val address: String,
            val mtu: Int,
        ) : BlueNoxDeviceEvent

        data class ConnectionParameters(
            val address: String,
            val interval: Int,
            val latency: Int,
            val timeout: Int,
            val status: Int,
        ) : BlueNoxDeviceEvent

        data class ServicesChanged(
            val address: String,
        ) : BlueNoxDeviceEvent

        data class Bond(
            val address: String,
            val bonded: Boolean,
        ) : BlueNoxDeviceEvent

        data class BondState(
            val address: String,
            val state: BlueNoxDeviceCallbacks.BlueNoxBondState,
            val detail: String,
        ) : BlueNoxDeviceEvent

        data class Failure(
            val address: String,
            val reason: BlueNoxDeviceCallbacks.BlueNoxFailureReason,
            val detail: String,
            val characteristicUuid: String?,
        ) : BlueNoxDeviceEvent

        data class CccdConfigured(
            val address: String,
            val result: BlueNoxCccdConfigurationResult,
        ) : BlueNoxDeviceEvent
    }

    private val _events = MutableSharedFlow<BlueNoxDeviceEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<BlueNoxDeviceEvent> = _events.asSharedFlow()

    override fun uiDeviceConnected(gatt: BluetoothGatt?, device: BluetoothDevice?) {
        val address = device?.address ?: return
        _events.tryEmit(BlueNoxDeviceEvent.Connection(address = address, connected = true))
    }

    override fun uiDeviceDisconnected(gatt: BluetoothGatt?, device: BluetoothDevice?) {
        val address = device?.address ?: return
        _events.tryEmit(BlueNoxDeviceEvent.Connection(address = address, connected = false))
    }

    override fun uiCharacteristicUpdated(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?,
        value: ByteArray,
    ) {
        val address = device?.address ?: return
        val uuid = characteristic?.uuid?.toString() ?: return
        _events.tryEmit(
            BlueNoxDeviceEvent.CharacteristicValue(
                address = address,
                characteristicUuid = uuid,
                value = value,
                source = "notify",
            ),
        )
    }

    override fun uiCharacteristicRead(
        gatt: BluetoothGatt?,
        device: BluetoothDevice?,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        val address = device?.address ?: return
        val c = characteristic ?: return
        _events.tryEmit(
            BlueNoxDeviceEvent.CharacteristicValue(
                address = address,
                characteristicUuid = c.uuid.toString(),
                value = c.value ?: byteArrayOf(),
                source = "read",
            ),
        )
    }

    override fun uiMtuUpdated(gatt: BluetoothGatt?, mtu: Int) {
        val address = gatt?.device?.address ?: return
        _events.tryEmit(BlueNoxDeviceEvent.Mtu(address = address, mtu = mtu))
    }

    override fun uiConnectionUpdated(
        gatt: BluetoothGatt?,
        interval: Int,
        latency: Int,
        timeout: Int,
        status: Int,
    ) {
        val address = gatt?.device?.address ?: return
        _events.tryEmit(
            BlueNoxDeviceEvent.ConnectionParameters(
                address = address,
                interval = interval,
                latency = latency,
                timeout = timeout,
                status = status,
            ),
        )
    }

    override fun uiServicesChanged(gatt: BluetoothGatt?, device: BluetoothDevice?) {
        val address = device?.address ?: return
        _events.tryEmit(BlueNoxDeviceEvent.ServicesChanged(address = address))
    }

    override fun uiBondingChanged(device: BlueNoxDevice?, bondState: Boolean) {
        val address = device?.getMacAddress().orEmpty()
        if (address.isBlank()) return
        _events.tryEmit(BlueNoxDeviceEvent.Bond(address = address, bonded = bondState))
    }

    override fun uiBondStateEvent(
        device: BlueNoxDevice?,
        state: BlueNoxDeviceCallbacks.BlueNoxBondState,
        detail: String,
    ) {
        val address = device?.getMacAddress().orEmpty()
        if (address.isBlank()) return
        _events.tryEmit(
            BlueNoxDeviceEvent.BondState(
                address = address,
                state = state,
                detail = detail,
            ),
        )
    }

    override fun uiOperationFailure(
        device: BluetoothDevice?,
        reason: BlueNoxDeviceCallbacks.BlueNoxFailureReason,
        detail: String,
        characteristicUuid: String?,
    ) {
        val address = device?.address.orEmpty()
        _events.tryEmit(
            BlueNoxDeviceEvent.Failure(
                address = address,
                reason = reason,
                detail = detail,
                characteristicUuid = characteristicUuid,
            ),
        )
    }

    override fun uiCccdConfigured(device: BlueNoxDevice?, result: BlueNoxCccdConfigurationResult) {
        val address = device?.getMacAddress().orEmpty()
        if (address.isBlank()) return
        _events.tryEmit(
            BlueNoxDeviceEvent.CccdConfigured(
                address = address,
                result = result,
            ),
        )
    }
}
