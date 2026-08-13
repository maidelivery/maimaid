package org.rhythmeta.maimaid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun appTextFieldColors(
    accentColor: Color = MiuixTheme.colorScheme.primary,
    backgroundColor: Color = accentColor.copy(alpha = 0.3f),
): TextFieldColors = TextFieldDefaults.textFieldColors(
    backgroundColor = backgroundColor,
    labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    borderColor = accentColor,
)
