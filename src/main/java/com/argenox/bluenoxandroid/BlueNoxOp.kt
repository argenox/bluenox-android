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
 * File:    BlueNoxOp.kt
 * Summary: BlueNox Operation Class
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

class BlueNoxOp
{
    val mOperationId: String = UUID.randomUUID().toString()
    lateinit var mOperationType: OpType
    lateinit var mTermCondition: OpCompleteCondition
    lateinit var mCharacteristic: BluetoothGattCharacteristic
    lateinit var mTermCharacteristic: BluetoothGattCharacteristic /* characteristic which ends the operation */
    lateinit var mWriteData: ByteArray
    var mStarted = false
    var mVal: Boolean = false
    var mTimeoutVal: Long = 0
    var mTimeoutEnabled: Boolean = false
    var mStartAttempts: Int = 0
    var mMaxStartAttempts: Int = 3

    constructor(
        t: OpType, n: OpCompleteCondition, c: BluetoothGattCharacteristic, data: ByteArray,
        termChar: BluetoothGattCharacteristic, timeout: Long
    ) : this(t, n, c, data, termChar) {
        if (timeout == 0L) {
            mTimeoutVal = timeout
            mTimeoutEnabled = false
        } else {
            mTimeoutVal = timeout
            mTimeoutEnabled = true
        }
    }

    constructor(t: OpType, n: OpCompleteCondition, c: BluetoothGattCharacteristic) {
        mOperationType = t
        mTermCondition = n
        mCharacteristic = c
        mTermCharacteristic = c
        mStarted = false
        mVal = false
    }

    constructor(
        t: OpType,
        n: OpCompleteCondition,
        c: BluetoothGattCharacteristic,
        `val`: Boolean
    ) {
        mOperationType = t
        mTermCondition = n
        mCharacteristic = c
        mTermCharacteristic = c
        mStarted = false
        mVal = `val`
        mTimeoutEnabled = true
    }

    constructor(
        t: OpType, n: OpCompleteCondition, c: BluetoothGattCharacteristic, data: ByteArray,
        termChar: BluetoothGattCharacteristic
    ) {
        if (t != OpType.Write && t != OpType.WriteNoResponse) return

        mOperationType = t
        mTermCondition = n
        mCharacteristic = c
        mWriteData = data
        mStarted = false
        mTermCharacteristic = termChar
    }

    constructor(
        t: OpType,
        n: OpCompleteCondition,
        c: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        if (t != OpType.Write && t != OpType.WriteNoResponse) return

        mOperationType = t
        mTermCondition = n
        mCharacteristic = c
        mTermCharacteristic = c
        mWriteData = data
        mStarted = false
    }

    enum class OpCompleteCondition(private val constValue: Int, private val description: String?) {
        Unknown(-1, null),
        None(0, "none"),
        Read(1, "none"),
        Notification(2, "notification"),
        Write(3, "write"),
        DescriptorWrite(4, "descriptor write");


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
    }

    enum class OpType(private val constValue: Int, private val description: String?) {
        Unknown(-1, null),
        Read(0, "read"),
        Write(1, "write with response"),
        WriteNoResponse(2, "write w/o response"),
        DescriptorWrite(3, "descriptor write");

        @Suppress("unused")
        fun constValue(): Int {
            return constValue
        }

        @Suppress("unused")
        fun description(): String? {
            return if (constValue != -1) description else "unknown operation type$constValue"
        }

        @Suppress("unused")
        fun description(altStatus: Int): String? {
            return if (constValue != -1) description else "unknown operation type $altStatus"
        }
    }
}
