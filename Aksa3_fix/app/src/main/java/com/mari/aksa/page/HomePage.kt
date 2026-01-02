@file:Suppress("DEPRECATION")

package com.mari.aksa.page

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mari.aksa.AssetConstants
import com.mari.aksa.ModelConstants
import com.mari.aksa.R
import com.mari.aksa.RouteConstants
import com.mari.aksa.components.DetectionBoxUI
import com.mari.aksa.utils.DetectionBox
import com.mari.aksa.utils.ImageUtils
import com.mari.aksa.utils.Utils
import com.mari.aksa.utils.saveToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import androidx.camera.core.Preview as CameraPreview
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import com.mari.aksa.tutorial.*
import android.app.Activity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow


@Composable
private fun CameraPermissionSheet(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Allow Aksa to use your camera?",
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Camera access is needed to capture Balinese script.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onAllow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFF3E0),
                        contentColor = Color(0xFF111111)
                    )
                ) { Text("Allow") }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onDeny,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFF3E0),
                        contentColor = Color(0xFF111111)
                    )
                ) { Text("Don’t allow") }
            }
        }
    }
}

/* ================ */
/*   HomePage View  */
/* ================ */
@SuppressLint("AutoboxingStateCreation")
@Composable
fun HomePage(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // ==== CAMERA PERMISSION STATE ====
    val initialGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }
    var cameraGranted by remember { mutableStateOf(initialGranted) }
    var showCameraPermSheet by remember { mutableStateOf(!initialGranted) }

    // Launcher untuk minta izin CAMERA
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showCameraPermSheet = false
        cameraGranted = granted
    }

    val ctx = LocalContext.current

    var rectGallery by remember { mutableStateOf<Rect?>(null) }
    var rectShutter by remember { mutableStateOf<Rect?>(null) }
    var rectFlip by remember { mutableStateOf<Rect?>(null) }
    var overlayWinOffset by remember { mutableStateOf(Offset.Zero) }

// Mulai tour jika pending (dari first-launch / dari menu Panduan)
    var step by remember { mutableStateOf(if (consumeHome(ctx)) 0 else -1) }

    val stepsHome = listOf(
        GuideStep(
            title = stringResource(R.string.tour_home_gallery_title),
            description = stringResource(R.string.tour_home_gallery_desc)
        ),
        GuideStep(
            title = stringResource(R.string.tour_home_shutter_title),
            description = stringResource(R.string.tour_home_shutter_desc)
        ),
        GuideStep(
            title = stringResource(R.string.tour_home_flip_title),
            description = stringResource(R.string.tour_home_flip_desc)
        )
    )

    // Pantau route aktif untuk bind/unbind kamera hanya saat di Home
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isOnHome = remember(backStackEntry) {
        backStackEntry?.destination?.route == RouteConstants.HOME_PAGE
                || backStackEntry?.destination?.route == "home"
    }

    val previewView = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var tflite by remember { mutableStateOf<Interpreter?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    val isProcessing = remember { AtomicBoolean(false) }
    var screenSize by remember { mutableStateOf<Size?>(null) }

    var cachedDetectionResults by remember { mutableStateOf<List<DetectionBox>>(emptyList()) }
    var translitText by remember { mutableStateOf("") }

    var lastDetectionUpdate by remember { mutableLongStateOf(0L) }
    val detectionIntervalMs = 3000L

    var lastRun by remember { mutableLongStateOf(0L) }
    val runEveryMs = 350L

    var cameraRestartKey by remember { mutableStateOf(0) }
    val ar = AspectRatio.RATIO_16_9


    fun getCorrectlyOrientedBitmap(context: Context, uri: Uri): Bitmap {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val exif  = ExifInterface(bytes.inputStream())
        val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (o) {
            ExifInterface.ORIENTATION_ROTATE_90  -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    // launcher UCrop tetap boleh, tapi hasilnya akan di-letterbox sebelum inferensi
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        // ===== USER CANCEL (tekan X / back) =====
        if (result.resultCode != Activity.RESULT_OK) {
            // Kita sudah tetap di HomePage, cukup paksa kamera re-bind
            cameraRestartKey++
            return@rememberLauncherForActivityResult
        }

        val data = result.data
        if (data == null) {
            Log.w("UCrop", "Result data null (maybe cancelled)")
            cameraRestartKey++          // jaga-jaga
            return@rememberLauncherForActivityResult
        }

        val out = com.yalantis.ucrop.UCrop.getOutput(data)
        if (out == null) {
            Log.w("UCrop", "Output URI null")
            cameraRestartKey++          // jaga-jaga
            return@rememberLauncherForActivityResult
        }

        try {
            val bmp   = getCorrectlyOrientedBitmap(context, out)
            val frame = ImageUtils.preprocessImageSquare640(bmp)
            val res   = Utils.runInference(tflite, frame, "FromCropOrEditor")
            val frameUri =
                frame.saveToCache(context, "frame_${System.currentTimeMillis()}.jpg")

            navController.currentBackStackEntry?.savedStateHandle?.apply {
                set("imageUri", frameUri.toString())
                set("detections", ArrayList(res))
                set("frameW", ModelConstants.INPUT_SIZE)
                set("frameH", ModelConstants.INPUT_SIZE)
            }

            navController.navigate(RouteConstants.RESULT_PAGE)
        } catch (e: Exception) {
            Log.e("UCrop", "Error handling crop result", e)
            // kalau gagal keras dan tetap di Home, rebind kamera
            cameraRestartKey++
        }
    }

    fun launchUCrop(src: Uri) {
        val dst  = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
        val opts = com.yalantis.ucrop.UCrop.Options().apply {
            setFreeStyleCropEnabled(true)           // user boleh geser/ubah rasio, bisa juga tidak ubah apa2
            setHideBottomControls(false)            // tampilkan pilihan rasio (1:1, 3:4, ORIGINAL, dst)
            setCompressionQuality(95)
        }
        val intent = com.yalantis.ucrop.UCrop.of(src, dst)
            .withOptions(opts)
            .withMaxResultSize(4096, 4096)          // aman, nanti kita square-640 sendiri
            .getIntent(context)
        cropLauncher.launch(intent)
    }


    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        launchUCrop(uri) // ✅ langsung editor
    }

    // Load model sekali di IO
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val assetFile = context.assets.openFd(AssetConstants.MODEL_PATH)
            val modelBuffer = assetFile.createInputStream().channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                assetFile.startOffset,
                assetFile.declaredLength
            )
            tflite = Interpreter(modelBuffer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                overlayWinOffset = coords.positionInWindow()
            }
            .onSizeChanged {
                if (screenSize == null) {
                    screenSize = Size(it.width.toFloat(), it.height.toFloat())
                }
            }
    ) {

    // Preview hanya ditampilkan jika permission sudah OK
        if (cameraGranted) {
            AndroidView(factory = { previewView }, modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
            )
        } else {
            // latar hitam kosong sementara menunggu izin
            Box(Modifier.fillMaxSize().background(Color.Black))
        }

        screenSize?.let {
            if (cameraGranted) {
                DetectionBoxUI(
                    detectionBoxes = cachedDetectionResults,
                    boxWidth = ModelConstants.INPUT_SIZE.toFloat(),
                    boxHeight = ModelConstants.INPUT_SIZE.toFloat()
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { flashEnabled = !flashEnabled },
                    enabled = cameraGranted
                ) {
                    val icon = if (flashEnabled) R.drawable.ic_flash else R.drawable.ic_flash_off
                    Image(
                        painter = rememberVectorPainter(ImageVector.vectorResource(icon)),
                        contentDescription = stringResource(R.string.toggle_flash)
                    )
                }
                Text("Aksa Lens", color = Color.White, fontSize = 18.sp)
                IconButton(onClick = {
                    try {
                        ProcessCameraProvider.getInstance(context).get().unbindAll()
                    } catch (_: Exception) {
                    }
                    imageCapture = null
                    cameraControl = null
                    navController.navigate("setting")
                }) {
                    Image(
                        painter = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_settings)),
                        contentDescription = "Settings"
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // === GALERI ===
                Box(
                    modifier = Modifier
                        .size(55.dp)
                        .onGloballyPositioned { coords ->
                            rectGallery = coords.boundsInWindow()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Image(
                            painter = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_person)),
                            contentDescription = "Gallery",
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                // Untuk take a picture
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .onGloballyPositioned { coords ->
                            rectShutter = coords.boundsInWindow()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (!cameraGranted) {
                                showCameraPermSheet = true; return@IconButton
                            }
                            imageCapture?.let { capture ->
                                val executor = ContextCompat.getMainExecutor(context)
                                val photoFile =
                                    File.createTempFile("AksaShot", ".jpg", context.cacheDir)
                                val output =
                                    ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                capture.takePicture(
                                    output,
                                    executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            val uri = Uri.fromFile(photoFile)
                                            launchUCrop(uri)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("Capture", "Failed", exception)
                                        }
                                    })
                            }
                        },
                        modifier = Modifier.size(90.dp),
                        enabled = cameraGranted
                    ) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = "Capture",
                                tint = Color.Black,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .onGloballyPositioned { coords ->
                            rectFlip = coords.boundsInWindow()
                        },
                    contentAlignment = Alignment.Center
                ){
                    IconButton(
                        onClick = { useFrontCamera = !useFrontCamera },
                        modifier = Modifier.size(48.dp),
                        enabled = cameraGranted
                    ) {
                        Image(
                            painter = rememberVectorPainter(ImageVector.vectorResource(R.drawable.ic_flip_camera)),
                            contentDescription = "Switch Camera",
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
            }
        }


        // ==== Tampilkan dialog permission kalau belum granted ====
        if (showCameraPermSheet) {
            CameraPermissionSheet(
                onAllow = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                onDeny = { showCameraPermSheet = false }
            )
        }
    }

    // Bind/unbind kamera mengikuti lifecycle + route + PERMISSION
    LaunchedEffect(isOnHome, useFrontCamera, cameraGranted, tflite, screenSize, cameraRestartKey) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        provider.unbindAll()

        // Kamera hanya butuh ini
        if (!isOnHome || !cameraGranted) {
            return@LaunchedEffect
        }

        val cameraSelector =
            if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

        val rotation = previewView.display.rotation

        val preview = CameraPreview.Builder()
            .setTargetAspectRatio(ar)
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // Selalu buat & set imageCapture ✅
        val capture = ImageCapture.Builder()
            .setTargetAspectRatio(ar)
            .setTargetRotation(rotation)
            .build()
        imageCapture = capture

        // Analyzer hanya dipasang kalau model & ukuran layar sudah siap
        val analyzer: ImageAnalysis? =
            if (tflite != null && screenSize != null) {
                ImageAnalysis.Builder()
                    .setTargetAspectRatio(ar)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(rotation)
                    .build().apply {

                        setAnalyzer(ContextCompat.getMainExecutor(context)) { image ->
                            val now = System.currentTimeMillis()
                            if (now - lastRun < runEveryMs) {
                                image.close(); return@setAnalyzer
                            }
                            if (!isProcessing.compareAndSet(false, true)) {
                                image.close(); return@setAnalyzer
                            }
                            lastRun = now

                            coroutineScope.launch {
                                try {
                                    val rotationDeg = image.imageInfo.rotationDegrees
                                    val bmp0 = image.toBitmap()
                                    val bmp = if (rotationDeg != 0) {
                                        Bitmap.createBitmap(
                                            bmp0, 0, 0, bmp0.width, bmp0.height,
                                            Matrix().apply { postRotate(rotationDeg.toFloat()) },
                                            true
                                        )
                                    } else bmp0

                                    val results = withContext(Dispatchers.Default) {
                                        val processed =
                                            ImageUtils.preprocessImageSquare640(bmp)
                                        Utils.runInference(tflite, processed, "HomePageRealTime")
                                    }

                                    val sorted = results.sortedBy { it.x }
                                    cachedDetectionResults = sorted

                                    val now2 = System.currentTimeMillis()
                                    if (now2 - lastDetectionUpdate > detectionIntervalMs) {
                                        translitText = com.mari.aksa.translit.PyPipeline
                                            .transliterate(sorted)
                                            .joinToString(" ")
                                        lastDetectionUpdate = now2
                                    }
                                } catch (e: Exception) {
                                    Log.e("RealTime", "Error: ${e.message}", e)
                                } finally {
                                    isProcessing.set(false)
                                    image.close()
                                }
                            }
                        }
                    }
            } else null

        // Bind sesuai ketersediaan analyzer
        val camera = if (analyzer != null) {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture,
                analyzer
            )
        } else {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
        }
        cameraControl = camera.cameraControl
    }

    if (step >= 0) {
        val index = step
        val total = stepsHome.size

        val rectWin = when (index) {
            0 -> rectGallery
            1 -> rectShutter
            2 -> rectFlip
            else -> null
        }

        val rectLocal = rectWin?.let { r ->
            Rect(
                left = r.left - overlayWinOffset.x,
                top = r.top - overlayWinOffset.y,
                right = r.right - overlayWinOffset.x,
                bottom = r.bottom - overlayWinOffset.y
            )
        }

        GuideOverlayCircle(
            show = rectLocal != null,
            targetRect = rectLocal,
            step = stepsHome[index],
            index = index,
            total = total,
            onPrev = { if (step > 0) step-- },
            onNext = {
                if (index < total - 1) step++ else {
                    step = -1
                    if (isChain(ctx)) {
                        scheduleDictOnly(ctx)
                        navController.navigate("dictionary") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            },
            onSkip = {
                step = -1
                if (isChain(ctx)) {
                    scheduleDictOnly(ctx)
                    navController.navigate("dictionary") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            highlightOffsetY = 0.dp,          // ✅ sementara nol dulu biar akurat semua device
            panelAnchor = PanelAnchor.Above
        )
    }

    LaunchedEffect(flashEnabled) {
        cameraControl?.enableTorch(flashEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) {}
        }
    }


}

/* ========== MOCK ========== */
@Composable
fun HomePageMock() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Image(
                        painter = ColorPainter(Color.LightGray),
                        contentDescription = "Flash"
                    )
                }
                Text(stringResource(R.string.title_home), color = Color.White, fontSize = 18.sp)
                IconButton(onClick = {}) {
                    Image(
                        painter = ColorPainter(Color.LightGray),
                        contentDescription = "Settings"
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Gray, shape = CircleShape)
                    )
                }

                IconButton(
                    onClick = {},
                    modifier = Modifier.size(72.dp)
                ) {
                    Image(
                        painter = ColorPainter(Color(0xFFFCE2B7)),
                        contentDescription = "Capture"
                    )
                }

                IconButton(onClick = {}) {
                    Image(
                        painter = ColorPainter(Color.LightGray),
                        contentDescription = "Switch Camera"
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePagePreview() {
    HomePageMock()
}
