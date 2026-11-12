package com.trucdecomptable.garanties.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.trucdecomptable.garanties.securite.CoffreSecret
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec

/**
 * Les justificatifs vivent dans le dossier privé de l'application
 * (filesDir/pieces) et sont **chiffrés au repos** en AES-GCM avec une clé du
 * Keystore Android. Une facture contient l'adresse, le moyen de paiement et le
 * numéro de série de son porteur : le seul bac à sable d'Android ne suffisait pas.
 *
 * Lecture : [clair] produit une copie déchiffrée dans le cache, le temps de
 * l'ouvrir, de l'analyser ou de la partager. Les fichiers importés par une
 * version antérieure (non chiffrés) restent lisibles : l'en-tête décide.
 */
object Fichiers {

    private const val DOSSIER = "pieces"
    private const val CACHE = "pieces-clair"
    private val MAGIE = byteArrayOf('G'.code.toByte(), 'C'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())
    private const val IV_OCTETS = 12

    fun dossier(context: Context): File =
        File(context.filesDir, DOSSIER).apply { mkdirs() }

    fun fichier(context: Context, nom: String): File = File(dossier(context), nom)

    fun existe(context: Context, nom: String): Boolean =
        nom.isNotBlank() && fichier(context, nom).exists()

    fun cache(context: Context): File = File(context.cacheDir, CACHE).apply { mkdirs() }

    /**
     * Copie le document choisi par l'utilisateur dans le coffre, chiffré, et
     * renvoie le nom de fichier interne. L'URI d'origine (content://) n'est pas
     * conservée : elle expire, le document doit rester lisible hors ligne pendant
     * des années.
     */
    fun importer(context: Context, uri: Uri): String? = runCatching {
        val nom = nomAffiche(context, uri)
        val extension = nom.substringAfterLast('.', "").lowercase().ifBlank { "bin" }
        val interne = "${UUID.randomUUID()}.$extension"
        val cible = fichier(context, interne)
        context.contentResolver.openInputStream(uri)?.use { entree ->
            chiffrerFlux(context, entree, cible.outputStream())
        } ?: return null
        if (cible.length() <= MAGIE.size + IV_OCTETS) {
            cible.delete()
            return null
        }
        Diag.record("fichier", "importé et chiffré $nom (${cible.length()} octets) → $interne")
        interne
    }.getOrElse {
        Diag.record("fichier", it)
        null
    }

    private fun chiffrerFlux(context: Context, entree: InputStream, sortie: OutputStream) {
        val iv = ByteArray(IV_OCTETS).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, CoffreSecret.clePieces(context), GCMParameterSpec(128, iv))
        sortie.use { destination ->
            destination.write(MAGIE)
            destination.write(byteArrayOf(iv.size.toByte()))
            destination.write(iv)
            CipherOutputStream(destination, cipher).use { chiffre ->
                entree.use { it.copyTo(chiffre) }
            }
        }
    }

    /** Écrit un flux déjà déchiffré dans le coffre, en le chiffrant pour ce téléphone. */
    fun enregistrerChiffre(context: Context, nom: String, entree: InputStream): String? = runCatching {
        val cible = fichier(context, nom)
        chiffrerFlux(context, entree, cible.outputStream())
        cible.name
    }.getOrElse {
        Diag.record("fichier", "écriture chiffrée impossible ($nom) : ${it.message}")
        null
    }

    fun estChiffre(fichier: File): Boolean {
        if (!fichier.exists() || fichier.length() < MAGIE.size + 1 + IV_OCTETS) return false
        val entete = ByteArray(MAGIE.size)
        runCatching { fichier.inputStream().use { it.read(entete) } }
        return entete.contentEquals(MAGIE)
    }

    /**
     * Fichier lisible par une application externe (visionneuse PDF, ML Kit) :
     * déchiffré dans le cache si besoin, sinon renvoyé tel quel (fichier importé
     * par une version antérieure).
     */
    fun clair(context: Context, nom: String): File {
        val source = fichier(context, nom)
        if (!source.exists() || !estChiffre(source)) return source
        val cible = File(cache(context), nom)
        if (cible.exists() && cible.lastModified() >= source.lastModified()) return cible
        runCatching {
            source.inputStream().use { entree ->
                val magie = entree.readNBytes(MAGIE.size)
                if (!magie.contentEquals(MAGIE)) throw IllegalStateException("en-tête inattendu")
                val tailleIv = entree.readNBytes(1).first().toInt()
                val iv = entree.readNBytes(tailleIv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, CoffreSecret.clePieces(context), GCMParameterSpec(128, iv))
                CipherInputStream(entree, cipher).use { dechiffre ->
                    cible.outputStream().use { sortie -> dechiffre.copyTo(sortie) }
                }
            }
        }.onFailure { Diag.record("fichier", "déchiffrement impossible ($nom) : ${it.message}") }
        return cible
    }

    /**
     * Chiffre les pièces importées par une version antérieure (avant SQLCipher).
     * Sans cela, la promesse « tout est chiffré au repos » serait fausse pour les
     * factures déjà en place : elles resteraient lisibles dans le dossier privé.
     */
    fun chiffrerExistants(context: Context): Int {
        var converties = 0
        runCatching {
            dossier(context).listFiles()?.forEach { piece ->
                if (piece.isFile && !estChiffre(piece)) {
                    val temporaire = File(piece.parentFile, "${piece.name}.tmp")
                    runCatching {
                        piece.inputStream().use { entree ->
                            chiffrerFlux(context, entree, temporaire.outputStream())
                        }
                        if (temporaire.length() > MAGIE.size + 1 + IV_OCTETS) {
                            piece.delete()
                            temporaire.renameTo(piece)
                            converties++
                        } else {
                            temporaire.delete()
                        }
                    }.onFailure {
                        temporaire.delete()
                        Diag.record("fichier", "conversion impossible (${piece.name}) : ${it.message}")
                    }
                }
            }
        }
        if (converties > 0) Diag.record("fichier", "$converties pièce(s) converties en chiffré")
        return converties
    }

    /** Purge les copies déchiffrées (appelée quand l'écran de verrouillage revient). */
    fun purgerCache(context: Context) {
        runCatching { cache(context).listFiles()?.forEach { it.delete() } }
    }

    fun nomAffiche(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { curseur ->
                val index = curseur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && curseur.moveToFirst()) {
                    val valeur = curseur.getString(index)
                    if (!valeur.isNullOrBlank()) return valeur
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    fun mime(nom: String): String = when (nom.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "txt", "csv" -> "text/plain"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    fun supprimer(context: Context, nom: String) {
        if (nom.isBlank()) return
        runCatching { fichier(context, nom).delete() }
        runCatching { File(cache(context), nom).delete() }
    }

    /** URI partageable pour une pièce : toujours la version déchiffrée. */
    fun uriPartage(context: Context, nom: String): Uri = uriPartageFichier(context, clair(context, nom))

    fun uriPartageFichier(context: Context, fichier: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            fichier,
        )

    /** Taille lisible, pour l'écran de sauvegarde. */
    fun tailleLisible(octets: Long): String = when {
        octets >= 1_073_741_824 -> String.format("%.1f Go", octets / 1_073_741_824.0)
        octets >= 1_048_576 -> String.format("%.1f Mo", octets / 1_048_576.0)
        octets >= 1024 -> String.format("%.0f ko", octets / 1024.0)
        else -> "$octets o"
    }

    fun nomSansAccent(texte: String): String =
        java.text.Normalizer.normalize(texte, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""[^A-Za-z0-9 ._-]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "sans-nom" }
}
