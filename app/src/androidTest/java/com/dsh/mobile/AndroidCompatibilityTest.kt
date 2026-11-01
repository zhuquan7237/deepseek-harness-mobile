package com.dsh.mobile

import android.os.Handler
import androidx.camera.core.ImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dsh.mobile.overlay.WhaleBallService
import com.dsh.mobile.ui.WhaleFace
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

/** Regression for production paths touched when making minSdk 26 lint-safe. */
@RunWith(AndroidJUnit4::class)
class AndroidCompatibilityTest {
    @Test fun scannerDecodesPaddedImageProxyPlaneAndClosesFrame() {
        val expected = "https://m.zhuquan.xyz/mobile/?pair=TEST-1234"
        val matrix = QRCodeWriter().encode(expected, BarcodeFormat.QR_CODE, 260, 260)
        val stride = matrix.width + 13
        val bytes = ByteArray(stride * matrix.height) { 127 }
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            bytes[y * stride + x] = if (matrix.get(x, y)) 0 else 255.toByte()
        }
        val plane = Proxy.newProxyInstance(ImageProxy.PlaneProxy::class.java.classLoader, arrayOf(ImageProxy.PlaneProxy::class.java)) { _, method, _ ->
            when (method.name) {
                "getBuffer" -> ByteBuffer.wrap(bytes)
                "getRowStride" -> stride
                "getPixelStride" -> 1
                else -> null
            }
        } as ImageProxy.PlaneProxy
        var closed = false
        val image = Proxy.newProxyInstance(ImageProxy::class.java.classLoader, arrayOf(ImageProxy::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlanes" -> arrayOf(plane)
                "getWidth" -> matrix.width
                "getHeight" -> matrix.height
                "getImage" -> throw AssertionError("unstable underlying Image must not be accessed")
                "close" -> { closed = true; null }
                else -> null
            }
        } as ImageProxy
        var decoded: String? = null
        val klass = Class.forName("com.dsh.mobile.ui.QrAnalyzer")
        val ctor = klass.declaredConstructors.single().apply { isAccessible = true }
        val callback: (String) -> Unit = { decoded = it }
        val analyzer = ctor.newInstance(callback)
        klass.getDeclaredMethod("analyze", ImageProxy::class.java).apply { isAccessible = true }.invoke(analyzer, image)
        assertEquals(expected, decoded)
        assertTrue("each camera frame must be closed", closed)
    }

    @Test fun whaleFaceResetSchedulingTracksReplacementAndCompletion() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val service = WhaleBallService()
            val cls = service.javaClass
            val setFace = cls.getDeclaredMethod("setFace", Int::class.javaPrimitiveType).apply { isAccessible = true }
            val pending = cls.getDeclaredField("faceResetPending").apply { isAccessible = true }
            val reset = cls.getDeclaredField("backToNormal").apply { isAccessible = true }.get(service) as Runnable
            val handler = cls.getDeclaredField("handler").apply { isAccessible = true }.get(service) as Handler
            try {
                setFace.invoke(service, WhaleFace.NORMAL.res)
                assertFalse(pending.getBoolean(service))
                setFace.invoke(service, WhaleFace.HAPPY.res)
                assertTrue(pending.getBoolean(service))
                setFace.invoke(service, WhaleFace.SLEEPY.res)
                assertTrue(pending.getBoolean(service))
                reset.run()
                assertFalse(pending.getBoolean(service))
                setFace.invoke(service, WhaleFace.SHY.res)
                assertTrue(pending.getBoolean(service))
                setFace.invoke(service, WhaleFace.NORMAL.res)
                assertFalse(pending.getBoolean(service))
            } finally {
                handler.removeCallbacksAndMessages(null)
            }
        }
    }
}
