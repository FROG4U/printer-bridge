package com.resdineos.printerbridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var spinner: Spinner
    private var macs: List<Pair<String, String>> = emptyList() // name to mac
    private val ui = Handler(Looper.getMainLooper())

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        loadPairedDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        root.addView(TextView(this).apply { text = "ResDineOS Printer Bridge"; textSize = 22f })
        root.addView(TextView(this).apply { text = "Keeps the receipt printer connected for ResDineOS."; setPadding(0, 8, 0, 32) })

        root.addView(TextView(this).apply { text = "Paired printer"; textSize = 13f })
        spinner = Spinner(this)
        root.addView(spinner)

        val start = Button(this).apply { text = "Start bridge" }
        val stop = Button(this).apply { text = "Stop bridge" }
        val test = Button(this).apply { text = "Test print" }
        root.addView(start); root.addView(stop); root.addView(test)

        status = TextView(this).apply { setPadding(0, 32, 0, 0); gravity = Gravity.START }
        root.addView(status)
        setContentView(root)

        start.setOnClickListener { startBridge() }
        stop.setOnClickListener { stopBridge() }
        test.setOnClickListener { testPrint() }

        ensurePermissions()
        pollStatus()
        autoStartIfConfigured()
    }

    // If a printer was already chosen on a previous run, start the bridge the
    // moment the app opens so it reconnects with no taps. The service reloads the
    // saved printer from storage itself (no extras needed).
    private fun autoStartIfConfigured() {
        val prefs = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
        if (!BridgeService.running && prefs.getString(BridgeService.KEY_MAC, null) != null) {
            ContextCompat.startForegroundService(this, Intent(this, BridgeService::class.java))
        }
    }

    private fun ensurePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            // Needed so the service's cancelDiscovery() is allowed; without it the
            // connect loop throws before it can reach the printer.
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray()) else loadPairedDevices()
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) { status.text = "Turn Bluetooth on, then reopen this app."; return }
        macs = try { adapter.bondedDevices.map { (it.name ?: it.address) to it.address } } catch (e: SecurityException) { emptyList() }
        val labels = if (macs.isEmpty()) listOf("No paired printers — pair it in Bluetooth settings first") else macs.map { it.first }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun startBridge() {
        if (macs.isEmpty()) { status.text = "Pair the printer in Bluetooth settings first."; return }
        val (name, mac) = macs[spinner.selectedItemPosition.coerceIn(0, macs.size - 1)]
        val i = Intent(this, BridgeService::class.java).putExtra(BridgeService.EXTRA_MAC, mac).putExtra(BridgeService.EXTRA_NAME, name)
        ContextCompat.startForegroundService(this, i)
    }

    private fun stopBridge() {
        stopService(Intent(this, BridgeService::class.java))
    }

    // Send a small ESC/POS test slip through the local bridge endpoint.
    private fun testPrint() {
        thread {
            try {
                val esc = buildTestSlip()
                val conn = URL("http://127.0.0.1:${BridgeService.PORT}/print").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 3000
                conn.readTimeout = 8000
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                // Pin the body length so HttpURLConnection never falls back to
                // chunked encoding — the bridge reads Content-Length bytes exactly,
                // and a missing length would make it print nothing.
                conn.setFixedLengthStreamingMode(esc.size)
                conn.outputStream.use { (it as OutputStream).write(esc) }
                val code = conn.responseCode
                val body = try {
                    (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.readText() ?: ""
                } catch (_: Exception) { "" }
                ui.post {
                    if (code == 200 && !body.contains("\"ok\":false")) {
                        toast("Test slip sent ✔  Check the printer.")
                    } else {
                        toast("Test failed (HTTP $code). Is the printer on and connected? $body")
                    }
                }
            } catch (e: Exception) {
                ui.post { toast("Test failed: ${e.message}. Start the bridge first.") }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun buildTestSlip(): ByteArray {
        val out = ArrayList<Byte>()
        fun add(vararg b: Int) = b.forEach { out.add(it.toByte()) }
        fun text(s: String) = s.toByteArray(Charsets.US_ASCII).forEach { out.add(it) }
        add(0x1B, 0x40)                 // init
        add(0x1B, 0x61, 0x01)           // center
        text("ResDineOS Printer Bridge\n")
        text("Test print OK\n")
        add(0x1B, 0x61, 0x00)           // left
        text("If you can read this, printing\nover the bridge works.\n")
        add(0x0A, 0x0A, 0x0A)           // feed
        add(0x1D, 0x56, 0x00)           // full cut
        return out.toByteArray()
    }

    private fun pollStatus() {
        ui.postDelayed(object : Runnable {
            override fun run() {
                if (BridgeService.running) {
                    if (BridgeService.connected) {
                        status.setTextColor(Color.parseColor("#158A3A"))  // green
                        status.setTypeface(null, Typeface.BOLD)
                        status.textSize = 17f
                        status.text = "✔ Connected to ${BridgeService.printerName}\nReady to print · serving 127.0.0.1:${BridgeService.PORT}"
                    } else {
                        status.setTextColor(Color.parseColor("#B26A00"))  // amber
                        status.setTypeface(null, Typeface.NORMAL)
                        status.textSize = 15f
                        status.text = "○ Connecting to ${BridgeService.printerName}…" + (BridgeService.lastError?.let { "\n($it)" } ?: "")
                    }
                }
                ui.postDelayed(this, 1500)
            }
        }, 1500)
    }
}
