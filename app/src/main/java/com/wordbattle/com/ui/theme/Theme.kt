package com.wordbattle.com.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WordBattleColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleLight,
    secondary = Gold,
    onSecondary = Ink,
    tertiary = Teal,
    error = Red,
    background = PurpleDark,
    onBackground = Color.White,
    surface = CardWhite,
    onSurface = Ink
)

@Composable
fun WordBattleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WordBattleColors, typography = WordBattleTypography, content = content)
}
