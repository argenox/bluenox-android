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
 * File:    BlueNoxCommon.java
 * Summary: Common BlueNox Functionality
 *
 **********************************************************************************/

package com.argenox.bluenoxandroid

import android.util.Log

object BlueNoxCommon {
    @Suppress("unused")
    fun getUInt32DataIntValue(data: ByteArray, offset: Int): Int {
        if (data.size < 4 + offset) {
            Log.e("BlueNoxCommon", "Error in getUInt32DataIntValue" + data.size)
            return 0
        }

        return ((data[offset + 3].toInt() and 0xFF) shl 24) or ((data[offset + 2].toInt() and 0xFF) shl 16) or ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
    }

    fun getUInt32DataIntValueReverse(data: ByteArray, offset: Int): Int {
        if (data.size < 4 + offset) {
            Log.e("BlueNoxCommon", "Error in getUInt32DataIntValue" + data.size)
            return 0
        }

        return ((data[offset].toInt() and 0xFF) shl 24) or ((data[offset + 1].toInt() and 0xFF) shl 16) or ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
    }

    @Suppress("unused")
    fun getUInt32DataFloatValue(data: ByteArray, offset: Int): Float {
        val `val` =
            ((data[offset + 3].toInt() and 0xFF) shl 24) or ((data[offset + 2].toInt() and 0xFF) shl 16) or ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
        return `val`.toFloat()
    }

    @Suppress("unused")
    fun getUInt16DataIntValue(data: ByteArray, offset: Int): Int {
        return ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
    }

    @Suppress("unused")
    fun getUInt16DataFloatValue(data: ByteArray, offset: Int): Float {
        val `val` = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)
        return `val`.toFloat()
    }

    @Suppress("unused")
    fun getInt16DataFloatValue(data: ByteArray, offset: Int): Float {
        val `val` =
            (((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset].toInt() and 0xFF)).toShort()
        return `val`.toFloat()
    }
}
