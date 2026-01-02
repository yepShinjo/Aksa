package com.mari.aksa.page.setting

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mari.aksa.LocaleHelper
import com.mari.aksa.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.mari.aksa.tutorial.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow


@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(LocaleHelper.getLanguage(context)) }

    val languageLabel = when (selectedLanguage) {
        "en" -> stringResource(R.string.language_english)
        else -> stringResource(R.string.language_indonesia)
    }

    // ===== GUIDE STATE =====
    val ctx = LocalContext.current
    var rectLang by remember { mutableStateOf<Rect?>(null) }
    var rectAbout by remember { mutableStateOf<Rect?>(null) }
    var rectGuide by remember { mutableStateOf<Rect?>(null) }

    var stepGuide by remember { mutableStateOf(if (consumeSetting(ctx)) 0 else -1) }
    var overlayWinOffset by remember { mutableStateOf(Offset.Zero) }
    val stepsSetting = listOf(
        GuideStep(
            title = stringResource(R.string.tour_settings_language_title),
            description = stringResource(R.string.tour_settings_language_desc)
        ),
        GuideStep(
            title = stringResource(R.string.tour_settings_about_title),
            description = stringResource(R.string.tour_settings_about_desc)
        ),
        GuideStep(
            title = stringResource(R.string.tour_settings_guide_title),
            description = stringResource(R.string.tour_settings_guide_desc)
        )
    )

    // ===== ROOT =====
    // ===== ROOT =====
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayWinOffset = coords.positionInWindow()
            }
    ) { Scaffold(
            topBar = {
                Surface(color = Color.White, shadowElevation = 0.dp) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        TopAppBar(
                            windowInsets = WindowInsets(0.dp),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.settings_title),
                                        color = Color(0xFFFF9800),
                                        fontSize = 20.sp
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                            modifier = Modifier
                                .height(50.dp)
                                .padding(horizontal = 4.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 1.dp,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            },
            containerColor = Color.White
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {

                        // ===== Bahasa Aplikasi =====
                        SettingMenuRow(
                            leading = {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_language),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = stringResource(R.string.language_app),
                            subtitle = languageLabel,
                            onClick = { showLanguageDialog = true },
                            onRect = { rectLang = it }
                        )

                        // ===== Tentang Aplikasi =====
                        SettingMenuRow(
                            leading = {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_about_app),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = stringResource(R.string.about_app),
                            subtitle = null,
                            onClick = { navController.navigate("about") },
                            onRect = { rectAbout = it }
                        )

                        // ===== Panduan Aplikasi =====
                        SettingMenuRow(
                            leading = {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_help_guide),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = stringResource(R.string.menu_app_guide),
                            subtitle = null,
                            onClick = {
                                startFullGuide(context)
                                navController.navigate("home") { launchSingleTop = true; restoreState = true }
                            },
                            onRect = { rectGuide = it }
                        )
                    }
                }
            }
        }

        // ===== Dialog bahasa =====
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                title = { Text(stringResource(R.string.language_dialog_title)) },
                text = {
                    Column {
                        listOf("id", "en").forEach { langCode ->
                            val label =
                                if (langCode == "en") stringResource(R.string.language_english)
                                else stringResource(R.string.language_indonesia)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        LocaleHelper.setLocale(context, langCode)
                                        selectedLanguage = langCode
                                        showLanguageDialog = false
                                        val activity = context as? Activity
                                        scope.launch { delay(120); activity?.recreate() }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedLanguage == langCode,
                                    onClick = {
                                        LocaleHelper.setLocale(context, langCode)
                                        selectedLanguage = langCode
                                        showLanguageDialog = false
                                        val activity = context as? Activity
                                        scope.launch { delay(120); activity?.recreate() }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                containerColor = Color.White
            )
        }

        // ===== Overlay guide di paling akhir =====
        // ===== Overlay guide di paling akhir =====
        if (stepGuide >= 0) {
            val rectWin = when (stepGuide) {
                0 -> rectLang
                1 -> rectAbout
                2 -> rectGuide
                else -> null
            }

            // konversi dari WINDOW -> LOKAL overlay (biar highlight tepat di tengah)
            val rectLocal = rectWin?.let { r ->
                Rect(
                    left = r.left - overlayWinOffset.x,
                    top = r.top - overlayWinOffset.y,
                    right = r.right - overlayWinOffset.x,
                    bottom = r.bottom - overlayWinOffset.y
                )
            }

            GuideOverlay(
                show = rectLocal != null,
                targetRect = rectLocal,
                step = stepsSetting[stepGuide],
                index = stepGuide,
                total = stepsSetting.size,
                onPrev = { if (stepGuide > 0) stepGuide-- },
                onNext = {
                    if (stepGuide < stepsSetting.lastIndex) stepGuide++ else {
                        stepGuide = -1
                        enableChain(ctx, false)
                    }
                },
                onSkip = {
                    stepGuide = -1
                    enableChain(ctx, false)
                }
            )
        }
    }
}

/* ─────────────────────────────── */
/* Helper: wrapper row konsisten   */
/* ─────────────────────────────── */
@Composable
private fun SettingMenuRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    onRect: (Rect) -> Unit,
    rowHeight: Dp = 68.dp,

    // kontrol ukuran highlight
    targetPadH: Dp = 0.dp,
    targetPadV: Dp = (-5).dp,
    shrinkLeft: Dp = 10.dp,
    shrinkRight: Dp = 10.dp,
    offsetY: Dp = 0.dp
) {
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .onGloballyPositioned { c ->
                val r = c.boundsInWindow()

                val padH = with(density) { targetPadH.toPx() }
                val padV = with(density) { targetPadV.toPx() }
                val shL  = with(density) { shrinkLeft.toPx() }
                val shR  = with(density) { shrinkRight.toPx() }
                val dy   = with(density) { offsetY.toPx() }

                var left   = r.left + shL
                var right  = r.right - shR
                var top    = r.top
                var bottom = r.bottom

                left   -= padH
                right  += padH
                top    -= padV
                bottom += padV

                top    += dy
                bottom += dy

                if (right > left && bottom > top) {
                    onRect(Rect(left, top, right, bottom))
                } else {
                    onRect(r)
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, color = Color.Gray)
        }

        Image(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}


