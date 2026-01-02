package com.mari.aksa.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mari.aksa.R
import kotlin.math.max

data class GuideStep(
    val title: String,
    val description: String
)

@Composable
fun GuideOverlay(
    show: Boolean,
    targetRect: Rect?,
    step: GuideStep,
    index: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    corner: Dp = 16.dp,
    padding: Dp = 10.dp
) {
    if (!show || targetRect == null || targetRect.width <= 0f) return

    val density = LocalDensity.current
    val cornerPx = with(density) { corner.toPx() }
    val padPx    = with(density) { padding.toPx() }
    val hole     = targetRect.inflate(padPx)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawIntoCanvas { canvas ->
                    val clear = Paint().apply { blendMode = BlendMode.Clear }
                    canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                    drawRect(color = Color(0xCC000000))
                    canvas.drawRoundRect(
                        hole.left, hole.top, hole.right, hole.bottom,
                        cornerPx, cornerPx, clear
                    )
                    canvas.restore()
                }
                drawContent()
            }
    ) {
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val placeBelow = hole.center.y < containerHeightPx / 2f

        Column(
            modifier = Modifier
                .align(if (placeBelow) Alignment.TopStart else Alignment.BottomStart)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (placeBelow) with(density) { hole.bottom.toDp() + 16.dp } else 24.dp,
                    bottom = if (!placeBelow) with(density) { (max(0f, containerHeightPx - hole.top)).toDp() + 16.dp } else 24.dp
                )
                .fillMaxWidth()
        ) {
            AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 14.dp
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(step.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF111111))
                        Spacer(Modifier.height(6.dp))
                        Text(step.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF333333))
                        Spacer(Modifier.height(10.dp))
                        Spacer(Modifier.height(12.dp))


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1} / $total", color = Color.Gray, style = MaterialTheme.typography.labelMedium)

                            Spacer(Modifier.weight(1f)) // dorong tombol ke kanan

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onSkip) {
                                    Text(text= stringResource(R.string.guide_skip), maxLines = 1, softWrap = false)
                                }
                                if (index > 0) {
                                    TextButton(onClick = onPrev) {
                                        Text(text= stringResource(R.string.guide_prev), maxLines = 1, softWrap = false)
                                    }
                                }
                                Button(
                                    onClick = onNext,
                                    // was: widthIn(min = 112.dp)
                                    modifier = Modifier
                                        .widthIn(min = 132.dp)   // ↗️ tambah ruang aman
                                        .heightIn(min = 40.dp),  // jaga tinggi biar nggak gepeng
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (index == total - 1){
                                                R.string.guide_done
                                            }else{
                                                R.string.guide_next
                                            }
                                        ),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                        // overflow nggak perlu; kita paksa muat
                                    )
                                }

                            }
                        }
                    }
                }
            }
        }
    }
}
