package com.trucdecomptable.garanties.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChiffrementTest {

    private val contenu = "Fiches, garanties et justificatifs".toByteArray()

    @Test
    fun `aller retour avec le bon mot de passe`() {
        val chiffre = Chiffrement.envelopper("motdepasse", contenu)
        assertArrayEquals(contenu, Chiffrement.deverrouiller(chiffre, "motdepasse"))
    }

    @Test
    fun `le chiffre ne contient pas le clair`() {
        val chiffre = Chiffrement.envelopper("motdepasse", contenu)
        val texte = String(chiffre, Charsets.ISO_8859_1)
        assertFalse(texte.contains("Fiches"))
    }

    @Test
    fun `l en tete identifie une sauvegarde`() {
        val chiffre = Chiffrement.envelopper("motdepasse", contenu)
        assertTrue(Chiffrement.estSauvegarde(chiffre))
        assertFalse(Chiffrement.estSauvegarde("PK\u0003\u0004zip".toByteArray()))
    }

    @Test(expected = Chiffrement.MauvaisMotDePasse::class)
    fun `mauvais mot de passe refuse`() {
        val chiffre = Chiffrement.envelopper("motdepasse", contenu)
        Chiffrement.deverrouiller(chiffre, "autre-chose")
    }

    @Test(expected = Chiffrement.MauvaisMotDePasse::class)
    fun `fichier etranger refuse`() {
        Chiffrement.deverrouiller("pas une sauvegarde".toByteArray(), "motdepasse")
    }

    @Test
    fun `deux sauvegardes du meme contenu different`() {
        val a = Chiffrement.envelopper("motdepasse", contenu)
        val b = Chiffrement.envelopper("motdepasse", contenu)
        // Sel et IV aléatoires : un même contenu ne produit jamais le même fichier.
        assertNotEquals(a.toList(), b.toList())
        assertArrayEquals(Chiffrement.deverrouiller(a, "motdepasse"), Chiffrement.deverrouiller(b, "motdepasse"))
    }

    @Test
    fun `fichier tronque refuse`() {
        val chiffre = Chiffrement.envelopper("motdepasse", contenu)
        val tronque = chiffre.copyOfRange(0, chiffre.size - 4)
        var leve = false
        try {
            Chiffrement.deverrouiller(tronque, "motdepasse")
        } catch (e: Chiffrement.MauvaisMotDePasse) {
            leve = true
        }
        assertTrue(leve)
    }

    @Test
    fun `csv echappe le separateur et les guillemets`() {
        val ligne = ExportManager.echapper("Bosch; modèle \"haut de gamme\"")
        assertTrue(ligne.startsWith("\""))
        assertTrue(ligne.endsWith("\""))
        assertTrue(ligne.contains("\"\""))
    }

    @Test
    fun `csvGaranties exporte une ligne par couverture`() {
        val appareils = listOf(
            com.trucdecomptable.garanties.data.Appareil(id = 1, nom = "Lave-linge"),
        )
        val garanties = listOf(
            com.trucdecomptable.garanties.data.Garantie(
                appareilId = 1, type = "Extension de garantie", organisme = "Darty",
                fin = "2029-03-15",
            ),
            com.trucdecomptable.garanties.data.Garantie(
                appareilId = 1, type = "Garantie constructeur", organisme = "Bosch", fin = "2028-03-15",
            ),
        )
        val csv = ExportManager.csvGaranties(appareils, garanties)
        val lignes = csv.trim().split("\r\n".toRegex())
        assertEqualsExport(3, lignes.size)
        assertTrueExport(lignes[1].startsWith("Lave-linge;"))
        assertTrueExport(lignes[1].contains("Garantie constructeur"))
        assertTrueExport(lignes[2].contains("Extension de garantie"))
    }

    @Test
    fun `csvDocuments liste les justificatifs avec leur fichier interne`() {
        val appareils = listOf(com.trucdecomptable.garanties.data.Appareil(id = 2, nom = "TV"))
        val documents = listOf(
            com.trucdecomptable.garanties.data.Document(
                appareilId = 2, type = "Facture", nom = "Facture Boulanger", chemin = "abc.pdf",
            ),
        )
        val csv = ExportManager.csvDocuments(appareils, documents)
        assertTrueExport(csv.contains("Facture Boulanger"))
        assertTrueExport(csv.contains("abc.pdf"))
        assertTrueExport(csv.startsWith("\uFEFF"))
    }

    private fun assertTrueExport(valeur: Boolean) = org.junit.Assert.assertTrue(valeur)

    private fun assertEqualsExport(attendu: Int, obtenu: Int) = org.junit.Assert.assertEquals(attendu, obtenu)

    @Test
    fun `csv conserve une valeur simple`() {
        assertEqualsSimple("Lave-linge", ExportManager.echapper("Lave-linge"))
    }

    private fun assertEqualsSimple(attendu: String, obtenu: String) {
        org.junit.Assert.assertEquals(attendu, obtenu)
    }
}
