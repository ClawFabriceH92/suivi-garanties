package com.trucdecomptable.garanties.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.trucdecomptable.garanties.BuildConfig
import com.trucdecomptable.garanties.data.Parametres
import com.trucdecomptable.garanties.update.UpdateManager
import com.trucdecomptable.garanties.util.Dates
import com.trucdecomptable.garanties.util.Diag
import com.trucdecomptable.garanties.util.Fichiers

private val DELAIS = listOf(
    365 to "1 an", 180 to "6 mois", 90 to "3 mois", 60 to "2 mois",
    30 to "30 j", 7 to "7 j", 0 to "Jour J",
)
private val FREQUENCES = listOf(30 to "Tous les mois", 90 to "Tous les 3 mois", 180 to "Tous les 6 mois", 0 to "Jamais")

@Composable
fun EcranReglages(
    parametres: Parametres,
    etatMaj: UpdateManager.State,
    onParametres: (Parametres) -> Unit,
    onDefinirCode: (String?) -> Unit,
    onExporterZip: () -> Unit,
    onExporterCsv: () -> Unit,
    onExporterPdf: () -> Unit,
    onPartagerExport: (java.io.File, String) -> Unit,
    onSauvegarder: (String) -> Unit,
    onRestaurer: () -> Unit,
    onVerifierMaj: () -> Unit,
    onTelechargerMaj: () -> Unit,
    onInstallerMaj: () -> Unit,
    onAutoriserInstallation: () -> Unit,
    onTesterRappels: () -> Unit,
) {
    var dialogueCode by remember { mutableStateOf(false) }
    var dialogueSauvegarde by remember { mutableStateOf(false) }
    var dialogueConfidentialite by remember { mutableStateOf(false) }
    var dialogueJournal by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Rappels avant expiration") {
            Text("Les délais cochés déclenchent une notification avant la fin de garantie.", fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DELAIS.forEach { (jours, libelle) ->
                    val actif = parametres.rappelsJours.contains(jours)
                    FilterChip(
                        selected = actif,
                        onClick = {
                            val nouveaux = if (actif) {
                                parametres.rappelsJours - jours
                            } else {
                                (parametres.rappelsJours + jours).sortedDescending()
                            }
                            onParametres(parametres.copy(rappelsJours = nouveaux))
                        },
                        label = { Text(libelle, fontSize = 12.sp) },
                    )
                }
            }
            LigneSwitch(
                "Notification regroupée",
                "Un récapitulatif quand plusieurs garanties arrivent à échéance",
                parametres.rappelGroupe,
            ) { onParametres(parametres.copy(rappelGroupe = it)) }
            TextButton(onClick = onTesterRappels) { Text("Vérifier maintenant") }
        }

        Section("Sauvegarde et export") {
            Text("Fréquence du rappel d'export :", fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FREQUENCES.forEach { (jours, libelle) ->
                    FilterChip(
                        selected = parametres.sauvegardeJours == jours,
                        onClick = { onParametres(parametres.copy(sauvegardeJours = jours)) },
                        label = { Text(libelle, fontSize = 12.sp) },
                    )
                }
            }
            Text(
                if (parametres.derniereSauvegarde <= 0) {
                    "Aucune sauvegarde effectuée."
                } else {
                    "Dernière sauvegarde : ${Dates.horodatage(parametres.derniereSauvegarde)}"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExporterZip) { Text("ZIP") }
                OutlinedButton(onClick = onExporterCsv) { Text("CSV") }
                OutlinedButton(onClick = onExporterPdf) { Text("PDF") }
            }
            Text(
                "L'application propose d'enregistrer le fichier là où vous voulez " +
                    "(Téléchargements, Drive, clé USB) : les exports restent lisibles sans elle.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { dialogueSauvegarde = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Sauvegarde chiffrée")
            }
            TextButton(onClick = onRestaurer) { Text("Restaurer une sauvegarde") }
        }

        Section("Sécurité") {
            LigneSwitch(
                "Verrouillage par code",
                if (parametres.verrouillageActif) "Code activé" else "Aucun code : l'application s'ouvre directement",
                parametres.verrouillageActif,
            ) { actif ->
                if (actif) dialogueCode = true else onDefinirCode(null)
            }
            Text(
                "Les données restent sur ce téléphone, dans le dossier privé de l'application. " +
                    "Aucun envoi vers un service tiers, aucune publicité, aucun compte à créer.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { dialogueConfidentialite = true }) { Text("Politique de confidentialité") }
        }

        Section("Apparence") {
            LigneSwitch("Thème sombre", "Confort de lecture le soir", parametres.themeSombre) {
                onParametres(parametres.copy(themeSombre = it))
            }
        }

        Section("Mise à jour") {
            Text(ligneEtatMaj(etatMaj), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onVerifierMaj) { Text("Vérifier") }
                when (etatMaj) {
                    is UpdateManager.State.Available ->
                        Button(onClick = onTelechargerMaj) { Text("Télécharger") }
                    is UpdateManager.State.ReadyToInstall ->
                        Button(onClick = onInstallerMaj) { Text("Installer") }
                    is UpdateManager.State.Downloading ->
                        Button(onClick = {}) { Text("Téléchargement…") }
                    else -> Unit
                }
                if (etatMaj is UpdateManager.State.Failed) {
                    OutlinedButton(onClick = onAutoriserInstallation) { Text("Autoriser l'installation") }
                }
            }
            LigneSwitch("Vérifier automatiquement", "Au lancement, au plus une fois par heure", parametres.majAuto) {
                onParametres(parametres.copy(majAuto = it))
            }
            TextButton(onClick = onAutoriserInstallation) { Text("Autoriser l'installation d'applications") }
        }

        Section("Diagnostic") {
            TextButton(onClick = { dialogueJournal = true }) { Text("Voir le journal technique") }
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 12.sp)
        }

        Section("À propos") {
            Text(
                "Mes Garanties conserve vos factures, suit les échéances de garantie et reste lisible " +
                    "sans l'application grâce aux exports CSV, PDF et ZIP. Vous restez propriétaire de " +
                    "vos données et pouvez les reprendre à tout moment.",
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(30.dp))
    }

    if (dialogueCode) {
        var code by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogueCode = false },
            title = { Text("Code d'accès") },
            text = {
                Column {
                    Text("4 chiffres minimum. Il sera demandé à chaque ouverture.", fontSize = 12.sp)
                    OutlinedTextField(
                        code, { code = it },
                        label = { Text("Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        confirmation, { confirmation = it },
                        label = { Text("Confirmation") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (code.length >= 4 && code == confirmation) {
                            onDefinirCode(code)
                            dialogueCode = false
                        }
                    },
                ) { Text("Activer") }
            },
            dismissButton = { TextButton(onClick = { dialogueCode = false }) { Text("Annuler") } },
        )
    }

    if (dialogueSauvegarde) {
        var motDePasse by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogueSauvegarde = false },
            title = { Text("Sauvegarde chiffrée") },
            text = {
                Column {
                    Text(
                        "Choisissez un mot de passe : il chiffre la base et tous les justificatifs. " +
                            "Sans lui, la sauvegarde ne peut pas être restaurée.",
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        motDePasse, { motDePasse = it },
                        label = { Text("Mot de passe") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (motDePasse.length >= 6) {
                        dialogueSauvegarde = false
                        onSauvegarder(motDePasse)
                    }
                }) { Text("Créer la sauvegarde") }
            },
            dismissButton = { TextButton(onClick = { dialogueSauvegarde = false }) { Text("Annuler") } },
        )
    }

    if (dialogueConfidentialite) {
        AlertDialog(
            onDismissRequest = { dialogueConfidentialite = false },
            title = { Text("Confidentialité") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Ce que l'application conserve : les fiches d'appareils, les garanties, les " +
                            "justificatifs et les photos que vous ajoutez.\n\n" +
                            "Où : uniquement dans le stockage privé de l'application, sur ce téléphone.\n\n" +
                            "Ce qui sort du téléphone : rien, sauf si vous décidez de partager un dossier " +
                            "ou de créer une sauvegarde (fichiers que vous envoyez vous-même). La recherche " +
                            "de mise à jour interroge GitHub sans transmettre aucune donnée personnelle.\n\n" +
                            "Ce qui n'est pas fait : aucun compte, aucune publicité, aucune mesure " +
                            "d'audience, aucun envoi de facture à un service d'analyse.\n\n" +
                            "Suppression : chaque fiche peut être supprimée définitivement, avec ses " +
                            "documents, depuis sa page.",
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { dialogueConfidentialite = false }) { Text("Fermer") } },
        )
    }

    if (dialogueJournal) {
        AlertDialog(
            onDismissRequest = { dialogueJournal = false },
            title = { Text("Journal technique") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(Diag.dump().ifBlank { "Aucun événement enregistré." }, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { dialogueJournal = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun Section(titre: String, contenu: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titre, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            HorizontalDivider()
            contenu()
        }
    }
}

@Composable
private fun LigneSwitch(titre: String, sousTitre: String, actif: Boolean, onChangement: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titre, fontSize = 14.sp)
            Text(sousTitre, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = actif, onCheckedChange = onChangement)
    }
}

private fun ligneEtatMaj(etat: UpdateManager.State): String = when (etat) {
    is UpdateManager.State.Idle -> "Vérification automatique activée en arrière-plan."
    is UpdateManager.State.Available -> "Version ${etat.version} disponible."
    is UpdateManager.State.Downloading -> "Téléchargement de la version ${etat.version}…"
    is UpdateManager.State.ReadyToInstall -> "Version ${etat.version} prête à être installée."
    is UpdateManager.State.UpToDate -> "À jour (version ${etat.version})."
    is UpdateManager.State.Failed -> "Vérification impossible : ${etat.detail ?: "réseau indisponible"}."
}

@Suppress("unused")
private val aide = Fichiers
