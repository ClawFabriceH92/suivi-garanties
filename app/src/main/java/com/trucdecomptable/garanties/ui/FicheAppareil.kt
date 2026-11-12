package com.trucdecomptable.garanties.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.garanties.data.Appareil
import com.trucdecomptable.garanties.data.Document
import com.trucdecomptable.garanties.data.Evenement
import com.trucdecomptable.garanties.data.Garantie
import com.trucdecomptable.garanties.data.Statuts
import com.trucdecomptable.garanties.data.TypesDocument
import com.trucdecomptable.garanties.data.TypesGarantie
import com.trucdecomptable.garanties.metier.Echeances
import com.trucdecomptable.garanties.util.Dates
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranFiche(
    appareil: Appareil,
    garanties: List<Garantie>,
    documents: List<Document>,
    journal: List<Evenement>,
    onRetour: () -> Unit,
    onModifier: () -> Unit,
    onArchiver: (Boolean) -> Unit,
    onSupprimer: () -> Unit,
    onAjouterGarantie: (Garantie) -> Unit,
    onSupprimerGarantie: (Garantie) -> Unit,
    onAjouterDocument: (String) -> Unit,
    onOuvrirDocument: (Document) -> Unit,
    onPartagerDocument: (Document) -> Unit,
    onSupprimerDocument: (Document) -> Unit,
    onRenommerDocument: (Document, String) -> Unit,
    onPartagerDossier: (List<Document>) -> Unit,
    onPrendrePhoto: () -> Unit,
) {
    var confirmerSuppression by remember { mutableStateOf(false) }
    var ajoutGarantie by remember { mutableStateOf(false) }
    var menuDocument by remember { mutableStateOf(false) }
    var menuPartage by remember { mutableStateOf(false) }
    var documentARenommer by remember { mutableStateOf<Document?>(null) }

    val fin = Echeances.finLaPlusLointaine(appareil, garanties)
    val etat = Echeances.etatDe(fin)
    val jours = Echeances.joursRestants(fin)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appareil.nom, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onModifier) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier")
                    }
                    IconButton(onClick = { onArchiver(!appareil.archive) }) {
                        Icon(
                            if (appareil.archive) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (appareil.archive) "Restaurer" else "Archiver",
                        )
                    }
                    IconButton(onClick = { menuPartage = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Partager le dossier")
                    }
                    DropdownMenu(expanded = menuPartage, onDismissRequest = { menuPartage = false }) {
                        DropdownMenuItem(
                            text = { Text("Tout le dossier (fiche + justificatifs)") },
                            onClick = { menuPartage = false; onPartagerDossier(documents) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PastilleEtat(etat, taille = 14)
                    Spacer(Modifier.width(8.dp))
                    EtiquetteEtat(etat)
                    if (jours != null && jours >= 0) {
                        Text(" · fin le ${Dates.afficher(fin)}", fontSize = 13.sp)
                    } else if (fin.isNotBlank()) {
                        Text(" · expirée le ${Dates.afficher(fin)}", fontSize = 13.sp)
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LigneInfo("Catégorie", appareil.categorie)
                        LigneInfo("Marque", appareil.marque)
                        LigneInfo("Modèle", appareil.modele)
                        LigneInfo("Numéro de série", appareil.numeroSerie)
                        LigneInfo("Code-barres", appareil.codeBarres)
                        LigneInfo("Date d'achat", Dates.afficher(appareil.dateAchat))
                        LigneInfo(
                            "Prix d'achat",
                            appareil.prixAchat?.let { String.format(Locale.FRANCE, "%.2f €", it) }.orEmpty(),
                        )
                        LigneInfo("Vendeur", appareil.vendeur)
                        LigneInfo("N° de facture", appareil.numeroFacture)
                        LigneInfo("Début de garantie", Dates.afficher(appareil.debutGarantie))
                        LigneInfo(
                            "Fin de garantie",
                            Dates.afficher(appareil.finGarantie) +
                                (appareil.dureeGarantieMois?.let { " ($it mois)" } ?: ""),
                        )
                        LigneInfo("Emplacement", appareil.emplacement)
                        LigneInfo("Propriétaire", appareil.proprietaire)
                        LigneInfo("Statut", appareil.statut)
                        if (appareil.notes.isNotBlank()) LigneInfo("Notes", appareil.notes)
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Garanties et couvertures", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { ajoutGarantie = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Ajouter")
                    }
                }
            }
            if (garanties.isEmpty()) {
                item {
                    Text(
                        "Aucune couverture secondaire enregistrée.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(garanties, key = { it.id }) { garantie ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PastilleEtat(Echeances.etatDe(garantie.fin), taille = 10)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(garantie.type, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val details = listOf(garantie.organisme, garantie.reference)
                                .filter { it.isNotBlank() }.joinToString(" · ")
                            if (details.isNotBlank()) {
                                Text(details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "du ${Dates.afficher(garantie.debut)} au ${Dates.afficher(garantie.fin)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (garantie.contact.isNotBlank()) {
                                Text(garantie.contact, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { onSupprimerGarantie(garantie) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer la garantie")
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Documents", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { menuDocument = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Ajouter")
                    }
                    DropdownMenu(expanded = menuDocument, onDismissRequest = { menuDocument = false }) {
                        listOf(
                            TypesDocument.FACTURE, TypesDocument.TICKET, TypesDocument.EXTENSION,
                            TypesDocument.CERTIFICAT, TypesDocument.ASSURANCE, TypesDocument.DEVIS,
                            TypesDocument.FACTURE_REPARATION, TypesDocument.SAV, TypesDocument.MANUEL,
                            TypesDocument.PHOTO_SERIE, TypesDocument.AUTRE,
                        ).forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = { menuDocument = false; onAjouterDocument(type) },
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPrendrePhoto) { Text("Photographier l'appareil") }
                }
            }
            if (documents.isEmpty()) {
                item {
                    Text(
                        "Aucun document : ajoutez la facture pour la retrouver en cas de panne.",
                        fontSize = 13.sp,
                        color = CouleursEtat.Orange,
                    )
                }
            }
            items(documents, key = { it.id }) { document ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(document.type, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                document.nom,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        TextButton(onClick = { onOuvrirDocument(document) }) { Text("Ouvrir") }
                        IconButton(onClick = { onPartagerDocument(document) }) {
                            Icon(Icons.Default.Share, contentDescription = "Partager")
                        }
                        IconButton(onClick = { documentARenommer = document }) {
                            Icon(Icons.Default.Edit, contentDescription = "Renommer")
                        }
                        IconButton(onClick = { onSupprimerDocument(document) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                        }
                    }
                }
            }

            item {
                Text(
                    "Historique",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (journal.isEmpty()) {
                item { Text("Aucun événement.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(journal, key = { it.id }) { evenement ->
                Column {
                    Text(Dates.horodatage(evenement.date), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(evenement.texte, fontSize = 13.sp)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { confirmerSuppression = true }) {
                    Text("Supprimer définitivement la fiche", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (confirmerSuppression) {
        AlertDialog(
            onDismissRequest = { confirmerSuppression = false },
            title = { Text("Supprimer définitivement ?") },
            text = {
                Text(
                    "La fiche, ses garanties et ses justificatifs seront effacés du téléphone. " +
                        "Cette action ne peut pas être annulée. Pour conserver l'historique, préférez l'archivage.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmerSuppression = false; onSupprimer() }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmerSuppression = false }) { Text("Annuler") }
            },
        )
    }

    if (ajoutGarantie) {
        DialogueGarantie(
            onFermer = { ajoutGarantie = false },
            onValider = { garantie ->
                ajoutGarantie = false
                onAjouterGarantie(garantie.copy(appareilId = appareil.id))
            },
        )
    }

    documentARenommer?.let { document ->
        var nom by remember(document.id) { mutableStateOf(document.nom) }
        AlertDialog(
            onDismissRequest = { documentARenommer = null },
            title = { Text("Renommer le document") },
            text = {
                OutlinedTextField(value = nom, onValueChange = { nom = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    documentARenommer?.let { onRenommerDocument(it, nom) }
                    documentARenommer = null
                }) { Text("Renommer") }
            },
            dismissButton = {
                TextButton(onClick = { documentARenommer = null }) { Text("Annuler") }
            },
        )
    }
}

@Composable
fun LigneInfo(libelle: String, valeur: String) {
    if (valeur.isBlank()) return
    Row {
        Text(
            libelle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(valeur, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DialogueGarantie(onFermer: () -> Unit, onValider: (Garantie) -> Unit) {
    var type by remember { mutableStateOf(TypesGarantie.CONSTRUCTEUR) }
    var organisme by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var debut by remember { mutableStateOf("") }
    var fin by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var conditions by remember { mutableStateOf("") }
    var menuType by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Nouvelle couverture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(type, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { menuType = true }) { Text("Changer") }
                    DropdownMenu(expanded = menuType, onDismissRequest = { menuType = false }) {
                        TypesGarantie.TOUS.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { type = it; menuType = false },
                            )
                        }
                    }
                }
                OutlinedTextField(organisme, { organisme = it }, label = { Text("Organisme") }, singleLine = true)
                OutlinedTextField(reference, { reference = it }, label = { Text("Référence du contrat") }, singleLine = true)
                OutlinedTextField(debut, { debut = it }, label = { Text("Début (JJ/MM/AAAA)") }, singleLine = true)
                OutlinedTextField(fin, { fin = it }, label = { Text("Fin (JJ/MM/AAAA)") }, singleLine = true)
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact du service") }, singleLine = true)
                OutlinedTextField(conditions, { conditions = it }, label = { Text("Conditions ou exclusions") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onValider(
                    Garantie(
                        appareilId = 0,
                        type = type,
                        organisme = organisme,
                        reference = reference,
                        debut = Dates.iso(Dates.lireSouple(debut)),
                        fin = Dates.iso(Dates.lireSouple(fin)),
                        contact = contact,
                        conditions = conditions,
                    ),
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Suppress("unused")
private val statutsDisponibles = Statuts.TOUS
