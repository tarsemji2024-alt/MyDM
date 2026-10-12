package com.example.mydm

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.View
import android.widget.*
import java.io.File

class MainActivity : Activity() {

    private var downloader: Downloader? = null
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    private lateinit var urlBox: EditText
    private lateinit var startBtn: Button
    private lateinit var pauseBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        urlBox = EditText(this).apply {
            hint = "डाउनलोड लिंक (URL) यहाँ डालें"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        startBtn = Button(this).apply { text = "Download / Resume" }
        pauseBtn = Button(this).apply { text = "Pause"; isEnabled = false }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        status = TextView(this).apply { text = "तैयार" }

        listOf<View>(urlBox, startBtn, pauseBtn, bar, status).forEach { root.addView(it) }
        setContentView(root)

        startBtn.setOnClickListener { startDownload() }
        pauseBtn.setOnClickListener { downloader?.paused = true }
    }

    private fun startDownload() {
        val url = urlBox.text.toString().trim()
        if (url.isEmpty()) { status.text = "पहले लिंक डालिए"; return }

        val name = url.substringBefore('?').substringAfterLast('/').ifEmpty { "download.bin" }
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        val file = File(dir, name)

        // एक ही URL पर resume के लिए वही Downloader दोबारा बनता है, meta फ़ाइल से प्रोग्रेस मिल जाती है
        val d = Downloader(url, file, parts = 4)
        downloader = d
        startBtn.isEnabled = false
        pauseBtn.isEnabled = true

        val t0 = System.currentTimeMillis()
        d.start(
            onProgress = { done, total ->
                runOnUiThread {
                    if (total > 0) bar.progress = (done * 100 / total).toInt()
                    val secs = (System.currentTimeMillis() - t0) / 1000.0 + 0.001
                    val speed = done / 1024.0 / secs
                    status.text = "%d / %d MB  (%.0f KB/s)".format(done shr 20, total shr 20, speed)
                }
            },
            onFinish = { ok, msg ->
                runOnUiThread {
                    status.text = if (ok) "$msg\n${file.absolutePath}" else msg
                    startBtn.isEnabled = true
                    pauseBtn.isEnabled = false
                }
            }
        )
    }
}
