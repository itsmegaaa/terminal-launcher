package com.ega.terminallauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ega.terminallauncher.ui.theme.ThemePalette

@Composable
fun TopHeader(
    palette: ThemePalette,
    systemInfo: String,
    modifier: Modifier = Modifier
) {
    val asciiBanner = """
▓█████   ▄████  ▄▄▄      
▓█   ▀  ██▒ ▀█▒▒████▄    
▒███   ▒██░▄▄▄░▒██  ▀█▄  
▒▓█  ▄ ░▓█  ██▓░██▄▄▄▄██ 
░▒████▒░▒▓███▀▒ ▓█   ▓██▒
░░ ▒░ ░ ░▒   ▒  ▒▒   ▓▒█░
 ░ ░  ░  ░   ░   ▒   ▒▒ ░
   ░   ░ ░   ░   ░   ▒   
   ░  ░      ░       ░  ░
""".trimIndent()

    val diamondSeparator = "\u25C8 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 \u25C8"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = asciiBanner,
            color = palette.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center
        )

        if (systemInfo.isNotBlank()) {
            Text(
                text = systemInfo,
                color = palette.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        Text(
            text = diamondSeparator,
            color = palette.secondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
