package com.trucdecomptable.garanties.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Une ligne par événement, gardée en mémoire et recopiée dans logcat. L'OCR et
 * la mise à jour échouent pour des raisons qui ne se comprennent qu'après coup :
 * l'application doit pouvoir dire ce qu'elle a tenté.
 */
object Diag {

    private const val TAG = "SuiviGaranties"
    private const val MAX = 400

    private val lignes = ArrayDeque<String>()

    @Synchronized
    fun record(categorie: String, message: String) {
        val heure = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
        val ligne = "$heure [$categorie] $message"
        lignes.addLast(ligne)
        while (lignes.size > MAX) lignes.removeFirst()
        Log.d(TAG, ligne)
    }

    fun record(categorie: String, e: Throwable) =
        record(categorie, "${e.javaClass.simpleName}: ${e.message.orEmpty()}")

    @Synchronized
    fun dump(): String = lignes.joinToString("\n")

    @Synchronized
    fun clear() = lignes.clear()
}
