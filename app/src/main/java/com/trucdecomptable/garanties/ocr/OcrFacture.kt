package com.trucdecomptable.garanties.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.trucdecomptable.garanties.util.Diag
import com.trucdecomptable.garanties.util.Fichiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reconnaissance de texte 100 % locale (ML Kit embarqué) : la facture ne sort
 * jamais du téléphone, aucune connexion n'est nécessaire. Un PDF importé est
 * rendu en image page par page, faute de quoi l'OCR n'aurait rien à lire.
 */
object OcrFacture {

    private const val PAGES_MAX = 3

    data class Resultat(val texte: String, val pages: Int)

    suspend fun lire(context: Context, uri: Uri, mime: String = ""): Resultat {
        val nom = Fichiers.nomAffiche(context, uri)
        val estPdf = mime.contains("pdf") || nom.endsWith(".pdf", ignoreCase = true)
        return if (estPdf) lirePdf(context, uri) else Resultat(lireImage(context, uri), 1)
    }

    private suspend fun lireImage(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        return reconnaitre(image)
    }

    private suspend fun lirePdf(context: Context, uri: Uri): Resultat {
        val textes = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            val descripteur: ParcelFileDescriptor = context.contentResolver
                .openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("PDF illisible")
            descripteur.use { fd ->
                PdfRenderer(fd).use { rendu ->
                    val pages = minOf(rendu.pageCount, PAGES_MAX)
                    for (index in 0 until pages) {
                        rendu.openPage(index).use { page ->
                            // 150 dpi environ : au-delà, ML Kit ne gagne rien et
                            // l'allocation mémoire devient risquée sur un vieux téléphone.
                            val largeur = maxOf(page.width * 2, 600)
                            val hauteur = maxOf(page.height * 2, 800)
                            val bitmap = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            textes += reconnaitre(InputImage.fromBitmap(bitmap, 0))
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        return Resultat(textes.joinToString("\n"), textes.size)
    }

    private suspend fun reconnaitre(image: InputImage): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { suite ->
                recognizer.process(image)
                    .addOnSuccessListener { resultat ->
                        Diag.record("ocr", "${resultat.textBlocks.size} bloc(s) de texte détecté(s)")
                        suite.resume(resultat.text)
                    }
                    .addOnFailureListener { erreur ->
                        Diag.record("ocr", erreur)
                        if (suite.isActive) suite.resumeWithException(erreur)
                    }
            }
        } finally {
            runCatching { recognizer.close() }
        }
    }
}
