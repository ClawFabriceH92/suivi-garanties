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
    fun `csv conserve une valeur simple`() {
        assertEqualsSimple("Lave-linge", ExportManager.echapper("Lave-linge"))
    }

    private fun assertEqualsSimple(attendu: String, obtenu: String) {
        org.junit.Assert.assertEquals(attendu, obtenu)
    }
}
