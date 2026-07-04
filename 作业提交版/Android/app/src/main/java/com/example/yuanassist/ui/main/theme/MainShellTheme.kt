package com.example.yuanassist.ui.main.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TitleInk = Color(0xFF75322D)
val BodyInk = Color(0xFF8A6B5E)
val QuietInk = Color(0xFFAA8978)
val PaperLine = Color(0xFFC79C5C)
val WarmRose = Color(0xFFD79A86)
val GlassPanel = Color(0xFFFFF8EA)
val GlassStroke = Color(0xCCB57A45)
val ConsolePanel = Color(0xFFFFFCF4)
val HighlightGold = Color(0xFF9A6435)

private val MainShellColorScheme = lightColorScheme(
    primary = TitleInk,
    secondary = WarmRose,
    tertiary = HighlightGold,
    background = Color(0xFFFFF7EA),
    surface = GlassPanel,
    onPrimary = Color.White,
    onSecondary = TitleInk,
    onTertiary = Color.White,
    onBackground = TitleInk,
    onSurface = TitleInk,
)

@Composable
fun MainShellTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MainShellColorScheme,
        content = content,
    )
}
