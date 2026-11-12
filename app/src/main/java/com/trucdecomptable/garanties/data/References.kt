package com.trucdecomptable.garanties.data

/**
 * Valeurs de référence de l'application. Les catégories sont modifiables par
 * l'utilisateur (stockées dans Parametres), les autres listes sont des codes
 * stables : les changer casserait les fiches déjà enregistrées.
 */
object Categories {
    val DEFAUT = listOf(
        "Informatique",
        "Téléphone et tablette",
        "Électroménager",
        "Télévision et audio",
        "Photo et vidéo",
        "Mobilier",
        "Outillage",
        "Véhicule et mobilité",
        "Matériel professionnel",
        "Autre",
    )
}

object Statuts {
    const val ACTIF = "Actif"
    const val PRETE = "Prêté"
    const val VENDU = "Vendu"
    const val DONNE = "Donné"
    const val HORS_SERVICE = "Hors service"
    const val PERDU = "Perdu ou volé"
    const val ARCHIVE = "Archivé"

    val TOUS = listOf(ACTIF, PRETE, VENDU, DONNE, HORS_SERVICE, PERDU, ARCHIVE)

    /** Un appareil qui n'est plus dans le parc mais qu'on garde pour l'historique. */
    val CLOS = setOf(VENDU, DONNE, PERDU, ARCHIVE)
}

object TypesDocument {
    const val FACTURE = "Facture"
    const val TICKET = "Ticket de caisse"
    const val EXTENSION = "Extension de garantie"
    const val ASSURANCE = "Contrat d'assurance"
    const val CERTIFICAT = "Certificat de garantie"
    const val MANUEL = "Manuel d'utilisation"
    const val PHOTO_SERIE = "Photo du numéro de série"
    const val DEVIS = "Devis de réparation"
    const val FACTURE_REPARATION = "Facture de réparation"
    const val RETOUR = "Bon de retour ou d'intervention"
    const val SAV = "Échange avec le SAV"
    const val PHOTO_APPAREIL = "Photo de l'appareil"
    const val AUTRE = "Autre document"

    val TOUS = listOf(
        FACTURE, TICKET, EXTENSION, ASSURANCE, CERTIFICAT, MANUEL,
        PHOTO_SERIE, PHOTO_APPAREIL, DEVIS, FACTURE_REPARATION, RETOUR, SAV, AUTRE,
    )
}

object TypesGarantie {
    const val LEGALE = "Garantie légale ou vendeur"
    const val CONSTRUCTEUR = "Garantie constructeur"
    const val COMMERCIALE = "Garantie commerciale"
    const val EXTENSION = "Extension de garantie"
    const val ASSURANCE = "Assurance casse, vol ou panne"

    val TOUS = listOf(LEGALE, CONSTRUCTEUR, COMMERCIALE, EXTENSION, ASSURANCE)
}
