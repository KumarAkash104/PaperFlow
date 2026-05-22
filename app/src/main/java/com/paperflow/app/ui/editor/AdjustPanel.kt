package com.paperflow.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paperflow.app.util.AdjustmentValues

@Composable
fun AdjustPanel(
    adjustments: AdjustmentValues,
    onAdjustmentsChanged: (AdjustmentValues) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Auto-enhance button
        FilledTonalButton(
            onClick = {
                onAdjustmentsChanged(
                    AdjustmentValues(
                        brightness = 10f,
                        contrast = 20f,
                        saturation = 10f,
                        sharpness = 30f,
                        warmth = 0f,
                        exposure = 5f,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier)
            Spacer(Modifier.width(8.dp))
            Text("Auto Enhance")
        }

        Spacer(Modifier.height(12.dp))

        AdjustSlider(
            label = "Brightness",
            value = adjustments.brightness,
            range = -100f..100f,
            onValueChange = { onAdjustmentsChanged(adjustments.copy(brightness = it)) },
        )
        AdjustSlider(
            label = "Contrast",
            value = adjustments.contrast,
            range = -100f..100f,
            onValueChange = { onAdjustmentsChanged(adjustments.copy(contrast = it)) },
        )
        AdjustSlider(
            label = "Saturation",
            value = adjustments.saturation,
            range = -100f..100f,
            onValueChange = { onAdjustmentsChanged(adjustments.copy(saturation = it)) },
        )
        AdjustSlider(
            label = "Sharpness",
            value = adjustments.sharpness,
            range = 0f..100f,
            onValueChange = { onAdjustmentsChanged(adjustments.copy(sharpness = it)) },
        )
        AdjustSlider(
            label = "Warmth",
            value = adjustments.warmth,
            range = -100f..100f,
            onValueChange = { onAdjustmentsChanged(adjustments.copy(warmth = it)) },
        )
        AdjustSlider(
            label = "Exposure",
            value = adjustments.exposure,
            range = -100f..100f,
            onValueChange = { onAdjustmentsChanged(adjustments.copy(exposure = it)) },
        )

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${value.toInt()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                )
            }
        )
    }
}
