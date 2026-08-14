package com.wordbattle.com.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wordbattle.com.R

val Baloo = FontFamily(
    Font(R.font.baloo2_variable, FontWeight.Normal),
    Font(R.font.baloo2_variable, FontWeight.SemiBold),
    Font(R.font.baloo2_variable, FontWeight.Bold),
    Font(R.font.baloo2_variable, FontWeight.ExtraBold)
)
val Nunito = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Normal),
    Font(R.font.nunito_variable, FontWeight.SemiBold),
    Font(R.font.nunito_variable, FontWeight.Bold),
    Font(R.font.nunito_variable, FontWeight.ExtraBold)
)

val WordBattleTypography = Typography(
    displayLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp),
    headlineLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp)
)
