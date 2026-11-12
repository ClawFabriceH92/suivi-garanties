package com.trucdecomptable.garanties.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.trucdecomptable.garanties.util.Diag
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Un appareil, un bien ou un équipement acheté. Les documents et les garanties
 * vivent dans leurs propres tables : une facture peut couvrir plusieurs
 * appareils, et un appareil peut porter plusieurs garanties successives.
 */
@Entity(tableName = "appareils")
data class Appareil(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val categorie: String = Categories.DEFAUT.first(),
    val marque: String = "",
    val modele: String = "",
    val numeroSerie: String = "",
    val codeBarres: String = "",
    /** Date d'achat ISO (AAAA-MM-JJ), vide si inconnue. */
    val dateAchat: String = "",
    val prixAchat: Double? = null,
    val vendeur: String = "",
    val numeroFacture: String = "",
    val debutGarantie: String = "",
    /** Date de fin de la garantie principale ; toujours corrigeable à la main. */
    val finGarantie: String = "",
    /** Durée en mois retenue, pour recalculer la fin si la date d'achat bouge. */
    val dureeGarantieMois: Int? = null,
    val emplacement: String = "",
    val proprietaire: String = "",
    val notes: String = "",
    val statut: String = Statuts.ACTIF,
    /** Nom du fichier photo dans le dossier privé de l'application. */
    val photo: String = "",
    val archive: Boolean = false,
    val creeLe: Long = System.currentTimeMillis(),
    val modifieLe: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = Appareil::class,
            parentColumns = ["id"],
            childColumns = ["appareilId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("appareilId"), Index("factureGroupe")],
)
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appareilId: Long,
    val type: String = TypesDocument.FACTURE,
    val nom: String,
    /** Fichier dans filesDir/pieces ; plusieurs documents peuvent partager le même. */
    val chemin: String,
    val mime: String = "",
    /**
     * Identifiant de la facture d'origine : quand une même facture est répartie
     * entre plusieurs appareils, une ligne Document existe par appareil avec le
     * même factureGroupe et le même fichier sur disque.
     */
    val factureGroupe: String = "",
    val ajouteLe: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "garanties",
    foreignKeys = [
        ForeignKey(
            entity = Appareil::class,
            parentColumns = ["id"],
            childColumns = ["appareilId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("appareilId")],
)
data class Garantie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appareilId: Long,
    val type: String = TypesGarantie.CONSTRUCTEUR,
    val organisme: String = "",
    val reference: String = "",
    val debut: String = "",
    val fin: String = "",
    val contact: String = "",
    val conditions: String = "",
    val notes: String = "",
    val documentId: Long? = null,
)

@Entity(
    tableName = "journal",
    foreignKeys = [
        ForeignKey(
            entity = Appareil::class,
            parentColumns = ["id"],
            childColumns = ["appareilId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("appareilId")],
)
data class Evenement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appareilId: Long,
    val date: Long = System.currentTimeMillis(),
    val texte: String,
)

@Dao
interface CoffreDao {

    @Query("SELECT * FROM appareils ORDER BY nom COLLATE NOCASE")
    fun observerTous(): Flow<List<Appareil>>

    @Query("SELECT * FROM appareils WHERE archive = 0 ORDER BY nom COLLATE NOCASE")
    fun observerActifs(): Flow<List<Appareil>>

    @Query("SELECT * FROM appareils WHERE archive = 1 ORDER BY nom COLLATE NOCASE")
    fun observerArchives(): Flow<List<Appareil>>

    @Query("SELECT * FROM appareils WHERE id = :id")
    fun observerAppareil(id: Long): Flow<Appareil?>

    @Query("SELECT * FROM appareils WHERE id = :id")
    suspend fun appareil(id: Long): Appareil?

    @Query("SELECT * FROM appareils")
    suspend fun tousLesAppareils(): List<Appareil>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserer(appareil: Appareil): Long

    @Update
    suspend fun modifier(appareil: Appareil)

    @Delete
    suspend fun supprimer(appareil: Appareil)

    @Query("DELETE FROM appareils WHERE id = :id")
    suspend fun supprimerParId(id: Long)

    @Query("SELECT * FROM documents WHERE appareilId = :appareilId ORDER BY ajouteLe DESC")
    fun observerDocuments(appareilId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY ajouteLe DESC")
    fun observerTousDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents")
    suspend fun tousLesDocuments(): List<Document>

    @Query("SELECT COUNT(*) FROM documents WHERE appareilId = :appareilId AND type = :type")
    suspend fun compterDocuments(appareilId: Long, type: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insererDocument(document: Document): Long

    @Query("UPDATE documents SET nom = :nom WHERE id = :id")
    suspend fun renommerDocument(id: Long, nom: String)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun supprimerDocument(id: Long)

    @Query("SELECT COUNT(*) FROM documents WHERE chemin = :chemin")
    suspend fun compterFichier(chemin: String): Int

    @Query("SELECT * FROM garanties WHERE appareilId = :appareilId ORDER BY fin")
    fun observerGaranties(appareilId: Long): Flow<List<Garantie>>

    @Query("SELECT * FROM garanties ORDER BY fin")
    fun observerToutesGaranties(): Flow<List<Garantie>>

    @Query("SELECT * FROM garanties")
    suspend fun toutesLesGaranties(): List<Garantie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insererGarantie(garantie: Garantie): Long

    @Query("DELETE FROM garanties WHERE id = :id")
    suspend fun supprimerGarantie(id: Long)

    @Query("SELECT * FROM journal WHERE appareilId = :appareilId ORDER BY date DESC")
    fun observerJournal(appareilId: Long): Flow<List<Evenement>>

    @Query("SELECT * FROM journal ORDER BY date DESC")
    suspend fun toutLeJournal(): List<Evenement>

    @Insert
    suspend fun journaliser(evenement: Evenement): Long

    @Query("DELETE FROM journal WHERE appareilId = :appareilId")
    suspend fun purgerJournal(appareilId: Long)
}

@Database(
    entities = [Appareil::class, Document::class, Garantie::class, Evenement::class],
    version = 1,
    exportSchema = false,
)
abstract class CoffreDb : RoomDatabase() {

    abstract fun dao(): CoffreDao

    companion object {
        const val FICHIER = "garanties.db"

        @Volatile
        private var instance: CoffreDb? = null

        /**
         * Base chiffrée (SQLCipher) : le fichier .db est illisible sans la phrase
         * secrète, elle-même protégée par le Keystore Android. Une installation
         * antérieure non chiffrée est convertie une seule fois, sans perte.
         */
        fun obtenir(context: Context): CoffreDb =
            instance ?: synchronized(this) {
                instance ?: construire(context.applicationContext).also { instance = it }
            }

        private fun construire(context: Context): CoffreDb {
            System.loadLibrary("sqlcipher")
            val fichier = context.getDatabasePath(FICHIER)
            val anciennes = if (estEnClair(fichier)) extraireEnClair(context) else null
            if (anciennes != null) {
                // Le fichier en clair est supprimé AVANT d'ouvrir la base chiffrée :
                // SQLite ne peut pas ouvrir un fichier non chiffré avec une clé.
                supprimerFichiers(fichier)
            }
            val chiffree = Room.databaseBuilder(context, CoffreDb::class.java, FICHIER)
                .openHelperFactory(
                    net.zetetic.database.sqlcipher.SupportOpenHelperFactory(
                        com.trucdecomptable.garanties.securite.CoffreSecret.phraseBase(context),
                    ),
                )
                .build()
            if (anciennes != null) {
                kotlinx.coroutines.runBlocking { reimporter(chiffree, anciennes) }
                Diag.record(
                    "base",
                    "convertie en chiffré : ${anciennes.appareils.size} appareil(s), " +
                        "${anciennes.documents.size} document(s) conservés",
                )
            }
            return chiffree
        }

        private data class Contenu(
            val appareils: List<Appareil>,
            val documents: List<Document>,
            val garanties: List<Garantie>,
            val journal: List<Evenement>,
        )

        /** Un fichier SQLite non chiffré commence par « SQLite format 3 ». */
        internal fun estEnClair(fichier: java.io.File): Boolean {
            if (!fichier.exists() || fichier.length() < 16) return false
            val entete = ByteArray(16)
            runCatching { fichier.inputStream().use { it.read(entete) } }
            return String(entete, Charsets.ISO_8859_1).startsWith("SQLite format 3")
        }

        private fun supprimerFichiers(fichier: java.io.File) {
            java.io.File("${fichier.path}-wal").delete()
            java.io.File("${fichier.path}-shm").delete()
            fichier.delete()
        }

        private fun extraireEnClair(context: Context): Contenu? = runCatching {
            val clair = Room.databaseBuilder(context, CoffreDb::class.java, FICHIER).build()
            val contenu = kotlinx.coroutines.runBlocking {
                Contenu(
                    appareils = clair.dao().tousLesAppareils(),
                    documents = clair.dao().tousLesDocuments(),
                    garanties = clair.dao().toutesLesGaranties(),
                    journal = clair.dao().toutLeJournal(),
                )
            }
            clair.close()
            contenu
        }.getOrElse {
            Diag.record("base", "conversion impossible : ${it.message}")
            null
        }

        private suspend fun reimporter(db: CoffreDb, contenu: Contenu) {
            val dao = db.dao()
            contenu.appareils.forEach { dao.inserer(it) }
            contenu.garanties.forEach { dao.insererGarantie(it) }
            contenu.documents.forEach { dao.insererDocument(it) }
            contenu.journal.forEach { dao.journaliser(it) }
        }

        /** Nécessaire avant de remplacer le fichier de base lors d'une restauration. */
        fun fermer() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }

        /** Après une restauration : la nouvelle base doit être rouverte. */
        fun rouvrir(context: Context): CoffreDb {
            fermer()
            return obtenir(context)
        }
    }
}
