package com.example.carscanner

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class Elm327WifiClient(
    private val host: String = "192.168.0.10",
    private val port: Int = 35000
) {
    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var reader: BufferedReader? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    @Throws(Exception::class)
    fun connect() {
        socket = Socket().apply {
            soTimeout = 5000
            connect(InetSocketAddress(host, port), 5000)
        }
        output = socket?.outputStream
        reader = BufferedReader(InputStreamReader(socket?.inputStream))
        isConnected = true
        initAdapter()
    }

    private fun initAdapter() {
        sendCommand("ATZ")
        sendCommand("ATE0")
        sendCommand("ATL0")
        sendCommand("ATS0")
        sendCommand("ATH0")
        sendCommand("ATSP0")
    }

    @Synchronized
    @Throws(Exception::class)
    fun sendCommand(command: String): String {
        val out = output ?: throw IllegalStateException("Not connected")
        out.write((command + "\r").toByteArray())
        out.flush()
        return readResponse()
    }

    private fun readResponse(): String {
        val sb = StringBuilder()
        val r = reader ?: return ""
        var c: Int
        while (true) {
            c = r.read()
            if (c == -1) break
            val ch = c.toChar()
            if (ch == '>') break
            sb.append(ch)
        }
        return sb.toString()
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    fun disconnect() {
        try {
            output?.close()
            reader?.close()
            socket?.close()
        } catch (_: Exception) {
        } finally {
            isConnected = false
        }
    }
}

object ObdParser {

    fun parseRpm(response: String): Int? {
        val bytes = extractDataBytes(response, "41 0C") ?: return null
        if (bytes.size < 2) return null
        return ((bytes[0] * 256) + bytes[1]) / 4
    }

    fun parseSpeed(response: String): Int? {
        val bytes = extractDataBytes(response, "41 0D") ?: return null
        if (bytes.isEmpty()) return null
        return bytes[0]
    }

    fun parseCoolantTemp(response: String): Int? {
        val bytes = extractDataBytes(response, "41 05") ?: return null
        if (bytes.isEmpty()) return null
        return bytes[0] - 40
    }

    fun parseThrottlePosition(response: String): Int? {
        val bytes = extractDataBytes(response, "41 11") ?: return null
        if (bytes.isEmpty()) return null
        return (bytes[0] * 100) / 255
    }

    fun parseEngineLoad(response: String): Int? {
        val bytes = extractDataBytes(response, "41 04") ?: return null
        if (bytes.isEmpty()) return null
        return (bytes[0] * 100) / 255
    }

    fun parseFuelLevel(response: String): Int? {
        val bytes = extractDataBytes(response, "41 2F") ?: return null
        if (bytes.isEmpty()) return null
        return (bytes[0] * 100) / 255
    }

    fun parseDtcCodes(response: String): List<String> {
        val clean = response.replace("SEARCHING...", "").trim()
        if (clean.contains("NO DATA") || clean.isBlank()) return emptyList()

        val tokens = clean.split(" ").filter { it.matches(Regex("[0-9A-Fa-f]{2}")) }
        if (tokens.isEmpty() || tokens[0].uppercase() != "43") return emptyList()

        val dataBytes = tokens.drop(1)
        val codes = mutableListOf<String>()
        var i = 0
        while (i + 1 < dataBytes.size) {
            val b1 = dataBytes[i].toInt(16)
            val b2 = dataBytes[i + 1].toInt(16)
            if (b1 == 0 && b2 == 0) { i += 2; continue }
            val code = decodeDtc(b1, b2)
            codes.add(code)
            i += 2
        }
        return codes
    }

    private fun decodeDtc(b1: Int, b2: Int): String {
        val prefixIndex = (b1 and 0xC0) shr 6
        val prefix = when (prefixIndex) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            else -> "U"
        }
        val digit1 = (b1 and 0x30) shr 4
        val digit2 = b1 and 0x0F
        val digit3 = (b2 and 0xF0) shr 4
        val digit4 = b2 and 0x0F
        return "$prefix$digit1$digit2${"%X".format(digit3)}${"%X".format(digit4)}"
    }

    private fun extractDataBytes(response: String, expectedPrefix: String): List<Int>? {
        val clean = response.uppercase().trim()
        if (!clean.contains(expectedPrefix)) return null
        val idx = clean.indexOf(expectedPrefix)
        val after = clean.substring(idx + expectedPrefix.length).trim()
        val tokens = after.split(" ").filter { it.matches(Regex("[0-9A-F]{2}")) }
        if (tokens.isEmpty()) return null
        return tokens.map { it.toInt(16) }
    }
}
