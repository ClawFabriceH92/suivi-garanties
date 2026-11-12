package com.trucdecomptable.garanties.metier

import com.trucdecomptable.garanties.data.Appareil
import com.trucdecomptable.garanties.data.Garantie
import com.trucdecomptable.garanties.data.TypesGarantie
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcheancesTest {

    private val aujourdhui = LocalDate.of(2026, 11, 12)

    private fun appareil(
        id: Long = 1,
        nom: String = "Lave-linge",
        fin: String = "",
        archive: Boolean = false,
    ) = Appareil(id = id, nom = nom, finGarantie = fin, archive = archive)

    @Test
    fun `moins de 30 jours est rouge`() {
        assertEquals(EtatGarantie.TRES_PROCHE, Echeances.etat(12))
    }

    @Test
    fun `30 jours pile reste tres proche`() {
        assertEquals(EtatGarantie.TRES_PROCHE, Echeances.etat(30))
    }

    @Test
    fun `31 jours passe en orange`() {
        assertEquals(EtatGarantie.PROCHE, Echeances.etat(31))
    }

    @Test
    fun `90 jours pile reste proche`() {
        assertEquals(EtatGarantie.PROCHE, Echeances.etat(90))
    }

    @Test
    fun `au dela de 90 jours est actif`() {
        assertEquals(EtatGarantie.ACTIVE, Echeances.etat(91))
    }

    @Test
    fun `jour de fin est encore sous garantie`() {
        assertEquals(EtatGarantie.TRES_PROCHE, Echeances.etat(0))
    }

    @Test
    fun `la veille de la fin est expiree`() {
        assertEquals(EtatGarantie.EXPIREE, Echeances.etat(-1))
    }

    @Test
    fun `date absente donne etat inconnu`() {
        assertEquals(EtatGarantie.INCONNUE, Echeances.etat(null))
    }

    @Test
    fun `jours restants calcules depuis la date ISO`() {
        assertEquals(19, Echeances.joursRestants("2026-12-01", aujourdhui))
    }

    @Test
    fun `date illisible ne donne pas de jours restants`() {
        assertNull(Echeances.joursRestants("2026-13-45", aujourdhui))
        assertNull(Echeances.joursRestants("", aujourdhui))
    }

    @Test
    fun `une extension de garantie sort l appareil du rouge`() {
        val machine = appareil(fin = "2026-11-20")
        val extension = Garantie(
            appareilId = 1,
            type = TypesGarantie.EXTENSION,
            fin = "2028-11-20",
        )
        assertEquals("2028-11-20", Echeances.finLaPlusLointaine(machine, listOf(extension)))
        assertTrue(Echeances.estSousGarantie(machine, listOf(extension), aujourdhui))
    }

    @Test
    fun `sans couverture secondaire la garantie principale fait foi`() {
        val machine = appareil(fin = "2026-10-01")
        assertEquals("2026-10-01", Echeances.finLaPlusLointaine(machine, emptyList()))
        assertFalse(Echeances.estSousGarantie(machine, emptyList(), aujourdhui))
    }

    @Test
    fun `les appareils archives sortent des echeances`() {
        val archive = appareil(id = 2, nom = "Vieux frigo", fin = "2026-12-01", archive = true)
        val actif = appareil(id = 1, nom = "Four", fin = "2026-12-01")
        val echeances = Echeances.echeances(listOf(archive, actif), emptyList(), aujourdhui)
        assertEquals(1, echeances.size)
        assertEquals("Four", echeances.first().appareilNom)
    }

    @Test
    fun `les echeances sont triees de la plus urgente a la plus lointaine`() {
        val a = appareil(id = 1, nom = "A", fin = "2027-01-30")
        val b = appareil(id = 2, nom = "B", fin = "2026-12-01")
        val echeances = Echeances.echeances(listOf(a, b), emptyList(), aujourdhui)
        assertEquals(listOf("B", "A"), echeances.map { it.appareilNom })
    }

    @Test
    fun `au dela de l horizon une echeance n est pas listee`() {
        val a = appareil(fin = "2028-01-01")
        assertTrue(Echeances.echeances(listOf(a), emptyList(), aujourdhui).isEmpty())
    }

    @Test
    fun `synthese compte les etats du parc`() {
        val appareils = listOf(
            appareil(id = 1, nom = "Actif", fin = "2027-06-01"),
            appareil(id = 2, nom = "Bientot", fin = "2026-12-01"),
            appareil(id = 3, nom = "Expire", fin = "2025-01-01"),
            appareil(id = 4, nom = "Archive", fin = "2027-01-01", archive = true),
        )
        val synthese = Echeances.synthese(
            appareils = appareils,
            garanties = emptyList(),
            documentsParAppareil = mapOf(1L to 2),
            derniereSauvegarde = 1234L,
            aujourdhui = aujourdhui,
        )
        assertEquals(3, synthese.total)
        assertEquals(2, synthese.sousGarantie)
        assertEquals(1, synthese.expirant90)
        assertEquals(1, synthese.expirees)
        assertEquals(1, synthese.archives)
        assertEquals(2, synthese.sansFacture)
        assertEquals(3, synthese.sansNumeroSerie)
        assertEquals(0, synthese.sansDateFin)
        assertEquals(1234L, synthese.derniereSauvegarde)
    }

    @Test
    fun `delai franchi retient le palier le plus proche`() {
        val paliers = listOf(90, 30, 7)
        assertEquals(90, Echeances.delaiFranchi(paliers, 85))
        assertEquals(30, Echeances.delaiFranchi(paliers, 25))
        assertEquals(7, Echeances.delaiFranchi(paliers, 3))
        assertNull(Echeances.delaiFranchi(paliers, 200))
    }

    @Test
    fun `jour de l expiration alerte si le palier zero existe`() {
        assertEquals(0, Echeances.delaiFranchi(listOf(90, 30, 7, 0), 0))
        // Sans palier « jour J », le dernier palier franchi reste 7 : la clé de
        // déduplication est la même, donc aucun second envoi.
        assertEquals(7, Echeances.delaiFranchi(listOf(90, 30, 7), 0))
        assertNull(Echeances.delaiFranchi(listOf(90, 30, 7), -4))
    }

    @Test
    fun `texte de notification selon les jours restants`() {
        assertEquals(
            "Four Bosch : la garantie expire aujourd'hui.",
            Echeances.texteNotification("Four Bosch", 0),
        )
        assertEquals(
            "Four Bosch sera hors garantie dans 30 jour(s).",
            Echeances.texteNotification("Four Bosch", 30),
        )
        assertEquals(
            "Four Bosch : garantie expirée depuis 3 jour(s).",
            Echeances.texteNotification("Four Bosch", -3),
        )
    }

    @Test
    fun `rappel de sauvegarde en retard`() {
        val maintenant = 1_700_000_000_000L
        assertTrue(Echeances.sauvegardeEnRetard(0, 90, maintenant))
        assertFalse(Echeances.sauvegardeEnRetard(maintenant - 10 * 86_400_000L, 90, maintenant))
        assertTrue(Echeances.sauvegardeEnRetard(maintenant - 200 * 86_400_000L, 90, maintenant))
        assertFalse(Echeances.sauvegardeEnRetard(0, 0, maintenant))
    }
}
