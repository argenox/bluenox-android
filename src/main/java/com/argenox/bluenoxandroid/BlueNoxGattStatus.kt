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
 * File:    GattStatus.kt
 * Summary: GATT status codes and error handling utilities
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.bluetooth.BluetoothGatt

enum class GattStatus(private val constValue: Int, private val description: String?) {
    Unknown(-1, null),
    Success(0, "success"),
    ReadNotPermitted(2, "read not permitted"),
    WriteNotPermitted(3, "write not permitted"),
    InsufficientAuthentication(5, "insufficient authentication"),
    RequestNotSupported(6, "request not supported"),
    InvalidOffset(7, "invalid offset"),
    InvalidAttributeLength(13, "invalid attribute length"),
    InsufficientEncryption(15, "insufficient encryption"),
    ConnectionCongested(143, "connection congested"),
    Failure(257, "failure"),
    PrepareQFull(9, "gatt_prepare_q_full"),
    CharacteristicDoesNotExist(1000, "characteristic not present in this service");

    @Suppress("unused")
    fun constValue(): Int {
        return constValue
    }

    @Suppress("unused")
    fun description(): String? {
        return if (constValue != -1) description else "unknown gatt status $constValue"
    }

    @Suppress("unused")
    fun description(altStatus: Int): String? {
        return if (constValue != -1) description else "unknown gatt status $altStatus"
    }

    companion object {
        @Suppress("unused")
        fun parse(status: Int): GattStatus {
            return when (status) {
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> WriteNotPermitted
                BluetoothGatt.GATT_SUCCESS -> Success
                BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> RequestNotSupported
                BluetoothGatt.GATT_READ_NOT_PERMITTED -> ReadNotPermitted
                BluetoothGatt.GATT_INVALID_OFFSET -> InvalidOffset
                BluetoothGatt.GATT_CONNECTION_CONGESTED -> ConnectionCongested
                BluetoothGatt.GATT_FAILURE -> Failure
                BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> InsufficientAuthentication
                BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> InsufficientEncryption
                BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> InvalidAttributeLength
                9 -> PrepareQFull
                else -> Unknown
            }
        }
    }
}
