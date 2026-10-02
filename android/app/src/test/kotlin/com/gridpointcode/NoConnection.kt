package com.gridpointcode

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * No connection, for the tests that run the app's own code on this computer:
 * every request goes to a proxy here that turns it away at once, so nothing the
 * site serves today can change an answer, and nothing waits on the network.
 * Turned away rather than sent to a closed port, which Windows takes two seconds
 * to refuse, each time.
 */
object NoConnection {

    fun turnAwayEveryRequest() {
        val port = TurnedAway.port
        for (scheme in listOf("http", "https")) {
            System.setProperty("$scheme.proxyHost", "127.0.0.1")
            System.setProperty("$scheme.proxyPort", port.toString())
        }
        System.setProperty("http.nonProxyHosts", "")
    }

    /** A proxy, reachable only from this computer, that answers everything with 503. */
    private object TurnedAway {
        val port: Int by lazy {
            val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            thread(isDaemon = true, name = "turned-away") {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching {
                        socket.use { it.getOutputStream().write(UNAVAILABLE) }
                    }
                }
            }
            server.localPort
        }

        private val UNAVAILABLE =
            "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
    }
}
