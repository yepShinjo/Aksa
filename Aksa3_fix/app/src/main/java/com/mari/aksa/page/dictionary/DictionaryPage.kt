package com.mari.aksa.page.dictionary

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mari.aksa.R
import com.mari.aksa.tutorial.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset

@SuppressLint("AutoboxingStateCreation")
@Composable
fun DictionaryPage(viewModel: DictionaryViewModel, navController: NavController) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val currentLanguage = configuration.locales[0].language

    LaunchedEffect(Unit) { viewModel.checkAndLoadInitialData(context) }
    LaunchedEffect(currentLanguage) { viewModel.setLanguage(currentLanguage) }

    val query by viewModel.query.collectAsStateWithLifecycle()
    val aksaraList by viewModel.aksaraList.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    var previewImage by remember { mutableStateOf<String?>(null) }

    // GUIDE STATE (Kamus)
    val ctx = LocalContext.current
    var rectSearch by remember { mutableStateOf<Rect?>(null) }      // window coords
    var rectFirstImage by remember { mutableStateOf<Rect?>(null) }  // window coords
    var rectSettings by remember { mutableStateOf<Rect?>(null) }    // window coords
    fun toRect(w: Int, h: Int, x: Float, y: Float) = Rect(x, y, x + w, y + h)

    var stepGuide by remember { mutableStateOf(if (consumeDict(ctx)) 0 else -1) }

    val stepsDict = listOf(
        GuideStep(
            title = stringResource(R.string.tour_dict_search_title),
            description = stringResource(R.string.tour_dict_search_desc)
        ),
        GuideStep(
            title = stringResource(R.string.tour_dict_preview_title),
            description = stringResource(R.string.tour_dict_preview_desc)
        ),
        GuideStep(
            title = stringResource(R.string.tour_dict_settings_title),
            description = stringResource(R.string.tour_dict_settings_desc)
        )
    )

    // ROOT: simpan origin Box terhadap WINDOW untuk konversi koordinat
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
    ) {

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFC107))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.nav_dictionary),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { navController.navigate("setting") },
                            modifier = Modifier.onGloballyPositioned { c ->
                                val p = c.positionInWindow()
                                rectSettings = toRect(c.size.width, c.size.height, p.x, p.y)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color.White)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .onGloballyPositioned { c ->
                                val p = c.positionInWindow()
                                rectSearch = toRect(c.size.width, c.size.height, p.x, p.y)
                            },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedBorderColor = Color(0xFFFFC107),
                            cursorColor = Color.Black,
                            focusedLabelColor = Color.Black,
                            unfocusedLabelColor = Color.Gray,
                            disabledContainerColor = Color.White,
                        )
                    )
                }
            },
            containerColor = Color.White
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        items(aksaraList.size) { i ->
                            DictionaryItem(
                                item = aksaraList[i],
                                language = language,
                                onImageClick = { assetName -> previewImage = assetName },
                                onFirstImagePositioned = if (i == 0) { r -> rectFirstImage = r } else null
                            )
                        }
                    }
                }
            }
        }

        // Dialog fullscreen preview
        if (previewImage != null) {
            ImagePreviewDialog(
                assetName = previewImage!!,
                onDismiss = { previewImage = null }
            )
        }

        // ===== Render GUIDE overlay – KONVERSI window → local =====
        if (stepGuide >= 0) {
            val winRect = when (stepGuide) {
                0 -> rectSearch
                1 -> rectFirstImage
                2 -> rectSettings
                else -> null
            }

            // convert ke koordinat lokal Box root
            val localRect = winRect?.let {
                Rect(
                    it.left - rootOrigin.x,
                    it.top - rootOrigin.y,
                    it.right - rootOrigin.x,
                    it.bottom - rootOrigin.y
                )
            }

            GuideOverlay(
                show = localRect != null,
                targetRect = localRect,
                step = stepsDict[stepGuide],
                index = stepGuide,
                total = stepsDict.size,
                onPrev = { if (stepGuide > 0) stepGuide-- },
                onNext = {
                    if (stepGuide < stepsDict.lastIndex) {
                        stepGuide++
                    } else {
                        stepGuide = -1
                        navController.navigate("setting") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onSkip = {
                    stepGuide = -1
                    if (isChain(ctx)) {
                        scheduleSettingOnly(ctx)
                        navController.navigate("setting") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}

/* ===========================
 *  Preview Dialog Gambar
 * =========================== */
@Composable
private fun ImagePreviewDialog(
    assetName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.55f)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("file:///android_asset/images/$assetName")
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.aksara_image_desc),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.Black
                        )
                    }
                }
            }
        }
    }
}

/* ===========================
 *   Item Kamus + posisi img
 * =========================== */
@Composable
fun DictionaryItem(
    item: KamusResponseItem,
    language: String,
    onImageClick: (String) -> Unit,
    onFirstImagePositioned: ((Rect) -> Unit)? = null
) {
    val description = when (language) {
        "en" -> item.description_en
        "id" -> item.description_id
        else -> item.description_id
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BOX untuk gambar aksara
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .onGloballyPositioned { c ->
                        onFirstImagePositioned?.let { cb ->
                            val p = c.positionInWindow() // window coords
                            cb(Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/images/${item.imageAssetName}")
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.aksara_image_desc),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(60.dp)
                        .clickable { onImageClick(item.imageAssetName) }
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = stringResource(R.string.label_latin, item.latin),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = stringResource(R.string.label_description, description),
                    fontSize = 13.sp
                )
            }
        }
    }
}
