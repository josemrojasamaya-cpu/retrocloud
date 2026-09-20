package com.retrosala.app

import com.retrosala.app.streaming.JpegParser
import org.junit.Assert.*
import org.junit.Test

class JpegParserTest {
    @Test fun `handles headers and markers split across TCP reads`() {
        val parser = JpegParser()
        val jpeg = byteArrayOf(255.toByte(),216.toByte(),1,2,255.toByte(),217.toByte())
        val frames = mutableListOf<ByteArray>()
        val stream = "--jpegframe\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray() + jpeg + jpeg
        stream.forEach { parser.accept(byteArrayOf(it)) { frame -> frames.add(frame) } }
        assertEquals(2, frames.size)
        assertArrayEquals(jpeg, frames[0])
    }
    @Test fun `recovers after an oversized incomplete frame`() {
        val parser = JpegParser(20)
        val frames = mutableListOf<ByteArray>()
        parser.accept(byteArrayOf(255.toByte(),216.toByte()) + ByteArray(80)) { frames.add(it) }
        val jpeg = byteArrayOf(255.toByte(),216.toByte(),9,255.toByte(),217.toByte())
        parser.accept(jpeg) { frames.add(it) }
        assertEquals(1, frames.size)
        assertArrayEquals(jpeg, frames[0])
    }
}
