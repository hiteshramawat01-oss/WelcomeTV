package com.guestexperience.shared.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Length-prefixed JSON frames. */
object FrameIO {
    fun write(out: DataOutputStream, message: WireMessage) {
        val bytes = SyncProtocol.json.encodeToString(WireMessage.serializer(), message).toByteArray(Charsets.UTF_8)
        require(bytes.size <= SyncProtocol.MAX_FRAME_BYTES) { "Frame too large" }
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun read(input: DataInputStream): WireMessage {
        val length = input.readInt()
        if (length <= 0 || length > SyncProtocol.MAX_FRAME_BYTES) throw IOException("Bad frame length $length")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return try {
            SyncProtocol.json.decodeFromString(WireMessage.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IOException("Unreadable frame", e)
        }
    }
}
