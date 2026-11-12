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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.trucdecomptable.garanties.data.Statuts
import com.trucdecomptable.garanties.metier.FactureExtraite
import com.trucdecomptable.garanties.util.Dates

/**
 * Parcours d'ajout : photo, import ou saisie manuelle, puis un unique écran de
 * validation. Les données lues par l'OCR ne sont jamais enregistrées telles
 * quelles : tout est visible, modifiable et supprimable champ par champ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranAjout(
    initial: Appareil?,
    categorieInitiale: String?,
    extraction: FactureExtraite?,
    ocrEnCours: Boolean,
    modeEdition: Boolean,
    categories: List<String>,
    onRetour: () -> Unit,
    onPhotographier: () -> Unit,
    onImporter: () -> Unit,
    onSaisieManuelle: () -> Unit,
    onEnregistrer: (List<Appareil>) -> Unit,
    onTexteBrut: (String) -> Unit,
) {
    var demarre by remember { mutableStateOf(initial != null || extraction != null || modeEdition) }

    // Report des valeurs détectées dans les champs, une fois l'analyse terminée.
    var nom by remember { mutableStateOf(initial?.nom.orEmpty()) }
    var categorie by remember { mutableStateOf(initial?.categorie ?: categorieInitiale ?: categories.first()) }
    var marque by remember { mutableStateOf(initial?.marque.orEmpty()) }
    var modele by remember { mutableStateOf(initial?.modele.orEmpty()) }
    var numeroSerie by remember { mutableStateOf(initial?.numeroSerie.orEmpty()) }
    var codeBarres by remember { mutableStateOf(initial?.codeBarres.orEmpty()) }
    var dateAchat by remember { mutableStateOf(Dates.afficher(initial?.dateAchat)) }
    var prix by remember { mutableStateOf(initial?.prixAchat?.toString() ?: "") }
    var vendeur by remember { mutableStateOf(initial?.vendeur.orEmpty()) }
    var numeroFacture by remember { mutableStateOf(initial?.numeroFacture.orEmpty()) }
    var debutGarantie by remember { mutableStateOf(Dates.afficher(initial?.debutGarantie)) }
    var dureeMois by remember { mutableStateOf(initial?.dureeGarantieMois?.toString() ?: "") }
    var finGarantie by remember { mutableStateOf(Dates.afficher(initial?.finGarantie)) }
    var emplacement by remember { mutableStateOf(initial?.emplacement.orEmpty()) }
    var proprietaire by remember { mutableStateOf(initial?.proprietaire.orEmpty()) }
    var statut by remember { mutableStateOf(initial?.statut ?: Statuts.ACTIF) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var nombre by remember { mutableStateOf("1") }
    var finModifiee by remember { mutableStateOf(initial?.finGarantie?.isNotBlank() == true) }
    var menuCategorie by remember { mutableStateOf(false) }
    var menuStatut by remember { mutableStateOf(false) }
    var texteBrut by remember { mutableStateOf("") }
    var confirmerVide by remember { mutableStateOf(false) }

    LaunchedEffect(extraction) {
        val e = extraction ?: return@LaunchedEffect
        texteBrut = e.texteBrut
        if (nom.isBlank()) {
            nom = listOf(e.marque, e.designation).filter { it.isNotBlank() }.joinToString(" ").trim()
                .ifBlank { e.designation }
        }
        if (marque.isBlank()) marque = e.marque
        if (modele.isBlank()) modele = e.modele
        if (numeroSerie.isBlank()) numeroSerie = e.numeroSerie
        if (vendeur.isBlank()) vendeur = e.vendeur
        if (numeroFacture.isBlank()) numeroFacture = e.numeroFacture
        if (dateAchat.isBlank()) dateAchat = Dates.afficher(e.dateAchat)
        if (prix.isBlank()) prix = e.prixTTC?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: ""
        if (debutGarantie.isBlank()) debutGarantie = Dates.afficher(e.dateAchat)
        if (dureeMois.isBlank()) dureeMois = e.dureeProposee.toString()
        if (finGarantie.isBlank() && !finModifiee) finGarantie = Dates.afficher(e.finGarantie)
        if (e.categorie.isNotBlank() && categories.contains(e.categorie)) categorie = e.categorie
    }

    // Fin de garantie recalculée tant que l'utilisateur ne l'a pas corrigée à la main.
    LaunchedEffect(dateAchat, dureeMois, debutGarantie) {
        if (finModifiee) return@LaunchedEffect
        val debut = Dates.lireSouple(debutGarantie) ?: Dates.lireSouple(dateAchat)
        val mois = dureeMois.toIntOrNull()
        if (debut != null && mois != null && mois > 0) {
            finGarantie = Dates.afficher(Dates.iso(Dates.ajouterMois(debut, mois)))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (modeEdition) "Modifier la fiche" else "Ajouter un appareil") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!demarre) {
                Text(
                    "Trois façons d'enregistrer un appareil :",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = { demarre = true; onPhotographier() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Photographier la facture ou le ticket")
                }
                OutlinedButton(onClick = { demarre = true; onImporter() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Importer un PDF ou une photo")
                }
                OutlinedButton(onClick = { demarre = true; onSaisieManuelle() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Saisir la fiche à la main")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "L'analyse se fait entièrement sur le téléphone : aucune facture n'est envoyée à un service externe.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            if (ocrEnCours) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Analyse du document en cours…", fontSize = 13.sp)
                    }
                }
            }

            if (extraction != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Données détectées : à vérifier", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Corrigez ou videz chaque champ avant d'enregistrer. Une valeur illisible reste vide " +
                                "plutôt que devinée.",
                            fontSize = 12.sp,
                        )
                        if (extraction.produits.size > 1) {
                            Spacer(Modifier.height(6.dp))
                            Text("Articles repérés sur la facture :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            extraction.produits.forEach { produit ->
                                Text(
                                    "· ${produit.designation} — ${produit.prixTTC?.let { String.format(java.util.Locale.FRANCE, "%.2f €", it) }.orEmpty()}",
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (texteBrut.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { onTexteBrut(texteBrut) }) { Text("Voir le texte reconnu") }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = nom,
                onValueChange = { nom = it },
                label = { Text("Nom de l'appareil *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = categorie,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Catégorie") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { menuCategorie = true }) { Text("Changer") }
                DropdownMenu(expanded = menuCategorie, onDismissRequest = { menuCategorie = false }) {
                    categories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { categorie = option; menuCategorie = false },
                        )
                    }
                }
            }

            OutlinedTextField(marque, { marque = it }, label = { Text("Marque") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(modele, { modele = it }, label = { Text("Modèle") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                numeroSerie, { numeroSerie = it },
                label = { Text("Numéro de série") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                codeBarres, { codeBarres = it },
                label = { Text("Code-barres ou QR code") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                dateAchat, { dateAchat = it },
                label = { Text("Date d'achat (JJ/MM/AAAA)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                prix, { prix = it },
                label = { Text("Prix d'achat TTC (€)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(vendeur, { vendeur = it }, label = { Text("Vendeur") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                numeroFacture, { numeroFacture = it },
                label = { Text("Numéro de facture ou de ticket") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                debutGarantie, { debutGarantie = it },
                label = { Text("Début de garantie (JJ/MM/AAAA)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                dureeMois, { dureeMois = it },
                label = { Text("Durée de garantie (mois)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                finGarantie,
                { finGarantie = it; finModifiee = true },
                label = { Text("Fin de garantie (JJ/MM/AAAA)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "La date de fin est calculée à partir du début et de la durée : elle reste modifiable à la main.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                emplacement, { emplacement = it },
                label = { Text("Emplacement (pièce, site)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                proprietaire, { proprietaire = it },
                label = { Text("Propriétaire ou utilisateur") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = statut,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Statut") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { menuStatut = true }) { Text("Changer") }
                DropdownMenu(expanded = menuStatut, onDismissRequest = { menuStatut = false }) {
                    Statuts.TOUS.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { statut = option; menuStatut = false })
                    }
                }
            }
            OutlinedTextField(
                notes, { notes = it },
                label = { Text("Notes libres") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )

            if (!modeEdition) {
                OutlinedTextField(
                    nombre, { nombre = it },
                    label = { Text("Nombre d'appareils identiques") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    "Utile quand une même facture couvre plusieurs exemplaires : une fiche par appareil, " +
                        "la facture étant rattachée à chacune.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetour, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Button(
                    onClick = {
                        if (nom.isBlank()) {
                            confirmerVide = true
                            return@Button
                        }
                        val quantite = (nombre.toIntOrNull() ?: 1).coerceIn(1, 20)
                        val base = construire(
                            initial = initial,
                            nom = nom,
                            categorie = categorie,
                            marque = marque,
                            modele = modele,
                            numeroSerie = numeroSerie,
                            codeBarres = codeBarres,
                            dateAchat = dateAchat,
                            prix = prix,
                            vendeur = vendeur,
                            numeroFacture = numeroFacture,
                            debutGarantie = debutGarantie,
                            finGarantie = finGarantie,
                            dureeMois = dureeMois,
                            emplacement = emplacement,
                            proprietaire = proprietaire,
                            statut = statut,
                            notes = notes,
                        )
                        val liste = (1..quantite).map { index ->
                            if (index == 1) base else base.copy(
                                id = 0,
                                nom = "${base.nom} ${index}",
                                numeroSerie = "",
                            )
                        }
                        onEnregistrer(liste)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Enregistrer") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmerVide) {
        AlertDialog(
            onDismissRequest = { confirmerVide = false },
            title = { Text("Nom obligatoire") },
            text = { Text("Donnez un nom à l'appareil pour pouvoir le retrouver (par exemple « Lave-linge Bosch »).") },
            confirmButton = { TextButton(onClick = { confirmerVide = false }) { Text("Compris") } },
        )
    }
}

private fun construire(
    initial: Appareil?,
    nom: String,
    categorie: String,
    marque: String,
    modele: String,
    numeroSerie: String,
    codeBarres: String,
    dateAchat: String,
    prix: String,
    vendeur: String,
    numeroFacture: String,
    debutGarantie: String,
    finGarantie: String,
    dureeMois: String,
    emplacement: String,
    proprietaire: String,
    statut: String,
    notes: String,
): Appareil = Appareil(
    id = initial?.id ?: 0,
    nom = nom.trim(),
    categorie = categorie,
    marque = marque.trim(),
    modele = modele.trim(),
    numeroSerie = numeroSerie.trim(),
    codeBarres = codeBarres.trim(),
    dateAchat = Dates.iso(Dates.lireSouple(dateAchat)),
    prixAchat = prix.replace(',', '.').trim().toDoubleOrNull(),
    vendeur = vendeur.trim(),
    numeroFacture = numeroFacture.trim(),
    debutGarantie = Dates.iso(Dates.lireSouple(debutGarantie)),
    finGarantie = Dates.iso(Dates.lireSouple(finGarantie)),
    dureeGarantieMois = dureeMois.trim().toIntOrNull(),
    emplacement = emplacement.trim(),
    proprietaire = proprietaire.trim(),
    notes = notes.trim(),
    statut = statut,
    photo = initial?.photo.orEmpty(),
    archive = initial?.archive ?: false,
)
