package ci.agent.shield

data class ParsedScreen(val amount: String?, val name: String?, val phone: String?, val isConfirm: Boolean)

/** Extrait montant / destinataire d'UN écran Wave (« Send Money »). */
object TransferParser {
    private val I = RegexOption.IGNORE_CASE
    private const val CUR = "(?:\\s?(?:F\\s?CFA|FCFA|CFA|F))?"
    private val labelRx = Regex("(?:send amount|montant(?: à envoyer)?)", I)
    private val labeledRx = Regex("(?:send amount|montant(?: à envoyer)?)\\s*[=:]\\s*([\\d\\s.,\\u00a0\\u202f]+)$CUR", I)
    private val numRx = Regex("([\\d\\s.,\\u00a0\\u202f]+)$CUR", I)
    private val amountRx = Regex("(\\d[\\d\\s.,\\u00a0\\u202f]*)\\s?(?:F\\s?CFA|FCFA|CFA|F)(?![A-Za-z])", I)
    private val skipRx = Regex("(fee|frais|balance|solde|receive|re[cç]evoir)", I)
    private val uiRx = Regex("(enter|scan|new number|search|contact|send|amount|saisir|scanner|nouveau)", I)
    private val phoneRx = Regex("(?<!\\d)(?:\\+?225[\\s.-]?)?(0\\d(?:[\\s.-]?\\d{2}){4})(?!\\d)")
    private val confirmRx = Regex("(send|confirm|envoyer|confirmer|valider|pay|payer|transf[eé]rer)", I)
    private val toRx = Regex("(?:to|à|pour|destinataire|recipient)\\s*:?\\s*(\\S.{0,38})?", I)

    private fun digits(s: String): String? = s.filter(Char::isDigit).trimStart('0').ifEmpty { null }

    fun parse(t: List<String>): ParsedScreen {
        var amount: String? = null; var name: String? = null; var phone: String? = null
        var confirm = false

        // 1) Montant : champ « Send Amount »
        for ((i, raw) in t.withIndex()) {
            val x = raw.trim()
            val m = labeledRx.matchEntire(x)
            if (m != null) { amount = digits(m.groupValues[1]); break }
            if (labelRx.matches(x)) {
                val m2 = t.getOrNull(i + 1)?.trim()?.let { numRx.matchEntire(it) }
                if (m2 != null) { amount = digits(m2.groupValues[1]); break }
            }
        }
        // 2) Repli : montant suivi de F (hors frais / solde / montant reçu)
        if (amount == null) {
            for ((i, raw) in t.withIndex()) {
                val x = raw.trim()
                if (skipRx.containsMatchIn(x) || (i > 0 && skipRx.containsMatchIn(t[i - 1]))) continue
                val m = amountRx.find(x) ?: continue
                amount = digits(m.groupValues[1])
                if (amount != null) break
            }
        }
        // 3) Destinataire, bouton
        for ((i, raw) in t.withIndex()) {
            val x = raw.trim()
            if (confirmRx.matches(x)) confirm = true
            if (phone == null) phoneRx.find(x)?.let { phone = it.groupValues[1].filter(Char::isDigit) }
            val m = toRx.matchEntire(x) ?: continue
            val v = m.groupValues[1].trim()
            val cand = if (v.isNotEmpty()) v else t.getOrNull(i + 1)?.trim().orEmpty()
            if (cand.isEmpty() || cand.length > 40 || uiRx.containsMatchIn(cand) || skipRx.containsMatchIn(cand)) continue
            if (!(cand[0].isUpperCase() || cand[0].isDigit() || cand[0] == '+')) continue
            phoneRx.find(cand)?.let { phone = it.groupValues[1].filter(Char::isDigit) }
            // nom lisible à la voix : lettres, chiffres, espaces (retire les emojis)
            val n = cand.replace(phoneRx, "").filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '\'' }.trim()
            if (n.isNotEmpty() && n != cand.filter(Char::isDigit)) name = n
        }
        return ParsedScreen(amount, name, phone, confirm)
    }

    fun message(amount: String, name: String?, phone: String?): String {
        val who = when {
            name != null && phone != null -> "$name, numéro se terminant par ${phone.takeLast(4)}"
            name != null -> name
            phone != null -> "le numéro se terminant par ${phone.takeLast(4)}"
            else -> "un destinataire"
        }
        return "Attention. Vous allez transférer $amount francs à $who. Veuillez vous assurer que c'est bien le montant souhaité."
    }
}