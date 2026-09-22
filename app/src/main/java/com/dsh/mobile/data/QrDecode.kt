package com.dsh.mobile.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * QR decoding, deliberately free of Android types so it can be exercised on the
 * JVM (see QrDecodeTest): the camera hands over the Y plane of a YUV_420_888
 * frame — already a greyscale image — and the one trap worth testing is row
 * padding. Planes arrive with a `rowStride` that is usually wider than the
 * frame, and passing that stride straight to zxing shears every row a little
 * further left until nothing is recognisable.
 */
object QrDecode {

    /**
     * @param y the luminance plane, row-major, [rowStride] bytes per row.
     * @param width visible width in pixels.
     * @param height visible height in pixels.
     * @param rowStride bytes per row in [y] (>= [width]).
     * @returns the QR payload, or null when this frame holds none.
     */
    fun fromLuminance(y: ByteArray, width: Int, height: Int, rowStride: Int): String? {
        if (width <= 0 || height <= 0 || rowStride < width) return null
        if (y.size < (height - 1) * rowStride + width) return null
        val pixels = if (rowStride == width) {
            y
        } else {
            ByteArray(width * height).also { out ->
                for (row in 0 until height) {
                    System.arraycopy(y, row * rowStride, out, row * width, width)
                }
            }
        }
        val source = PlanarYUVLuminanceSource(pixels, width, height, 0, 0, width, height, false)
        val reader = MultiFormatReader()
        reader.setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
