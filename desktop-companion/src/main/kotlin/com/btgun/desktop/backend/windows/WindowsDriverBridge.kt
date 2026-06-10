package com.btgun.desktop.backend.windows

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class WindowsDriverBridgeConfig(
    val command: List<String> = listOf("DriverBridge.exe"),
    val workingDirectory: File? = null,
) {
    init {
        require(command.isNotEmpty()) { "command must not be empty" }
        require(command.all { it.isNotBlank() }) { "command entries must be nonblank" }
    }
}

data class WindowsDriverBridgeStatus(
    val driverStarted: Boolean = false,
    val vhfStarted: Boolean = false,
    val queueDepth: Int = 0,
    val lastInputSequence: Long = 0L,
    val lastOutputSequence: Long = 0L,
    val submittedInputReports: Long = 0L,
    val queuedOutputReports: Long = 0L,
    val droppedOutputReports: Long = 0L,
    val malformedInputReports: Long = 0L,
    val malformedOutputReports: Long = 0L,
    val lastNtStatus: Int = 0,
)

sealed interface WindowsDriverBridgeResult {
    data object Ok : WindowsDriverBridgeResult
    data class Error(val detail: String) : WindowsDriverBridgeResult {
        init {
            require(detail.isNotBlank()) { "detail must be nonblank" }
        }
    }
}

interface WindowsDriverBridgeClient : AutoCloseable {
    fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult
    fun readOutputReport(): ByteArray?
    fun readStatus(): WindowsDriverBridgeStatus
    override fun close()
}

class WindowsDriverBridge(
    private val config: WindowsDriverBridgeConfig = WindowsDriverBridgeConfig(),
) : WindowsDriverBridgeClient {
    private val lock = Any()
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var stdout: BufferedReader? = null

    override fun submitInputReport(report: WindowsInputReport): WindowsDriverBridgeResult =
        synchronized(lock) {
            runCatching {
                writeCommand("SUBMIT_INPUT ${report.bytes.toHex()}")
                when (val response = readResponse()) {
                    "OK" -> WindowsDriverBridgeResult.Ok
                    null -> WindowsDriverBridgeResult.Error("driver bridge closed")
                    else -> parseError(response) ?: WindowsDriverBridgeResult.Error("unexpected driver bridge response")
                }
            }.getOrElse {
                WindowsDriverBridgeResult.Error("driver bridge unavailable")
            }
        }

    override fun readOutputReport(): ByteArray? =
        synchronized(lock) {
            runCatching {
                writeCommand("READ_OUTPUT")
                val response = readResponse() ?: return@synchronized null
                if (response == "OK") return@synchronized null
                if (!response.startsWith("OUTPUT ")) return@synchronized null
                response.removePrefix("OUTPUT ")
                    .hexToBytes()
                    ?.takeIf { it.size == WINDOWS_OUTPUT_REPORT_LENGTH_BYTES }
            }.getOrNull()
        }

    override fun readStatus(): WindowsDriverBridgeStatus =
        synchronized(lock) {
            runCatching {
                writeCommand("STATUS")
                val response = readResponse() ?: return@synchronized WindowsDriverBridgeStatus()
                parseStatus(response) ?: WindowsDriverBridgeStatus()
            }.getOrDefault(WindowsDriverBridgeStatus())
        }

    override fun close() {
        synchronized(lock) {
            runCatching {
                if (process?.isAlive == true) {
                    stdin?.write("QUIT")
                    stdin?.newLine()
                    stdin?.flush()
                }
            }
            runCatching { stdin?.close() }
            runCatching { stdout?.close() }
            runCatching { process?.destroy() }
            stdin = null
            stdout = null
            process = null
        }
    }

    private fun writeCommand(command: String) {
        ensureProcess()
        val writer = requireNotNull(stdin) { "driver bridge stdin unavailable" }
        writer.write(command)
        writer.newLine()
        writer.flush()
    }

    private fun readResponse(): String? {
        ensureProcess()
        return stdout?.readLine()?.trim()
    }

    private fun ensureProcess() {
        if (process?.isAlive == true && stdin != null && stdout != null) return
        close()
        val builder = ProcessBuilder(config.command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        config.workingDirectory?.let(builder::directory)
        val started = builder.start()
        process = started
        stdin = BufferedWriter(OutputStreamWriter(started.outputStream, Charsets.UTF_8))
        stdout = BufferedReader(InputStreamReader(started.inputStream, Charsets.UTF_8))
    }

    private fun parseError(response: String): WindowsDriverBridgeResult.Error? {
        if (!response.startsWith("ERR ")) return null
        val token = response.removePrefix("ERR ").trim()
        if (!token.matches(ERROR_TOKEN)) return WindowsDriverBridgeResult.Error("driver bridge returned ERR")
        return WindowsDriverBridgeResult.Error("driver bridge returned ERR $token")
    }

    private fun parseStatus(response: String): WindowsDriverBridgeStatus? {
        if (!response.startsWith("STATUS ")) return null
        val body = response.removePrefix("STATUS ")
        val json = Json.parseToJsonElement(body).jsonObject
        return WindowsDriverBridgeStatus(
            driverStarted = json.booleanField("driverStarted"),
            vhfStarted = json.booleanField("vhfStarted"),
            queueDepth = json.intField("queueDepth"),
            lastInputSequence = json.longField("lastInputSequence"),
            lastOutputSequence = json.longField("lastOutputSequence"),
            submittedInputReports = json.longField("submittedInputReports"),
            queuedOutputReports = json.longField("queuedOutputReports"),
            droppedOutputReports = json.longField("droppedOutputReports"),
            malformedInputReports = json.longField("malformedInputReports"),
            malformedOutputReports = json.longField("malformedOutputReports"),
            lastNtStatus = json.intField("lastNtStatus"),
        )
    }

    private fun Map<String, kotlinx.serialization.json.JsonElement>.booleanField(name: String): Boolean =
        get(name)?.jsonPrimitive?.booleanOrNull ?: false

    private fun Map<String, kotlinx.serialization.json.JsonElement>.intField(name: String): Int =
        get(name)?.jsonPrimitive?.intOrNull ?: 0

    private fun Map<String, kotlinx.serialization.json.JsonElement>.longField(name: String): Long =
        get(name)?.jsonPrimitive?.longOrNull ?: 0L

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun String.hexToBytes(): ByteArray? {
        val compact = filterNot(Char::isWhitespace)
        if (compact.length % 2 != 0) return null
        val bytes = ByteArray(compact.length / 2)
        for (index in bytes.indices) {
            val hi = compact[index * 2].hexValue() ?: return null
            val lo = compact[index * 2 + 1].hexValue() ?: return null
            bytes[index] = ((hi shl 4) or lo).toByte()
        }
        return bytes
    }

    private fun Char.hexValue(): Int? =
        when (this) {
            in '0'..'9' -> this - '0'
            in 'a'..'f' -> 10 + (this - 'a')
            in 'A'..'F' -> 10 + (this - 'A')
            else -> null
        }

    companion object {
        private val ERROR_TOKEN = Regex("""[A-Za-z0-9_.:-]+""")
    }
}
