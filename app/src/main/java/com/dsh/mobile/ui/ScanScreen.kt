package com.dsh.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dsh.mobile.data.BridgeRepository
import com.dsh.mobile.data.QrDecode
import com.dsh.mobile.data.View
import com.dsh.mobile.data.Wire
import com.dsh.mobile.ui.theme.LocalDsh
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The gold of every scanner ever shipped; kept, because it reads on any photo. */
private val BracketGold = Color(0xFFE9A93C)

/**
 * The scanner, in the app's own language rather than zxing's stock capture
 * activity: a rounded camera window on the app background, four bracket marks,
 * one line of instruction and a way back to typing a code. That is the layout
 * the ChatGPT scanner uses, and it matches the rest of the app instead of
 * looking like a library demo with a Chinese prompt bolted on.
 *
 * The screen pairs on the form's behalf: the draft the form handed over via
 * [BridgeRepository.pairingDraft] decides address, device name and whether to
 * ask for the `config` scope, and the payload is left in
 * [BridgeRepository.scanned] so a failed attempt lands back on a filled-in form.
 */
@Composable
fun ScanScreen(repo: BridgeRepository) {
    val palette = LocalDsh.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val finished = remember { AtomicBoolean(false) }
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    var cameraError by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var torch by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TEXTURE, not the default SURFACE: a SurfaceView punches its own
            // hole in the window, so the rounded corners of the window above it
            // would not clip it (and screenshots come back black).
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    fun onDecoded(raw: String) {
        // A frame can land after the screen is gone (or right after a hit);
        // pairing twice is not what either the user or the desktop expects.
        if (repo.state.value.view != View.SCAN) return
        if (!finished.compareAndSet(false, true)) return
        val payload = Wire.parsePairPayload(raw)
        if (payload == null) {
            repo.toast("无法识别的二维码")
            finished.set(false)
            return
        }
        val draft = repo.pairingDraft
        repo.scanned = payload
        repo.closeScan()
        repo.pair(
            payload.base ?: draft?.base.orEmpty(),
            payload.code,
            draft?.deviceName?.ifBlank { defaultDeviceName() } ?: defaultDeviceName(),
            draft?.withConfig ?: true,
        )
    }

    LaunchedEffect(previewView, hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val bound = future.get()
                provider = bound
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, QrAnalyzer { text -> main.post { onDecoded(text) } })
                bound.unbindAll()
                camera = bound.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (error: Exception) {
                cameraError = error.message ?: "打不开相机"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(torch) {
        runCatching { camera?.cameraControl?.enableTorch(torch) }
    }

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            CircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                label = "返回",
                onClick = { repo.closeScan() },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(palette.surface),
                ) {
                    if (hasPermission && cameraError == null) {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                        ViewfinderBrackets(Modifier.fillMaxSize())
                    } else {
                        Box(
                            Modifier.fillMaxSize().padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cameraError ?: "需要相机权限才能扫码",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "对准电脑端显示的配对二维码",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.weight(1f))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.surfaceHi, RoundedCornerShape(999.dp))
                    .clickable { repo.closeScan() },
                contentAlignment = Alignment.Center,
            ) {
                Text("手动配对", style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            }
            Spacer(Modifier.height(28.dp))
        }

        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            TorchToggle(
                on = torch,
                onToggle = { torch = !torch },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 20.dp),
            )
        }
    }
}

/** Four L marks, the way every viewfinder since the first barcode app draws them. */
@Composable
private fun ViewfinderBrackets(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val inset = 22.dp.toPx()
        val arm = 26.dp.toPx()
        val stroke = 3.dp.toPx()
        val left = inset
        val right = size.width - inset
        val top = inset
        val bottom = size.height - inset
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(BracketGold, Offset(x, y), Offset(x + dx, y), stroke, StrokeCap.Butt)
            drawLine(BracketGold, Offset(x, y), Offset(x, y + dy), stroke, StrokeCap.Butt)
        }
        corner(left, top, arm, arm)
        corner(right, top, -arm, arm)
        corner(left, bottom, arm, -arm)
        corner(right, bottom, -arm, -arm)
    }
}

/** The flashlight, only when this camera has one (an emulator's does not). */
@Composable
private fun BoxScope.TorchToggle(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CircleButton(
        icon = if (on) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
        label = if (on) "关闭闪光灯" else "打开闪光灯",
        onClick = onToggle,
        modifier = modifier,
    )
}

/** Pulls QR payloads out of preview frames, one decode at a time. */
private class QrAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val busy = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        try {
            if (busy.get()) return
            val media = image.image ?: return
            val plane = media.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            busy.set(true)
            val text = QrDecode.fromLuminance(bytes, image.width, image.height, plane.rowStride)
            if (text != null) onDecoded(text)
        } catch (_: Exception) {
            // A frame that cannot be read is not an error worth showing anyone.
        } finally {
            busy.set(false)
            image.close()
        }
    }
}
