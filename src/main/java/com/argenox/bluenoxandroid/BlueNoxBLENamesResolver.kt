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
 * File:    BLEManager.java
 * Summary: Implementation of BLE Manager for Android
 *
 **********************************************************************************/
package com.argenox.bluenoxandroid

import android.util.SparseArray

internal object BLENamesResolver {
    private val mServices = HashMap<String, String>()
    private val mCharacteristics = HashMap<String, String>()
    private val mValueFormats = SparseArray<String>()
    private val mAppearance = SparseArray<String>()
    private val mHeartRateSensorLocation = SparseArray<String>()

    fun resolveServiceName(uuid: String): String {
        var result = mServices[uuid]
        if (result == null) {
            result = "Unknown Service"
        }
        return result
    }

    fun resolveValueTypeDescription(format: Int): String {
        return mValueFormats[format, "Unknown Format"]
    }

    fun resolveCharacteristicName(uuid: String): String {
        var result = mCharacteristics[uuid]
        if (result == null) {
            result = "Unknown Characteristic"
        }
        return result
    }

    fun resolveUuid(uuid: String): String {
        var result = mServices[uuid]
        if (result != null) {
            return "Service: $result"
        }

        result = mCharacteristics[uuid]
        if (result != null) {
            return "Characteristic: $result"
        }

        result = "Unknown UUID: $uuid"
        return result
    }

    fun resolveUuidName(uuid: String): String {
        var result = mServices[uuid]
        if (result != null) {
            return result
        }

        result = mCharacteristics[uuid]
        if (result != null) {
            return result
        }

        result = "Unknown"
        return result
    }

    fun resolveAppearance(key: Int): String {
        return mAppearance[key, "Unknown Appearance"]
    }

    fun resolveHeartRateSensorLocation(key: Int): String {
        return mHeartRateSensorLocation[key, "Other"]
    }

    fun isService(uuid: String): Boolean {
        return mServices.containsKey(uuid)
    }

    fun isCharacteristic(uuid: String): Boolean {
        return mCharacteristics.containsKey(uuid)
    }

    /* If UUID has Bluetooth base, return 16-bit, otherwise keep the same */
    fun get16BluetoothUUID(uuid: String): String {
        if (uuid.contains("-0000-1000-8000-00805f9b34fb")) {
            return uuid.substring(4, 8)
        }

        return uuid
    }

    init {
        mServices["00001811-0000-1000-8000-00805f9b34fb"] = "Alert Notification Service"
        mServices["0000180f-0000-1000-8000-00805f9b34fb"] = "Battery Service"
        mServices["00001810-0000-1000-8000-00805f9b34fb"] = "Blood Pressure"
        mServices["00001805-0000-1000-8000-00805f9b34fb"] = "Current Time Service"
        mServices["00001818-0000-1000-8000-00805f9b34fb"] = "Cycling Power"
        mServices["00001816-0000-1000-8000-00805f9b34fb"] = "Cycling Speed and Cadence"
        mServices["0000180a-0000-1000-8000-00805f9b34fb"] = "Device Information"
        mServices["00001800-0000-1000-8000-00805f9b34fb"] = "Generic Access"
        mServices["00001801-0000-1000-8000-00805f9b34fb"] = "Generic Attribute"
        mServices["00001808-0000-1000-8000-00805f9b34fb"] = "Glucose"
        mServices["00001809-0000-1000-8000-00805f9b34fb"] = "Health Thermometer"
        mServices["0000180d-0000-1000-8000-00805f9b34fb"] = "Heart Rate"
        mServices["00001812-0000-1000-8000-00805f9b34fb"] = "Human Interface Device"
        mServices["00001802-0000-1000-8000-00805f9b34fb"] = "Immediate Alert"
        mServices["00001803-0000-1000-8000-00805f9b34fb"] = "Link Loss"
        mServices["00001819-0000-1000-8000-00805f9b34fb"] = "Location and Navigation"
        mServices["00001807-0000-1000-8000-00805f9b34fb"] = "Next DST Change Service"
        mServices["0000180e-0000-1000-8000-00805f9b34fb"] = "Phone Alert Status Service"
        mServices["00001806-0000-1000-8000-00805f9b34fb"] = "Reference Time Update Service"
        mServices["00001814-0000-1000-8000-00805f9b34fb"] = "Running Speed and Cadence"
        mServices["00001813-0000-1000-8000-00805f9b34fb"] = "Scan Parameters"
        mServices["00001804-0000-1000-8000-00805f9b34fb"] = "Tx Power"

        mCharacteristics["00002a43-0000-1000-8000-00805f9b34fb"] = "Alert Category ID"
        mCharacteristics["00002a42-0000-1000-8000-00805f9b34fb"] = "Alert Category ID Bit Mask"
        mCharacteristics["00002a06-0000-1000-8000-00805f9b34fb"] = "Alert Level"
        mCharacteristics["00002a44-0000-1000-8000-00805f9b34fb"] = "Alert Notification Control Point"
        mCharacteristics["00002a3f-0000-1000-8000-00805f9b34fb"] = "Alert Status"
        mCharacteristics["00002a01-0000-1000-8000-00805f9b34fb"] = "Appearance"
        mCharacteristics["00002a19-0000-1000-8000-00805f9b34fb"] = "Battery Level"
        mCharacteristics["00002a49-0000-1000-8000-00805f9b34fb"] = "Blood Pressure Feature"
        mCharacteristics["00002a35-0000-1000-8000-00805f9b34fb"] = "Blood Pressure Measurement"
        mCharacteristics["00002a38-0000-1000-8000-00805f9b34fb"] = "Body Sensor Location"
        mCharacteristics["00002a22-0000-1000-8000-00805f9b34fb"] = "Boot Keyboard Input Report"
        mCharacteristics["00002a32-0000-1000-8000-00805f9b34fb"] = "Boot Keyboard Output Report"
        mCharacteristics["00002a33-0000-1000-8000-00805f9b34fb"] = "Boot Mouse Input Report"
        mCharacteristics["00002a5c-0000-1000-8000-00805f9b34fb"] = "CSC Feature"
        mCharacteristics["00002a5b-0000-1000-8000-00805f9b34fb"] = "CSC Measurement"
        mCharacteristics["00002a2b-0000-1000-8000-00805f9b34fb"] = "Current Time"
        mCharacteristics["00002a66-0000-1000-8000-00805f9b34fb"] = "Cycling Power Control Point"
        mCharacteristics["00002a65-0000-1000-8000-00805f9b34fb"] = "Cycling Power Feature"
        mCharacteristics["00002a63-0000-1000-8000-00805f9b34fb"] = "Cycling Power Measurement"
        mCharacteristics["00002a64-0000-1000-8000-00805f9b34fb"] = "Cycling Power Vector"
        mCharacteristics["00002a08-0000-1000-8000-00805f9b34fb"] = "Date Time"
        mCharacteristics["00002a0a-0000-1000-8000-00805f9b34fb"] = "Day Date Time"
        mCharacteristics["00002a09-0000-1000-8000-00805f9b34fb"] = "Day of Week"
        mCharacteristics["00002a00-0000-1000-8000-00805f9b34fb"] = "Device Name"
        mCharacteristics["00002a0d-0000-1000-8000-00805f9b34fb"] = "DST Offset"
        mCharacteristics["00002a0c-0000-1000-8000-00805f9b34fb"] = "Exact Time 256"
        mCharacteristics["00002a26-0000-1000-8000-00805f9b34fb"] = "Firmware Revision String"
        mCharacteristics["00002a51-0000-1000-8000-00805f9b34fb"] = "Glucose Feature"
        mCharacteristics["00002a18-0000-1000-8000-00805f9b34fb"] = "Glucose Measurement"
        mCharacteristics["00002a34-0000-1000-8000-00805f9b34fb"] = "Glucose Measurement Context"
        mCharacteristics["00002a27-0000-1000-8000-00805f9b34fb"] = "Hardware Revision String"
        mCharacteristics["00002a39-0000-1000-8000-00805f9b34fb"] = "Heart Rate Control Point"
        mCharacteristics["00002a37-0000-1000-8000-00805f9b34fb"] = "Heart Rate Measurement"
        mCharacteristics["00002a4c-0000-1000-8000-00805f9b34fb"] = "HID Control Point"
        mCharacteristics["00002a4a-0000-1000-8000-00805f9b34fb"] = "HID Information"
        mCharacteristics["00002a2a-0000-1000-8000-00805f9b34fb"] = "IEEE 11073-20601 Regulatory Certification Data List"
        mCharacteristics["00002a36-0000-1000-8000-00805f9b34fb"] = "Intermediate Cuff Pressure"
        mCharacteristics["00002a1e-0000-1000-8000-00805f9b34fb"] = "Intermediate Temperature"
        mCharacteristics["00002a6b-0000-1000-8000-00805f9b34fb"] = "LN Control Point"
        mCharacteristics["00002a6a-0000-1000-8000-00805f9b34fb"] = "LN Feature"
        mCharacteristics["00002a0f-0000-1000-8000-00805f9b34fb"] = "Local Time Information"
        mCharacteristics["00002a67-0000-1000-8000-00805f9b34fb"] = "Location and Speed"
        mCharacteristics["00002a29-0000-1000-8000-00805f9b34fb"] = "Manufacturer Name String"
        mCharacteristics["00002a21-0000-1000-8000-00805f9b34fb"] = "Measurement Interval"
        mCharacteristics["00002a24-0000-1000-8000-00805f9b34fb"] = "Model Number String"
        mCharacteristics["00002a68-0000-1000-8000-00805f9b34fb"] = "Navigation"
        mCharacteristics["00002a46-0000-1000-8000-00805f9b34fb"] = "New Alert"
        mCharacteristics["00002a04-0000-1000-8000-00805f9b34fb"] = "Peripheral Preferred Connection Parameters"
        mCharacteristics["00002a02-0000-1000-8000-00805f9b34fb"] = "Peripheral Privacy Flag"
        mCharacteristics["00002a50-0000-1000-8000-00805f9b34fb"] = "PnP ID"
        mCharacteristics["00002a69-0000-1000-8000-00805f9b34fb"] = "Position Quality"
        mCharacteristics["00002a4e-0000-1000-8000-00805f9b34fb"] = "Protocol Mode"
        mCharacteristics["00002a03-0000-1000-8000-00805f9b34fb"] = "Reconnection Address"
        mCharacteristics["00002a52-0000-1000-8000-00805f9b34fb"] = "Record Access Control Point"
        mCharacteristics["00002a14-0000-1000-8000-00805f9b34fb"] = "Reference Time Information"
        mCharacteristics["00002a4d-0000-1000-8000-00805f9b34fb"] = "Report"
        mCharacteristics["00002a4b-0000-1000-8000-00805f9b34fb"] = "Report Map"
        mCharacteristics["00002a40-0000-1000-8000-00805f9b34fb"] = "Ringer Control Point"
        mCharacteristics["00002a41-0000-1000-8000-00805f9b34fb"] = "Ringer Setting"
        mCharacteristics["00002a54-0000-1000-8000-00805f9b34fb"] = "RSC Feature"
        mCharacteristics["00002a53-0000-1000-8000-00805f9b34fb"] = "RSC Measurement"
        mCharacteristics["00002a55-0000-1000-8000-00805f9b34fb"] = "SC Control Point"
        mCharacteristics["00002a4f-0000-1000-8000-00805f9b34fb"] = "Scan Interval Window"
        mCharacteristics["00002a31-0000-1000-8000-00805f9b34fb"] = "Scan Refresh"
        mCharacteristics["00002a5d-0000-1000-8000-00805f9b34fb"] = "Sensor Location"
        mCharacteristics["00002a25-0000-1000-8000-00805f9b34fb"] = "Serial Number String"
        mCharacteristics["00002a05-0000-1000-8000-00805f9b34fb"] = "Service Changed"
        mCharacteristics["00002a28-0000-1000-8000-00805f9b34fb"] = "Software Revision String"
        mCharacteristics["00002a47-0000-1000-8000-00805f9b34fb"] = "Supported New Alert Category"
        mCharacteristics["00002a48-0000-1000-8000-00805f9b34fb"] = "Supported Unread Alert Category"
        mCharacteristics["00002a23-0000-1000-8000-00805f9b34fb"] = "System ID"
        mCharacteristics["00002a1c-0000-1000-8000-00805f9b34fb"] = "Temperature Measurement"
        mCharacteristics["00002a1d-0000-1000-8000-00805f9b34fb"] = "Temperature Type"
        mCharacteristics["00002a12-0000-1000-8000-00805f9b34fb"] = "Time Accuracy"
        mCharacteristics["00002a13-0000-1000-8000-00805f9b34fb"] = "Time Source"
        mCharacteristics["00002a16-0000-1000-8000-00805f9b34fb"] = "Time Update Control Point"
        mCharacteristics["00002a17-0000-1000-8000-00805f9b34fb"] = "Time Update State"
        mCharacteristics["00002a11-0000-1000-8000-00805f9b34fb"] = "Time with DST"
        mCharacteristics["00002a0e-0000-1000-8000-00805f9b34fb"] = "Time Zone"
        mCharacteristics["00002a07-0000-1000-8000-00805f9b34fb"] = "Tx Power Level"
        mCharacteristics["00002a45-0000-1000-8000-00805f9b34fb"] = "Unread Alert Status"

        // TI SensorTag Proprietary Services
        mServices["f000aa10-0451-4000-b000-000000000000"] = "SensorTag Accelerometer Service"
        mServices["f000aa20-0451-4000-b000-000000000000"] = "SensorTag Humidity Service"
        mServices["f000aa30-0451-4000-b000-000000000000"] = "SensorTag Magnetometer Service"
        mServices["f000aa40-0451-4000-b000-000000000000"] = "SensorTag Barometer Service"
        mServices["f000aa50-0451-4000-b000-000000000000"] = "SensorTag Gyroscope Service"
        mServices["0000ffe0-0000-1000-8000-00805f9b34fb"] = "SensorTag Keys Service"

        // TI SensorTag Proprietary Characteristics
        mCharacteristics["f000aa00-0451-4000-b000-000000000000"] = "SensorTag Infrared Temperature Service"
        mCharacteristics["f000aa01-0451-4000-b000-000000000000"] = "SensorTag Infrared Temperature Data"
        mCharacteristics["f000aa02-0451-4000-b000-000000000000"] = "SensorTag Infrared Temperature Config"
        mCharacteristics["f000aa03-0451-4000-b000-000000000000"] = "SensorTag Infrared Temperature Period"
        mCharacteristics["f000aa11-0451-4000-b000-000000000000"] = "SensorTag Accelerometer Data"
        mCharacteristics["f000aa12-0451-4000-b000-000000000000"] = "SensorTag Accelerometer Config"
        mCharacteristics["f000aa13-0451-4000-b000-000000000000"] = "SensorTag Accelerometer Period"
        mCharacteristics["f000aa21-0451-4000-b000-000000000000"] = "SensorTag Humidity Data"
        mCharacteristics["f000aa22-0451-4000-b000-000000000000"] = "SensorTag Humidity Config"
        mCharacteristics["f000aa23-0451-4000-b000-000000000000"] = "SensorTag Humidity Period"
        mCharacteristics["f000aa31-0451-4000-b000-000000000000"] = "SensorTag Magnetometer Data"
        mCharacteristics["f000aa32-0451-4000-b000-000000000000"] = "SensorTag Magnetometer Config"
        mCharacteristics["f000aa33-0451-4000-b000-000000000000"] = "SensorTag Magnetometer Period"
        mCharacteristics["f000aa41-0451-4000-b000-000000000000"] = "SensorTag Barometer Data"
        mCharacteristics["f000aa42-0451-4000-b000-000000000000"] = "SensorTag Barometer Config"
        mCharacteristics["f000aa43-0451-4000-b000-000000000000"] = "SensorTag Barometer Calibration"
        mCharacteristics["f000aa44-0451-4000-b000-000000000000"] = "SensorTag Barometer Period"
        mCharacteristics["f000aa51-0451-4000-b000-000000000000"] = "SensorTag Gyroscope Data"
        mCharacteristics["f000aa52-0451-4000-b000-000000000000"] = "SensorTag Gyroscope Config"
        mCharacteristics["f000aa53-0451-4000-b000-000000000000"] = "SensorTag Gyroscope Period"
        mCharacteristics["0000ffe1-0000-1000-8000-00805f9b34fb"] = "SensorTag Keys Data"

        mValueFormats.put(52, "32bit float")
        mValueFormats.put(50, "16bit float")
        mValueFormats.put(34, "16bit signed int")
        mValueFormats.put(36, "32bit signed int")
        mValueFormats.put(33, "8bit signed int")
        mValueFormats.put(18, "16bit unsigned int")
        mValueFormats.put(20, "32bit unsigned int")
        mValueFormats.put(17, "8bit unsigned int")

        // lets add also couple appearance string description
        // https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.gap.appearance.xml
        mAppearance.put(833, "Heart Rate Sensor: Belt")
        mAppearance.put(832, "Generic Heart Rate Sensor")
        mAppearance.put(0, "Unknown")
        mAppearance.put(64, "Generic Phone")
        mAppearance.put(1157, "Cycling: Speed and Cadence Sensor")
        mAppearance.put(1152, "General Cycling")
        mAppearance.put(1153, "Cycling Computer")
        mAppearance.put(1154, "Cycling: Speed Sensor")
        mAppearance.put(1155, "Cycling: Cadence Sensor")
        mAppearance.put(1156, "Cycling: Speed and Cadence Sensor")
        mAppearance.put(1157, "Cycling: Power Sensor")

        mHeartRateSensorLocation.put(0, "Other")
        mHeartRateSensorLocation.put(1, "Chest")
        mHeartRateSensorLocation.put(2, "Wrist")
        mHeartRateSensorLocation.put(3, "Finger")
        mHeartRateSensorLocation.put(4, "Hand")
        mHeartRateSensorLocation.put(5, "Ear Lobe")
        mHeartRateSensorLocation.put(6, "Foot")
    }
}
