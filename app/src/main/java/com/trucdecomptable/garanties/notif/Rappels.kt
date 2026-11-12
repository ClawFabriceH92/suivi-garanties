package com.trucdecomptable.garanties.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trucdecomptable.garanties.MainActivity
import com.trucdecomptable.garanties.R
import com.trucdecomptable.garanties.data.CoffreDb
import com.trucdecomptable.garanties.data.Parametres
import com.trucdecomptable.garanties.metier.Echeances
import com.trucdecomptable.garanties.util.Dates
import com.trucdecomptable.garanties.util.Diag
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Rappels d'expiration. WorkManager plutôt qu'AlarmManager : le système garde
 * la tâche planifiée après un redémarrage ou une mise à jour, ce qui est
 * exactement l'écueil signalé dans le cahier des charges (notifications à
 * reprogrammer). Une notification ouvre la fiche de l'appareil concerné.
 */
object Rappels {

    const val CANAL_GARANTIES = "garanties"
    const val CANAL_SAUVEGARDE = "sauvegarde"
    private const val TRAVAIL = "rappels-garanties"
    private const val CLE_ENVOYES = "notifs_envoyees"

    fun creerCanaux(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestionnaire = context.getSystemService(NotificationManager::class.java) ?: return
        gestionnaire.createNotificationChannel(
            NotificationChannel(
                CANAL_GARANTIES,
                "Expiration des garanties",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Rappels avant la fin d'une garantie" },
        )
        gestionnaire.createNotificationChannel(
            NotificationChannel(
                CANAL_SAUVEGARDE,
                "Sauvegarde des données",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Rappel d'exporter vos factures et vos fiches" },
        )
    }

    /** Une vérification par jour, vers 9 h : l'heure à laquelle on agit sur un rappel. */
    fun planifier(context: Context) {
        creerCanaux(context)
        val maintenant = LocalDateTime.now()
        var prochain = maintenant.with(LocalTime.of(9, 0))
        if (!prochain.isAfter(maintenant)) prochain = prochain.plusDays(1)
        val delai = ChronoUnit.MINUTES.between(maintenant, prochain).coerceAtLeast(1)
        val demande = PeriodicWorkRequestBuilder<RappelWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delai, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(TRAVAIL, ExistingPeriodicWorkPolicy.UPDATE, demande)
        Diag.record("rappels", "vérification quotidienne planifiée (dans $delai min)")
    }

    /** Vérification immédiate, pour le bouton « tester les rappels » des réglages. */
    fun verifierMaintenant(context: Context) {
        WorkManager.getInstance(context)
            .enqueue(androidx.work.OneTimeWorkRequestBuilder<RappelWorker>().build())
    }

    fun permissionAccordee(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun dejaEnvoyes(context: Context): MutableSet<String> =
        context.getSharedPreferences("garanties-notifs", Context.MODE_PRIVATE)
            .getStringSet(CLE_ENVOYES, emptySet())
            ?.toMutableSet() ?: mutableSetOf()

    private fun marquerEnvoye(context: Context, cle: String) {
        val ensemble = dejaEnvoyes(context)
        ensemble.add(cle)
        context.getSharedPreferences("garanties-notifs", Context.MODE_PRIVATE).edit()
            .putStringSet(CLE_ENVOYES, ensemble).apply()
    }

    fun intentionFiche(context: Context, appareilId: Long): PendingIntent {
        val intention = Intent(context, MainActivity::class.java).apply {
            putExtra("appareil_id", appareilId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            appareilId.toInt(),
            intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notifierGarantie(
        context: Context,
        appareilId: Long,
        appareilNom: String,
        joursRestants: Int,
        libelle: String,
        identifiant: Int,
    ) {
        if (!permissionAccordee(context)) {
            Diag.record("rappels", "notification impossible : permission refusée")
            return
        }
        val texte = Echeances.texteNotification(appareilNom, joursRestants)
        val notification = NotificationCompat.Builder(context, CANAL_GARANTIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Garantie · $appareilNom")
            .setContentText(texte)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$texte\n$libelle · appuyez pour ouvrir la fiche, la facture et le service après-vente.",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intentionFiche(context, appareilId))
            .build()
        NotificationManagerCompat.from(context).notify(identifiant, notification)
    }

    fun notifierGroupe(context: Context, nombre: Int) {
        if (!permissionAccordee(context)) return
        val notification = NotificationCompat.Builder(context, CANAL_GARANTIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Garanties à surveiller")
            .setContentText("$nombre garanties arrivent à expiration dans les 90 prochains jours.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(intentionFiche(context, 0))
            .build()
        NotificationManagerCompat.from(context).notify(2001, notification)
    }

    fun notifierSauvegarde(context: Context, jours: Long) {
        if (!permissionAccordee(context)) return
        val notification = NotificationCompat.Builder(context, CANAL_SAUVEGARDE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sauvegarde des garanties")
            .setContentText("Votre dernière sauvegarde date de $jours jours. Souhaitez-vous exporter vos données ?")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Votre dernière sauvegarde date de $jours jours. Souhaitez-vous exporter " +
                        "vos appareils et leurs justificatifs ?",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(intentionFiche(context, 0))
            .build()
        NotificationManagerCompat.from(context).notify(2002, notification)
    }

    class RappelWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val contexte = applicationContext
            val parametres = Parametres.lire(contexte)
            val db = CoffreDb.obtenir(contexte)
            return runCatching {
                val appareils = db.dao().tousLesAppareils()
                val garanties = db.dao().toutesLesGaranties()
                val aujourdhui = Dates.aujourdhui()
                val envoyes = dejaEnvoyes(contexte)
                var compteur = 0
                var alertes = 0
                for (echeance in Echeances.echeances(appareils, garanties, aujourdhui, horizon = 400)) {
                    val delai = Echeances.delaiFranchi(parametres.rappelsJours, echeance.joursRestants)
                        ?: continue
                    val cle = "${echeance.appareilId}|${echeance.fin}|${echeance.libelle}|$delai"
                    if (cle in envoyes) continue
                    notifierGarantie(
                        context = contexte,
                        appareilId = echeance.appareilId,
                        appareilNom = echeance.appareilNom,
                        joursRestants = echeance.joursRestants,
                        libelle = echeance.libelle,
                        identifiant = cle.hashCode(),
                    )
                    marquerEnvoye(contexte, cle)
                    compteur++
                    if (echeance.joursRestants in 0..90) alertes++
                    Diag.record("rappels", "notifié ${echeance.appareilNom} (${echeance.joursRestants} j)")
                }
                if (parametres.rappelGroupe && alertes >= 2) notifierGroupe(contexte, alertes)

                if (Echeances.sauvegardeEnRetard(
                        parametres.derniereSauvegarde,
                        parametres.sauvegardeJours,
                        System.currentTimeMillis(),
                    ) && parametres.derniereSauvegarde > 0
                ) {
                    val jours = (System.currentTimeMillis() - parametres.derniereSauvegarde) / 86_400_000L
                    notifierSauvegarde(contexte, jours)
                }
                Diag.record("rappels", "$compteur rappel(s) envoyé(s)")
                Result.success()
            }.getOrElse {
                Diag.record("rappels", it)
                Result.success() // ne pas boucler en retry : la prochaine journée suffit
            }
        }
    }
}
