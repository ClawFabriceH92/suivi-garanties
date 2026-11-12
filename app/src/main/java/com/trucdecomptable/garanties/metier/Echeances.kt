package com.trucdecomptable.garanties.metier

import com.trucdecomptable.garanties.data.Appareil
import com.trucdecomptable.garanties.data.Garantie
import com.trucdecomptable.garanties.util.Dates
import java.time.LocalDate

/**
 * Tout ce qui décide « garantie active / bientôt expirée / expirée » vit ici,
 * en fonctions pures : c'est testable sans Android et c'est la seule règle que
 * le tableau de bord, les filtres et les notifications partagent.
 */
enum class EtatGarantie { ACTIVE, PROCHE, TRES_PROCHE, EXPIREE, INCONNUE }

data class Echeance(
    val appareilId: Long,
    val appareilNom: String,
    val libelle: String,
    val fin: LocalDate,
    val joursRestants: Int,
    val etat: EtatGarantie,
    /** Date de fin de la garantie principale de l'appareil, pas d'une couverture secondaire. */
    val principale: Boolean,
)

data class Synthese(
    val total: Int = 0,
    val sousGarantie: Int = 0,
    val expirant90: Int = 0,
    val expirees: Int = 0,
    val archives: Int = 0,
    val sansFacture: Int = 0,
    val sansNumeroSerie: Int = 0,
    val sansDateFin: Int = 0,
    val derniereSauvegarde: Long = 0,
)

object Echeances {

    const val HORIZON_JOURS = 90

    /** Une garantie expirée reste dans « À faire » ce nombre de jours, pas plus. */
    const val RAPPEL_EXPIRE_JOURS = 30

    /** L'échéance mérite-t-elle d'être affichée : à venir dans l'horizon, ou expirée récemment. */
    fun dansLaFenetre(joursRestants: Int, horizon: Int = HORIZON_JOURS): Boolean =
        joursRestants <= horizon && joursRestants >= -RAPPEL_EXPIRE_JOURS

    /** Rouge < 30 jours, orange < 90 jours, vert actif, gris expiré. */
    fun etat(joursRestants: Int?): EtatGarantie = when {
        joursRestants == null -> EtatGarantie.INCONNUE
        joursRestants < 0 -> EtatGarantie.EXPIREE
        joursRestants <= 30 -> EtatGarantie.TRES_PROCHE
        joursRestants <= HORIZON_JOURS -> EtatGarantie.PROCHE
        else -> EtatGarantie.ACTIVE
    }

    fun joursRestants(fin: String?, aujourdhui: LocalDate = Dates.aujourdhui()): Int? =
        Dates.joursRestants(Dates.parse(fin), aujourdhui)

    fun etatDe(fin: String?, aujourdhui: LocalDate = Dates.aujourdhui()): EtatGarantie =
        etat(joursRestants(fin, aujourdhui))

    /**
     * La garantie la plus favorable d'un appareil : la date de fin la plus
     * lointaine entre la garantie principale et ses couvertures secondaires.
     * Une extension de garantie doit sortir l'appareil du rouge.
     */
    fun finLaPlusLointaine(appareil: Appareil, garanties: List<Garantie>): String {
        val fins = buildList {
            if (appareil.finGarantie.isNotBlank()) add(appareil.finGarantie)
            garanties.filter { it.appareilId == appareil.id }.forEach { add(it.fin) }
        }
        return fins.mapNotNull { Dates.parse(it) }.maxOrNull()?.let { Dates.iso(it) }.orEmpty()
    }

    fun estSousGarantie(
        appareil: Appareil,
        garanties: List<Garantie>,
        aujourdhui: LocalDate = Dates.aujourdhui(),
    ): Boolean {
        val fin = Dates.parse(finLaPlusLointaine(appareil, garanties)) ?: return false
        return !fin.isBefore(aujourdhui)
    }

    /**
     * Échéances à afficher dans « À faire » : toutes les fins (principale et
     * couvertures) déjà expirées depuis moins de 30 jours ou à venir dans
     * l'horizon, les plus urgentes d'abord.
     */
    fun echeances(
        appareils: List<Appareil>,
        garanties: List<Garantie>,
        aujourdhui: LocalDate = Dates.aujourdhui(),
        horizon: Int = HORIZON_JOURS,
    ): List<Echeance> {
        val visibles = appareils.filter { !it.archive }
        val resultat = mutableListOf<Echeance>()
        for (appareil in visibles) {
            val jours = joursRestants(appareil.finGarantie, aujourdhui)
            if (jours != null && dansLaFenetre(jours, horizon)) {
                resultat += Echeance(
                    appareilId = appareil.id,
                    appareilNom = appareil.nom,
                    libelle = "Garantie principale",
                    fin = Dates.parse(appareil.finGarantie)!!,
                    joursRestants = jours,
                    etat = etat(jours),
                    principale = true,
                )
            }
            garanties.filter { it.appareilId == appareil.id }.forEach { garantie ->
                val j = joursRestants(garantie.fin, aujourdhui) ?: return@forEach
                if (dansLaFenetre(j, horizon)) {
                    resultat += Echeance(
                        appareilId = appareil.id,
                        appareilNom = appareil.nom,
                        libelle = garantie.type,
                        fin = Dates.parse(garantie.fin)!!,
                        joursRestants = j,
                        etat = etat(j),
                        principale = false,
                    )
                }
            }
        }
        return resultat.sortedWith(compareBy({ it.fin }, { it.appareilNom }))
    }

    fun synthese(
        appareils: List<Appareil>,
        garanties: List<Garantie>,
        documentsParAppareil: Map<Long, Int> = emptyMap(),
        derniereSauvegarde: Long = 0,
        aujourdhui: LocalDate = Dates.aujourdhui(),
    ): Synthese {
        val actifs = appareils.filter { !it.archive }
        val sousGarantie = actifs.count { estSousGarantie(it, garanties, aujourdhui) }
        val aux = echeances(actifs, garanties, aujourdhui)
        return Synthese(
            total = actifs.size,
            sousGarantie = sousGarantie,
            expirant90 = aux.count { it.joursRestants >= 0 },
            expirees = actifs.count { !estSousGarantie(it, garanties, aujourdhui) },
            archives = appareils.count { it.archive },
            sansFacture = actifs.count { documentsParAppareil[it.id] == null },
            sansNumeroSerie = actifs.count { it.numeroSerie.isBlank() },
            sansDateFin = actifs.count { it.finGarantie.isBlank() },
            derniereSauvegarde = derniereSauvegarde,
        )
    }

    /** Rappels à déclencher aujourd'hui (0 = le jour de l'expiration). */
    fun rappelsDus(rappelsJours: List<Int>, joursRestants: Int): List<Int> =
        rappelsJours.filter { it == joursRestants }.distinct()

    /**
     * Délai de rappel franchi, pour ne notifier qu'une fois par palier même si
     * le téléphone était éteint le jour exact. Le plus petit palier franchi est
     * retenu : à 25 jours restants avec les paliers 90/30/7, on a déjà prévenu à
     * 90 et à 30, le palier à annoncer est 7 s'il n'est pas encore parti.
     */
    fun delaiFranchi(rappelsJours: List<Int>, joursRestants: Int): Int? {
        if (joursRestants < 0) return if (rappelsJours.contains(0)) 0 else null
        return rappelsJours.filter { joursRestants <= it }.minOrNull()
    }

    /** Message d'une notification, prêt à afficher. */
    fun texteNotification(appareilNom: String, joursRestants: Int): String = when {
        joursRestants < 0 -> "$appareilNom : garantie expirée depuis ${-joursRestants} jour(s)."
        joursRestants == 0 -> "$appareilNom : la garantie expire aujourd'hui."
        else -> "$appareilNom sera hors garantie dans $joursRestants jour(s)."
    }

    /** Export : la sauvegarde est-elle en retard ? */
    fun sauvegardeEnRetard(derniere: Long, frequenceJours: Int, maintenant: Long): Boolean {
        if (frequenceJours <= 0) return false
        if (derniere <= 0) return true
        val jours = (maintenant - derniere) / 86_400_000L
        return jours >= frequenceJours
    }
}
