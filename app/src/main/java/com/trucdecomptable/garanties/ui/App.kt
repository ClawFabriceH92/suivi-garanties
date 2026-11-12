package com.trucdecomptable.garanties.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.trucdecomptable.garanties.BuildConfig
import com.trucdecomptable.garanties.data.Appareil
import com.trucdecomptable.garanties.data.Document
import com.trucdecomptable.garanties.export.ExportManager
import com.trucdecomptable.garanties.metier.Echeances
import com.trucdecomptable.garanties.notif.Rappels
import com.trucdecomptable.garanties.update.UpdateManager
import com.trucdecomptable.garanties.util.Dates
import com.trucdecomptable.garanties.util.Diag
import com.trucdecomptable.garanties.util.Fichiers
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun ApplicationGaranties(vm: GarantiesViewModel) {
    val contexte = LocalContext.current
    val appareils by vm.appareils.collectAsState()
    val documents by vm.documents.collectAsState()
    val garanties by vm.garanties.collectAsState()
    val parametres by vm.parametres.collectAsState()
    val synthese by vm.synthese.collectAsState()
    val extraction by vm.extraction.collectAsState()
    val ocrEnCours by vm.ocrEnCours.collectAsState()
    val factureImportee by vm.factureImportee.collectAsState()
    val ficheDemandee by vm.ficheDemandee.collectAsState()
    val message by vm.message.collectAsState()
    val journal by vm.journalDe(0).collectAsState()
    val etatMaj by UpdateManager.state.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var verrouille by remember { mutableStateOf(parametres.verrouillageActif) }
    var onglet by remember { mutableIntStateOf(0) }
    var ficheId by remember { mutableStateOf<Long?>(null) }
    var modeAjout by remember { mutableStateOf(false) }
    var edition by remember { mutableStateOf<Appareil?>(null) }
    var pieceAttendue by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var dialogueRestauration by remember { mutableStateOf(false) }
    var uriSauvegarde by remember { mutableStateOf<Uri?>(null) }
    var texteBrut by remember { mutableStateOf<String?>(null) }
    var photoAppareilPour by remember { mutableStateOf<Long?>(null) }

    // ------------------------------------------------------------ lanceurs

    var uriPhoto by remember { mutableStateOf<Uri?>(null) }
    val appareilPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { reussi ->
        val uri = uriPhoto
        if (!reussi || uri == null) return@rememberLauncherForActivityResult
        val cible = photoAppareilPour
        if (cible != null) {
            val interne = runCatching { Fichiers.importer(contexte, uri) }.getOrNull()
            if (interne != null) {
                vm.enregistrerPhoto(cible, interne)
                vm.signaler("Photo de l'appareil enregistrée.")
            }
            photoAppareilPour = null
        } else {
            modeAjout = true
            vm.analyserFacture(uri)
        }
    }

    fun prendrePhoto(pourAppareil: Long? = null) {
        photoAppareilPour = pourAppareil
        val fichier = File(Fichiers.dossier(contexte), "photo-${System.currentTimeMillis()}.jpg")
        val cible = FileProvider.getUriForFile(contexte, "${contexte.packageName}.fileprovider", fichier)
        uriPhoto = cible
        appareilPhoto.launch(cible)
    }

    val importFacture = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            modeAjout = true
            vm.analyserFacture(uri)
        }
    }

    val importPiece = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val attente = pieceAttendue
        pieceAttendue = null
        if (uri != null && attente != null) {
            runCatching {
                contexte.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.ajouterDocument(attente.first, uri, attente.second)
        }
    }

    val importSauvegarde = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uriSauvegarde = uri
            dialogueRestauration = true
        }
    }

    val permissionNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { accordee ->
        if (!accordee) vm.signaler("Notifications refusées : les rappels ne pourront pas s'afficher.")
    }

    LaunchedEffect(Unit) {
        if (!Rappels.permissionAccordee(contexte)) {
            permissionNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        Rappels.planifier(contexte)
        Diag.record("app", "démarrage v${BuildConfig.VERSION_NAME}")
    }

    // Vérification de mise à jour : au lancement puis une fois par heure au plus.
    LaunchedEffect(parametres.majAuto) {
        if (!parametres.majAuto) return@LaunchedEffect
        while (true) {
            UpdateManager.checkAtLaunch(contexte, skippedVersion = "")
            delay(30 * 60_000L)
        }
    }

    LaunchedEffect(ficheDemandee) {
        val id = ficheDemandee
        if (id != null) {
            vm.ficheConsommee()
            if (id > 0) {
                ficheId = id
                vm.chargerJournal(id)
            }
        }
    }

    LaunchedEffect(message) {
        val texte = message
        if (texte != null) {
            snackbar.showSnackbar(texte)
            vm.signaler(null)
        }
    }

    LaunchedEffect(ficheId) {
        ficheId?.let { vm.chargerJournal(it) }
    }

    ThemeGaranties(sombreForce = parametres.themeSombre) {
        Surface(Modifier.fillMaxSize()) {
            if (verrouille) {
                EcranVerrouillage(
                    verifier = { code -> vm.verifierCode(code) },
                    onDeblocage = { verrouille = false },
                )
                return@Surface
            }

            val appareilCourant = ficheId?.let { id -> appareils.firstOrNull { it.id == id } }

            when {
                modeAjout || edition != null -> EcranAjout(
                    initial = edition,
                    categorieInitiale = null,
                    extraction = extraction,
                    ocrEnCours = ocrEnCours,
                    modeEdition = edition != null,
                    categories = parametres.categories,
                    onRetour = {
                        modeAjout = false
                        edition = null
                        vm.viderExtraction()
                    },
                    onPhotographier = { prendrePhoto() },
                    onImporter = { importFacture.launch("*/*") },
                    onSaisieManuelle = { vm.viderExtraction() },
                    onEnregistrer = { liste ->
                        val modification = edition
                        if (modification != null) {
                            vm.enregistrer(liste.first()) { id ->
                                ficheId = id
                                edition = null
                                modeAjout = false
                                vm.viderExtraction()
                            }
                        } else {
                            vm.enregistrerDepuisFacture(liste, factureImportee) { ids ->
                                modeAjout = false
                                vm.viderExtraction()
                                if (ids.size == 1) ficheId = ids.first()
                            }
                        }
                    },
                    onTexteBrut = { texteBrut = it },
                )

                appareilCourant != null -> EcranFiche(
                    appareil = appareilCourant,
                    garanties = vm.garantiesDe(appareilCourant.id),
                    documents = vm.documentsDe(appareilCourant.id),
                    journal = journal,
                    onRetour = { ficheId = null },
                    onModifier = { edition = appareilCourant },
                    onArchiver = { archive ->
                        vm.archiver(appareilCourant, archive)
                        ficheId = null
                    },
                    onSupprimer = {
                        vm.supprimer(appareilCourant)
                        ficheId = null
                    },
                    onAjouterGarantie = { vm.ajouterGarantie(it) },
                    onSupprimerGarantie = { vm.supprimerGarantie(it) },
                    onAjouterDocument = { type -> pieceAttendue = appareilCourant.id to type },
                    onOuvrirDocument = { document -> ouvrirDocument(contexte, document) },
                    onPartagerDocument = { document -> partagerDocument(contexte, document) },
                    onSupprimerDocument = { vm.supprimerDocument(it) },
                    onRenommerDocument = { document, nom -> vm.renommerDocument(document, nom) },
                    onPartagerDossier = { docs -> partagerDossier(contexte, appareilCourant, docs) },
                    onPrendrePhoto = { prendrePhoto(appareilCourant.id) },
                )

                else -> Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = onglet == 0,
                                onClick = { onglet = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("Accueil") },
                            )
                            NavigationBarItem(
                                selected = onglet == 1,
                                onClick = { onglet = 1 },
                                icon = { Icon(Icons.Default.List, contentDescription = null) },
                                label = { Text("Appareils") },
                            )
                            NavigationBarItem(
                                selected = onglet == 2,
                                onClick = { onglet = 2 },
                                icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                label = { Text("Archives") },
                            )
                            NavigationBarItem(
                                selected = onglet == 3,
                                onClick = { onglet = 3 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Réglages") },
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    Column(Modifier.padding(padding)) {
                        when (onglet) {
                            0 -> TableauDeBord(
                                synthese = synthese,
                                echeances = Echeances.echeances(appareils, garanties),
                                onOuvrirFiche = { ficheId = it },
                                onAjouter = { modeAjout = true },
                                onVoirArchives = { onglet = 2 },
                            )
                            1 -> ListeAppareils(
                                appareils = appareils,
                                garanties = garanties,
                                documents = documents,
                                categories = parametres.categories,
                                onOuvrirFiche = { ficheId = it },
                                onAjouter = { modeAjout = true },
                            )
                            2 -> ListeAppareils(
                                appareils = appareils,
                                garanties = garanties,
                                documents = documents,
                                categories = parametres.categories,
                                onOuvrirFiche = { ficheId = it },
                                onAjouter = { modeAjout = true },
                                archives = true,
                            )
                            else -> EcranReglages(
                                parametres = parametres,
                                etatMaj = etatMaj,
                                onParametres = { vm.majParametres(it) },
                                onDefinirCode = { vm.definirCode(it) },
                                onExporterZip = {
                                    vm.exporterZip()?.let { partagerFichier(contexte, it, "application/zip") }
                                },
                                onExporterCsv = {
                                    vm.exporterCsv()?.let { partagerFichier(contexte, it, "text/csv") }
                                },
                                onExporterPdf = {
                                    vm.exporterPdf()?.let { partagerFichier(contexte, it, "application/pdf") }
                                },
                                onSauvegarder = { motDePasse ->
                                    vm.sauvegarder(motDePasse) { fichier ->
                                        if (fichier != null) {
                                            partagerFichier(contexte, fichier, "application/octet-stream")
                                        }
                                    }
                                },
                                onRestaurer = { importSauvegarde.launch(arrayOf("*/*")) },
                                onVerifierMaj = { UpdateManager.checkNow(contexte) },
                                onTelechargerMaj = { UpdateManager.download(contexte) },
                                onInstallerMaj = { UpdateManager.install(contexte) },
                                onAutoriserInstallation = { UpdateManager.openInstallSettings(contexte) },
                                onTesterRappels = { Rappels.verifierMaintenant(contexte); vm.signaler("Vérification des rappels lancée.") },
                            )
                        }
                    }
                }
            }

            // Disponibilité d'une mise à jour : visible quel que soit l'écran.
            DialogueMiseAJour(
                etat = etatMaj,
                onTelecharger = { UpdateManager.download(contexte) },
                onInstaller = { UpdateManager.install(contexte) },
                onAutoriser = { UpdateManager.openInstallSettings(contexte) },
                onPlusTard = { UpdateManager.dismiss() },
            )

            if (dialogueRestauration) {
                var motDePasse by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { dialogueRestauration = false },
                    title = { Text("Restaurer une sauvegarde") },
                    text = {
                        Column {
                            Text(
                                "La base actuelle est remplacée par celle de la sauvegarde. Un export préalable " +
                                    "est conseillé si le téléphone contient des fiches récentes.",
                                fontSize = 12.sp,
                            )
                            OutlinedTextField(
                                motDePasse, { motDePasse = it },
                                label = { Text("Mot de passe de la sauvegarde") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val uri = uriSauvegarde
                            dialogueRestauration = false
                            if (uri != null) {
                                vm.restaurer(uri, motDePasse) { ok ->
                                    if (ok) {
                                        vm.signaler("Sauvegarde restaurée. Relancez l'application pour voir les fiches.")
                                    }
                                }
                            }
                        }) { Text("Restaurer") }
                    },
                    dismissButton = { TextButton(onClick = { dialogueRestauration = false }) { Text("Annuler") } },
                )
            }

            texteBrut?.let { texte ->
                AlertDialog(
                    onDismissRequest = { texteBrut = null },
                    title = { Text("Texte reconnu") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(texte, fontSize = 11.sp)
                        }
                    },
                    confirmButton = { TextButton(onClick = { texteBrut = null }) { Text("Fermer") } },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ partage

fun partagerFichier(contexte: Context, fichier: File, mime: String) {
    runCatching {
        val uri = Fichiers.uriPartageFichier(contexte, fichier)
        val envoi = ExportManager.partageUri(uri, mime, fichier.name)
        contexte.startActivity(Intent.createChooser(envoi, "Envoyer ${fichier.name}"))
    }.onFailure { Diag.record("partage", it) }
}

fun partagerDocument(contexte: Context, document: Document) {
    runCatching {
        val uri = Fichiers.uriPartage(contexte, document.chemin)
        val envoi = ExportManager.partageUri(uri, document.mime.ifBlank { Fichiers.mime(document.chemin) }, document.nom)
        contexte.startActivity(Intent.createChooser(envoi, "Partager ${document.nom}"))
    }.onFailure { Diag.record("partage", it) }
}

/** Fiche + justificatifs sélectionnés, dans un seul envoi. */
fun partagerDossier(contexte: Context, appareil: Appareil, documents: List<Document>) {
    runCatching {
        val resume = buildString {
            appendLine("Fiche appareil : ${appareil.nom}")
            if (appareil.marque.isNotBlank()) appendLine("Marque : ${appareil.marque}")
            if (appareil.modele.isNotBlank()) appendLine("Modèle : ${appareil.modele}")
            if (appareil.numeroSerie.isNotBlank()) appendLine("Numéro de série : ${appareil.numeroSerie}")
            if (appareil.dateAchat.isNotBlank()) appendLine("Acheté le : ${Dates.afficher(appareil.dateAchat)}")
            if (appareil.vendeur.isNotBlank()) appendLine("Vendeur : ${appareil.vendeur}")
            if (appareil.finGarantie.isNotBlank()) {
                appendLine("Garantie jusqu'au : ${Dates.afficher(appareil.finGarantie)}")
            }
            appareil.prixAchat?.let { appendLine("Prix : " + String.format(Locale.FRANCE, "%.2f €", it)) }
        }
        val uris = documents.mapNotNull { document ->
            runCatching { Fichiers.uriPartage(contexte, document.chemin) }.getOrNull()
        }
        val envoi = if (uris.isEmpty()) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, resume)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                putExtra(Intent.EXTRA_TEXT, resume)
                putExtra(Intent.EXTRA_SUBJECT, "Dossier ${appareil.nom}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        contexte.startActivity(Intent.createChooser(envoi, "Partager le dossier"))
    }.onFailure { Diag.record("partage", it) }
}

fun ouvrirDocument(contexte: Context, document: Document) {
    runCatching {
        val uri = Fichiers.uriPartage(contexte, document.chemin)
        val intention = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, document.mime.ifBlank { Fichiers.mime(document.chemin) })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        contexte.startActivity(intention)
    }.onFailure {
        Diag.record("ouverture", it)
    }
}

// ---------------------------------------------------------------- verrouillage

@Composable
fun EcranVerrouillage(verifier: (String) -> Boolean, onDeblocage: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Mes Garanties", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Saisissez votre code d'accès", fontSize = 13.sp)
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it.filter { c -> c.isDigit() }.take(12)
                erreur = false
            },
            label = { Text("Code") },
            visualTransformation = PasswordVisualTransformation(),
            isError = erreur,
            modifier = Modifier.fillMaxWidth(0.7f),
            singleLine = true,
        )
        if (erreur) Text("Code incorrect", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Button(
            onClick = { if (verifier(code)) onDeblocage() else erreur = true },
            modifier = Modifier.fillMaxWidth(0.7f),
        ) { Text("Déverrouiller") }
    }
}

// --------------------------------------------------------------------- mise à jour

@Composable
fun DialogueMiseAJour(
    etat: UpdateManager.State,
    onTelecharger: () -> Unit,
    onInstaller: () -> Unit,
    onAutoriser: () -> Unit,
    onPlusTard: () -> Unit,
) {
    val visible = etat is UpdateManager.State.Available || etat is UpdateManager.State.ReadyToInstall
    if (!visible) return
    val version = when (etat) {
        is UpdateManager.State.Available -> etat.version
        is UpdateManager.State.ReadyToInstall -> etat.version
        else -> ""
    }
    AlertDialog(
        onDismissRequest = onPlusTard,
        title = { Text("Mise à jour $version") },
        text = {
            Text(
                if (etat is UpdateManager.State.ReadyToInstall) {
                    "La version $version est téléchargée. L'installation demande une confirmation Android."
                } else {
                    "Une nouvelle version ($version) est disponible. Vos fiches et vos factures sont conservées."
                },
            )
        },
        confirmButton = {
            if (etat is UpdateManager.State.ReadyToInstall) {
                Button(onClick = onInstaller) { Text("Installer") }
            } else {
                OutlinedButton(onClick = onTelecharger) { Text("Télécharger") }
            }
        },
        dismissButton = {
            TextButton(onClick = if (etat is UpdateManager.State.Failed) onAutoriser else onPlusTard) {
                Text(if (etat is UpdateManager.State.Failed) "Autoriser" else "Plus tard")
            }
        },
    )
}
