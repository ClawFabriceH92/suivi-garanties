package com.trucdecomptable.garanties.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `compare les versions segment par segment`() {
        assertEquals(1, UpdateChecker.compareVersions("0.10.0", "0.9.0"))
        assertEquals(-1, UpdateChecker.compareVersions("1.0.0", "1.0.1"))
        assertEquals(0, UpdateChecker.compareVersions("1.2.3", "1.2.3"))
        assertEquals(1, UpdateChecker.compareVersions("1.2", "1.1.9"))
    }

    @Test
    fun `manifeste avec une version plus recente`() {
        val json = """{"version":"1.2.0","notes":"corrections","apk":"https://exemple/app-release.apk"}"""
        val resultat = UpdateChecker.parseManifest(json, "1.1.0")
        assertTrue(resultat is UpdateChecker.CheckResult.Available)
        val info = (resultat as UpdateChecker.CheckResult.Available).info
        assertEquals("1.2.0", info.version)
        assertEquals("https://exemple/app-release.apk", info.apkUrl)
    }

    @Test
    fun `manifeste a jour`() {
        val json = """{"version":"1.2.0"}"""
        assertTrue(UpdateChecker.parseManifest(json, "1.2.0") is UpdateChecker.CheckResult.UpToDate)
    }

    @Test
    fun `manifeste illisible ne conclut pas`() {
        assertNull(UpdateChecker.parseManifest("pas du json", "1.0.0"))
    }

    @Test
    fun `manifeste sans version numerique ignore`() {
        assertNull(UpdateChecker.parseManifest("""{"version":"latest"}""", "1.0.0"))
    }

    @Test
    fun `api github retient la version la plus recente avec un apk`() {
        val json = """
            [
              {"tag_name":"latest","name":"Mes Garanties v1.3.0","draft":false,"assets":[
                {"name":"app-release.apk","browser_download_url":"https://exemple/1.3.0.apk"}]},
              {"tag_name":"v1.1.0","name":"v1.1.0","draft":false,"assets":[
                {"name":"app-v1.1.0.apk","browser_download_url":"https://exemple/1.1.0.apk"}]}
            ]
        """.trimIndent()
        val resultat = UpdateChecker.parseApi(json, "1.0.0")
        assertTrue(resultat is UpdateChecker.CheckResult.Available)
        assertEquals("1.3.0", (resultat as UpdateChecker.CheckResult.Available).info.version)
    }

    @Test
    fun `api github ignore les brouillons`() {
        val json = """
            [{"tag_name":"v2.0.0","name":"v2.0.0","draft":true,"assets":[
              {"name":"app-release.apk","browser_download_url":"https://exemple/2.0.0.apk"}]}]
        """.trimIndent()
        assertTrue(UpdateChecker.parseApi(json, "1.0.0") is UpdateChecker.CheckResult.UpToDate)
    }

    @Test
    fun `release sans apk ignoree`() {
        val json = """
            [{"tag_name":"v2.0.0","name":"v2.0.0","draft":false,"assets":[
              {"name":"version.json","browser_download_url":"https://exemple/version.json"}]}]
        """.trimIndent()
        assertTrue(UpdateChecker.parseApi(json, "1.0.0") is UpdateChecker.CheckResult.UpToDate)
    }

    @Test
    fun `api illisible ne conclut pas`() {
        assertNull(UpdateChecker.parseApi("{}", "1.0.0"))
    }

    @Test
    fun `extrait un numero de version du libelle`() {
        assertEquals("1.4.2", UpdateChecker.extractVersion("Mes Garanties v1.4.2"))
        assertNull(UpdateChecker.extractVersion("latest"))
    }

    @Test
    fun `plancher horaire respecte`() {
        val maintenant = 10_000_000L
        assertTrue(UpdateSchedule.isDue(true, maintenant, maintenant))
        assertTrue(UpdateSchedule.isDue(false, UpdateSchedule.NEVER, maintenant))
        assertTrue(UpdateSchedule.isDue(false, maintenant - 61 * 60_000L, maintenant))
        assertTrue(!UpdateSchedule.isDue(false, maintenant - 30 * 60_000L, maintenant))
    }

    @Test
    fun `erreur http expliquee`() {
        assertNotNull(UpdateChecker.httpReason(403, "0"))
        assertTrue(UpdateChecker.httpReason(403, "0").contains("quota"))
        assertTrue(UpdateChecker.httpReason(404, null).contains("404"))
    }

    @Test
    fun `depot public et lien de telechargement stable`() {
        assertEquals("ClawFabriceH92/suivi-garanties", UpdateChecker.REPO)
        assertTrue(UpdateChecker.APK_URL.endsWith("/releases/download/latest/app-release.apk"))
        assertTrue(UpdateChecker.MANIFEST_URL.endsWith("/releases/download/latest/version.json"))
    }
}
