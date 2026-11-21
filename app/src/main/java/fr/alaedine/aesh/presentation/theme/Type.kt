package fr.alaedine.aesh.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val MaterialTypography = Typography()

/**
 * Material 3's type scale with heavier headings: screen titles (title large)
 * and headlines stand out from the body text they introduce instead of
 * sharing its regular weight. Sizes and line heights stay Material's, so
 * every style keeps scaling with the user's font size setting.
 */
val Typography =
    MaterialTypography.copy(
        headlineLarge = MaterialTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = MaterialTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = MaterialTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = MaterialTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
    )
