package com.mari.aksa.page

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.mari.aksa.ModelConstants
import com.mari.aksa.components.DetectionBoxUI
import com.mari.aksa.utils.DetectionBox
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultPage(navController: NavController) {
    val handle = navController.previousBackStackEntry?.savedStateHandle
        ?: navController.currentBackStackEntry?.savedStateHandle

    val imageUriStr = handle?.get<String>("imageUri")
    val detections = handle?.get<ArrayList<DetectionBox>>("detections") ?: arrayListOf()
    val frameW = handle?.get<Int>("frameW") ?: ModelConstants.INPUT_SIZE
    val frameH = handle?.get<Int>("frameH") ?: ModelConstants.INPUT_SIZE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aksa Lens", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.height(56.dp)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (imageUriStr == null) {
                Text("Tidak ada gambar ditemukan.", color = Color.White)
            } else {
                ResultContent(
                    imageUri = imageUriStr.toUri(),
                    detections = detections,
                    frameW = frameW,
                    frameH = frameH
                )
            }
        }
        println("RESULTPAGE DETECTIONS: ${detections.map { it.label to it.x to it.y }}")
    }
}

@Composable
private fun ResultContent(
    imageUri: Uri,
    detections: List<DetectionBox>,
    frameW: Int,
    frameH: Int
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(imageUri)
            .allowHardware(false)      // model sudah 640x640, tidak perlu paksa size lagi
            .build()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(frameW.toFloat() / frameH.toFloat())  // biasanya 1:1
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = "Detected Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit   // ✅ jaga aspect ratio, tidak diseret
        )

        DetectionBoxUI(
            detectionBoxes = detections,
            boxWidth = frameW.toFloat(),
            boxHeight = frameH.toFloat(),
            scale = 1f
        )
    }
}

