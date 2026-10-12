package com.example.mydm

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Multi-thread downloader (IDM जैसा): फ़ाइल को हिस्सों में बाँटकर एक साथ डाउनलोड करता है।
 * Pause/Resume के लिए हर हिस्से की प्रोग्रेस ".meta" फ़ाइल में सेव होती है।
 */
class Downloader(
    private val url: String,
    private val file: File,
    private val parts: Int = 4
) {
    @Volatile var paused = false
    private val meta = File(file.path + ".meta")

    private fun open(u: String, range: String? = null): HttpURLConnection {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MyDM")
        if (range != null) c.setRequestProperty("Range", range)
        return c
    }

    fun start(
        onProgress: (done: Long, total: Long) -> Unit,
        onFinish: (success: Boolean, message: String) -> Unit
    ) {
        paused = false
        Thread {
            try {
                val head = open(url).apply { requestMethod = "HEAD"; connect() }
                val total = head.contentLengthLong
                val rangeOk = head.getHeaderField("Accept-Ranges") == "bytes"
                head.disconnect()

                if (total > 0 && rangeOk) multi(total, onProgress, onFinish)
                else single(onProgress, onFinish)
            } catch (e: Exception) {
                onFinish(false, "Error: ${e.message}")
            }
        }.start()
    }

    // सर्वर Range सपोर्ट नहीं करता तो सिंगल-थ्रेड (resume नहीं होगा)
    private fun single(
        onProgress: (Long, Long) -> Unit,
        onFinish: (Boolean, String) -> Unit
    ) {
        val c = open(url).apply { connect() }
        val total = c.contentLengthLong
        var done = 0L
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(8192)
                while (!paused) {
                    val n = input.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done, total)
                }
            }
        }
        if (paused) onFinish(false, "Paused") else onFinish(true, "पूरा हुआ")
    }

    private fun multi(
        total: Long,
        onProgress: (Long, Long) -> Unit,
        onFinish: (Boolean, String) -> Unit
    ) {
        val size = total / parts
        val starts = LongArray(parts) { it * size }
        val ends = LongArray(parts) { if (it == parts - 1) total - 1 else (it + 1) * size - 1 }
        val downloaded = LongArray(parts)

        // पुरानी प्रोग्रेस पढ़ें (resume)
        if (meta.exists() && file.exists()) {
            val lines = meta.readLines()
            if (lines.size == parts) lines.forEachIndexed { i, s -> downloaded[i] = s.toLongOrNull() ?: 0L }
        } else {
            RandomAccessFile(file, "rw").use { it.setLength(total) }
        }

        val workers = (0 until parts).map { i ->
            Thread {
                try {
                    val pos = starts[i] + downloaded[i]
                    if (pos > ends[i]) return@Thread
                    val c = open(url, "bytes=$pos-${ends[i]}").apply { connect() }
                    c.inputStream.use { input ->
                        RandomAccessFile(file, "rw").use { raf ->
                            raf.seek(pos)
                            val buf = ByteArray(8192)
                            while (!paused) {
                                val n = input.read(buf)
                                if (n == -1) break
                                raf.write(buf, 0, n)
                                downloaded[i] = downloaded[i] + n.toLong()
                            }
                        }
                    }
                } catch (e: Exception) { /* इस हिस्से में दिक्कत: meta सेव है, दोबारा resume करें */ }
            }.also { it.start() }
        }

        while (workers.any { it.isAlive }) {
            onProgress(downloaded.sum(), total)
            meta.writeText(downloaded.joinToString("\n"))
            Thread.sleep(500)
        }
        meta.writeText(downloaded.joinToString("\n"))
        onProgress(downloaded.sum(), total)

        val complete = (0 until parts).all { starts[it] + downloaded[it] > ends[it] }
        when {
            complete -> { meta.delete(); onFinish(true, "पूरा हुआ") }
            paused -> onFinish(false, "Paused")
            else -> onFinish(false, "रुक गया, Resume दबाइए")
        }
    }
}
