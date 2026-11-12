package com.trucdecomptable.garanties.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trucdecomptable.garanties.data.Appareil
import com.trucdecomptable.garanties.data.CoffreDb
import com.trucdecomptable.garanties.data.Document
import com.trucdecomptable.garanties.data.Evenement
import com.trucdecomptable.garanties.data.Garantie
import com.trucdecomptable.garanties.data.Parametres
import com.trucdecomptable.garanties.data.Statuts
import com.trucdecomptable.garanties.data.TypesDocument
import com.trucdecomptable.garanties.metier.Echeances
import com.trucdecomptable.garanties.metier.ExtractionFacture
import com.trucdecomptable.garanties.metier.FactureExtraite
import com.trucdecomptable.garanties.metier.Synthese
import com.trucdecomptable.garanties.notif.Rappels
import com.trucdecomptable.garanties.ocr.OcrFacture
import com.trucdecomptable.garanties.util.Dates
import com.trucdecomptable.garanties.util.Diag
import com.trucdecomptable.garanties.util.Fichiers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.net.Uri
import java.io.File

/**
 * Un seul état observable pour toute l'application : Room alimente les flux,
 * les compteurs du tableau de bord sont recalculés à chaque changement.
 */
class GarantiesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CoffreDb.obtenir(application)
    private val dao = db.dao()

    val appareils: StateFlow<List<Appareil>> = dao.observerTous()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val documents: StateFlow<List<Document>> = dao.observerTousDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val garanties: StateFlow<List<Garantie>> = dao.observerToutesGaranties()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _parametres = MutableStateFlow(Parametres.lire(application))
    val parametres: StateFlow<Parametres> = _parametres

    val synthese: StateFlow<Synthese> = combine(appareils, garanties, documents, _parametres) {
            apps, gars, docs, params ->
        Echeances.synthese(
            appareils = apps,
            garanties = gars,
            documentsParAppareil = docs.groupingBy { it.appareilId }.eachCount(),
            derniereSauvegarde = params.derniereSauvegarde,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Synthese())

    /** Appareil demandé par une notification : la fiche s'ouvre directement. */
    private val _ficheDemandee = MutableStateFlow<Long?>(null)
    val ficheDemandee: StateFlow<Long?> = _ficheDemandee

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // ------------------------------------------------------------- lecture

    fun appareil(id: Long): Appareil? = appareils.value.firstOrNull { it.id == id }

    fun documentsDe(appareilId: Long): List<Document> = documents.value.filter { it.appareilId == appareilId }

    fun garantiesDe(appareilId: Long): List<Garantie> = garanties.value.filter { it.appareilId == appareilId }

    fun journalDe(appareilId: Long): StateFlow<List<Evenement>> = journal

    private val journal = MutableStateFlow<List<Evenement>>(emptyList())
    private var journalJob: kotlinx.coroutines.Job? = null

    /** Charge l'historique d'une fiche (une seule collecte active à la fois). */
    fun chargerJournal(appareilId: Long) {
        journalJob?.cancel()
        journalJob = viewModelScope.launch {
            dao.observerJournal(appareilId).collect { journal.value = it }
        }
    }

    fun echeances(): List<com.trucdecomptable.garanties.metier.Echeance> =
        Echeances.echeances(appareils.value, garanties.value)

    fun ouvrirFiche(appareilId: Long?) {
        _ficheDemandee.value = appareilId
    }

    fun ficheConsommee() {
        _ficheDemandee.value = null
    }

    fun signaler(texte: String?) {
        _message.value = texte
    }

    // ------------------------------------------------------- écriture fiches

    fun enregistrer(appareil: Appareil, apres: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = if (appareil.id == 0L) {
                val nouveau = appareil.copy(creeLe = System.currentTimeMillis(), modifieLe = System.currentTimeMillis())
                dao.inserer(nouveau)
            } else {
                dao.modifier(appareil.copy(modifieLe = System.currentTimeMillis()))
                dao.journaliser(Evenement(appareilId = appareil.id, texte = "Fiche modifiée"))
                appareil.id
            }
            if (appareil.id == 0L) {
                dao.journaliser(Evenement(appareilId = id, texte = "Fiche créée"))
            }
            Diag.record("fiche", "enregistrée #$id ${appareil.nom}")
            apres?.invoke(id)
        }
    }

    fun archiver(appareil: Appareil, archive: Boolean) {
        viewModelScope.launch {
            dao.modifier(
                appareil.copy(
                    archive = archive,
                    statut = if (archive) Statuts.ARCHIVE else Statuts.ACTIF,
                    modifieLe = System.currentTimeMillis(),
                ),
            )
            dao.journaliser(
                Evenement(
                    appareilId = appareil.id,
                    texte = if (archive) "Archivé (les justificatifs sont conservés)" else "Restauré depuis les archives",
                ),
            )
        }
    }

    /** Suppression définitive : la confirmation est demandée par l'écran. */
    fun supprimer(appareil: Appareil) {
        viewModelScope.launch {
            val pieces = documentsDe(appareil.id).map { it.chemin }.distinct()
            dao.supprimerParId(appareil.id)
            pieces.forEach { chemin ->
                if (dao.compterFichier(chemin) == 0) Fichiers.supprimer(getApplication(), chemin)
            }
            Fichiers.supprimer(getApplication(), appareil.photo)
            Diag.record("fiche", "supprimée #${appareil.id} ${appareil.nom}")
        }
    }

    fun changerStatut(appareil: Appareil, statut: String) {
        viewModelScope.launch {
            dao.modifier(appareil.copy(statut = statut, modifieLe = System.currentTimeMillis()))
            dao.journaliser(Evenement(appareilId = appareil.id, texte = "Statut : $statut"))
        }
    }

    // ----------------------------------------------------------- documents

    fun ajouterDocument(appareilId: Long, uri: Uri, type: String, nomPersonnalise: String = "") {
        viewModelScope.launch {
            val contexte = getApplication<Application>()
            val nomAffiche = nomPersonnalise.ifBlank { Fichiers.nomAffiche(contexte, uri) }
            val interne = withContext(Dispatchers.IO) { Fichiers.importer(contexte, uri) }
            if (interne == null) {
                _message.value = "Impossible de lire ce fichier."
                return@launch
            }
            dao.insererDocument(
                Document(
                    appareilId = appareilId,
                    type = type,
                    nom = nomAffiche,
                    chemin = interne,
                    mime = Fichiers.mime(nomAffiche),
                ),
            )
            dao.journaliser(Evenement(appareilId = appareilId, texte = "Document ajouté : $type · $nomAffiche"))
            _message.value = "Document enregistré hors ligne."
        }
    }

    fun renommerDocument(document: Document, nom: String) {
        viewModelScope.launch { dao.renommerDocument(document.id, nom.ifBlank { document.nom }) }
    }

    fun supprimerDocument(document: Document) {
        viewModelScope.launch {
            dao.supprimerDocument(document.id)
            if (dao.compterFichier(document.chemin) == 0) Fichiers.supprimer(getApplication(), document.chemin)
            dao.journaliser(Evenement(appareilId = document.appareilId, texte = "Document supprimé : ${document.nom}"))
        }
    }

    fun enregistrerPhoto(appareilId: Long, nomFichier: String) {
        viewModelScope.launch {
            dao.insererDocument(
                Document(
                    appareilId = appareilId,
                    type = TypesDocument.PHOTO_APPAREIL,
                    nom = "Photo de l'appareil",
                    chemin = nomFichier,
                    mime = Fichiers.mime(nomFichier),
                ),
            )
        }
    }

    // ----------------------------------------------------------- garanties

    fun ajouterGarantie(garantie: Garantie, apres: (() -> Unit)? = null) {
        viewModelScope.launch {
            dao.insererGarantie(garantie)
            dao.journaliser(
                Evenement(appareilId = garantie.appareilId, texte = "Garantie « ${garantie.type} » enregistrée"),
            )
            apres?.invoke()
        }
    }

    fun supprimerGarantie(garantie: Garantie) {
        viewModelScope.launch {
            dao.supprimerGarantie(garantie.id)
            dao.journaliser(
                Evenement(appareilId = garantie.appareilId, texte = "Garantie « ${garantie.type} » supprimée"),
            )
        }
    }

    // ----------------------------------------------------------------- OCR

    private val _ocrEnCours = MutableStateFlow(false)
    val ocrEnCours: StateFlow<Boolean> = _ocrEnCours

    private val _extraction = MutableStateFlow<FactureExtraite?>(null)
    val extraction: StateFlow<FactureExtraite?> = _extraction

    /** Le fichier importé est conservé : il devient la facture du ou des appareils. */
    private val _factureImportee = MutableStateFlow<String?>(null)
    val factureImportee: StateFlow<String?> = _factureImportee

    fun analyserFacture(uri: Uri) {
        if (_ocrEnCours.value) return
        viewModelScope.launch {
            _ocrEnCours.value = true
            val contexte = getApplication<Application>()
            try {
                val interne = withContext(Dispatchers.IO) { Fichiers.importer(contexte, uri) }
                if (interne == null) {
                    _message.value = "Document illisible."
                    return@launch
                }
                val resultat = OcrFacture.lire(contexte, Fichiers.uriPartageFichier(contexte, Fichiers.fichier(contexte, interne)), Fichiers.mime(interne))
                if (resultat.texte.isBlank()) {
                    _message.value = "Aucun texte détecté : la photo est peut-être floue. Vous pouvez saisir la fiche à la main."
                    _factureImportee.value = interne
                    return@launch
                }
                _factureImportee.value = interne
                _extraction.value = ExtractionFacture.analyser(resultat.texte)
                Diag.record("ocr", "extraction sur ${resultat.pages} page(s)")
            } catch (e: Exception) {
                Diag.record("ocr", e)
                _message.value = "Analyse impossible : ${e.message ?: "erreur inconnue"}. Saisie manuelle possible."
            } finally {
                _ocrEnCours.value = false
            }
        }
    }

    fun viderExtraction() {
        _extraction.value = null
        _factureImportee.value = null
    }

    /** Enregistre la fiche issue d'une facture, en rattachant le justificatif. */
    fun enregistrerDepuisFacture(
        appareils: List<Appareil>,
        facture: String?,
        apres: (List<Long>) -> Unit,
    ) {
        viewModelScope.launch {
            val ids = mutableListOf<Long>()
            val groupe = if (appareils.size > 1) java.util.UUID.randomUUID().toString() else ""
            for (appareil in appareils) {
                val id = dao.inserer(appareil.copy(creeLe = System.currentTimeMillis(), modifieLe = System.currentTimeMillis()))
                ids += id
                dao.journaliser(
                    Evenement(
                        appareilId = id,
                        texte = if (facture.isNullOrBlank()) "Fiche créée" else "Fiche créée depuis une facture",
                    ),
                )
                if (!facture.isNullOrBlank()) {
                    dao.insererDocument(
                        Document(
                            appareilId = id,
                            type = TypesDocument.FACTURE,
                            nom = "Facture ${appareil.vendeur}".trim(),
                            chemin = facture,
                            mime = Fichiers.mime(facture),
                            factureGroupe = groupe,
                        ),
                    )
                }
            }
            _extraction.value = null
            _factureImportee.value = null
            apres(ids)
        }
    }

    // ------------------------------------------------------------ réglages

    fun majParametres(nouveaux: Parametres) {
        val rappelsChangent = nouveaux.rappelsJours != _parametres.value.rappelsJours
        _parametres.value = nouveaux
        Parametres.ecrire(getApplication(), nouveaux)
        if (rappelsChangent) Rappels.planifier(getApplication())
    }

    fun definirCode(code: String?) {
        val empreinte = code?.takeIf { it.length >= 4 }?.let { Parametres.empreinte(it) }.orEmpty()
        majParametres(_parametres.value.copy(codeHash = empreinte))
    }

    fun verifierCode(code: String): Boolean =
        Parametres.empreinte(code) == _parametres.value.codeHash

    fun marquerSauvegarde() {
        majParametres(_parametres.value.copy(derniereSauvegarde = System.currentTimeMillis()))
    }

    // ------------------------------------------------------------ exports

    fun exporterZip(): File? = runCatching {
        com.trucdecomptable.garanties.export.ExportManager.exporterZip(
            getApplication(),
            appareils.value,
            documents.value,
            garanties.value,
        )
    }.onFailure { Diag.record("export", it); _message.value = "Export impossible : ${it.message}" }.getOrNull()

    fun exporterCsv(): File? = runCatching {
        com.trucdecomptable.garanties.export.ExportManager.exporterCsv(
            getApplication(), appareils.value, documents.value, garanties.value,
        )
    }.onFailure { Diag.record("export", it) }.getOrNull()

    fun exporterPdf(): File? = runCatching {
        com.trucdecomptable.garanties.export.ExportManager.exporterPdf(
            getApplication(), appareils.value, documents.value, garanties.value,
        )
    }.onFailure { Diag.record("export", it) }.getOrNull()

    fun sauvegarder(motDePasse: String, apres: (File?) -> Unit) {
        viewModelScope.launch {
            val fichier = runCatching {
                withContext(Dispatchers.IO) {
                    com.trucdecomptable.garanties.export.ExportManager.sauvegarder(
                        getApplication(), db, motDePasse,
                    )
                }
            }.onFailure {
                Diag.record("sauvegarde", it)
                _message.value = "Sauvegarde impossible : ${it.message}"
            }.getOrNull()
            if (fichier != null) marquerSauvegarde()
            apres(fichier)
        }
    }

    fun restaurer(uri: Uri, motDePasse: String, apres: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    com.trucdecomptable.garanties.export.ExportManager.restaurer(getApplication(), uri, motDePasse)
                }
            }.onFailure {
                Diag.record("restauration", it)
                _message.value = "Restauration impossible : ${it.message ?: "mot de passe incorrect"}"
            }.isSuccess
            apres(ok)
        }
    }

    fun aujourdhui() = Dates.aujourdhui()
}
