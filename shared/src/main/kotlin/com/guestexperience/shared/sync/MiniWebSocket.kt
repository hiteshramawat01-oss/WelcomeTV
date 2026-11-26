package com.guestexperience.shared.sync

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * A minimal RFC 6455 WebSocket client for text messages: plain `ws://`, no extensions.
 *
 * It exists because Samsung TVs' multiscreen server silently ignores clients that offer the
 * permessage-deflate extension (which stock clients such as OkHttp and browsers send), and because
 * keeping the shared module dependency-free lets it be tested on the desktop.
 */
class MiniWebSocket private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: OutputStream,
) : Closeable {

    /** Sends one text message. */
    @Synchronized
    fun sendText(text: String) = writeFrame(OP_TEXT, text.toByteArray(Charsets.UTF_8))

    /** Blocks for the next complete text message. Answers pings; throws [IOException] on close or timeout. */
    fun receiveText(): String {
        val message = ByteArrayOutputStream()
        var inMessage = false
        while (true) {
            val b0 = input.readUnsignedByte()
            val b1 = input.readUnsignedByte()
            val fin = b0 and 0x80 != 0
            val op = b0 and 0x0F
            if (b1 and 0x80 != 0) throw IOException("Server frames must not be masked")
            val length = when (val l = b1 and 0x7F) {
                126 -> input.readUnsignedShort().toLong()
                127 -> input.readLong()
                else -> l.toLong()
            }
            if (length < 0 || length + message.size() > MAX_MESSAGE_BYTES) throw IOException("Message too large")
            val payload = ByteArray(length.toInt()).also(input::readFully)
            when (op) {
                OP_TEXT, OP_CONTINUATION -> {
                    if (op == OP_TEXT) { message.reset(); inMessage = true }
                    if (!inMessage) throw IOException("Unexpected continuation frame")
                    message.write(payload)
                    if (fin) return message.toString(Charsets.UTF_8.name())
                }
                OP_BINARY -> Unit // not used by this app; ignored
                OP_PING -> synchronized(this) { writeFrame(OP_PONG, payload) }
                OP_PONG -> Unit
                OP_CLOSE -> throw IOException("Closed by the TV")
                else -> throw IOException("Unknown frame type $op")
            }
        }
    }

    override fun close() {
        runCatching { synchronized(this) { writeFrame(OP_CLOSE, ByteArray(0)) } }
        runCatching { socket.close() }
    }

    private fun writeFrame(op: Int, payload: ByteArray) {
        val mask = ByteArray(4).also(random::nextBytes)
        val header = ByteArrayOutputStream()
        header.write(0x80 or op)
        when {
            payload.size < 126 -> header.write(0x80 or payload.size)
            payload.size <= 0xFFFF -> { header.write(0x80 or 126); header.write(payload.size shr 8); header.write(payload.size and 0xFF) }
            else -> { header.write(0x80 or 127); for (shift in 56 downTo 0 step 8) header.write(((payload.size.toLong() shr shift) and 0xFF).toInt()) }
        }
        header.write(mask)
        val masked = ByteArray(payload.size) { i -> (payload[i].toInt() xor mask[i % 4].toInt()).toByte() }
        output.write(header.toByteArray())
        output.write(masked)
        output.flush()
    }

    companion object {
        const val MAX_MESSAGE_BYTES = SyncProtocol.MAX_FRAME_BYTES
        private const val OP_CONTINUATION = 0x0
        private const val OP_TEXT = 0x1
        private const val OP_BINARY = 0x2
        private const val OP_CLOSE = 0x8
        private const val OP_PING = 0x9
        private const val OP_PONG = 0xA
        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private val random = SecureRandom()

        /** Opens `ws://host:port/pathAndQuery`. Throws [IOException] if the TV refuses the upgrade. */
        fun connect(host: String, port: Int, pathAndQuery: String, connectTimeoutMs: Int, readTimeoutMs: Int): MiniWebSocket {
            val socket = Socket()
            try {
                socket.soTimeout = readTimeoutMs
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val output = socket.getOutputStream()
                val key = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))
                val request = "GET $pathAndQuery HTTP/1.1\r\n" +
                    "Host: $host:$port\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n"
                output.write(request.toByteArray(Charsets.US_ASCII))
                output.flush()

                val headers = readHeaders(input)
                val status = headers.firstOrNull().orEmpty()
                if (!status.startsWith("HTTP/1.1 101")) throw IOException("Upgrade refused: $status")
                val expected = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.US_ASCII)),
                )
                val accept = headers.drop(1).firstOrNull { it.startsWith("sec-websocket-accept:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()
                if (accept != expected) throw IOException("Bad Sec-WebSocket-Accept")
                return MiniWebSocket(socket, input, output)
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e as? IOException ?: IOException(e)
            }
        }

        private fun readHeaders(input: DataInputStream): List<String> {
            val lines = mutableListOf<String>()
            val line = StringBuilder()
            var total = 0
            while (true) {
                val c = input.readUnsignedByte()
                if (++total > 8_192) throw IOException("Handshake too long")
                if (c == '\n'.code) {
                    val text = line.toString().trimEnd('\r')
                    if (text.isEmpty()) return lines
                    lines += text
                    line.clear()
                } else {
                    line.append(c.toChar())
                }
            }
        }
    }
}
