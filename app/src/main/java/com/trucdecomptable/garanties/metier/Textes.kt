package com.trucdecomptable.garanties.metier

/**
 * Mise en forme des libellés lus par l'OCR. Une facture imprime souvent le
 * produit en capitales (« LAVE-LINGE BOSCH WAN28280FF ») : tel quel, le nom
 * proposé est désagréable dans une liste. On remet une capitale initiale, en
 * laissant intactes les références techniques (lettres + chiffres).
 */
object Textes {

    fun joliNom(texte: String): String {
        val propre = texte.replace(Regex("""\s+"""), " ").trim()
        if (propre.isBlank()) return propre
        val mots = propre.split(" ").map { mot -> joliMot(mot) }
        return mots.joinToString(" ")
    }

    private fun joliMot(mot: String): String {
        if (mot.isBlank()) return mot
        val lettres = mot.count { it.isLetter() }
        val chiffres = mot.count { it.isDigit() }
        // Référence de modèle (WAN28280FF, 2026, 990-PRO) : on n'y touche pas.
        if (chiffres > 0 && lettres > 0) return mot
        if (chiffres > 0) return mot
        if (mot.length <= 3) return mot.uppercase()
        return mot.lowercase().replaceFirstChar { it.titlecase() }
    }
}
