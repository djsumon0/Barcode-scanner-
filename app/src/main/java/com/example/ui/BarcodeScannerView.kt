package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScannerView(
    viewModel: BarcodeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    val overlays by viewModel.activeOverlays.collectAsState()
    val isScannerActive by viewModel.isScannerActive.collectAsState()
    val currentBatch by viewModel.scannedSessionBatch.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Keep camera instance to control torch/flashlight
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }

    // Reactively toggle camera torch/flashlight based on isFlashOn state
    LaunchedEffect(isFlashOn, camera) {
        val currentCamera = camera
        if (currentCamera != null) {
            try {
                // Attempt to enable torch directly for continuous pass-through support, catching any hardware restriction exceptions gracefully
                currentCamera.cameraControl.enableTorch(isFlashOn)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }
    }

    // Dimensions of analyzed frame for coordinate translation
    var frameWidth by remember { mutableStateOf(1) }
    var frameHeight by remember { mutableStateOf(1) }

    // Remember scanner executor to avoid rebuilding on every frame
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Haptic buzz on new detections
    var lastBatchCount by remember { mutableStateOf(0) }
    LaunchedEffect(currentBatch.size) {
        if (currentBatch.size > lastBatchCount) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastBatchCount = currentBatch.size
    }

    val appContext = context.applicationContext
    DisposableEffect(appContext) {
        onDispose {
            analysisExecutor.shutdown()
            try {
                val providerFuture = ProcessCameraProvider.getInstance(appContext)
                if (providerFuture.isDone) {
                    providerFuture.get().unbindAll()
                }
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E24))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D37)),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Camera Permission Required",
                        tint = Color(0xFFE07A5F),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This app requires access to your camera to scan Code 128 barcodes simultaneously in real-time.",
                        fontSize = 14.sp,
                        color = Color(0xFFB0B0C0),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE07A5F)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            // CameraX Preview is always active and mounted to handle virtual/sandbox engines beautifully
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    // Obtain application context to avoid attribution source matching bugs on virtual systems
                    val app = ctx.applicationContext
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(app)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }

                            // Setup Google ML Kit Barcode Scanner
                            val options = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(
                                    Barcode.FORMAT_CODE_128,
                                    Barcode.FORMAT_ALL_FORMATS // Includes formats like Code 39, QR, etc.
                                )
                                .build()
                            val mlKitScanner = BarcodeScanning.getClient(options)

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                // Short-circuit if scanning is paused to preserve CPU cycles
                                if (!viewModel.isScannerActive.value) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val rotation = imageProxy.imageInfo.rotationDegrees
                                    val isSwapped = rotation == 90 || rotation == 270
                                    val currentWidth = if (isSwapped) mediaImage.height else mediaImage.width
                                    val currentHeight = if (isSwapped) mediaImage.width else mediaImage.height

                                    frameWidth = currentWidth
                                    frameHeight = currentHeight

                                    val image = InputImage.fromMediaImage(mediaImage, rotation)
                                    mlKitScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val detectedList = barcodes.map { barcode ->
                                                DetectedBarcodeOverlay(
                                                    value = barcode.rawValue ?: "",
                                                    boundingBox = barcode.boundingBox
                                                )
                                            }
                                            viewModel.onBarcodesAnalyzed(detectedList)
                                        }
                                        .addOnFailureListener {
                                            // Silent handle
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            // Resolve underlying ComponentActivity to prevent lifecycle desync on customized sandbox managers
                            val activity = ctx as? ComponentActivity ?: (ctx as? ContextWrapper)?.let {
                                var c = it.baseContext
                                while (c is ContextWrapper) {
                                    if (c is ComponentActivity) break
                                    c = c.baseContext
                                }
                                c as? ComponentActivity
                            }
                            val targetLifecycleOwner = activity ?: lifecycleOwner

                            // Bind if target lifecycle is active to prevent OpCAMERA exception sequence
                            if (targetLifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                                cameraProvider.unbindAll()
                                camera = cameraProvider.bindToLifecycle(
                                    targetLifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                // Apply initial flashlight state
                                camera?.cameraControl?.enableTorch(isFlashOn)
                            }
                        } catch (exc: Exception) {
                            exc.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(app))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // High-fidelity Virtual Flash/Torch Simulation
            if (isFlashOn) {
                // Highly visual amber/yellow light simulation overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFD600).copy(alpha = 0.28f),
                                    Color(0xFFFFD600).copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Flash Indicator Badge / Glow Effect at the top center
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                        .background(Color(0xE6FFD600), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape)
                        )
                        Text(
                            text = "FLASH ACTIVE",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

                // High-fidelity AR Canvas overlay to draw boundaries around barcodes if active
                if (isScannerActive) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val scaleX = canvasWidth / frameWidth.toFloat()
                    val scaleY = canvasHeight / frameHeight.toFloat()

                    for (overlay in overlays) {
                        val bounds = overlay.boundingBox ?: continue

                        // Scale rect matching viewfinder
                        val leftScaled = bounds.left * scaleX
                        val topScaled = bounds.top * scaleY
                        val rightScaled = bounds.right * scaleX
                        val bottomScaled = bounds.bottom * scaleY

                        // Draw corner brackets
                        val bracketColor = if (overlay.value.isNotBlank()) Color(0xFF00E676) else Color(0xFFFFD600)
                        val strokeWidth = 3.dp.toPx()
                        val len = 20.dp.toPx()

                        // Top Left
                        drawLine(bracketColor, Offset(leftScaled, topScaled), Offset(leftScaled + len, topScaled), strokeWidth)
                        drawLine(bracketColor, Offset(leftScaled, topScaled), Offset(leftScaled, topScaled + len), strokeWidth)

                        // Top Right
                        drawLine(bracketColor, Offset(rightScaled, topScaled), Offset(rightScaled - len, topScaled), strokeWidth)
                        drawLine(bracketColor, Offset(rightScaled, topScaled), Offset(rightScaled, topScaled + len), strokeWidth)

                        // Bottom Left
                        drawLine(bracketColor, Offset(leftScaled, bottomScaled), Offset(leftScaled + len, bottomScaled), strokeWidth)
                        drawLine(bracketColor, Offset(leftScaled, bottomScaled), Offset(leftScaled, bottomScaled - len), strokeWidth)

                        // Bottom Right
                        drawLine(bracketColor, Offset(rightScaled, bottomScaled), Offset(rightScaled - len, bottomScaled), strokeWidth)
                        drawLine(bracketColor, Offset(rightScaled, bottomScaled), Offset(rightScaled, bottomScaled - len), strokeWidth)

                        // Overlay translucent bounding area
                        drawRect(
                            color = bracketColor.copy(alpha = 0.15f),
                            topLeft = Offset(leftScaled, topScaled),
                            size = androidx.compose.ui.geometry.Size(rightScaled - leftScaled, bottomScaled - topScaled)
                        )

                        // Draw value tag directly above the box using native Android Canvas
                        if (overlay.value.isNotBlank()) {
                            drawContext.canvas.nativeCanvas.apply {
                                val textPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 14.sp.toPx()
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                                    isAntiAlias = true
                                }
                                val bgPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb(200, 0, 230, 118) // semi-transparent neon green
                                    style = android.graphics.Paint.Style.FILL
                                    isAntiAlias = true
                                }

                                val textValue = if (overlay.value.length > 20) overlay.value.take(18) + ".." else overlay.value
                                val textBounds = Rect()
                                textPaint.getTextBounds(textValue, 0, textValue.length, textBounds)

                                val paddingX = 8.dp.toPx()
                                val paddingY = 4.dp.toPx()

                                val rectLeft = leftScaled + (rightScaled - leftScaled) / 2 - textBounds.width() / 2 - paddingX
                                val rectTop = topScaled - textBounds.height() - paddingY * 2 - 8.dp.toPx()
                                val rectRight = rectLeft + textBounds.width() + paddingX * 2
                                val rectBottom = topScaled - 8.dp.toPx()

                                val roundPx = 6.dp.toPx()
                                drawRoundRect(
                                    rectLeft, rectTop, rectRight, rectBottom,
                                    roundPx, roundPx, bgPaint
                                )

                                drawText(
                                    textValue,
                                    rectLeft + paddingX,
                                    rectBottom - paddingY,
                                    textPaint
                                )
                            }
                        }
                    }
                }
            }

            if (!isScannerActive) {
                // Show 'Scanner Paused' Slate semi-transparent overlay over the continuous camera feed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0x1AFFFFFF), CircleShape)
                                .border(1.dp, Color(0x33FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Scanner Paused",
                                tint = Color.LightGray,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanner is Paused",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Resume scanning to detect more Code 128 barcodes in real-time.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )
                    }
                }
            }

            // HUD Floating Controls (Top-Right / Bottom-Left)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                // Torch Switcher
                IconButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xCC202026), CircleShape)
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        .testTag("flashlight_button")
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flashlight",
                        tint = if (isFlashOn) Color(0xFFFFD600) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pause/Resume Analyzer Controls
                IconButton(
                    onClick = { viewModel.toggleScanner(!isScannerActive) },
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xCC202026), CircleShape)
                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        .testTag("pause_scanner_button")
                ) {
                    Icon(
                        imageVector = if (isScannerActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isScannerActive) "Pause Cam" else "Start Cam",
                        tint = if (isScannerActive) Color.White else Color(0xFF00E676),
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (currentBatch.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Clear Current Buffer Action
                    IconButton(
                        onClick = { viewModel.clearActiveBatch() },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xCC202026), CircleShape)
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .testTag("clear_scans_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Active Buffer",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
