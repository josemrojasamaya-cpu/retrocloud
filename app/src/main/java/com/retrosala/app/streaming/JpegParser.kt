package com.retrosala.app.streaming

import java.io.ByteArrayOutputStream

/** Accept both legacy raw MJPEG and MIME parts. Ignore headers before SOI. */
class JpegParser(private val maxBytes: Int = 2 * 1024 * 1024) {
    private val bytes = ByteArrayOutputStream(65536)
    private var previous = -1
    private var inFrame = false

    fun accept(chunk: ByteArray, length: Int = chunk.size, onFrame: (ByteArray) -> Unit) {
        for (i in 0 until length) {
            val value = chunk[i].toInt() and 255
            if (!inFrame) {
                if (previous == 255 && value == 216) {
                    bytes.reset(); bytes.write(255); bytes.write(216); inFrame = true
                }
            } else {
                bytes.write(value)
                if (previous == 255 && value == 217) {
                    onFrame(bytes.toByteArray()); bytes.reset(); inFrame = false
                } else if (bytes.size() > maxBytes) {
                    bytes.reset(); inFrame = false
                }
            }
            previous = value
        }
    }
}
