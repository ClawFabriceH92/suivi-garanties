package com.trucdecomptable.garanties.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.trucdecomptable.garanties.metier.EtatGarantie

/** Rouge < 30 jours, orange < 90 jours, vert actif, gris expiré. */
object CouleursEtat {
    val Rouge = Color(0xFFC62828)
    val Orange = Color(0xFFEF6C00)
    val Vert = Color(0xFF2E7D32)
    val Gris = Color(0xFF757575)

    fun de(etat: EtatGarantie): Color = when (etat) {
        EtatGarantie.TRES_PROCHE -> Rouge
        EtatGarantie.PROCHE -> Orange
        EtatGarantie.ACTIVE -> Vert
        EtatGarantie.EXPIREE -> Gris
        EtatGarantie.INCONNUE -> Gris
    }
}

private val clair = lightColorScheme(
    primary = Color(0xFF1B5E9B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F5),
    onPrimaryContainer = Color(0xFF0B2B4A),
    secondary = Color(0xFF2E7D6B),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7EBF0),
    error = Color(0xFFC62828),
)

private val sombre = darkColorScheme(
    primary = Color(0xFF9CC5F0),
    onPrimary = Color(0xFF06305A),
    primaryContainer = Color(0xFF15406B),
    onPrimaryContainer = Color(0xFFD6E4F5),
    secondary = Color(0xFF8FD5C4),
    background = Color(0xFF12161B),
    surface = Color(0xFF1A1F25),
    surfaceVariant = Color(0xFF2A3138),
    error = Color(0xFFEF9A9A),
)

@Composable
fun ThemeGaranties(sombreForce: Boolean = isSystemInDarkTheme(), contenu: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (sombreForce) sombre else clair,
        content = contenu,
    )
}
