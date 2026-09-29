package ci.agent.shield

enum class Urgency { IGNORE, NORMAL, IMPORTANT }
data class MailVerdict(val urgency: Urgency, val reason: String)

/**
 * Niveau 1 : règles. Interface volontairement simple pour brancher plus tard
 * un LLM (local ou cloud) sur les cas ambigus (Urgency.NORMAL).
 */
object MailClassifier {
    private val promo = Regex(
        "(promo|solde|offre|newsletter|d[ée]sabonn|unsubscribe|r[ée]duction|gagnez|bon plan|-\\d+ ?%)",
        RegexOption.IGNORE_CASE)
    private val urgent = Regex(
        "(urgent|important|imm[ée]diat|dernier d[ée]lai|derni[eè]re? (rappel|relance)|convocation|examen|" +
        "soutenance|entretien|facture|paiement|[ée]ch[ée]ance|retard|contrat|rendez-vous|r[ée]union|deadline|" +
        "mot de passe|s[ée]curit[ée]|connexion suspecte)", RegexOption.IGNORE_CASE)

    fun classify(sender: String, subject: String, vip: Set<String>): MailVerdict {
        val s = sender.lowercase()
        if (vip.any { s.contains(it) }) return MailVerdict(Urgency.IMPORTANT, "Expéditeur prioritaire")
        val all = "$sender $subject"
        if (promo.containsMatchIn(all)) return MailVerdict(Urgency.IGNORE, "Promotion")
        if (urgent.containsMatchIn(all)) return MailVerdict(Urgency.IMPORTANT, "Mot-clé d'urgence")
        return MailVerdict(Urgency.NORMAL, "Standard")
    }
}