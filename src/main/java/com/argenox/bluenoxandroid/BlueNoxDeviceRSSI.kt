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
 * File:    DeviceRSSI.kt
 * Summary: Class for tracking RSSI measurements for a Bluetooth device
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import java.util.Calendar
import java.util.Date

internal class DeviceRSSI(private val address: String)
{
    private val rssiList: ArrayList<Int> = ArrayList()
    private val mDateList = ArrayList<Date>()
    private val mMacAddress: String = address



    fun addEntry(rssi: Int) {
        rssiList.add(rssi)
        mDateList.add(Calendar.getInstance().time)
    }

    fun getRssiList() : ArrayList<Int>
    {
        return rssiList
    }

    public fun getAddress(): String
    {
        return mMacAddress
    }


}
