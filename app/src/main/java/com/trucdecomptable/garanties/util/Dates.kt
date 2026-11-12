package com.trucdecomptable.garanties.util

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

/**
 * Les dates sont stockées en ISO (AAAA-MM-JJ) : triables en SQL, lisibles dans
 * un export CSV, et convertibles sans dépendre de la locale du téléphone.
 */
object Dates {

    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val FR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
    private val FR_LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

    fun parse(texte: String?): LocalDate? {
        if (texte.isNullOrBlank()) return null
        return try {
            LocalDate.parse(texte.trim(), ISO)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun iso(date: LocalDate?): String = date?.format(ISO) ?: ""

    fun aujourdhui(): LocalDate = LocalDate.now()

    fun afficher(texte: String?): String = parse(texte)?.format(FR) ?: ""

    fun afficherLong(texte: String?): String = parse(texte)?.format(FR_LONG) ?: ""

    /**
     * Lit 12/03/2026, 12-03-2026, 2026-03-12, 12/03/26 (année à 2 chiffres : 20xx)
     * et tolère l'espace que l'OCR insère dans les nombres (« 15/03/2 026 »).
     */
    fun lireSouple(texte: String?): LocalDate? {
        parse(texte)?.let { return it }
        val t = texte?.trim().orEmpty()
        if (t.isBlank()) return null
        val m = Regex("""(\d{1,2})\s?[/.\-]\s?(\d{1,2})\s?[/.\-]\s?(\d(?:\s?\d){1,3})""").find(t)
            ?: return null
        val (j, mo, a) = m.destructured
        val annee = a.replace(" ", "").filter { it.isDigit() }.toIntOrNull() ?: return null
        return runCatching {
            LocalDate.of(if (annee < 100) 2000 + annee else annee, mo.toInt(), j.toInt())
        }.getOrNull()
    }

    /**
     * Fin de garantie à partir du début et d'une durée en mois. Le dernier jour
     * du mois est retenu quand le mois cible est plus court (31/01 + 1 mois →
     * 28/02, et non 03/03).
     */
    fun ajouterMois(debut: LocalDate, mois: Int): LocalDate =
        YearMonth.from(debut).plusMonths(mois.toLong())
            .atDay(minOf(debut.dayOfMonth, YearMonth.from(debut).plusMonths(mois.toLong()).lengthOfMonth()))

    fun joursRestants(fin: LocalDate?, aujourdhui: LocalDate = aujourdhui()): Int? =
        fin?.let { java.time.temporal.ChronoUnit.DAYS.between(aujourdhui, it).toInt() }

    fun horodatage(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(millis))

    fun horodatageCourt(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(millis))

    fun dateFichier(): String =
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(Date())
}
