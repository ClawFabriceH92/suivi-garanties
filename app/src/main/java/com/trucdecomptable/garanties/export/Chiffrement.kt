package com.trucdecomptable.garanties.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sauvegarde chiffrée : AES-GCM avec une clé dérivée du mot de passe choisi par
 * l'utilisateur (PBKDF2, 120 000 itérations). Le fichier porte un en-tête
 * lisible qui permet de reconnaître une sauvegarde de l'application et de
 * refuser proprement un fichier étranger ou un mot de passe erroné.
 */
object Chiffrement {

    const val MAGIE = "GARANTIES1"
    const val SEL_OCTETS = 16
    const val IV_OCTETS = 12
    const val TAG_BITS = 128
    const val ITERATIONS = 120_000

    class MauvaisMotDePasse : Exception("mot de passe incorrect ou fichier altéré")

    fun sel(): ByteArray = ByteArray(SEL_OCTETS).also { SecureRandom().nextBytes(it) }

    fun iv(): ByteArray = ByteArray(IV_OCTETS).also { SecureRandom().nextBytes(it) }

    fun cle(motDePasse: String, sel: ByteArray, iterations: Int = ITERATIONS): SecretKey {
        val spec = PBEKeySpec(motDePasse.toCharArray(), sel, iterations, 256)
        val derivee = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(derivee.encoded, "AES")
    }

    fun enveloppe(sortie: OutputStream, motDePasse: String, contenu: ByteArray) {
        val sel = sel()
        val iv = iv()
        sortie.write(MAGIE.toByteArray())
        sortie.write(byteArrayOf(sel.size.toByte(), iv.size.toByte()))
        sortie.write(sel)
        sortie.write(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, cle(motDePasse, sel), GCMParameterSpec(TAG_BITS, iv))
        CipherOutputStream(sortie, cipher).use { clair -> clair.write(contenu) }
    }

    fun envelopper(motDePasse: String, contenu: ByteArray): ByteArray =
        ByteArrayOutputStream().also { enveloppe(it, motDePasse, contenu) }.toByteArray()

    /** Vérifie l'en-tête sans consommer le mot de passe (utile pour l'UI). */
    fun estSauvegarde(entete: ByteArray): Boolean =
        entete.size >= MAGIE.length && String(entete, 0, MAGIE.length, Charsets.US_ASCII) == MAGIE

    fun deverrouiller(entree: InputStream, motDePasse: String): InputStream {
        val entete = entree.readNBytes(MAGIE.length)
        if (!estSauvegarde(entete)) throw MauvaisMotDePasse()
        val tailles = entree.readNBytes(2)
        if (tailles.size < 2) throw MauvaisMotDePasse()
        val sel = entree.readNBytes(tailles[0].toInt())
        val iv = entree.readNBytes(tailles[1].toInt())
        if (sel.size != tailles[0].toInt() || iv.size != tailles[1].toInt()) throw MauvaisMotDePasse()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, cle(motDePasse, sel), GCMParameterSpec(TAG_BITS, iv))
        return CipherInputStream(entree, cipher)
    }

    /** Déchiffre en mémoire ; lève [MauvaisMotDePasse] si le tag GCM ne colle pas. */
    fun deverrouiller(contenu: ByteArray, motDePasse: String): ByteArray = try {
        deverrouiller(ByteArrayInputStream(contenu), motDePasse).use { it.readBytes() }
    } catch (e: Exception) {
        throw MauvaisMotDePasse()
    }
}
