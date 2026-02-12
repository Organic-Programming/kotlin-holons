package org.organicprogramming.holons

import java.net.InetSocketAddress
import java.net.ServerSocket

/** URI-based listener factory for gRPC servers. */
object Transport {
    const val DEFAULT_URI = "tcp://:9090"

    fun scheme(uri: String): String {
        val idx = uri.indexOf("://")
        return if (idx >= 0) uri.substring(0, idx) else uri
    }

    fun listen(uri: String): ServerSocket = when {
        uri.startsWith("tcp://") -> listenTcp(uri.removePrefix("tcp://"))
        else -> throw IllegalArgumentException("unsupported transport URI: $uri")
    }

    private fun listenTcp(addr: String): ServerSocket {
        val lastColon = addr.lastIndexOf(':')
        val host = if (lastColon > 0) addr.substring(0, lastColon) else "0.0.0.0"
        val port = addr.substring(lastColon + 1).toInt()
        return ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(host, port))
        }
    }
}
