package com.dsh.mobile

import com.dsh.mobile.data.QrDecode
import com.dsh.mobile.data.Wire
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The scanning screen lives on the phone, but the part that can be wrong
 * silently is the decoder: camera planes arrive padded, sheared or handed in a
 * different size than the preview. Everything here runs on the JVM against a QR
 * this test encodes itself, which is the closest thing to "pointed at the
 * desktop's real QR" that does not need a camera.
 */
class QrDecodeTest {

    private val pairingLink = "https://m.zhuquan.xyz/mobile/?pair=4FWD-FYH5"

    private fun matrix(payload: String, size: Int = 260): BitMatrix =
        QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)

    /** The Y plane a camera would hand over: black/white samples, padded rows. */
    private fun yPlane(matrix: BitMatrix, rowStride: Int = matrix.width, pad: Byte = 0x7F): ByteArray {
        val y = ByteArray(rowStride * matrix.height)
        if (pad != 0.toByte()) y.fill(pad)
        for (row in 0 until matrix.height) {
            for (col in 0 until matrix.width) {
                y[row * rowStride + col] = if (matrix.get(col, row)) 0 else 255.toByte()
            }
        }
        return y
    }

    @Test
    fun `decodes the pairing link the desktop renders`() {
        val m = matrix(pairingLink)
        val text = QrDecode.fromLuminance(yPlane(m), m.width, m.height, m.width) ?: "（没解出来）"
        assertEquals(pairingLink, text)
    }

    @Test
    fun `decodes through padded rows, the way real camera planes arrive`() {
        val m = matrix(pairingLink)
        // Real Y planes carry a rowStride wider than the frame (often 64px
        // aligned); feeding that stride straight to zxing shears the image.
        val stride = m.width + 57
        val text = QrDecode.fromLuminance(yPlane(m, stride), m.width, m.height, stride) ?: "（没解出来）"
        assertEquals(pairingLink, text)
    }

    @Test
    fun `the payload parses into the same base and code the form fills in`() {
        val m = matrix(pairingLink)
        val text = QrDecode.fromLuminance(yPlane(m), m.width, m.height, m.width) ?: "（没解出来）"
        val payload = Wire.parsePairPayload(text) ?: error("payload did not parse")
        assertEquals("https://m.zhuquan.xyz", payload.base ?: "（没带服务器地址）")
        assertEquals("4FWD-FYH5", Wire.formatCode(payload.code))
    }

    @Test
    fun `decodes a bare code, not just links`() {
        val m = matrix("WXYZ-1234")
        val text = QrDecode.fromLuminance(yPlane(m), m.width, m.height, m.width) ?: "（没解出来）"
        assertEquals("WXYZ-1234", text)
        assertEquals("WXYZ-1234", Wire.formatCode(Wire.parsePairPayload(text)?.code ?: ""))
    }

    @Test
    fun `a frame with no code returns null instead of throwing`() {
        val noise = ByteArray(640 * 480) { ((it * 7919) % 251).toByte() }
        assertNull(QrDecode.fromLuminance(noise, 640, 480, 640))
    }

    @Test
    fun `garbage geometry is refused, not guessed`() {
        val m = matrix(pairingLink)
        val plane = yPlane(m)
        assertNull(QrDecode.fromLuminance(plane, 0, m.height, m.width))
        assertNull(QrDecode.fromLuminance(plane, m.width, 0, m.width))
        assertNull(QrDecode.fromLuminance(plane, m.width, m.height, m.width - 1))
        // A truncated buffer must fail closed rather than read out of bounds.
        assertNull(QrDecode.fromLuminance(ByteArray(16), m.width, m.height, m.width))
    }
}
