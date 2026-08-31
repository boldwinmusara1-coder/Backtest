package com.example.tradestrat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun IntParameterStepper(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    unit: String = ""
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BentoCardElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary)
                Text(
                    text = "$value $unit",
                    style = MaterialTheme.typography.titleMedium,
                    color = BentoLilac,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { if (value - step >= range.first) onValueChange(value - step) },
                    enabled = value - step >= range.first,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { if (value + step <= range.last) onValueChange(value + step) },
                    enabled = value + step <= range.last,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = BentoLilac, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun DoubleParameterStepper(
    title: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 0.1,
    unit: String = ""
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BentoCardElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary)
                Text(
                    text = String.format(java.util.Locale.US, "%.2f %s", value, unit),
                    style = MaterialTheme.typography.titleMedium,
                    color = BentoLilac,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { if (value - step >= range.start - 0.0001) onValueChange(Math.round((value - step) * 100.0) / 100.0) },
                    enabled = value - step >= range.start - 0.0001,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { if (value + step <= range.endInclusive + 0.0001) onValueChange(Math.round((value + step) * 100.0) / 100.0) },
                    enabled = value + step <= range.endInclusive + 0.0001,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = BentoLilac, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun DoubleParameterSlider(
    title: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    displayFormatter: (Double) -> String = { String.format("%.1f", it) },
    steps: Int = 0
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BentoCardElevated
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary)
                Text(
                    text = displayFormatter(value),
                    style = MaterialTheme.typography.titleMedium,
                    color = BentoLilac,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toDouble()) },
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = BentoLilac,
                    activeTrackColor = BentoLilac,
                    inactiveTrackColor = BentoBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
