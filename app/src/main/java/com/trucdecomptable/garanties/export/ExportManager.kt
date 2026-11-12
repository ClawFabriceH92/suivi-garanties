package com.trucdecomptable.garanties.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.trucdecomptable.garanties.data.Appareil
import com.trucdecomptable.garanties.data.CoffreDb
import com.trucdecomptable.garanties.data.Document
import com.trucdecomptable.garanties.data.Evenement
import com.trucdecomptable.garanties.data.Garantie
import com.trucdecomptable.garanties.metier.Echeances
import com.trucdecomptable.garanties.util.Dates
import com.trucdecomptable.garanties.util.Diag
import com.trucdecomptable.garanties.util.Fichiers
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portabilité des données : l'utilisateur doit pouvoir tout récupérer et
 * repartir sans l'application. Trois sorties lisibles (ZIP arborescent, CSV,
 * PDF) et une sauvegarde chiffrée restaurable sur une installation propre.
 */
object ExportManager {

    const val SEPARATEUR = ';'

    // ------------------------------------------------------------------ CSV

    private val COLONNES = listOf(
        "Nom", "Categorie", "Marque", "Modele", "NumeroSerie", "DateAchat", "PrixAchat",
        "Vendeur", "NumeroFacture", "DebutGarantie", "FinGarantie", "DureeGarantieMois",
        "Emplacement", "Proprietaire", "Statut", "Archive", "Documents", "Garanties", "Notes",
    )

    fun csv(appareils: List<Appareil>, documents: List<Document>, garanties: List<Garantie>): String {
        val sb = StringBuilder()
        // BOM : sans lui, Excel en français affiche « Ã© » à la place des accents.
        sb.append('\uFEFF')
        sb.append(COLONNES.joinToString(SEPARATEUR.toString()))
        sb.append("\r\n")
        for (appareil in appareils.sortedBy { it.nom.lowercase() }) {
            val docs = documents.count { it.appareilId == appareil.id }
            val gars = garanties.count { it.appareilId == appareil.id }
            val valeurs = listOf(
                appareil.nom,
                appareil.categorie,
                appareil.marque,
                appareil.modele,
                appareil.numeroSerie,
                appareil.dateAchat,
                appareil.prixAchat?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "",
                appareil.vendeur,
                appareil.numeroFacture,
                appareil.debutGarantie,
                appareil.finGarantie,
                appareil.dureeGarantieMois?.toString() ?: "",
                appareil.emplacement,
                appareil.proprietaire,
                appareil.statut,
                if (appareil.archive) "oui" else "non",
                docs.toString(),
                gars.toString(),
                appareil.notes,
            )
            sb.append(valeurs.joinToString(SEPARATEUR.toString()) { echapper(it) })
            sb.append("\r\n")
        }
        return sb.toString()
    }

    /** Une ligne par couverture : l'export d'un appareil ne doit pas perdre ses garanties secondaires. */
    fun csvGaranties(appareils: List<Appareil>, garanties: List<Garantie>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(
            listOf(
                "Appareil", "TypeCouverture", "Organisme", "Reference", "Debut", "Fin",
                "Contact", "Conditions", "Notes",
            ).joinToString(SEPARATEUR.toString()),
        )
        sb.append("\r\n")
        val noms = appareils.associate { it.id to it.nom }
        garanties.sortedWith(compareBy({ noms[it.appareilId] ?: "" }, { it.fin })).forEach { g ->
            sb.append(
                listOf(
                    noms[g.appareilId] ?: "",
                    g.type, g.organisme, g.reference, g.debut, g.fin,
                    g.contact, g.conditions, g.notes,
                ).joinToString(SEPARATEUR.toString()) { echapper(it) },
            )
            sb.append("\r\n")
        }
        return sb.toString()
    }

    /** Une ligne par document : l'inventaire des justificatifs, avec son chemin dans le ZIP. */
    fun csvDocuments(appareils: List<Appareil>, documents: List<Document>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(
            listOf("Appareil", "TypeDocument", "Nom", "Fichier", "AjouteLe").joinToString(SEPARATEUR.toString()),
        )
        sb.append("\r\n")
        val noms = appareils.associate { it.id to it.nom }
        documents.forEach { d ->
            sb.append(
                listOf(
                    noms[d.appareilId] ?: "", d.type, d.nom, d.chemin,
                    Dates.horodatageCourt(d.ajouteLe),
                ).joinToString(SEPARATEUR.toString()) { echapper(it) },
            )
            sb.append("\r\n")
        }
        return sb.toString()
    }

    fun echapper(valeur: String): String {
        val propre = valeur.replace(Regex("[\\r\\n]+"), " ")
        return if (propre.contains(SEPARATEUR) || propre.contains('"')) {
            "\"" + propre.replace("\"", "\"\"") + "\""
        } else {
            propre
        }
    }

    // ------------------------------------------------------------------ JSON

    fun json(
        appareils: List<Appareil>,
        documents: List<Document>,
        garanties: List<Garantie>,
    ): String {
        val racine = JSONObject()
        racine.put("application", "Mes Garanties")
        racine.put("exporte_le", Dates.horodatage(System.currentTimeMillis()))
        racine.put("appareils", JSONArray().apply {
            appareils.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id); put("nom", a.nom); put("categorie", a.categorie)
                    put("marque", a.marque); put("modele", a.modele)
                    put("numero_serie", a.numeroSerie); put("date_achat", a.dateAchat)
                    a.prixAchat?.let { put("prix_achat", it) }
                    put("vendeur", a.vendeur); put("numero_facture", a.numeroFacture)
                    put("debut_garantie", a.debutGarantie); put("fin_garantie", a.finGarantie)
                    a.dureeGarantieMois?.let { put("duree_garantie_mois", it) }
                    put("emplacement", a.emplacement); put("proprietaire", a.proprietaire)
                    put("statut", a.statut); put("archive", a.archive); put("notes", a.notes)
                })
            }
        })
        racine.put("garanties", JSONArray().apply {
            garanties.forEach { g ->
                put(JSONObject().apply {
                    put("appareil_id", g.appareilId); put("type", g.type)
                    put("organisme", g.organisme); put("reference", g.reference)
                    put("debut", g.debut); put("fin", g.fin); put("contact", g.contact)
                    put("conditions", g.conditions); put("notes", g.notes)
                })
            }
        })
        racine.put("documents", JSONArray().apply {
            documents.forEach { d ->
                put(JSONObject().apply {
                    put("appareil_id", d.appareilId); put("type", d.type)
                    put("nom", d.nom); put("fichier", d.chemin)
                })
            }
        })
        return racine.toString(2)
    }

    // ------------------------------------------------------------------- ZIP

    /**
     * Arborescence lisible hors application :
     * Categorie / Marque / Appareil / Documents / fichier, plus donnees.csv et
     * donnees.json à la racine.
     */
    fun exporterZip(
        context: Context,
        appareils: List<Appareil>,
        documents: List<Document>,
        garanties: List<Garantie>,
        journal: List<Evenement> = emptyList(),
    ): File {
        val sortie = File(context.cacheDir, "mes-garanties-${Dates.dateFichier()}.zip")
        ZipOutputStream(sortie.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("donnees.csv"))
            zip.write(csv(appareils, documents, garanties).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("donnees.json"))
            zip.write(json(appareils, documents, garanties).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            if (journal.isNotEmpty()) {
                val lignes = buildString {
                    append('\uFEFF')
                    append("Date;Appareil;Evenement\r\n")
                    val noms = appareils.associate { it.id to it.nom }
                    journal.sortedBy { it.date }.forEach {
                        append(Dates.horodatage(it.date)).append(SEPARATEUR)
                        append(echapper(noms[it.appareilId] ?: "?").replace(';', ',')).append(SEPARATEUR)
                        append(echapper(it.texte)).append("\r\n")
                    }
                }
                zip.putNextEntry(ZipEntry("journal.csv"))
                zip.write(lignes.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("garanties.csv"))
            zip.write(csvGaranties(appareils, garanties).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("documents.csv"))
            zip.write(csvDocuments(appareils, documents).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val dejaEcrits = mutableSetOf<String>()
            for (document in documents) {
                val appareil = appareils.firstOrNull { it.id == document.appareilId } ?: continue
                val piece = Fichiers.clair(context, document.chemin)
                if (!piece.exists()) continue
                // L'extension d'origine est conservée : sans elle, un PDF ou une
                // photo extraits du ZIP ne s'ouvrent pas au double-clic.
                val extension = document.chemin.substringAfterLast('.', "")
                val nomInterne = "${appareil.categorie}/${appareil.marque.ifBlank { "Sans marque" }}/" +
                    "${Fichiers.nomSansAccent(appareil.nom)}/Documents/" +
                    "${document.type.replace('/', '-')} - ${Fichiers.nomSansAccent(document.nom)}" +
                    if (extension.isBlank()) "" else ".$extension"
                val cheminZip = unique(nomInterne, dejaEcrits)
                zip.putNextEntry(ZipEntry(cheminZip))
                piece.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        Diag.record("export", "ZIP ${sortie.name} (${Fichiers.tailleLisible(sortie.length())})")
        return sortie
    }

    private fun unique(chemin: String, vus: MutableSet<String>): String {
        if (vus.add(chemin)) return chemin
        val base = chemin.substringBeforeLast('.', chemin)
        val extension = chemin.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidat = if (extension.isBlank()) "$base ($index)" else "$base ($index).$extension"
            if (vus.add(candidat)) return candidat
            index++
        }
    }

    fun exporterCsv(
        context: Context,
        appareils: List<Appareil>,
        documents: List<Document>,
        garanties: List<Garantie>,
    ): File {
        val sortie = File(context.cacheDir, "mes-garanties-${Dates.dateFichier()}.csv")
        sortie.writeText(csv(appareils, documents, garanties), Charsets.UTF_8)
        return sortie
    }

    // ------------------------------------------------------------------- PDF

    /** Récapitulatif imprimable, pour l'assurance ou un dossier de sinistre. */
    fun exporterPdf(
        context: Context,
        appareils: List<Appareil>,
        documents: List<Document>,
        garanties: List<Garantie>,
        aujourdhui: java.time.LocalDate = Dates.aujourdhui(),
    ): File {
        val document = PdfDocument()
        val marge = 32f
        val largeur = 595 // A4 à 72 dpi
        val hauteur = 842
        val titre = Paint().apply { textSize = 16f; isFakeBoldText = true; color = Color.BLACK }
        val gras = Paint().apply { textSize = 11f; isFakeBoldText = true; color = Color.BLACK }
        val normal = Paint().apply { textSize = 10f; color = Color.DKGRAY }
        var pageNumero = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(largeur, hauteur, pageNumero).create())
        var canvas = page.canvas
        var y = marge + 12f

        fun nouvellePage() {
            document.finishPage(page)
            pageNumero++
            page = document.startPage(PdfDocument.PageInfo.Builder(largeur, hauteur, pageNumero).create())
            canvas = page.canvas
            y = marge + 12f
        }

        fun ecrire(texte: String, paint: Paint) {
            if (y > hauteur - marge - 14f) nouvellePage()
            canvas.drawText(texte, marge, y, paint)
            y += paint.textSize + 5f
        }

        ecrire("Inventaire des biens et garanties", titre)
        ecrire("Édité le ${Dates.afficher(Dates.iso(aujourdhui))} · ${appareils.size} appareil(s)", normal)
        y += 8f

        for (appareil in appareils.sortedWith(compareBy({ it.archive }, { it.categorie }, { it.nom.lowercase() }))) {
            val fin = Echeances.finLaPlusLointaine(appareil, garanties)
            val etat = Echeances.etatDe(fin, aujourdhui)
            ecrire(appareil.nom, gras)
            ecrire(
                listOfNotNull(
                    appareil.categorie,
                    appareil.marque.takeIf { it.isNotBlank() },
                    appareil.modele.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                normal,
            )
            if (appareil.numeroSerie.isNotBlank()) ecrire("Numéro de série : ${appareil.numeroSerie}", normal)
            if (appareil.dateAchat.isNotBlank()) {
                ecrire("Acheté le ${Dates.afficher(appareil.dateAchat)} chez ${appareil.vendeur}".trim(), normal)
            }
            appareil.prixAchat?.let {
                ecrire("Prix : ${String.format(java.util.Locale.FRANCE, "%.2f €", it)}", normal)
            }
            if (fin.isNotBlank()) {
                ecrire(
                    "Garantie jusqu'au ${Dates.afficher(fin)} (${libelleEtat(etat)})",
                    normal,
                )
            }
            val docs = documents.count { it.appareilId == appareil.id }
            ecrire("$docs document(s) conservé(s)" + if (appareil.archive) " · archivé" else "", normal)
            y += 8f
        }
        document.finishPage(page)

        val sortie = File(context.cacheDir, "inventaire-garanties-${Dates.dateFichier()}.pdf")
        sortie.outputStream().use { document.writeTo(it) }
        document.close()
        return sortie
    }

    private fun libelleEtat(etat: com.trucdecomptable.garanties.metier.EtatGarantie): String = when (etat) {
        com.trucdecomptable.garanties.metier.EtatGarantie.ACTIVE -> "active"
        com.trucdecomptable.garanties.metier.EtatGarantie.PROCHE -> "expire bientôt"
        com.trucdecomptable.garanties.metier.EtatGarantie.TRES_PROCHE -> "expire sous 30 jours"
        com.trucdecomptable.garanties.metier.EtatGarantie.EXPIREE -> "expirée"
        com.trucdecomptable.garanties.metier.EtatGarantie.INCONNUE -> "date inconnue"
    }

    // -------------------------------------------------------- sauvegarde

    /** Sauvegarde complète et chiffrée : base + justificatifs + photos. */
    fun sauvegarder(context: Context, db: CoffreDb, motDePasse: String): File {
        val travail = File(context.cacheDir, "sauvegarde-travail").apply {
            deleteRecursively()
            mkdirs()
        }
        val base = context.getDatabasePath("garanties.db")
        // Sans checkpoint, le journal WAL garde des écritures absentes du fichier
        // .db : la sauvegarde serait en retard d'une transaction.
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
        val brut = File(travail, "sauvegarde.zip")
        ZipOutputStream(brut.outputStream().buffered()).use { zip ->
            listOf(base, File("${base.path}-wal"), File("${base.path}-shm"))
                .filter { it.exists() }
                .forEach { fichier ->
                    zip.putNextEntry(ZipEntry("base/${fichier.name}"))
                    fichier.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            // La phrase secrète de la base voyage dans l'archive : sans elle, la
            // base restaurée ne s'ouvrirait que sur le téléphone d'origine (la clé
            // du Keystore ne se copie pas). L'archive entière est chiffrée par le
            // mot de passe choisi par l'utilisateur.
            zip.putNextEntry(ZipEntry("base/cle.txt"))
            zip.write(java.util.Base64.getEncoder().encode(
                com.trucdecomptable.garanties.securite.CoffreSecret.phraseBaseBrute(context),
            ))
            zip.closeEntry()

            val pieces = Fichiers.dossier(context)
            pieces.listFiles()?.forEach { piece ->
                // Écrit en clair DANS l'archive chiffrée : les pièces doivent être
                // ré-encryptées avec la clé du téléphone d'arrivée, pas du départ.
                zip.putNextEntry(ZipEntry("pieces/${piece.name}"))
                Fichiers.clair(context, piece.name).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("manifeste.txt"))
            zip.write(
                "Mes Garanties · sauvegarde du ${Dates.horodatage(System.currentTimeMillis())}\n".toByteArray(),
            )
            zip.closeEntry()
        }
        val sortie = File(context.cacheDir, "mes-garanties-sauvegarde-${Dates.dateFichier()}.gcf")
        sortie.outputStream().use { Chiffrement.enveloppe(it, motDePasse, brut.readBytes()) }
        brut.delete()
        travail.deleteRecursively()
        Diag.record("sauvegarde", "${sortie.name} (${Fichiers.tailleLisible(sortie.length())})")
        return sortie
    }

    /**
     * Restaure une sauvegarde. La base est fermée AVANT d'être remplacée :
     * écraser un fichier SQLite ouvert le corrompt. L'appelant doit relancer
     * l'application ensuite.
     */
    fun restaurer(context: Context, uri: Uri, motDePasse: String) {
        val contenu = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("fichier illisible")
        val clair = Chiffrement.deverrouiller(contenu, motDePasse)
        val travail = File(context.cacheDir, "restauration").apply {
            deleteRecursively()
            mkdirs()
        }
        ZipInputStream(clair.inputStream()).use { zip ->
            var entree = zip.nextEntry
            while (entree != null) {
                val nom = entree.name
                if (nom.contains("..")) throw IllegalStateException("archive suspecte")
                if (entree.isDirectory) {
                    entree = zip.nextEntry
                    continue
                }
                val relatif = when {
                    nom.startsWith("base/") -> "base/" + nom.removePrefix("base/")
                    nom.startsWith("pieces/") -> "pieces/" + nom.removePrefix("pieces/")
                    else -> nom
                }
                val cible = File(travail, relatif)
                cible.parentFile?.mkdirs()
                cible.outputStream().use { zip.copyTo(it) }
                entree = zip.nextEntry
            }
        }
        val nouvelleBase = File(travail, "base/garanties.db")
        if (!nouvelleBase.exists()) throw IllegalStateException("sauvegarde sans base de données")

        File(travail, "base/cle.txt").takeIf { it.exists() }?.let { fichierCle ->
            val phrase = java.util.Base64.getDecoder().decode(fichierCle.readText().trim())
            com.trucdecomptable.garanties.securite.CoffreSecret.remplacerPhraseBase(context, phrase)
        }

        CoffreDb.fermer()
        val base = context.getDatabasePath("garanties.db")
        base.parentFile?.mkdirs()
        File("${base.path}-wal").delete()
        File("${base.path}-shm").delete()
        base.delete()
        nouvelleBase.copyTo(base, overwrite = true)

        val pieces = Fichiers.dossier(context)
        pieces.listFiles()?.forEach { it.delete() }
        Fichiers.purgerCache(context)
        File(travail, "pieces").listFiles()?.forEach { origine ->
            // Ré-encryption avec la clé de CE téléphone.
            origine.inputStream().use { entree ->
                Fichiers.enregistrerChiffre(context, origine.name, entree)
            }
        }
        travail.deleteRecursively()
        Diag.record("restauration", "base et justificatifs remplacés")
    }

    /**
     * Copie l'export vers l'emplacement choisi par l'utilisateur (Documents,
     * Téléchargements…). C'est le chemin fiable : la feuille de partage ne sert
     * que si une application accepte le fichier.
     */
    fun ecrireVers(context: Context, source: File, destination: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(destination, "wt")?.use { sortie ->
            source.inputStream().use { it.copyTo(sortie) }
        } ?: return false
        Diag.record("export", "écrit vers $destination (${Fichiers.tailleLisible(source.length())})")
        true
    }.getOrElse {
        Diag.record("export", "écriture impossible : ${it.message}")
        false
    }

    // --------------------------------------------------------------- partage

    fun partageUri(uri: Uri, mime: String, sujet: String = ""): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (sujet.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, sujet)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
