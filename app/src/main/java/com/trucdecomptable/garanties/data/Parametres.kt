package com.trucdecomptable.garanties.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Réglages de l'application, en SharedPreferences sous forme d'un seul JSON :
 * une lecture au démarrage, aucun aller-retour en base, et un fichier lisible.
 */
data class Parametres(
    /** Rappels en jours avant l'expiration. 0 = le jour même. */
    val rappelsJours: List<Int> = listOf(90, 30, 7),
    /** Récapitulatif groupé quand plusieurs garanties arrivent à échéance. */
    val rappelGroupe: Boolean = true,
    /** Rappel d'export tous les N jours. 0 = jamais. */
    val sauvegardeJours: Int = 90,
    val derniereSauvegarde: Long = 0,
    /** SHA-256 du code d'accès, vide quand l'application n'est pas verrouillée. */
    val codeHash: String = "",
    val themeSombre: Boolean = false,
    val categories: List<String> = Categories.DEFAUT,
    val majAuto: Boolean = true,
    /** Version de la politique de confidentialité acceptée, vide = jamais lue. */
    val confidentialiteLue: String = "",
) {
    val verrouillageActif: Boolean get() = codeHash.isNotBlank()

    fun json(): String = JSONObject().apply {
        put("rappelsJours", JSONArray(rappelsJours))
        put("rappelGroupe", rappelGroupe)
        put("sauvegardeJours", sauvegardeJours)
        put("derniereSauvegarde", derniereSauvegarde)
        put("codeHash", codeHash)
        put("themeSombre", themeSombre)
        put("categories", JSONArray(categories))
        put("majAuto", majAuto)
        put("confidentialiteLue", confidentialiteLue)
    }.toString()

    companion object {
        private const val FICHIER = "garanties-prefs"
        private const val CLE = "parametres"

        fun lire(context: Context): Parametres {
            val brut = context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
                .getString(CLE, null) ?: return Parametres()
            return runCatching {
                val json = JSONObject(brut)
                Parametres(
                    rappelsJours = json.optJSONArray("rappelsJours").toIntList(listOf(90, 30, 7)),
                    rappelGroupe = json.optBoolean("rappelGroupe", true),
                    sauvegardeJours = json.optInt("sauvegardeJours", 90),
                    derniereSauvegarde = json.optLong("derniereSauvegarde", 0),
                    codeHash = json.optString("codeHash", ""),
                    themeSombre = json.optBoolean("themeSombre", false),
                    categories = json.optJSONArray("categories").toStringList(Categories.DEFAUT),
                    majAuto = json.optBoolean("majAuto", true),
                    confidentialiteLue = json.optString("confidentialiteLue", ""),
                )
            }.getOrElse { Parametres() }
        }

        fun ecrire(context: Context, parametres: Parametres) {
            context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
                .putString(CLE, parametres.json()).apply()
        }

        /** Jamais le code en clair : seul le condensat est stocké. */
        fun empreinte(code: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(("garanties:$code").toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

internal fun JSONArray?.toIntList(defaut: List<Int>): List<Int> {
    if (this == null || length() == 0) return defaut
    return (0 until length()).map { optInt(it) }.filter { it >= 0 }.distinct().sortedDescending()
}

internal fun JSONArray?.toStringList(defaut: List<String>): List<String> {
    if (this == null || length() == 0) return defaut
    val valeurs = (0 until length()).mapNotNull { optString(it).takeIf { v -> v.isNotBlank() } }
    return valeurs.ifEmpty { defaut }
}
