package com.shareanything.app

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A minimal single-purpose HTTP server used to let nearby devices on the
 * same local Wi-Fi network download one specific file by opening a link
 * (or scanning the generated QR code) in their browser.
 *
 * This intentionally avoids any third-party server dependency: it speaks
 * just enough raw HTTP/1.1 to serve one GET request with the file bytes.
 */
class LocalShareServer(
    private val file: File,
    private val displayName: String
) {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    var port: Int = -1
        private set

    /** Starts the server on a background thread and returns the port it bound to. */
    fun start(): Int {
        val socket = ServerSocket(0) // 0 = let the OS pick a free port
        serverSocket = socket
        port = socket.localPort
        running.set(true)

        Thread {
            while (running.get()) {
                try {
                    val client = socket.accept()
                    handleClient(client)
                } catch (e: Exception) {
                    if (running.get()) {
                        // Swallow per-connection errors so the server keeps serving.
                    }
                }
            }
        }.apply { isDaemon = true }.start()

        return port
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
    }

    private fun handleClient(client: Socket) {
        client.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val requestLine = reader.readLine() ?: return
            // We don't care about the exact path or headers; any GET returns the file.
            val isGet = requestLine.startsWith("GET")

            val output: OutputStream = s.getOutputStream()
            if (!isGet) {
                writeStatus(output, 405, "Method Not Allowed")
                return
            }

            val mime = URLConnection.guessContentTypeFromName(displayName) ?: "application/octet-stream"
            val encodedName = URLEncoder.encode(displayName, "UTF-8").replace("+", "%20")

            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $mime\r\n")
                append("Content-Length: ${file.length()}\r\n")
                append("Content-Disposition: attachment; filename=\"$encodedName\"\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(header.toByteArray())

            file.inputStream().use { input ->
                input.copyTo(output)
            }
            output.flush()
        }
    }

    private fun writeStatus(output: OutputStream, code: Int, message: String) {
        output.write("HTTP/1.1 $code $message\r\nConnection: close\r\n\r\n".toByteArray())
        output.flush()
    }

    companion object {
        /** Returns the device's local Wi-Fi / LAN IPv4 address, or null if none is found. */
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    val addresses = Collections.list(intf.inetAddresses)
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                            return addr.hostAddress
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return null
        }
    }
}
