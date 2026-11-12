package com.trucdecomptable.garanties.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.trucdecomptable.garanties.util.Fichiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --------------------------------------------------------------- composables

/**
 * Photo de l'appareil, lue depuis le coffre chiffré : déchiffrée dans le cache
 * puis décodée hors du fil principal (une photo de téléphone fait 3 à 8 Mo).
 */
@Composable
fun PhotoLocale(nom: String, taille: androidx.compose.ui.unit.Dp, arrondi: Int = 8, pleineLargeur: Boolean = false) {
    val contexte = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, nom) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (nom.isBlank()) return@runCatching null
                val fichier = Fichiers.clair(contexte, nom)
                if (!fichier.exists()) return@runCatching null
                val bornes = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(fichier.absolutePath, bornes)
                var facteur = 1
                val cote = if (pleineLargeur) 900 else 320
                while (bornes.outWidth / facteur > cote * 2) facteur *= 2
                android.graphics.BitmapFactory.decodeFile(
                    fichier.absolutePath,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = facteur },
                )?.asImageBitmap()
            }.getOrNull()
        }
    }
    val forme = androidx.compose.foundation.shape.RoundedCornerShape(arrondi.dp)
    Box(
        if (pleineLargeur) {
            Modifier
                .fillMaxWidth()
                .height(taille)
                .clip(forme)
        } else {
            Modifier
                .size(taille)
                .clip(forme)
        }.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Photo de l'appareil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
            if (appareil.photo.isNotBlank()) {
                PhotoLocale(appareil.photo, taille = 48.dp)
                Spacer(Modifier.width(10.dp))
            } else {
                PastilleEtat(etat, taille = 14)
                Spacer(Modifier.width(12.dp))
            }
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
    var annee by remember { mutableStateOf("") }
    var emplacement by remember { mutableStateOf("") }
    var proprietaire by remember { mutableStateOf("") }
    var montantMin by remember { mutableStateOf("") }
    var filtresAvances by remember { mutableStateOf(false) }

    val annees = appareils.mapNotNull { it.dateAchat.take(4).toIntOrNull() }
        .distinct().sortedDescending()
    val emplacements = appareils.map { it.emplacement }.filter { it.isNotBlank() }.distinct().sorted()
    val proprietaires = appareils.map { it.proprietaire }.filter { it.isNotBlank() }.distinct().sorted()
    val seuil = montantMin.replace(',', '.').toDoubleOrNull()

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
        .filter { annee.isBlank() || it.dateAchat.startsWith(annee) }
        .filter { emplacement.isBlank() || it.emplacement == emplacement }
        .filter { proprietaire.isBlank() || it.proprietaire == proprietaire }
        .filter { seuil == null || (it.prixAchat ?: 0.0) >= seuil }
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
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = filtresAvances || annee.isNotBlank() || emplacement.isNotBlank() ||
                        proprietaire.isNotBlank() || montantMin.isNotBlank(),
                    onClick = { filtresAvances = !filtresAvances },
                    label = { Text("Plus de filtres", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                )
                if (annee.isNotBlank()) {
                    FilterChip(selected = true, onClick = { annee = "" }, label = { Text(annee, fontSize = 12.sp) })
                }
                if (emplacement.isNotBlank()) {
                    FilterChip(selected = true, onClick = { emplacement = "" }, label = { Text(emplacement, fontSize = 12.sp) })
                }
                if (proprietaire.isNotBlank()) {
                    FilterChip(selected = true, onClick = { proprietaire = "" }, label = { Text(proprietaire, fontSize = 12.sp) })
                }
                if (montantMin.isNotBlank()) {
                    FilterChip(selected = true, onClick = { montantMin = "" }, label = { Text("dès $montantMin €", fontSize = 12.sp) })
                }
            }
            if (filtresAvances) {
                if (annees.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        annees.forEach { valeur ->
                            FilterChip(
                                selected = annee == valeur.toString(),
                                onClick = { annee = if (annee == valeur.toString()) "" else valeur.toString() },
                                label = { Text(valeur.toString(), fontSize = 12.sp) },
                            )
                        }
                    }
                }
                if (emplacements.isNotEmpty() || proprietaires.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        emplacements.forEach { valeur ->
                            FilterChip(
                                selected = emplacement == valeur,
                                onClick = { emplacement = if (emplacement == valeur) "" else valeur },
                                label = { Text(valeur, fontSize = 12.sp) },
                            )
                        }
                        proprietaires.forEach { valeur ->
                            FilterChip(
                                selected = proprietaire == valeur,
                                onClick = { proprietaire = if (proprietaire == valeur) "" else valeur },
                                label = { Text(valeur, fontSize = 12.sp) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = montantMin,
                    onValueChange = { montantMin = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Montant d'achat minimum (€)") },
                )
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
