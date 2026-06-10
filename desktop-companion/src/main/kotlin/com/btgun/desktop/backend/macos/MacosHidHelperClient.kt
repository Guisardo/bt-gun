package com.btgun.desktop.backend.macos

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

data class MacosHidHelperConfig(
    val command: List<String> = listOf("native/macos-hid-helper/.build/debug/BtGunMacosHidHelper"),
    val workingDirectory: File? = null,
) {
    init {
        require(command.isNotEmpty()) { "command must not be empty" }
        require(command.all { it.isNotBlank() }) { "command entries must be nonblank" }
    }
}

data class MacosHidHelperStatus(
    val version: Int = 1,
    val deviceActive: Boolean = false,
    val osVisible: Boolean = false,
    val setReportCallbackSeen: Boolean = false,
    val inputReportsSubmitted: Long = 0L,
    val outputReportsQueued: Long = 0L,
    val malformedInputReports: Long = 0L,
    val malformedOutputReports: Long = 0L,
)

sealed interface MacosHidHelperResult {
    data object Ok : MacosHidHelperResult
    data class Error(val detail: String) : MacosHidHelperResult {
        init {
            require(detail.isNotBlank()) { "detail must be nonblank" }
        }
    }
}

interface MacosHidHelperClient : AutoCloseable {
    fun submitInputReport(report: MacosInputReport): MacosHidHelperResult
    fun readOutputReport(): ByteArray?
    fun readStatus(): MacosHidHelperStatus
    override fun close()
}

class MacosHidHelper(
    private val config: MacosHidHelperConfig = MacosHidHelperConfig(),
) : MacosHidHelperClient {
    private val lock = Any()
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var stdout: BufferedReader? = null

    override fun submitInputReport(report: MacosInputReport): MacosHidHelperResult =
        synchronized(lock) {
            runCatching {
                writeCommand("SUBMIT_INPUT ${report.bytes.toHex()}")
                when (val response = readResponse()) {
                    "OK" -> MacosHidHelperResult.Ok
                    null -> MacosHidHelperResult.Error("macos helper closed")
                    else -> parseError(response) ?: MacosHidHelperResult.Error("unexpected macos helper response")
                }
            }.getOrElse {
                MacosHidHelperResult.Error("macos helper unavailable")
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
                    ?.takeIf { it.size == MACOS_OUTPUT_REPORT_LENGTH_BYTES }
            }.getOrNull()
        }

    override fun readStatus(): MacosHidHelperStatus =
        synchronized(lock) {
            runCatching {
                writeCommand("STATUS")
                val response = readResponse() ?: return@synchronized MacosHidHelperStatus()
                parseStatus(response) ?: MacosHidHelperStatus()
            }.getOrDefault(MacosHidHelperStatus())
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
        val writer = requireNotNull(stdin) { "macos helper stdin unavailable" }
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
        val writer = requireNotNull(stdin)
        writer.write("HELLO 1")
        writer.newLine()
        writer.flush()
        val response = stdout?.readLine()?.trim()
        if (response != "OK") {
            close()
            error("macos helper handshake failed")
        }
    }

    private fun parseError(response: String): MacosHidHelperResult.Error? {
        if (!response.startsWith("ERR ")) return null
        val token = response.removePrefix("ERR ").trim()
        if (!token.matches(ERROR_TOKEN)) return MacosHidHelperResult.Error("macos helper returned ERR")
        return MacosHidHelperResult.Error("macos helper returned ERR $token")
    }

    private fun parseStatus(response: String): MacosHidHelperStatus? {
        if (!response.startsWith("STATUS ")) return null
        val body = response.removePrefix("STATUS ")
        val json = Json.parseToJsonElement(body).jsonObject
        val version = json.intField("version")
        if (version != 1) return null
        return MacosHidHelperStatus(
            version = version,
            deviceActive = json.booleanField("deviceActive"),
            osVisible = json.booleanField("osVisible"),
            setReportCallbackSeen = json.booleanField("setReportCallbackSeen"),
            inputReportsSubmitted = json.longField("inputReportsSubmitted"),
            outputReportsQueued = json.longField("outputReportsQueued"),
            malformedInputReports = json.longField("malformedInputReports"),
            malformedOutputReports = json.longField("malformedOutputReports"),
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
