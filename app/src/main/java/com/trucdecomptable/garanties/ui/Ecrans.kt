package com.trucdecomptable.garanties.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.garanties.data.Appareil
import com.trucdecomptable.garanties.data.Document
import com.trucdecomptable.garanties.data.Garantie
import com.trucdecomptable.garanties.metier.Echeance
import com.trucdecomptable.garanties.metier.Echeances
import com.trucdecomptable.garanties.metier.EtatGarantie
import com.trucdecomptable.garanties.metier.Synthese
import com.trucdecomptable.garanties.util.Dates

// --------------------------------------------------------------- composables

@Composable
fun PastilleEtat(etat: EtatGarantie, taille: Int = 12) {
    Box(
        Modifier
            .size(taille.dp)
            .clip(CircleShape)
            .background(CouleursEtat.de(etat)),
    )
}

@Composable
fun EtiquetteEtat(etat: EtatGarantie) {
    val libelle = when (etat) {
        EtatGarantie.ACTIVE -> "Sous garantie"
        EtatGarantie.PROCHE -> "Expire bientôt"
        EtatGarantie.TRES_PROCHE -> "Moins de 30 jours"
        EtatGarantie.EXPIREE -> "Expirée"
        EtatGarantie.INCONNUE -> "Date inconnue"
    }
    Text(
        libelle,
        color = CouleursEtat.de(etat),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun Compteur(libelle: String, valeur: String, couleur: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(valeur, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = couleur)
            Text(libelle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun CarteAppareil(
    appareil: Appareil,
    garanties: List<Garantie>,
    nombreDocuments: Int,
    onClick: () -> Unit,
) {
    val fin = Echeances.finLaPlusLointaine(appareil, garanties)
    val etat = Echeances.etatDe(fin)
    val jours = Echeances.joursRestants(fin)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PastilleEtat(etat, taille = 14)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    appareil.nom,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOf(appareil.categorie, appareil.marque, appareil.modele)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtiquetteEtat(etat)
                    if (jours != null && jours >= 0) {
                        Text(
                            " · ${jours} j · fin ${Dates.afficher(fin)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (fin.isNotBlank()) {
                        Text(
                            " · fin ${Dates.afficher(fin)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (nombreDocuments == 0) {
                    Text(
                        "Aucun justificatif",
                        fontSize = 11.sp,
                        color = CouleursEtat.Orange,
                    )
                }
            }
            if (appareil.archive) {
                Icon(Icons.Default.Archive, contentDescription = "Archivé", tint = CouleursEtat.Gris)
            }
        }
    }
}

// ------------------------------------------------------------ tableau de bord

@Composable
fun TableauDeBord(
    synthese: Synthese,
    echeances: List<Echeance>,
    onOuvrirFiche: (Long) -> Unit,
    onAjouter: () -> Unit,
    onVoirArchives: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAjouter) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un appareil")
            }
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
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Compteur("Appareils", synthese.total.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    Compteur("Sous garantie", synthese.sousGarantie.toString(), CouleursEtat.Vert, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Compteur("Échéance < 90 j", synthese.expirant90.toString(), CouleursEtat.Orange, Modifier.weight(1f))
                    Compteur("Expirées", synthese.expirees.toString(), CouleursEtat.Gris, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        Modifier.weight(1f).clickable(onClick = onVoirArchives),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(synthese.archives.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Archivés", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(
                        Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            val texte = if (synthese.derniereSauvegarde <= 0) {
                                "Jamais"
                            } else {
                                Dates.horodatageCourt(synthese.derniereSauvegarde)
                            }
                            Text(texte, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Dernière sauvegarde", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (synthese.sansFacture > 0 || synthese.sansNumeroSerie > 0 || synthese.sansDateFin > 0) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReportProblem, contentDescription = null, tint = CouleursEtat.Orange)
                                Spacer(Modifier.width(8.dp))
                                Text("Justificatifs manquants", fontWeight = FontWeight.SemiBold)
                            }
                            if (synthese.sansFacture > 0) Text("· ${synthese.sansFacture} appareil(s) sans facture", fontSize = 13.sp)
                            if (synthese.sansNumeroSerie > 0) Text("· ${synthese.sansNumeroSerie} sans numéro de série", fontSize = 13.sp)
                            if (synthese.sansDateFin > 0) Text("· ${synthese.sansDateFin} sans date de fin de garantie", fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                Text(
                    "À faire",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (echeances.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CouleursEtat.Vert)
                            Spacer(Modifier.width(8.dp))
                            Text("Aucune échéance dans les 90 prochains jours.", fontSize = 13.sp)
                        }
                    }
                }
            }
            items(echeances.take(12), key = { "${it.appareilId}-${it.fin}-${it.libelle}" }) { echeance ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOuvrirFiche(echeance.appareilId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PastilleEtat(echeance.etat, taille = 10)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(echeance.appareilNom, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "${echeance.libelle} · ${Dates.afficher(Dates.iso(echeance.fin))}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (echeance.joursRestants < 0) "expirée" else "${echeance.joursRestants} j",
                            color = CouleursEtat.de(echeance.etat),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }
}

// ------------------------------------------------------------------- liste

enum class FiltreListe(val libelle: String) {
    TOUS("Tous"),
    SOUS_GARANTIE("Sous garantie"),
    BIENTOT("Expire < 90 j"),
    EXPIREES("Expirées"),
    SANS_FACTURE("Sans facture"),
}

@Composable
fun ListeAppareils(
    appareils: List<Appareil>,
    garanties: List<Garantie>,
    documents: List<Document>,
    categories: List<String>,
    onOuvrirFiche: (Long) -> Unit,
    onAjouter: () -> Unit,
    archives: Boolean = false,
) {
    var recherche by remember { mutableStateOf("") }
    var filtre by remember { mutableStateOf(FiltreListe.TOUS) }
    var categorie by remember { mutableStateOf("") }

    val maintenant = Dates.aujourdhui()
    val docsParAppareil = documents.groupingBy { it.appareilId }.eachCount()

    val filtres = appareils.asSequence()
        .filter { it.archive == archives }
        .filter { appareil ->
            val texte = recherche.trim().lowercase()
            texte.isBlank() || listOf(
                appareil.nom, appareil.marque, appareil.modele, appareil.categorie,
                appareil.vendeur, appareil.numeroSerie, appareil.emplacement, appareil.proprietaire,
                appareil.numeroFacture, appareil.notes,
            ).any { it.lowercase().contains(texte) }
        }
        .filter { categorie.isBlank() || it.categorie == categorie }
        .filter { appareil ->
            val fin = Echeances.finLaPlusLointaine(appareil, garanties)
            val jours = Echeances.joursRestants(fin, maintenant)
            when (filtre) {
                FiltreListe.TOUS -> true
                FiltreListe.SOUS_GARANTIE -> jours != null && jours >= 0
                FiltreListe.BIENTOT -> jours != null && jours in 0..90
                FiltreListe.EXPIREES -> jours == null || jours < 0
                FiltreListe.SANS_FACTURE -> (docsParAppareil[appareil.id] ?: 0) == 0
            }
        }
        .sortedBy { it.nom.lowercase() }
        .toList()

    Scaffold(
        floatingActionButton = {
            if (!archives) {
                FloatingActionButton(onClick = onAjouter) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter un appareil")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            OutlinedTextField(
                value = recherche,
                onValueChange = { recherche = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Appareil, marque, vendeur, n° de série…") },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FiltreListe.entries.forEach { option ->
                    FilterChip(
                        selected = filtre == option,
                        onClick = { filtre = option },
                        label = { Text(option.libelle, fontSize = 12.sp) },
                    )
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = categorie.isBlank(),
                    onClick = { categorie = "" },
                    label = { Text("Toutes catégories", fontSize = 12.sp) },
                )
                categories.forEach { nom ->
                    FilterChip(
                        selected = categorie == nom,
                        onClick = { categorie = if (categorie == nom) "" else nom },
                        label = { Text(nom, fontSize = 12.sp) },
                    )
                }
            }
            if (filtres.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (archives) "Aucun appareil archivé." else "Aucun appareil. Appuyez sur + pour commencer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) {
                    items(filtres, key = { it.id }) { appareil ->
                        CarteAppareil(
                            appareil = appareil,
                            garanties = garanties,
                            nombreDocuments = docsParAppareil[appareil.id] ?: 0,
                            onClick = { onOuvrirFiche(appareil.id) },
                        )
                    }
                    item { Spacer(Modifier.height(70.dp)) }
                }
            }
        }
    }
}

@Composable
fun BandeauAlerte(texte: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = CouleursEtat.Orange)
            Spacer(Modifier.width(8.dp))
            Text(texte, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
