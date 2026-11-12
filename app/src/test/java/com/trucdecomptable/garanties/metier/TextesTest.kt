package com.trucdecomptable.garanties.metier

import org.junit.Assert.assertEquals
import org.junit.Test

class TextesTest {

    @Test
    fun `une designation en capitales redevient lisible`() {
        assertEquals("Lave-linge Bosch WAN28280FF", Textes.joliNom("LAVE-LINGE BOSCH WAN28280FF"))
    }

    @Test
    fun `les references techniques ne sont pas touchees`() {
        assertEquals("WAN28280FF", Textes.joliNom("WAN28280FF"))
        assertEquals("OLED55C4", Textes.joliNom("OLED55C4"))
        assertEquals("990 PRO", Textes.joliNom("990 PRO"))
    }

    @Test
    fun `les mots courts restent en capitales`() {
        assertEquals("TV LG", Textes.joliNom("TV LG"))
    }

    @Test
    fun `espaces multiples reduits`() {
        assertEquals("Aspirateur Dyson", Textes.joliNom("  ASPIRATEUR    DYSON "))
    }

    @Test
    fun `texte vide reste vide`() {
        assertEquals("", Textes.joliNom(""))
    }
}
