package com.trucdecomptable.garanties.metier

import com.trucdecomptable.garanties.data.Categories
import com.trucdecomptable.garanties.util.Dates
import java.time.LocalDate

/** Un article détecté sur la facture, pour proposer une répartition entre appareils. */
data class ProduitExtrait(
    val designation: String = "",
    val quantite: Int = 1,
    val prixTTC: Double? = null,
)

/**
 * Ce que la reconnaissance de texte a réussi à tirer d'une facture. Aucun champ
 * n'est enregistré tel quel : l'écran de validation les affiche tous, et
 * l'utilisateur corrige, vide ou complète avant l'enregistrement.
 */
data class FactureExtraite(
    val vendeur: String = "",
    val adresseVendeur: String = "",
    val dateAchat: String = "",
    val numeroFacture: String = "",
    val designation: String = "",
    val categorie: String = "",
    val marque: String = "",
    val modele: String = "",
    val numeroSerie: String = "",
    val quantite: Int = 1,
    val prixHT: Double? = null,
    val tva: Double? = null,
    val tauxTva: Double? = null,
    val prixTTC: Double? = null,
    val moyenPaiement: String = "",
    val dureeGarantieMois: Int? = null,
    val finGarantie: String = "",
    val produits: List<ProduitExtrait> = emptyList(),
    val texteBrut: String = "",
) {
    /** Délai de garantie par défaut proposé à l'utilisateur (24 mois sinon). */
    val dureeProposee: Int get() = dureeGarantieMois ?: 24

    val finCalculee: String
        get() {
            val debut = Dates.parse(dateAchat) ?: return finGarantie
            return Dates.iso(Dates.ajouterMois(debut, dureeProposee))
        }
}

/**
 * Extraction déterministe, sans réseau : le texte de l'OCR est passé au crible
 * de règles françaises (montants à virgule, dates JJ/MM/AAAA, mentions
 * « total TTC », « garantie 2 ans »…). Un LLM n'est pas utilisé ici : sur des
 * tickets, il invente des lignes et saute des montants. Chaque valeur douteuse
 * reste vide plutôt que d'être devinée.
 */
object ExtractionFacture {

    private val SUFFIXES_SOCIETE = listOf("sarl", "sas", "sasu", "sa ", "eurl", "sci", "snc", "gmbh", "inc")
    private val MOTS_TECHNIQUES = listOf(
        "facture", "ticket", "reçu", "recu", "tva", "siret", "siren", "rcs", "tél", "tel ",
        "client", "date", "n°", "numero", "numéro", "adresse", "merci", "caisse", "caissier",
        "total", "montant", "règlement", "reglement", "paiement", "espèces", "especes",
        "carte", "bancaire", "vendeur", "magasin", "horaires", "www.", "http", "monnaie",
        "rendu", "avoir", "ticket caisse", "code", "ean", "référence", "reference",
    )
    private val MOTS_MONTANT = listOf(
        "total", "montant", "à payer", "a payer", "net", "ttc", "sous-total", "sous total",
        "tva", "remise", "acompte", "escompte", "port", "livraison",
    )

    private val MARQUES = listOf(
        "Samsung", "Apple", "Bosch", "Siemens", "Miele", "Whirlpool", "Electrolux", "AEG",
        "LG", "Sony", "Philips", "Panasonic", "Dyson", "Rowenta", "Tefal", "Moulinex", "Seb",
        "HP", "Dell", "Lenovo", "Asus", "Acer", "MSI", "Microsoft", "Xiaomi", "Huawei", "Honor",
        "Google", "OnePlus", "Oppo", "Nokia", "Motorola", "Canon", "Nikon", "Fujifilm", "Olympus",
        "Epson", "Brother", "Makita", "DeWalt", "Black+Decker", "Kärcher", "Karcher", "Gardena",
        "IKEA", "But", "Conforama", "Bose", "JBL", "Sonos", "Garmin", "Fitbit", "GoPro", "DJI",
        "Boulanger", "Darty", "Fnac", "Amazon", "Leroy Merlin", "Castorama", "Decathlon",
        "Tesla", "Dyson", "Beko", "Brandt", "Candy", "Indesit", "Hotpoint", "Smeg", "De Dietrich",
    )

    private val CATEGORIES_MOTS = mapOf(
        "Téléphone et tablette" to listOf(
            "smartphone", "telephone", "téléphone", "iphone", "ipad", "tablette", "galaxy", "pixel",
            "mobile", "redmi", "poco",
        ),
        "Informatique" to listOf(
            "ordinateur", "pc ", "laptop", "portable", "clavier", "souris", "souris", "ssd",
            "disque dur", "imprimante", "scanner", "écran pc", "moniteur", "macbook", "chromebook",
            "routeur", "nas", "webcam", "casque micro",
        ),
        "Électroménager" to listOf(
            "lave-linge", "lave linge", "lave-vaisselle", "lave vaisselle", "réfrigérateur",
            "refrigerateur", "frigo", "congélateur", "four", "micro-ondes", "micro ondes",
            "aspirateur", "cafetière", "cafetiere", "sèche-linge", "seche linge", "hotte",
            "plaque", "cuisinière", "cuisiniere", "bouilloire", "robot", "friteuse",
        ),
        "Télévision et audio" to listOf(
            "téléviseur", "televiseur", "tv ", "barre de son", "enceinte", "casque", "home cinéma",
            "home cinema", "amplificateur", "platine",
        ),
        "Photo et vidéo" to listOf(
            "appareil photo", "reflex", "objectif", "caméra", "camera", "drone", "gopro", "camescope",
        ),
        "Mobilier" to listOf(
            "chaise", "table", "canapé", "canape", "bureau", "armoire", "lit ", "matelas",
            "étagère", "etagere", "commode", "fauteuil",
        ),
        "Outillage" to listOf(
            "perceuse", "visseuse", "scie", "tournevis", "marteau", "ponceuse", "compresseur",
            "niveau laser", "clé à choc", "cle a choc", "meuleuse",
        ),
        "Véhicule et mobilité" to listOf(
            "vélo", "velo", "trottinette", "pneu", "voiture", "scooter", "casque moto", "remorque",
        ),
        "Matériel professionnel" to listOf(
            "photocopieur", "destructeur", "terminal de paiement", "caisse enregistreuse",
            "vidéoprojecteur", "videoprojecteur",
        ),
    )

    private val MOTS_PAIEMENT = listOf(
        "carte bancaire" to "Carte bancaire",
        "cb " to "Carte bancaire",
        "espèces" to "Espèces",
        "especes" to "Espèces",
        "virement" to "Virement",
        "paypal" to "Paypal",
        "chèque" to "Chèque",
        "cheque" to "Chèque",
        "apple pay" to "Apple Pay",
        "google pay" to "Google Pay",
        "prélèvement" to "Prélèvement",
    )

    fun analyser(texte: String, aujourdhui: LocalDate = Dates.aujourdhui()): FactureExtraite {
        val brut = texte.replace('\u00A0', ' ').replace("\r", "")
        val lignes = brut.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lignes.isEmpty()) return FactureExtraite(texteBrut = brut)

        val minuscules = lignes.map { it.lowercase() }

        val vendeur = detecterVendeur(lignes)
        val adresse = detecterAdresse(lignes, vendeur)
        val dateAchat = detecterDate(lignes, minuscules)
        val numero = detecterNumeroFacture(lignes)
        val marque = detecterMarque(brut)
        val designation = detecterDesignation(lignes, minuscules, vendeur, marque)
        val modele = detecterModele(lignes, marque, designation)
        val serie = detecterNumeroSerie(lignes)
        val quantite = detecterQuantite(minuscules)
        val taux = detecterTauxTva(lignes, minuscules)
        val tva = detecterTva(lignes, minuscules)
        val ttc = detecterTotalTtc(lignes, minuscules)
        val ht = detecterTotalHt(lignes, minuscules) ?: if (ttc != null && taux != null) {
            arrondi(ttc / (1 + taux / 100.0))
        } else {
            null
        }
        val paiement = detecterPaiement(minuscules)
        val duree = detecterDureeGarantie(brut)
        val fin = if (dateAchat.isNotBlank() && duree != null) {
            Dates.parse(dateAchat)?.let { Dates.iso(Dates.ajouterMois(it, duree)) }.orEmpty()
        } else {
            ""
        }
        val produits = detecterProduits(lignes, minuscules)

        return FactureExtraite(
            vendeur = vendeur,
            adresseVendeur = adresse,
            dateAchat = dateAchat,
            numeroFacture = numero,
            designation = designation,
            categorie = detecterCategorie(listOf(designation).plus(lignes).joinToString(" ").lowercase()),
            marque = marque,
            modele = modele,
            numeroSerie = serie,
            quantite = quantite,
            prixHT = ht,
            tva = tva,
            tauxTva = taux,
            prixTTC = ttc,
            moyenPaiement = paiement,
            dureeGarantieMois = duree,
            finGarantie = fin,
            produits = produits,
            texteBrut = brut,
        )
    }

    // ---------------------------------------------------------------- montants

    /** « 1 299,00 », « 1299.00 », « 49,90 € », « 12,5 » */
    fun montants(ligne: String): List<Double> =
        Regex("""(?:^|[\s(])(\d{1,3}(?:[ .]\d{3})+|\d+)[,.](\d{1,2})(?![\d])""")
            .findAll(ligne)
            .mapNotNull { m ->
                val entier = m.groupValues[1].replace(" ", "").replace(".", "")
                val decimales = m.groupValues[2]
                entier.toDoubleOrNull()?.let { e ->
                    if (decimales.length == 1) e + decimales.toDouble() / 10 else e + decimales.toDouble() / 100
                }
            }
            .toList()

    private fun dernierMontant(ligne: String): Double? = montants(ligne).lastOrNull()

    private fun arrondi(v: Double): Double = Math.round(v * 100.0) / 100.0

    private fun ligneChiffree(minuscule: String): Boolean =
        MOTS_MONTANT.any { minuscule.contains(it) }

    private fun detecterTotalTtc(lignes: List<String>, minuscules: List<String>): Double? {
        val prioritaires = listOf("net à payer", "net a payer", "total ttc", "montant total", "à payer", "a payer", "total")
        for (mot in prioritaires) {
            val index = minuscules.indexOfFirst { it.contains(mot) }
            if (index >= 0) {
                val valeur = dernierMontant(lignes[index])
                if (valeur != null && valeur > 0) return valeur
            }
        }
        // Repli : le plus gros montant du document, un ticket sans mention de total
        // (typiquement une photo coupée en bas).
        return lignes.flatMap { montants(it) }.filter { it > 0 }.maxOrNull()
    }

    private fun detecterTotalHt(lignes: List<String>, minuscules: List<String>): Double? {
        val index = minuscules.indexOfFirst {
            it.contains("total ht") || it.contains("sous-total ht") || it.contains("hors taxe")
        }
        if (index < 0) return null
        return dernierMontant(lignes[index])?.takeIf { it > 0 }
    }

    private fun detecterTauxTva(lignes: List<String>, minuscules: List<String>): Double? {
        val index = minuscules.indexOfFirst { it.contains("tva") || it.contains("taux") }
        val ligne = lignes.getOrNull(index) ?: return null
        Regex("""(\d{1,2}(?:[.,]\d{1,2})?)\s*%""").find(ligne)?.let { m ->
            return m.groupValues[1].replace(',', '.').toDoubleOrNull()
        }
        return null
    }

    private fun detecterTva(lignes: List<String>, minuscules: List<String>): Double? {
        val index = minuscules.indexOfFirst { it.contains("tva") }
        if (index < 0) return null
        val candidats = montants(lignes[index]).filter { it > 0 }
        // Sur « TVA 20% 12,34 », le taux est capté comme montant : on écarte les
        // valeurs qui suivent un signe %.
        val sansTaux = ligneSansTaux(lignes[index])
        return sansTaux.lastOrNull() ?: candidats.lastOrNull()
    }

    private fun ligneSansTaux(ligne: String): List<Double> {
        val nettoyee = ligne.replace(Regex("""\d{1,2}(?:[.,]\d+)?\s*%"""), " ")
        return montants(nettoyee)
    }

    // ------------------------------------------------------------------ dates

    private fun detecterDate(lignes: List<String>, minuscules: List<String>): String {
        val exclues = listOf("garantie", "échéance", "echeance", "expire", "validité", "validite", "fin ")
        val candidats = mutableListOf<Pair<String, Boolean>>() // date ISO, ligne contient « date »
        for ((index, ligne) in lignes.withIndex()) {
            val minus = minuscules[index]
            if (exclues.any { minus.contains(it) }) continue
            val d = Dates.lireSouple(ligne) ?: continue
            if (d.year < 2000 || d.year > 2100) continue
            candidats += Dates.iso(d) to minus.contains("date")
        }
        return candidats.firstOrNull { it.second }?.first ?: candidats.firstOrNull()?.first ?: ""
    }

    // -------------------------------------------------------------- vendeur

    private fun detecterVendeur(lignes: List<String>): String {
        val entete = lignes.take(8)
        val societe = entete.firstOrNull { ligne ->
            val minus = ligne.lowercase()
            SUFFIXES_SOCIETE.any { Regex("""\b$it""").containsMatchIn(minus) }
        }
        if (societe != null) return nettoyerNom(societe)
        val marque = entete.firstOrNull { ligne -> MARQUES.any { Regex("""(?i)\b${Regex.escape(it)}\b""").containsMatchIn(ligne) } }
        if (marque != null) return nettoyerNom(marque)
        return entete.firstOrNull { ligne ->
            val minus = ligne.lowercase()
            val lettres = ligne.count { it.isLetter() }
            ligne.length in 3..40 && lettres >= 3 && !ligneChiffree(minus) &&
                MOTS_TECHNIQUES.none { minus.contains(it) } &&
                montants(ligne).isEmpty()
        }?.let { nettoyerNom(it) } ?: ""
    }

    private fun nettoyerNom(ligne: String): String =
        ligne.replace(Regex("""\s+"""), " ").trim().trim('-', '*', ':').take(60)

    private fun detecterAdresse(lignes: List<String>, vendeur: String): String {
        val indexVendeur = lignes.indexOfFirst { nettoyerNom(it) == vendeur }
        val debut = if (indexVendeur >= 0) indexVendeur + 1 else 0
        val zone = lignes.drop(debut).take(4)
        val morceaux = zone.filter { ligne ->
            val minus = ligne.lowercase()
            (Regex("""\d{1,4}\s+(rue|avenue|av\.|boulevard|bd|place|chemin|route|impasse|allée|allee)""").containsMatchIn(minus) ||
                Regex("""\b\d{5}\s+[a-zà-ÿ]""").containsMatchIn(minus))
        }
        return morceaux.joinToString(" · ").take(120)
    }

    // ------------------------------------------------------------- produit

    private fun detecterMarque(texte: String): String =
        MARQUES.firstOrNull { marque ->
            Regex("""(?i)(?<![\p{L}])${Regex.escape(marque)}(?![\p{L}])""").containsMatchIn(texte)
        }.orEmpty()

    private fun detecterDesignation(
        lignes: List<String>,
        minuscules: List<String>,
        vendeur: String,
        marque: String,
    ): String {
        val candidats = lignes.filterIndexed { index, ligne ->
            val minus = minuscules[index]
            val lettres = ligne.count { it.isLetter() }
            ligne.length in 6..80 &&
                lettres >= 4 &&
                nettoyerNom(ligne) != vendeur &&
                !ligneChiffree(minus) &&
                MOTS_TECHNIQUES.none { minus.contains(it) } &&
                !minus.contains("garantie") &&
                !Regex("""^\d+$""").matches(ligne)
        }
        if (candidats.isEmpty()) return ""
        val avecMarque = candidats.firstOrNull {
            marque.isNotBlank() && it.contains(marque, ignoreCase = true)
        }
        val choisi = nettoyerNom(
            (avecMarque ?: candidats.maxByOrNull { it.length }!!)
                .replace(Regex("""\d+[.,]\d{2}\s*(?:€|EUR)?.*$"""), ""),
        )
        return choisi
    }

    private fun detecterModele(lignes: List<String>, marque: String, designation: String): String {
        val indexModele = lignes.indexOfFirst {
            val m = it.lowercase()
            m.contains("modèle") || m.contains("modele") || m.contains("réf.") || m.contains("ref.")
        }
        if (indexModele >= 0) {
            val valeur = lignes[indexModele].substringAfter(':').substringAfter(".").trim()
            if (valeur.length in 2..40) return nettoyerNom(valeur)
        }
        if (marque.isNotBlank() && designation.contains(marque, ignoreCase = true)) {
            val reste = designation.substringAfter(marque, "").trim()
            if (reste.length in 2..40) return reste
        }
        // Référence produit : suite de majuscules et chiffres d'au moins 5 signes.
        return Regex("""\b[A-Z0-9][A-Z0-9\-/]{4,}\b""").find(designation)?.value.orEmpty()
    }

    private fun detecterNumeroSerie(lignes: List<String>): String {
        val motifs = listOf(
            Regex("""(?i)(?:s/?n|n°\s*(?:de\s*)?s[ée]rie|num[ée]ro\s*(?:de\s*)?s[ée]rie|serial(?:\s*number)?|imei)\s*[:#]?\s*([A-Z0-9][A-Z0-9\-\s]{5,24})"""),
            Regex("""(?i)\bSN\s*[:#]?\s*([A-Z0-9\-]{6,24})"""),
        )
        for (ligne in lignes) {
            for (motif in motifs) {
                val valeur = motif.find(ligne)?.groupValues?.getOrNull(1)?.trim()
                if (valeur != null && valeur.count { it.isDigit() } >= 3) {
                    return valeur.replace(" ", "").take(24)
                }
            }
        }
        return ""
    }

    private fun detecterNumeroFacture(lignes: List<String>): String {
        val motifs = listOf(
            Regex("""(?i)(?:facture|ticket|re[çc]u|avoir|commande|order)\s*(?:n[°o]|num[ée]ro)?\s*[:#\-]?\s*([A-Z0-9][A-Z0-9\-_/]{2,24})"""),
            Regex("""(?i)n[°o]\s*(?:de\s*)?(?:facture|ticket|commande)?\s*[:#\-]?\s*([A-Z0-9][A-Z0-9\-_/]{2,24})"""),
        )
        for (ligne in lignes) {
            for (motif in motifs) {
                val valeur = motif.find(ligne)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('-', '.')
                // Un numéro doit contenir au moins un chiffre : « FACTURE TTC » n'en est pas un.
                if (valeur != null && valeur.length >= 3 && valeur.any { it.isDigit() }) return valeur
            }
        }
        return ""
    }

    private fun detecterQuantite(minuscules: List<String>): Int {
        for (ligne in minuscules) {
            Regex("""(?:qt[ée]?|quantit[ée]|qty)\s*[:x]?\s*(\d{1,3})""").find(ligne)?.let {
                it.groupValues[1].toIntOrNull()?.let { q -> if (q in 1..999) return q }
            }
            Regex("""^\s*(\d{1,2})\s*[x×]\s""").find(ligne)?.let {
                it.groupValues[1].toIntOrNull()?.let { q -> if (q in 1..99) return q }
            }
        }
        return 1
    }

    private fun detecterPaiement(minuscules: List<String>): String {
        val texte = minuscules.joinToString(" ")
        return MOTS_PAIEMENT.firstOrNull { texte.contains(it.first) }?.second.orEmpty()
    }

    fun detecterCategorie(texte: String): String {
        val minus = texte.lowercase()
        var meilleure = Categories.DEFAUT.last() // « Autre »
        var meilleurScore = 0
        for ((categorie, mots) in CATEGORIES_MOTS) {
            val score = mots.count { minus.contains(it) }
            if (score > meilleurScore) {
                meilleurScore = score
                meilleure = categorie
            }
        }
        return meilleure
    }

    /** « garantie 2 ans », « garantie constructeur 24 mois », « extension 3 ans ». */
    fun detecterDureeGarantie(texte: String): Int? {
        val motif = Regex("""(?i)garantie[^\n.]{0,40}?(\d{1,2})\s*(ans?|ann[ée]es?|mois)""")
        val m = motif.find(texte) ?: return null
        val nombre = m.groupValues[1].toIntOrNull() ?: return null
        val unite = m.groupValues[2].lowercase()
        val mois = if (unite.startsWith("mois")) nombre else nombre * 12
        return mois.takeIf { it in 1..600 }
    }

    private fun detecterProduits(lignes: List<String>, minuscules: List<String>): List<ProduitExtrait> =
        lignes.mapIndexedNotNull { index, ligne ->
            val minus = minuscules[index]
            if (ligneChiffree(minus)) return@mapIndexedNotNull null
            if (minus.contains("garantie") || minus.contains("total")) return@mapIndexedNotNull null
            val prix = dernierMontant(ligne) ?: return@mapIndexedNotNull null
            if (prix <= 0) return@mapIndexedNotNull null
            val designation = nettoyerNom(ligne.replace(Regex("""\d+[.,]\d{2}.*$"""), ""))
            if (designation.count { it.isLetter() } < 4) return@mapIndexedNotNull null
            ProduitExtrait(designation = designation.take(60), prixTTC = prix)
        }.distinctBy { it.designation }.take(8)
}
