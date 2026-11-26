package com.guestexperience.shared.sync

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * One conversation with a TV: the phone sends [WireMessage]s and reads the replies. The protocol in
 * [SyncProtocol] is the same for every TV; only the transport differs (a TCP socket to the Android TV
 * app, or the Samsung multiscreen channel to the Samsung TV app). Methods block and throw
 * [java.io.IOException] when the TV can't be reached or the link breaks.
 */
interface SyncConnection : Closeable {
    fun send(message: WireMessage)
    fun receive(): WireMessage
}

/** Opens a new [SyncConnection] to one TV. */
fun interface SyncDialer {
    fun dial(): SyncConnection
}

/** Length-prefixed JSON frames over a TCP socket: the Android TV app's [TvSyncServer]. */
class TcpDialer(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 5_000,
) : SyncDialer {
    override fun dial(): SyncConnection {
        val socket = Socket()
        try {
            socket.soTimeout = SyncProtocol.SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        return object : SyncConnection {
            override fun send(message: WireMessage) = FrameIO.write(output, message)
            override fun receive(): WireMessage = FrameIO.read(input)
            override fun close() = socket.close()
        }
    }
}
