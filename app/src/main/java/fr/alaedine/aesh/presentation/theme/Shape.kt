package fr.alaedine.aesh.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounder corners than Material 3's defaults (4/8/12/16/28dp), for a softer,
 * friendlier look on every component that reads its shape from the theme:
 * text fields, menus and snackbars (extra small), chips (small), cards
 * (medium), floating action buttons (large) and dialogs (extra large).
 */
val Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
