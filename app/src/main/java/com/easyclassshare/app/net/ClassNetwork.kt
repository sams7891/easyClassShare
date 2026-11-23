package com.easyclassshare.app.net

import android.content.Context
import android.net.wifi.WifiManager
import com.easyclassshare.app.data.ShareMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Sends and receives [ShareMessage]s as UDP broadcasts on the local Wi-Fi.
 *
 * No server or internet is needed: every tablet on the same network that is
 * on the "Receive" screen hears the broadcast. Because UDP can drop packets,
 * each message is sent several times; receivers ignore duplicates by id.
 */
class ClassNetwork(context: Context) {
    private val appContext = context.applicationContext

    suspend fun send(message: ShareMessage) = withContext(Dispatchers.IO) {
        val bytes = message.toJson().toByteArray(Charsets.UTF_8)
        val targets = broadcastAddresses()
        var delivered = 0
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(REPEAT_COUNT) { attempt ->
                for (address in targets) {
                    try {
                        socket.send(DatagramPacket(bytes, bytes.size, address, PORT))
                        delivered++
                    } catch (e: IOException) {
                        // Some interfaces refuse broadcasts; try the others.
                    }
                }
                if (attempt < REPEAT_COUNT - 1) delay(REPEAT_DELAY_MS)
            }
        }
        if (delivered == 0) throw IOException("Could not broadcast on any network")
    }

    /** Emits every valid message heard on the network until the collector is cancelled. */
    fun incoming(): Flow<ShareMessage> = flow {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Many devices drop broadcast packets to save battery unless we hold this lock.
        val lock = wifi.createMulticastLock("easyClassShare").apply {
            setReferenceCounted(false)
            acquire()
        }
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = RECEIVE_TIMEOUT_MS
        }
        try {
            socket.bind(InetSocketAddress(PORT))
            val buffer = ByteArray(MAX_PACKET_BYTES)
            while (currentCoroutineContext().isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue // wake up regularly so cancellation is noticed
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                ShareMessage.fromJson(text)?.let { emit(it) }
            }
        } finally {
            socket.close()
            if (lock.isHeld) lock.release()
        }
    }.flowOn(Dispatchers.IO)

    /** Subnet broadcast address of every active interface (Wi-Fi, hotspot) plus the global one. */
    private fun broadcastAddresses(): List<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nic ->
                if (nic.isUp && !nic.isLoopback) {
                    nic.interfaceAddresses.forEach { addr -> addr.broadcast?.let(result::add) }
                }
            }
        } catch (e: Exception) {
            // Fall back to the global broadcast address below.
        }
        result.add(InetAddress.getByName("255.255.255.255"))
        return result.toList()
    }

    companion object {
        const val PORT = 47474
        private const val REPEAT_COUNT = 5
        private const val REPEAT_DELAY_MS = 250L
        private const val RECEIVE_TIMEOUT_MS = 1000
        private const val MAX_PACKET_BYTES = 16 * 1024
    }
}
