package com.trucdecomptable.garanties.securite

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clés de l'application, protégées par le magasin de clés Android (Keystore) :
 * matériel ou TEE quand le téléphone en a un, donc jamais extractibles par une
 * simple copie de fichiers. Sert à deux choses :
 *
 * - la phrase secrète de la base SQLCipher (générée au hasard, l'utilisateur ne
 *   la voit jamais : la perdre n'est pas possible tant que le Keystore vit) ;
 * - la clé de chiffrement des justificatifs (factures, photos).
 *
 * Les valeurs sont stockées chiffrées en AES-GCM dans les préférences : copier
 * le dossier de l'application sur un autre téléphone ne donne rien.
 */
object CoffreSecret {

    private const val ALIAS_BASE = "garanties-db"
    private const val ALIAS_PIECES = "garanties-pieces"
    private const val PREFS = "garanties-cles"
    private const val CLE_BASE = "base"
    private const val CLE_PIECES = "pieces"

    private fun magasin(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun cle(alias: String): SecretKey {
        val ks = magasin()
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generateur.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generateur.generateKey()
    }

    private fun chiffrer(alias: String, clair: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, cle(alias))
        val iv = cipher.iv
        val chiffre = cipher.doFinal(clair)
        return Base64.getEncoder().encodeToString(byteArrayOf(iv.size.toByte()) + iv + chiffre)
    }

    private fun dechiffrer(alias: String, stocke: String): ByteArray {
        val brut = Base64.getDecoder().decode(stocke)
        val tailleIv = brut[0].toInt()
        val iv = brut.copyOfRange(1, 1 + tailleIv)
        val chiffre = brut.copyOfRange(1 + tailleIv, brut.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, cle(alias), GCMParameterSpec(128, iv))
        return cipher.doFinal(chiffre)
    }

    private fun secret(context: Context, alias: String, clePref: String): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stocke = prefs.getString(clePref, null)
        if (stocke != null) {
            runCatching { return dechiffrer(alias, stocke) }
                .onFailure { DiagSecurite.signaler("secret $clePref illisible : ${it.javaClass.simpleName}") }
        }
        val neuf = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(clePref, chiffrer(alias, neuf)).apply()
        return neuf
    }

    /** Phrase secrète de SQLCipher. Une copie neuve à chaque ouverture. */
    fun phraseBase(context: Context): ByteArray = secret(context, ALIAS_BASE, CLE_BASE)

    /** Clé AES-GCM des justificatifs. */
    fun clePieces(context: Context): SecretKey {
        val brute = secret(context, ALIAS_PIECES, CLE_PIECES)
        return javax.crypto.spec.SecretKeySpec(brute, "AES")
    }

    /** Une sauvegarde doit pouvoir se restaurer sur un autre téléphone : la phrase
     *  est alors rangée dans l'archive, elle-même protégée par le mot de passe. */
    fun phraseBaseBrute(context: Context): ByteArray = phraseBase(context)

    fun remplacerPhraseBase(context: Context, phrase: ByteArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(CLE_BASE, chiffrer(ALIAS_BASE, phrase))
            .apply()
    }

    /**
     * Verrouillage par code : compteur d'essais pour ne pas laisser deviner un
     * code à 4 chiffres par force brute. Le compteur vit dans les préférences de
     * l'application ; il survit au redémarrage et n'est remis à zéro qu'après
     * une saisie correcte.
     */
    fun essaisRestants(context: Context): Int =
        context.getSharedPreferences("garanties-verrou", Context.MODE_PRIVATE)
            .getInt("essais", MAX_ESSAIS)

    fun echecEssai(context: Context): Int {
        val prefs = context.getSharedPreferences("garanties-verrou", Context.MODE_PRIVATE)
        val restants = (prefs.getInt("essais", MAX_ESSAIS) - 1).coerceAtLeast(0)
        prefs.edit().putInt("essais", restants).apply()
        return restants
    }

    fun reinitialiserEssais(context: Context) {
        context.getSharedPreferences("garanties-verrou", Context.MODE_PRIVATE)
            .edit().putInt("essais", MAX_ESSAIS).apply()
    }

    const val MAX_ESSAIS = 5
}

/** Journal minimal, sans dépendance vers `util.Diag`, pour éviter un cycle. */
internal object DiagSecurite {
    private val lignes = ArrayDeque<String>()

    @Synchronized
    fun signaler(message: String) {
        lignes.addLast(message)
        while (lignes.size > 50) lignes.removeFirst()
    }

    @Synchronized
    fun dump(): String = lignes.joinToString("\n")
}
