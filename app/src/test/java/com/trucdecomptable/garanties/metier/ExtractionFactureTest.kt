package com.trucdecomptable.garanties.metier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ces textes imitent la sortie de l'OCR sur de vraies factures françaises
 * (ML Kit rend le texte ligne par ligne, avec les montants à virgule).
 */
class ExtractionFactureTest {

    private val factureDarty = """
        DARTY PARIS WAGRAM
        23 avenue de Wagram
        75017 PARIS
        Tél : 01 45 00 00 00
        FACTURE N° FA2026-0456
        Date : 15/03/2026
        Client : M HEUVRARD
        LAVE-LINGE BOSCH WAN28280FF
        Réf. WAN28280FF
        Qté 1
        Total HT 515,83
        TVA 20% 103,17
        Total TTC 619,00
        Garantie 2 ans
        Carte bancaire
        Merci de votre visite
    """.trimIndent()

    private val ticketFnac = """
        FNAC PARIS MONTPARNASSE
        136 rue de Rennes
        75006 PARIS
        Ticket n° 0045218
        le 02/01/2026 à 15:32
        PC PORTABLE ASUS ZENBOOK 14 OLED
        N° de série : N4N0CV12345678
        Quantité : 1
        Prix TTC 1 299,99 €
        dont TVA 20% 216,67 €
        ESPÈCES
    """.trimIndent()

    private val commandeAmazon = """
        AMAZON FR
        Commande n° 171-2345678-9012345
        Date de commande : 20/11/2025
        Expédition à : M. Heuvrard
        1 x SSD SAMSUNG 990 PRO 2 To        179,99 €
        1 x CLAVIER LOGITECH MX KEYS        109,00 €
        Total TTC                           288,99 €
        Carte bancaire
    """.trimIndent()

    private val factureBoulanger = """
        BOULANGER
        Centre commercial Les Tanneurs
        59000 LILLE
        Facture 2024/004512
        Date 08/06/2024
        TELEVISEUR LG OLED55C4
        Garantie 2 ans constructeur
        Montant TTC : 1 490,00
    """.trimIndent()

    @Test
    fun `vendeur lu dans l entete`() {
        assertEquals("DARTY PARIS WAGRAM", ExtractionFacture.analyser(factureDarty).vendeur)
    }

    @Test
    fun `adresse du vendeur reconstruite`() {
        val extrait = ExtractionFacture.analyser(factureDarty)
        assertTrue(extrait.adresseVendeur.contains("23 avenue de Wagram"))
        assertTrue(extrait.adresseVendeur.contains("75017"))
    }

    @Test
    fun `date d achat prioritaire sur les autres dates`() {
        assertEquals("2026-03-15", ExtractionFacture.analyser(factureDarty).dateAchat)
    }

    @Test
    fun `numero de facture extrait`() {
        assertEquals("FA2026-0456", ExtractionFacture.analyser(factureDarty).numeroFacture)
    }

    @Test
    fun `numero de ticket extrait`() {
        assertEquals("0045218", ExtractionFacture.analyser(ticketFnac).numeroFacture)
    }

    @Test
    fun `numero de commande extrait`() {
        assertEquals("171-2345678-9012345", ExtractionFacture.analyser(commandeAmazon).numeroFacture)
    }

    @Test
    fun `designation produit sans le montant`() {
        val extrait = ExtractionFacture.analyser(factureDarty)
        assertEquals("LAVE-LINGE BOSCH WAN28280FF", extrait.designation)
    }

    @Test
    fun `marque reconnue dans le texte`() {
        assertEquals("Bosch", ExtractionFacture.analyser(factureDarty).marque)
        assertEquals("Asus", ExtractionFacture.analyser(ticketFnac).marque)
        assertEquals("Samsung", ExtractionFacture.analyser(commandeAmazon).marque)
    }

    @Test
    fun `categorie deduite de la designation`() {
        assertEquals("Électroménager", ExtractionFacture.analyser(factureDarty).categorie)
        assertEquals("Informatique", ExtractionFacture.analyser(ticketFnac).categorie)
        assertEquals("Télévision et audio", ExtractionFacture.analyser(factureBoulanger).categorie)
    }

    @Test
    fun `modele lu sur la ligne de reference`() {
        assertEquals("WAN28280FF", ExtractionFacture.analyser(factureDarty).modele)
    }

    @Test
    fun `numero de serie lu sur la ligne dediee`() {
        assertEquals("N4N0CV12345678", ExtractionFacture.analyser(ticketFnac).numeroSerie)
    }

    @Test
    fun `numero de serie absent ne doit pas etre invente`() {
        assertEquals("", ExtractionFacture.analyser(factureDarty).numeroSerie)
    }

    @Test
    fun `total ttc prix ht et tva`() {
        val extrait = ExtractionFacture.analyser(factureDarty)
        assertEquals(619.00, extrait.prixTTC!!, 0.001)
        assertEquals(515.83, extrait.prixHT!!, 0.001)
        assertEquals(103.17, extrait.tva!!, 0.001)
        assertEquals(20.0, extrait.tauxTva!!, 0.001)
    }

    @Test
    fun `total ttc retenu meme sans mot total`() {
        val extrait = ExtractionFacture.analyser(ticketFnac)
        assertEquals(1299.99, extrait.prixTTC!!, 0.001)
        assertEquals(1083.33, extrait.prixHT!!, 0.001)
    }

    @Test
    fun `quantite lue`() {
        assertEquals(1, ExtractionFacture.analyser(ticketFnac).quantite)
    }

    @Test
    fun `moyen de paiement reconnu`() {
        assertEquals("Carte bancaire", ExtractionFacture.analyser(factureDarty).moyenPaiement)
        assertEquals("Espèces", ExtractionFacture.analyser(ticketFnac).moyenPaiement)
    }

    @Test
    fun `duree de garantie et date de fin calculee`() {
        val extrait = ExtractionFacture.analyser(factureDarty)
        assertEquals(24, extrait.dureeGarantieMois)
        assertEquals("2028-03-15", extrait.finGarantie)
    }

    @Test
    fun `duree par defaut quand la facture n en parle pas`() {
        val extrait = ExtractionFacture.analyser(commandeAmazon)
        assertNull(extrait.dureeGarantieMois)
        assertEquals(24, extrait.dureeProposee)
        assertEquals("2027-11-20", extrait.finCalculee)
    }

    @Test
    fun `facture multi produits propose la repartition`() {
        val extrait = ExtractionFacture.analyser(commandeAmazon)
        assertEquals(2, extrait.produits.size)
        assertTrue(extrait.produits.any { it.prixTTC == 179.99 })
        assertTrue(extrait.produits.any { it.prixTTC == 109.00 })
    }

    @Test
    fun `les lignes de total ne sont pas des produits`() {
        val extrait = ExtractionFacture.analyser(factureDarty)
        assertTrue(extrait.produits.none { it.designation.contains("Total", ignoreCase = true) })
    }

    @Test
    fun `texte vide ne produit aucune invention`() {
        val extrait = ExtractionFacture.analyser("")
        assertEquals("", extrait.vendeur)
        assertEquals("", extrait.dateAchat)
        assertNull(extrait.prixTTC)
        assertTrue(extrait.produits.isEmpty())
    }

    @Test
    fun `montants francais correctement parses`() {
        assertEquals(listOf(1299.0), ExtractionFacture.montants("Prix 1 299,00 €"))
        assertEquals(listOf(49.9), ExtractionFacture.montants("Total 49,9"))
        assertEquals(listOf(1299.0), ExtractionFacture.montants("Total 1299.00"))
        assertTrue(ExtractionFacture.montants("TVA 20%").isEmpty())
    }

    @Test
    fun `douze mois donne une fin a un an`() {
        assertEquals(12, ExtractionFacture.detecterDureeGarantie("garantie 1 an pièces et main d'oeuvre"))
        assertEquals(36, ExtractionFacture.detecterDureeGarantie("Extension de garantie 3 ans"))
        assertEquals(24, ExtractionFacture.detecterDureeGarantie("garantie constructeur 24 mois"))
        assertNull(ExtractionFacture.detecterDureeGarantie("aucune mention ici"))
    }

    @Test
    fun `categorie par mots cles`() {
        assertEquals("Électroménager", ExtractionFacture.detecterCategorie("aspirateur dyson v15"))
        assertEquals("Téléphone et tablette", ExtractionFacture.detecterCategorie("iphone 15 pro 256 go"))
        assertEquals("Mobilier", ExtractionFacture.detecterCategorie("canapé 3 places"))
        assertEquals("Outillage", ExtractionFacture.detecterCategorie("perceuse visseuse 18v"))
        assertEquals("Autre", ExtractionFacture.detecterCategorie("objet non identifié"))
    }
}
