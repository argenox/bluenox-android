package com.argenox.bluenoxandroid

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid
import android.util.SparseArray
import java.util.Locale
import java.util.UUID

sealed interface BlueNoxBeaconFrame {
    data class IBeacon(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val txPower: Int,
        val companyId: Int = APPLE_COMPANY_ID,
    ) : BlueNoxBeaconFrame

    data class EddystoneUid(
        val txPower: Int,
        val namespaceIdHex: String,
        val instanceIdHex: String,
    ) : BlueNoxBeaconFrame

    data class EddystoneUrl(
        val txPower: Int,
        val url: String,
    ) : BlueNoxBeaconFrame

    data class EddystoneTlm(
        val version: Int,
        val batteryMilliVolts: Int,
        val temperatureCelsius: Float?,
        val advertisementCount: Long,
        val uptimeSeconds: Float,
    ) : BlueNoxBeaconFrame

    data class EddystoneEid(
        val txPower: Int,
        val ephemeralIdHex: String,
    ) : BlueNoxBeaconFrame

    companion object {
        const val APPLE_COMPANY_ID = 0x004C
    }
}

object BlueNoxBeaconDecoder {
    private val EDDYSTONE_SERVICE_UUID = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
    private const val FRAME_UID = 0x00
    private const val FRAME_URL = 0x10
    private const val FRAME_TLM = 0x20
    private const val FRAME_EID = 0x30

    /**
     * Decodes recognized beacon frames from [ScanRecord].
     *
     * Currently supports:
     * - iBeacon (Apple manufacturer frame)
     * - Eddystone UID / URL / TLM / EID (service UUID FEAA)
     */
    fun decode(scanRecord: ScanRecord?): List<BlueNoxBeaconFrame> {
        if (scanRecord == null) return emptyList()
        return decode(
            manufacturerData = scanRecord.manufacturerSpecificData,
            serviceData = scanRecord.serviceData,
        )
    }

    /**
     * Decodes recognized beacon frames from raw manufacturer/service data.
     */
    fun decode(
        manufacturerData: SparseArray<ByteArray>?,
        serviceData: Map<ParcelUuid, ByteArray>?,
    ): List<BlueNoxBeaconFrame> {
        val frames = mutableListOf<BlueNoxBeaconFrame>()
        decodeIBeacon(manufacturerData)?.let { frames.add(it) }
        decodeEddystone(serviceData).forEach(frames::add)
        return frames
    }

    private fun decodeIBeacon(manufacturerData: SparseArray<ByteArray>?): BlueNoxBeaconFrame.IBeacon? {
        if (manufacturerData == null) return null
        val payload = manufacturerData.get(BlueNoxBeaconFrame.APPLE_COMPANY_ID) ?: return null
        if (payload.size < 23) return null
        if ((payload[0].toInt() and 0xFF) != 0x02 || (payload[1].toInt() and 0xFF) != 0x15) return null

        val uuidBytes = payload.copyOfRange(2, 18)
        val major = ((payload[18].toInt() and 0xFF) shl 8) or (payload[19].toInt() and 0xFF)
        val minor = ((payload[20].toInt() and 0xFF) shl 8) or (payload[21].toInt() and 0xFF)
        val tx = payload[22].toInt()

        return BlueNoxBeaconFrame.IBeacon(
            uuid = bytesToUuid(uuidBytes).toString(),
            major = major,
            minor = minor,
            txPower = tx,
        )
    }

    private fun decodeEddystone(serviceData: Map<ParcelUuid, ByteArray>?): List<BlueNoxBeaconFrame> {
        if (serviceData == null) return emptyList()
        val data = serviceData[EDDYSTONE_SERVICE_UUID] ?: return emptyList()
        if (data.size < 2) return emptyList()

        val frameType = data[0].toInt() and 0xFF
        val txPower = data[1].toInt()
        return when (frameType) {
            FRAME_UID -> decodeEddystoneUid(data, txPower)?.let { listOf(it) } ?: emptyList()
            FRAME_URL -> decodeEddystoneUrl(data, txPower)?.let { listOf(it) } ?: emptyList()
            FRAME_TLM -> decodeEddystoneTlm(data)?.let { listOf(it) } ?: emptyList()
            FRAME_EID -> decodeEddystoneEid(data, txPower)?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }

    private fun decodeEddystoneUid(data: ByteArray, txPower: Int): BlueNoxBeaconFrame.EddystoneUid? {
        if (data.size < 18) return null
        val namespace = data.copyOfRange(2, 12).toHex()
        val instance = data.copyOfRange(12, 18).toHex()
        return BlueNoxBeaconFrame.EddystoneUid(
            txPower = txPower,
            namespaceIdHex = namespace,
            instanceIdHex = instance,
        )
    }

    private fun decodeEddystoneUrl(data: ByteArray, txPower: Int): BlueNoxBeaconFrame.EddystoneUrl? {
        if (data.size < 3) return null
        val encoded = data.copyOfRange(2, data.size)
        val url = decodeEddystoneUrlPayload(encoded)
        return BlueNoxBeaconFrame.EddystoneUrl(txPower = txPower, url = url)
    }

    private fun decodeEddystoneTlm(data: ByteArray): BlueNoxBeaconFrame.EddystoneTlm? {
        if (data.size < 14) return null
        val version = data[1].toInt() and 0xFF
        val battery = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val tempRaw = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val temp = if (tempRaw == 0x8000) {
            null
        } else {
            // Signed 8.8 fixed-point as per Eddystone-TLM.
            val signed = tempRaw.toShort().toInt()
            signed / 256f
        }
        val advCount = bytesToUInt32(data, 6)
        val secCountTicks = bytesToUInt32(data, 10)
        return BlueNoxBeaconFrame.EddystoneTlm(
            version = version,
            batteryMilliVolts = battery,
            temperatureCelsius = temp,
            advertisementCount = advCount,
            uptimeSeconds = secCountTicks / 10f,
        )
    }

    private fun decodeEddystoneEid(data: ByteArray, txPower: Int): BlueNoxBeaconFrame.EddystoneEid? {
        if (data.size < 10) return null
        val eid = data.copyOfRange(2, 10).toHex()
        return BlueNoxBeaconFrame.EddystoneEid(
            txPower = txPower,
            ephemeralIdHex = eid,
        )
    }

    private fun decodeEddystoneUrlPayload(encoded: ByteArray): String {
        val sb = StringBuilder()
        if (encoded.isEmpty()) return ""
        val prefix = when (encoded[0].toInt() and 0xFF) {
            0x00 -> "http://www."
            0x01 -> "https://www."
            0x02 -> "http://"
            0x03 -> "https://"
            else -> ""
        }
        sb.append(prefix)
        for (i in 1 until encoded.size) {
            val b = encoded[i].toInt() and 0xFF
            val expansion = when (b) {
                0x00 -> ".com/"
                0x01 -> ".org/"
                0x02 -> ".edu/"
                0x03 -> ".net/"
                0x04 -> ".info/"
                0x05 -> ".biz/"
                0x06 -> ".gov/"
                0x07 -> ".com"
                0x08 -> ".org"
                0x09 -> ".edu"
                0x0A -> ".net"
                0x0B -> ".info"
                0x0C -> ".biz"
                0x0D -> ".gov"
                else -> null
            }
            if (expansion != null) {
                sb.append(expansion)
            } else {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(Locale.US, it) }

    private fun bytesToUInt32(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        require(bytes.size == 16) { "UUID byte array must be 16 bytes" }
        var msb = 0L
        var lsb = 0L
        for (i in 0 until 8) {
            msb = (msb shl 8) or (bytes[i].toLong() and 0xFF)
        }
        for (i in 8 until 16) {
            lsb = (lsb shl 8) or (bytes[i].toLong() and 0xFF)
        }
        return UUID(msb, lsb)
    }
}
