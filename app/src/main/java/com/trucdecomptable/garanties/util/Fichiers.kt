package com.trucdecomptable.garanties.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.trucdecomptable.garanties.R
import java.io.File
import java.util.UUID

/**
 * Les justificatifs vivent dans le dossier privé de l'application
 * (filesDir/pieces). Rien n'est copié dans la galerie ni envoyé ailleurs :
 * une facture peut contenir l'adresse et le moyen de paiement de son porteur.
 */
object Fichiers {

    private const val DOSSIER = "pieces"

    fun dossier(context: Context): File =
        File(context.filesDir, DOSSIER).apply { mkdirs() }

    fun fichier(context: Context, nom: String): File = File(dossier(context), nom)

    fun existe(context: Context, nom: String): Boolean =
        nom.isNotBlank() && fichier(context, nom).exists()

    /**
     * Copie le document choisi par l'utilisateur dans le coffre et renvoie le
     * nom de fichier interne. L'URI d'origine (content://) n'est pas conservée :
     * elle expire, le document doit rester lisible hors ligne pendant des années.
     */
    fun importer(context: Context, uri: Uri): String? = runCatching {
        val nom = nomAffiche(context, uri)
        val extension = nom.substringAfterLast('.', "").lowercase().ifBlank { "bin" }
        val interne = "${UUID.randomUUID()}.$extension"
        val cible = fichier(context, interne)
        context.contentResolver.openInputStream(uri)?.use { entree ->
            cible.outputStream().use { sortie -> entree.copyTo(sortie) }
        } ?: return null
        if (cible.length() <= 0L) {
            cible.delete()
            return null
        }
        Diag.record("fichier", "importé $nom (${cible.length()} octets) → $interne")
        interne
    }.getOrElse {
        Diag.record("fichier", it)
        null
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
    }

    /** URI partageable pour une pièce (partage d'un dossier, export ponctuel). */
    fun uriPartage(context: Context, nom: String): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            fichier(context, nom),
        )

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

    @Suppress("unused")
    private val unused = R.string.app_name
}
