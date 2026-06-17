package com.btgun.host.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class UdpInputFrameType(val wireValue: Int) {
    SNAPSHOT(1),
    EDGE(2);

    companion object {
        fun fromWireValue(wireValue: Int): UdpInputFrameType? =
            entries.firstOrNull { it.wireValue == wireValue }
    }
}

data class UdpInputFrame(
    val type: UdpInputFrameType,
    val streamSessionId: String,
    val sequence: Long,
    val captureElapsedNanos: Long,
    val sendElapsedNanos: Long,
    val buttonBitmask: Int,
    val stickX: Int,
    val stickY: Int,
    val motionProvider: Int,
    val motionCapabilityFlags: Int,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val rawAimX: Float,
    val rawAimY: Float,
    val sourceSensorElapsedNanos: Long,
    val streamFlags: Int = 0,
    val productAimX: Float = yaw,
    val productAimY: Float = pitch,
    val rawRoll: Float = roll,
) {
    init {
        require(streamSessionId.length == 32 && streamSessionId.all { it in '0'..'9' || it in 'a'..'f' }) {
            "streamSessionId must be 16 lowercase hex bytes"
        }
        require(sequence > 0L) { "sequence must be positive" }
        require(captureElapsedNanos >= 0L) { "captureElapsedNanos must be non-negative" }
        require(sendElapsedNanos >= captureElapsedNanos) { "sendElapsedNanos must be greater than or equal to captureElapsedNanos" }
        require(stickX in Short.MIN_VALUE..Short.MAX_VALUE) { "stickX must fit int16" }
        require(stickY in Short.MIN_VALUE..Short.MAX_VALUE) { "stickY must fit int16" }
        require(motionProvider in 0..255) { "motionProvider must fit uint8" }
        require(motionCapabilityFlags in 0..255) { "motionCapabilityFlags must fit uint8" }
        require(sourceSensorElapsedNanos >= 0L) { "sourceSensorElapsedNanos must be non-negative" }
        require(streamFlags in 0..0xffff) { "streamFlags must fit uint16" }
        require(streamFlags and KNOWN_STREAM_FLAGS == streamFlags) { "streamFlags contains unknown bits" }
    }

    companion object {
        const val FLAG_MAPPED_PRODUCT_STREAM: Int = 0x0001
        const val FLAG_RAW_DEBUG_EXTRAS: Int = 0x0002
        const val KNOWN_STREAM_FLAGS: Int = FLAG_MAPPED_PRODUCT_STREAM or FLAG_RAW_DEBUG_EXTRAS
    }
}

sealed interface UdpInputFrameDecodeResult {
    data class Accepted(val frame: UdpInputFrame) : UdpInputFrameDecodeResult
    data class Rejected(val reason: UdpInputFrameRejectReason, val detail: String? = null) : UdpInputFrameDecodeResult
}

enum class UdpInputFrameRejectReason {
    INVALID_LENGTH,
    BAD_MAGIC,
    UNSUPPORTED_VERSION,
    UNKNOWN_TYPE,
    WRONG_STREAM_SESSION,
    BAD_HMAC,
    MALFORMED_FIELD,
}

data class UdpInputFrameDebugSummary(
    val accepted: Boolean,
    val reason: UdpInputFrameRejectReason? = null,
    val frameType: String? = null,
    val sequence: Long? = null,
    val captureElapsedNanos: Long? = null,
    val sendElapsedNanos: Long? = null,
    val buttonBitmask: Int? = null,
    val stickX: Int? = null,
    val stickY: Int? = null,
    val motionProvider: Int? = null,
    val motionCapabilityFlags: Int? = null,
    val streamFlags: Int? = null,
    val productAimX: Float? = null,
    val productAimY: Float? = null,
    val rawRoll: Float? = null,
    val hasRawAim: Boolean? = null,
    val sourceSensorElapsedNanos: Long? = null,
)

object UdpInputFrameCodec {
    const val FRAME_SIZE = 120
    const val COMPACT_FRAME_SIZE = 92
    const val TAG_SIZE = 32
    const val MAGIC = "BTGI"
    const val COMPACT_MAGIC = "BTG2"
    const val VERSION = 1
    const val COMPACT_VERSION = 2
    const val OFFSET_STREAM_FLAGS = 6
    const val OFFSET_RESERVED_FLAGS = OFFSET_STREAM_FLAGS
    const val OFFSET_SEQUENCE = 24
    const val OFFSET_CAPTURE_ELAPSED_NANOS = 32
    const val OFFSET_SEND_ELAPSED_NANOS = 40
    const val OFFSET_BUTTON_BITMASK = 48
    const val OFFSET_STICK_X = 52
    const val OFFSET_STICK_Y = 54
    const val OFFSET_MOTION_PROVIDER = 56
    const val OFFSET_MOTION_CAPABILITY_FLAGS = 57
    const val OFFSET_RESERVED_MOTION = 58
    const val OFFSET_YAW = 60
    const val OFFSET_PITCH = 64
    const val OFFSET_ROLL = 68
    const val OFFSET_RAW_AIM_X = 72
    const val OFFSET_RAW_AIM_Y = 76
    const val OFFSET_SOURCE_SENSOR_ELAPSED_NANOS = 80
    const val OFFSET_HMAC_TAG = 88
    const val COMPACT_OFFSET_SEQUENCE = 24
    const val COMPACT_OFFSET_CAPTURE_ELAPSED_NANOS = 32
    const val COMPACT_OFFSET_SEND_ELAPSED_NANOS = 40
    const val COMPACT_OFFSET_BUTTON_BITMASK = 48
    const val COMPACT_OFFSET_STICK_X = 52
    const val COMPACT_OFFSET_STICK_Y = 54
    const val COMPACT_OFFSET_AIM_X = 56
    const val COMPACT_OFFSET_AIM_Y = 58
    const val COMPACT_OFFSET_HMAC_TAG = 60

    fun encode(frame: UdpInputFrame, config: InputStreamConfig): ByteArray {
        require(frame.streamSessionId == config.streamSessionIdHex) { "frame stream id must match config" }
        val bytes = ByteArray(FRAME_SIZE)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.put(VERSION.toByte())
        buffer.put(frame.type.wireValue.toByte())
        buffer.putShort(frame.streamFlags.toShort())
        buffer.put(frame.streamSessionId.hexToBytes())
        buffer.putLong(frame.sequence)
        buffer.putLong(frame.captureElapsedNanos)
        buffer.putLong(frame.sendElapsedNanos)
        buffer.putInt(frame.buttonBitmask)
        buffer.putShort(frame.stickX.toShort())
        buffer.putShort(frame.stickY.toShort())
        buffer.put(frame.motionProvider.toByte())
        buffer.put(frame.motionCapabilityFlags.toByte())
        buffer.putShort(0)
        buffer.putFloat(frame.productAimX)
        buffer.putFloat(frame.productAimY)
        buffer.putFloat(frame.rawRoll)
        buffer.putFloat(frame.rawAimX)
        buffer.putFloat(frame.rawAimY)
        buffer.putLong(frame.sourceSensorElapsedNanos)
        hmac(bytes.copyOfRange(0, OFFSET_HMAC_TAG), config.hmacKeyBytes()).copyInto(bytes, OFFSET_HMAC_TAG)
        return bytes
    }

    fun encodeCompact(frame: UdpInputFrame, config: InputStreamConfig): ByteArray {
        require(frame.streamSessionId == config.streamSessionIdHex) { "frame stream id must match config" }
        val bytes = ByteArray(COMPACT_FRAME_SIZE)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.put(COMPACT_MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.put(COMPACT_VERSION.toByte())
        buffer.put(frame.type.wireValue.toByte())
        buffer.putShort(frame.streamFlags.toShort())
        buffer.put(frame.streamSessionId.hexToBytes())
        buffer.putLong(frame.sequence)
        buffer.putLong(frame.captureElapsedNanos)
        buffer.putLong(frame.sendElapsedNanos)
        buffer.putInt(frame.buttonBitmask)
        buffer.putShort(frame.stickX.toShort())
        buffer.putShort(frame.stickY.toShort())
        buffer.putShort(frame.productAimX.toInt16Axis().toShort())
        buffer.putShort(frame.productAimY.toInt16Axis().toShort())
        hmac(bytes.copyOfRange(0, COMPACT_OFFSET_HMAC_TAG), config.hmacKeyBytes()).copyInto(bytes, COMPACT_OFFSET_HMAC_TAG)
        return bytes
    }

    fun authenticateAndDecodeMux(bytes: ByteArray, config: InputStreamConfig): UdpInputFrameDecodeResult {
        val frameFormat = frameFormatFor(bytes)
            ?: return when {
                bytes.size != FRAME_SIZE && bytes.size != COMPACT_FRAME_SIZE ->
                    UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.INVALID_LENGTH, "size=${bytes.size}")
                else -> UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.BAD_MAGIC)
            }
        if (frameFormat != config.frameFormat) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.MALFORMED_FIELD, "frame format mismatch")
        }
        return when (frameFormat) {
            InputFrameFormat.V1 -> authenticateAndDecode(bytes, config)
            InputFrameFormat.COMPACT_V2 -> authenticateAndDecodeCompact(bytes, config)
        }
    }

    fun authenticateAndDecodeCompact(bytes: ByteArray, config: InputStreamConfig): UdpInputFrameDecodeResult {
        if (bytes.size != COMPACT_FRAME_SIZE) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.INVALID_LENGTH, "size=${bytes.size}")
        }
        if (!bytes.copyOfRange(0, 4).contentEquals(COMPACT_MAGIC.toByteArray(Charsets.US_ASCII))) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.BAD_MAGIC)
        }
        if ((bytes[4].toInt() and 0xff) != COMPACT_VERSION) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.UNSUPPORTED_VERSION)
        }
        val type = UdpInputFrameType.fromWireValue(bytes[5].toInt() and 0xff)
            ?: return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.UNKNOWN_TYPE)
        val streamSessionId = bytes.copyOfRange(8, COMPACT_OFFSET_SEQUENCE).toHex()
        if (streamSessionId != config.streamSessionIdHex) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.WRONG_STREAM_SESSION)
        }
        val expectedTag = hmac(bytes.copyOfRange(0, COMPACT_OFFSET_HMAC_TAG), config.hmacKeyBytes())
        val actualTag = bytes.copyOfRange(COMPACT_OFFSET_HMAC_TAG, COMPACT_FRAME_SIZE)
        if (!MessageDigest.isEqual(expectedTag, actualTag)) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.BAD_HMAC)
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val streamFlags = buffer.getShort(OFFSET_STREAM_FLAGS).toInt() and 0xffff
        if (streamFlags and UdpInputFrame.KNOWN_STREAM_FLAGS != streamFlags) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.MALFORMED_FIELD, "stream flags")
        }
        val aimX = buffer.getShort(COMPACT_OFFSET_AIM_X).toInt().fromInt16Axis()
        val aimY = buffer.getShort(COMPACT_OFFSET_AIM_Y).toInt().fromInt16Axis()
        val frame = runCatching {
            UdpInputFrame(
                type = type,
                streamSessionId = streamSessionId,
                sequence = buffer.getLong(COMPACT_OFFSET_SEQUENCE),
                captureElapsedNanos = buffer.getLong(COMPACT_OFFSET_CAPTURE_ELAPSED_NANOS),
                sendElapsedNanos = buffer.getLong(COMPACT_OFFSET_SEND_ELAPSED_NANOS),
                buttonBitmask = buffer.getInt(COMPACT_OFFSET_BUTTON_BITMASK),
                stickX = buffer.getShort(COMPACT_OFFSET_STICK_X).toInt(),
                stickY = buffer.getShort(COMPACT_OFFSET_STICK_Y).toInt(),
                motionProvider = 0,
                motionCapabilityFlags = 0,
                yaw = aimX,
                pitch = aimY,
                roll = 0f,
                rawAimX = Float.NaN,
                rawAimY = Float.NaN,
                sourceSensorElapsedNanos = buffer.getLong(COMPACT_OFFSET_CAPTURE_ELAPSED_NANOS),
                streamFlags = streamFlags,
                productAimX = aimX,
                productAimY = aimY,
                rawRoll = 0f,
            )
        }.getOrElse { error ->
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.MALFORMED_FIELD, error.message)
        }
        return UdpInputFrameDecodeResult.Accepted(frame)
    }

    fun authenticateAndDecode(bytes: ByteArray, config: InputStreamConfig): UdpInputFrameDecodeResult {
        if (bytes.size != FRAME_SIZE) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.INVALID_LENGTH, "size=${bytes.size}")
        }
        if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.BAD_MAGIC)
        }
        if ((bytes[4].toInt() and 0xff) != VERSION) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.UNSUPPORTED_VERSION)
        }
        val type = UdpInputFrameType.fromWireValue(bytes[5].toInt() and 0xff)
            ?: return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.UNKNOWN_TYPE)
        val streamSessionId = bytes.copyOfRange(8, OFFSET_SEQUENCE).toHex()
        if (streamSessionId != config.streamSessionIdHex) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.WRONG_STREAM_SESSION)
        }
        val expectedTag = hmac(bytes.copyOfRange(0, OFFSET_HMAC_TAG), config.hmacKeyBytes())
        val actualTag = bytes.copyOfRange(OFFSET_HMAC_TAG, FRAME_SIZE)
        if (!MessageDigest.isEqual(expectedTag, actualTag)) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.BAD_HMAC)
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val streamFlags = buffer.getShort(OFFSET_STREAM_FLAGS).toInt() and 0xffff
        if (streamFlags and UdpInputFrame.KNOWN_STREAM_FLAGS != streamFlags) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.MALFORMED_FIELD, "stream flags")
        }
        if (buffer.getShort(OFFSET_RESERVED_MOTION).toInt() != 0) {
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.MALFORMED_FIELD, "reserved motion")
        }
        val frame = runCatching {
            UdpInputFrame(
                type = type,
                streamSessionId = streamSessionId,
                sequence = buffer.getLong(OFFSET_SEQUENCE),
                captureElapsedNanos = buffer.getLong(OFFSET_CAPTURE_ELAPSED_NANOS),
                sendElapsedNanos = buffer.getLong(OFFSET_SEND_ELAPSED_NANOS),
                buttonBitmask = buffer.getInt(OFFSET_BUTTON_BITMASK),
                stickX = buffer.getShort(OFFSET_STICK_X).toInt(),
                stickY = buffer.getShort(OFFSET_STICK_Y).toInt(),
                motionProvider = bytes[OFFSET_MOTION_PROVIDER].toInt() and 0xff,
                motionCapabilityFlags = bytes[OFFSET_MOTION_CAPABILITY_FLAGS].toInt() and 0xff,
                yaw = buffer.getFloat(OFFSET_YAW),
                pitch = buffer.getFloat(OFFSET_PITCH),
                roll = buffer.getFloat(OFFSET_ROLL),
                rawAimX = buffer.getFloat(OFFSET_RAW_AIM_X),
                rawAimY = buffer.getFloat(OFFSET_RAW_AIM_Y),
                sourceSensorElapsedNanos = buffer.getLong(OFFSET_SOURCE_SENSOR_ELAPSED_NANOS),
                streamFlags = streamFlags,
                productAimX = buffer.getFloat(OFFSET_YAW),
                productAimY = buffer.getFloat(OFFSET_PITCH),
                rawRoll = buffer.getFloat(OFFSET_ROLL),
            )
        }.getOrElse { error ->
            return UdpInputFrameDecodeResult.Rejected(UdpInputFrameRejectReason.MALFORMED_FIELD, error.message)
        }
        return UdpInputFrameDecodeResult.Accepted(frame)
    }

    fun debugDecode(bytes: ByteArray, config: InputStreamConfig): UdpInputFrameDebugSummary =
        when (val decoded = authenticateAndDecodeMux(bytes, config)) {
            is UdpInputFrameDecodeResult.Rejected -> UdpInputFrameDebugSummary(
                accepted = false,
                reason = decoded.reason,
            )
            is UdpInputFrameDecodeResult.Accepted -> decoded.frame.toDebugSummary()
        }

    private fun UdpInputFrame.toDebugSummary(): UdpInputFrameDebugSummary =
        UdpInputFrameDebugSummary(
            accepted = true,
            frameType = type.name,
            sequence = sequence,
            captureElapsedNanos = captureElapsedNanos,
            sendElapsedNanos = sendElapsedNanos,
            buttonBitmask = buttonBitmask,
            stickX = stickX,
            stickY = stickY,
            motionProvider = motionProvider,
            motionCapabilityFlags = motionCapabilityFlags,
            streamFlags = streamFlags,
            productAimX = productAimX,
            productAimY = productAimY,
            rawRoll = rawRoll,
            hasRawAim = !rawAimX.isNaN() || !rawAimY.isNaN(),
            sourceSensorElapsedNanos = sourceSensorElapsedNanos,
        )

    private fun hmac(input: ByteArray, secret: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(input)
    }

    private fun frameFormatFor(bytes: ByteArray): InputFrameFormat? =
        when {
            bytes.size == FRAME_SIZE && bytes.copyOfRange(0, 4).contentEquals(MAGIC.toByteArray(Charsets.US_ASCII)) ->
                InputFrameFormat.V1
            bytes.size == COMPACT_FRAME_SIZE && bytes.copyOfRange(0, 4).contentEquals(COMPACT_MAGIC.toByteArray(Charsets.US_ASCII)) ->
                InputFrameFormat.COMPACT_V2
            else -> null
        }
}

private fun Float.toInt16Axis(): Int =
    ((if (isFinite()) coerceIn(-1f, 1f) else 0f) * Short.MAX_VALUE).toInt()

private fun Int.fromInt16Axis(): Float =
    if (this == Short.MIN_VALUE.toInt()) -1f else (this.toFloat() / Short.MAX_VALUE.toFloat()).coerceIn(-1f, 1f)

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
