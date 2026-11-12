# Mes Garanties

Application Android de centralisation des achats, des factures et des garanties.

« Je photographie ma facture, l'application lit les informations utiles et suit la garantie. »

## Ce que fait la version 1.0

- Fiche appareil complète (catégorie, marque, modèle, numéro de série, prix, vendeur, emplacement, propriétaire, statut, notes).
- Ajout par photo, par import PDF/JPG/PNG ou saisie manuelle.
- Extraction locale des factures : vendeur, adresse, date d'achat, numéro de facture, désignation, catégorie, marque, modèle, numéro de série, quantité, prix HT, TVA, prix TTC, moyen de paiement, durée de garantie et date de fin calculée. **Tout est affiché et corrigeable avant enregistrement** : aucune valeur incertaine n'est validée en silence.
- Une facture peut couvrir plusieurs appareils : répartition en plusieurs fiches, le même justificatif restant rattaché à chacune.
- Plusieurs garanties par appareil (légale, constructeur, commerciale, extension, assurance).
- Documents illimités par appareil (facture, ticket, extension, assurance, certificat, manuel, photo du numéro de série, devis, facture de réparation, SAV…).
- Tableau de bord : appareils, garanties actives, échéances à 90 jours, expirées, archivés, dernière sauvegarde, justificatifs manquants.
- Code couleur : rouge < 30 jours, orange < 90 jours, vert actif, gris expiré.
- Notifications configurables (1 an, 6 mois, 3 mois, 2 mois, 30 j, 7 j, jour J) plus un récapitulatif groupé ; une notification ouvre la fiche de l'appareil.
- Recherche plein texte et filtres (état, catégorie).
- Archivage sans perte de justificatifs, suppression définitive distincte.
- Export ZIP (arborescence `Catégorie / Marque / Appareil / Documents`), CSV (`;` + BOM Excel FR), PDF d'inventaire.
- Sauvegarde chiffrée AES-GCM (PBKDF2 120 000 itérations) et restauration sur une installation propre.
- Verrouillage par code, mode sombre, journal technique.
- Mise à jour automatique depuis les releases GitHub (signature stable, APK téléchargé puis installé avec confirmation Android).

## Nouveautés v1.1

- **Base chiffrée au repos** (SQLCipher, phrase secrète protégée par le Keystore Android) et **justificatifs chiffrés en AES-GCM**. Les données d'une version antérieure sont converties automatiquement au premier lancement, sans perte.
- **Restauration qui fonctionne** : la sauvegarde embarquée se restaure sur une installation propre (testé de bout en bout), l'application se relance d'elle-même et retrouve fiches, garanties et justificatifs.
- **Exports enregistrés où vous voulez** : sélecteur de fichier Android (Téléchargements, Drive, clé USB), plus seulement la feuille de partage. Le ZIP contient désormais `garanties.csv` et `documents.csv`, et les pièces gardent leur extension.
- **Photo principale** affichée sur la fiche et en vignette dans la liste.
- **Filtres complets** : année d'achat, emplacement, propriétaire, montant minimum, en plus de l'état et de la catégorie.
- **Une fiche par article** en un geste quand la facture contient plusieurs produits.
- **Déverrouillage biométrique** quand le téléphone le propose, avec code de secours et limitation à 5 essais puis pause de 30 s.
- **« À faire » assaini** : une garantie expirée depuis plus de 30 jours n'y reste plus indéfiniment.
- Correction d'un plantage de la fiche (clés de liste dupliquées) et noms proposés mis en forme (« Lave-linge Bosch WAN28280FF » au lieu de capitales brutes).

## Confidentialité

Tout reste dans le stockage privé de l'application, sur le téléphone. L'OCR est embarqué (ML Kit) : aucune facture n'est envoyée à un service externe. Aucun compte, aucune publicité, aucune mesure d'audience. La recherche de mise à jour interroge GitHub sans transmettre de donnée personnelle.

## Installation

APK signé, mise à jour automatique :

https://github.com/ClawFabriceH92/suivi-garanties/releases/download/latest/app-release.apk

## Technique

- Kotlin, Jetpack Compose, Material 3, minSdk 26 / targetSdk 35.
- Room + SQLCipher (base locale chiffrée), WorkManager (rappels quotidiens à 9 h, reprogrammés par le système après redémarrage).
- ML Kit Text Recognition v2 embarqué (latin).
- 88 tests unitaires sur la logique d'échéances, l'extraction de factures, les dates, le chiffrement, les exports et la mise à jour.

## Compiler

```bash
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
./gradlew testDebugUnitTest assembleRelease
```

La signature lit `GARANTIES_KEYSTORE_*` depuis l'environnement (CI) ou `/root/.secrets/keystores-android/garanties-keystore.env` (poste local).
