package com.trucdecomptable.garanties.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatesTest {

    @Test
    fun `lit une date ISO`() {
        assertEquals(LocalDate.of(2026, 3, 12), Dates.parse("2026-03-12"))
    }

    @Test
    fun `lit une date francaise`() {
        assertEquals(LocalDate.of(2026, 3, 12), Dates.lireSouple("12/03/2026"))
    }

    @Test
    fun `lit un annee a deux chiffres`() {
        assertEquals(LocalDate.of(2024, 12, 1), Dates.lireSouple("01/12/24"))
    }

    @Test
    fun `lit un point comme separateur`() {
        assertEquals(LocalDate.of(2025, 7, 4), Dates.lireSouple("04.07.2025"))
    }

    @Test
    fun `ignore le texte autour de la date`() {
        assertEquals(LocalDate.of(2026, 1, 9), Dates.lireSouple("Date : 09/01/2026 - caisse 3"))
    }

    @Test
    fun `refuse le vide et le bruit`() {
        assertNull(Dates.lireSouple(""))
        assertNull(Dates.lireSouple("total 129,90"))
        assertNull(Dates.parse("pas une date"))
    }

    @Test
    fun `ajoute des mois en fin de mois`() {
        assertEquals(LocalDate.of(2026, 2, 28), Dates.ajouterMois(LocalDate.of(2026, 1, 31), 1))
        assertEquals(LocalDate.of(2026, 3, 31), Dates.ajouterMois(LocalDate.of(2026, 1, 31), 2))
    }

    @Test
    fun `ajoute deux ans a une date d achat`() {
        assertEquals(LocalDate.of(2028, 9, 5), Dates.ajouterMois(LocalDate.of(2026, 9, 5), 24))
    }

    @Test
    fun `jours restants negatifs une fois expiree`() {
        assertEquals(-2, Dates.joursRestants(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun `affiche une date francaise`() {
        assertEquals("05/09/2026", Dates.afficher("2026-09-05"))
        assertEquals("", Dates.afficher(""))
    }
}
