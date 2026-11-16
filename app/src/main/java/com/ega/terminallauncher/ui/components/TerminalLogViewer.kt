package com.ega.terminallauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.ega.terminallauncher.ui.theme.ThemePalette

@Composable
fun TerminalLogViewer(
    logs: List<String>,
    palette: ThemePalette,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onLogClick: () -> Unit = {}
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onLogClick() }
    ) {
        items(logs) { line ->
            val textColor = when {
                line.startsWith("❯") -> palette.primary
                line.startsWith("[Exit: 0]") || line.startsWith("Exit: 0") -> palette.success
                line.startsWith("Error:") ||
                    line.startsWith("[Error:") ||
                    line.startsWith("[Command timed out") ||
                    line.startsWith("[Command force-killed") -> palette.error
                line.startsWith("[Running") || line.startsWith("[Launching") -> palette.warning
                line.startsWith("[") -> palette.warning
                else -> palette.text
            }

            Text(
                text = line,
                color = textColor,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            )
        }
    }
}
