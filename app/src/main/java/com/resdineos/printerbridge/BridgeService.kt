package com.resdineos.printerbridge

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Build
import android.os.IBinder
import fi.iki.elonen.NanoHTTPD
import java.io.OutputStream
import java.util.UUID

/**
 * Foreground service that keeps a Bluetooth Classic (SPP) connection to the
 * printer alive and serves a tiny HTTP endpoint on 127.0.0.1:9100 that ResDineOS
 * posts ESC/POS bytes to.
 */
class BridgeService : Service() {

    companion object {
        const val PORT = 9100
        const val EXTRA_MAC = "mac"
        const val EXTRA_NAME = "name"
        const val PREFS = "bridge"
        const val KEY_MAC = "mac"
        const val KEY_NAME = "name"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHANNEL = "bridge"
        private const val NOTIF_ID = 1

        // Simple shared state so the Activity can show status.
        @Volatile var connected = false
        @Volatile var printerName: String? = null
        @Volatile var running = false
        @Volatile var lastError: String? = null
    }

    private var mac: String? = null
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var server: Server? = null
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started with a chosen printer (from the app) → use it and remember it.
        // Started with no extras (from boot, or auto-start on app open) → reload
        // the last chosen printer from storage so it reconnects on its own.
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val intentMac = intent?.getStringExtra(EXTRA_MAC)
        if (intentMac != null) {
            mac = intentMac
            printerName = intent.getStringExtra(EXTRA_NAME)
            prefs.edit().putString(KEY_MAC, mac).putString(KEY_NAME, printerName).apply()
        } else {
            mac = prefs.getString(KEY_MAC, null)
            printerName = prefs.getString(KEY_NAME, null)
        }

        running = true
        startForeground(NOTIF_ID, buildNotification())

        // No printer chosen yet: satisfy the foreground-start contract, then stop.
        if (mac == null) { running = false; stopSelf(); return START_NOT_STICKY }

        if (server == null) {
            server = Server().also { try { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) } catch (e: Exception) { lastError = "server: ${e.message}" } }
        }
        if (worker == null) {
            worker = Thread { connectLoop() }.also { it.isDaemon = true; it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        connected = false
        try { server?.stop() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        server = null; socket = null; out = null; worker = null
        super.onDestroy()
    }

    // Keep trying to (re)connect to the printer forever while the service runs.
    @SuppressLint("MissingPermission")
    private fun connectLoop() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        while (running) {
            if (!connected) {
                try {
                    val dev = adapter.getRemoteDevice(mac)
                    // Optional: free the radio before connecting. Needs BLUETOOTH_SCAN
                    // on Android 12+; if that permission is missing it throws a
                    // SecurityException — swallow it so it never blocks the connect
                    // below (the actual RFCOMM connect only needs BLUETOOTH_CONNECT).
                    try { adapter.cancelDiscovery() } catch (_: Exception) {}
                    val s = dev.createRfcommSocketToServiceRecord(SPP_UUID)
                    s.connect()
                    socket = s
                    out = s.outputStream
                    connected = true
                    lastError = null
                    notifyUpdate()
                } catch (e: Exception) {
                    connected = false
                    lastError = e.message
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null; out = null
                    notifyUpdate()
                    Thread.sleep(2500)
                }
            }
            Thread.sleep(1000)
        }
    }

    @Synchronized
    private fun writeToPrinter(bytes: ByteArray): Boolean {
        val o = out ?: return false
        return try {
            o.write(bytes)
            o.flush()
            true
        } catch (e: Exception) {
            connected = false
            lastError = e.message
            try { socket?.close() } catch (_: Exception) {}
            socket = null; out = null
            notifyUpdate()
            false
        }
    }

    // ── Local HTTP server ───────────────────────────────────────────────────
    private inner class Server : NanoHTTPD("127.0.0.1", PORT) {
        override fun serve(session: IHTTPSession): Response {
            if (session.method == Method.OPTIONS) return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""))

            if (session.method == Method.GET && session.uri == "/status") {
                val json = "{\"connected\":$connected,\"printer\":\"${(printerName ?: "").replace("\"", "")}\"}"
                return cors(newFixedLengthResponse(Response.Status.OK, "application/json", json))
            }

            if (session.method == Method.POST && session.uri == "/print") {
                val len = session.headers["content-length"]?.toIntOrNull() ?: 0
                val buf = ByteArray(len)
                var read = 0
                while (read < len) {
                    val r = session.inputStream.read(buf, read, len - read)
                    if (r < 0) break
                    read += r
                }
                val ok = writeToPrinter(buf.copyOf(read))
                val status = if (ok) Response.Status.OK else Response.Status.INTERNAL_ERROR
                val body = if (ok) "{\"ok\":true}" else "{\"ok\":false,\"error\":\"printer not connected\"}"
                return cors(newFixedLengthResponse(status, "application/json", body))
            }
            return cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"not found\"}"))
        }

        private fun cors(r: Response): Response {
            r.addHeader("Access-Control-Allow-Origin", "*")
            r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            r.addHeader("Access-Control-Allow-Headers", "Content-Type")
            r.addHeader("Access-Control-Allow-Private-Network", "true")
            return r
        }
    }

    // ── Notification (foreground service requirement) ───────────────────────
    private fun notifyUpdate() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Printer Bridge", NotificationManager.IMPORTANCE_LOW))
        }
        val text = if (connected) "Connected to ${printerName ?: "printer"} · serving :$PORT" else "Connecting to ${printerName ?: "printer"}…"
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("ResDineOS Printer Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }
}
