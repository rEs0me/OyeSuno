package com.oyesuno.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders an audio waveform as vertical bars with optional seek support.
 *
 * @param amplitudes List of amplitude values (0f..1f)
 * @param progress Playback progress (0f..1f)
 * @param activeColor Color for bars before the playback position
 * @param inactiveColor Color for bars after the playback position
 * @param barWidth Width of each amplitude bar
 * @param barSpacing Spacing between bars
 * @param cornerRadius Corner radius for bars
 * @param onSeek Callback when user taps/drags to seek (0f..1f position)
 */
@Composable
fun WaveformView(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    cornerRadius: Dp = 1.5.dp,
    onSeek: ((Float) -> Unit)? = null
) {
    val density = LocalDensity.current
    val barWidthPx = with(density) { barWidth.toPx() }
    val barSpacingPx = with(density) { barSpacing.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    val seekModifier = if (onSeek != null) {
        modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val seekPosition = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(seekPosition)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val seekPosition = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(seekPosition)
                }
            }
    } else {
        modifier
    }

    Canvas(modifier = seekModifier) {
        if (amplitudes.isEmpty()) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val totalBarWidth = barWidthPx + barSpacingPx
        val barCount = ((canvasWidth + barSpacingPx) / totalBarWidth).toInt().coerceAtLeast(1)

        // Resample amplitudes to fit the available bar count
        val sampledAmplitudes = if (amplitudes.size == barCount) {
            amplitudes
        } else {
            List(barCount) { index ->
                val srcIndex = (index.toFloat() / barCount * amplitudes.size).toInt()
                    .coerceIn(0, amplitudes.lastIndex)
                amplitudes[srcIndex]
            }
        }

        val progressIndex = (progress * barCount).toInt()

        sampledAmplitudes.forEachIndexed { index, amplitude ->
            val clampedAmplitude = amplitude.coerceIn(0.05f, 1f)
            val barHeight = canvasHeight * clampedAmplitude
            val x = index * totalBarWidth
            val y = (canvasHeight - barHeight) / 2f
            val color = if (index <= progressIndex) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        }
    }
}
