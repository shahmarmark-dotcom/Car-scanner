package com.example.carscanner

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnLive: Button
    private lateinit var btnDtc: Button
    private lateinit var btnClearDtc: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvRpm: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvCoolant: TextView
    private lateinit var tvLoad: TextView
    private lateinit var tvThrottle: TextView
    private lateinit var tvFuel: TextView
    private lateinit var tvLog: TextView

    private var client: Elm327WifiClient? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var liveRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etIp = findViewById(R.id.etIp)
        etPort = findViewById(R.id.etPort)
        btnConnect = findViewById(R.id.btnConnect)
        btnLive = findViewById(R.id.btnLive)
        btnDtc = findViewById(R.id.btnDtc)
        btnClearDtc = findViewById(R.id.btnClearDtc)
        tvStatus = findViewById(R.id.tvStatus)
        tvRpm = findViewById(R.id.tvRpm)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvCoolant = findViewById(R.id.tvCoolant)
        tvLoad = findViewById(R.id.tvLoad)
        tvThrottle = findViewById(R.id.tvThrottle)
        tvFuel = findViewById(R.id.tvFuel)
        tvLog = findViewById(R.id.tvLog)

        btnConnect.setOnClickListener { connect() }
        btnLive.setOnClickListener { toggleLive() }
        btnDtc.setOnClickListener { readDtc() }
        btnClearDtc.setOnClickListener { clearDtc() }
    }

    private fun log(msg: String) {
        mainHandler.post {
            tvLog.append("$msg\n")
        }
    }

    private fun connect() {
        val ip = etIp.text.toString().trim()
        val port = etPort.text.toString().trim().toIntOrNull() ?: 35000

        tvStatus.text = "Status: Qoşulur..."
        executor.execute {
            try {
                val c = Elm327WifiClient(ip, port)
                c.connect()
                client = c
                mainHandler.post {
                    tvStatus.text = "Status: Qoşuldu ($ip:$port)"
                    Toast.makeText(this, "Adapterə qoşuldu", Toast.LENGTH_SHORT).show()
                }
                log("Qoşuldu: $ip:$port")
            } catch (e: Exception) {
                mainHandler.post {
                    tvStatus.text = "Status: Xəta"
                    Toast.makeText(this, "Qoşulma xətası: ${e.message}", Toast.LENGTH_LONG).show()
                }
                log("Qoşulma xətası: ${e.message}")
            }
        }
    }

    private fun toggleLive() {
        if (liveRunning) {
            liveRunning = false
            btnLive.text = "Canlı Oxumağa Başla"
            return
        }
        val c = client
        if (c == null || !c.isConnected) {
            Toast.makeText(this, "Əvvəlcə adapterə qoşulun", Toast.LENGTH_SHORT).show()
            return
        }
        liveRunning = true
        btnLive.text = "Canlı Oxumayı Dayandır"
        executor.execute { liveLoop() }
    }

    private fun liveLoop() {
        val c = client ?: return
        while (liveRunning && c.isConnected) {
            try {
                val rpmResp = c.sendCommand("01 0C")
                val speedResp = c.sendCommand("01 0D")
                val coolantResp = c.sendCommand("01 05")
                val loadResp = c.sendCommand("01 04")
                val throttleResp = c.sendCommand("01 11")
                val fuelResp = c.sendCommand("01 2F")

                val rpm = ObdParser.parseRpm(rpmResp)
                val speed = ObdParser.parseSpeed(speedResp)
                val coolant = ObdParser.parseCoolantTemp(coolantResp)
                val load = ObdParser.parseEngineLoad(loadResp)
                val throttle = ObdParser.parseThrottlePosition(throttleResp)
                val fuel = ObdParser.parseFuelLevel(fuelResp)

                mainHandler.post {
                    tvRpm.text = rpm?.toString() ?: "N/A"
                    tvSpeed.text = speed?.toString() ?: "N/A"
                    tvCoolant.text = coolant?.toString() ?: "N/A"
                    tvLoad.text = load?.toString() ?: "N/A"
                    tvThrottle.text = throttle?.toString() ?: "N/A"
                    tvFuel.text = fuel?.toString() ?: "N/A"
                }
                Thread.sleep(500)
            } catch (e: Exception) {
                log("Canlı oxuma xətası: ${e.message}")
                liveRunning = false
                mainHandler.post { btnLive.text = "Canlı Oxumağa Başla" }
                break
            }
        }
    }

    private fun readDtc() {
        val c = client
        if (c == null || !c.isConnected) {
            Toast.makeText(this, "Əvvəlcə adapterə qoşulun", Toast.LENGTH_SHORT).show()
            return
        }
        executor.execute {
            try {
                val resp = c.sendCommand("03")
                val codes = ObdParser.parseDtcCodes(resp)
                if (codes.isEmpty()) {
                    log("Xəta kodu tapılmadı. Raw: $resp")
                } else {
                    log("Tapılan xəta kodları: ${codes.joinToString(", ")}")
                }
            } catch (e: Exception) {
                log("DTC oxuma xətası: ${e.message}")
            }
        }
    }

    private fun clearDtc() {
        val c = client
        if (c == null || !c.isConnected) {
            Toast.makeText(this, "Əvvəlcə adapterə qoşulun", Toast.LENGTH_SHORT).show()
            return
        }
        executor.execute {
            try {
                val resp = c.sendCommand("04")
                log("Xəta kodları silindi. Cavab: $resp")
            } catch (e: Exception) {
                log("Silmə xətası: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        liveRunning = false
        client?.disconnect()
        executor.shutdownNow()
    }
}
