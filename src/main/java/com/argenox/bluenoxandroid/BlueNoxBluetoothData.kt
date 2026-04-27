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
 * File:    BluetoothData.kt
 * Summary: Data container for Bluetooth GATT operations
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

internal class BluetoothData {
    var gatt: BluetoothGatt? = null
    var c: BluetoothGattCharacteristic? = null
    var d: BluetoothGattDescriptor? = null
    var status: Int = 0
    lateinit var value: ByteArray
    var mtu: Int = 0
    var interval: Int = 0
    var latency: Int = 0
    var timeout: Int = 0
}
