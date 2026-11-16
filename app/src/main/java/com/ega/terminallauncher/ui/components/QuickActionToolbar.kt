package com.ega.terminallauncher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ega.terminallauncher.ui.theme.ThemePalette

@Composable
fun QuickActionToolbar(
    palette: ThemePalette,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onHistoryPrev: () -> Unit,
    onHistoryNext: () -> Unit,
    onTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarButton(label = "Clear", palette = palette, modifier = Modifier.weight(1f), onClick = onClear)
        ToolbarButton(label = "Paste", palette = palette, modifier = Modifier.weight(1f), onClick = onPaste)
        ToolbarButton(label = "▲", palette = palette, modifier = Modifier.weight(0.8f), onClick = onHistoryPrev)
        ToolbarButton(label = "▼", palette = palette, modifier = Modifier.weight(0.8f), onClick = onHistoryNext)
        ToolbarButton(label = "Tab", palette = palette, modifier = Modifier.weight(1f), onClick = onTab)
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .background(palette.surface, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, palette.secondary.copy(alpha = 0.6f)), RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = palette.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
