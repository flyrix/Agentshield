package ci.agent.shield

data class ParsedScreen(val amount: String?, val name: String?, val phone: String?, val isConfirm: Boolean)

/** Analyse UNIQUEMENT l'écran « Send Money » de Wave. Ailleurs : rien n'est détecté. */
object TransferParser {
    private val I = RegexOption.IGNORE_CASE
    private const val CUR = "(?:\\s?(?:F\\s?CFA|FCFA|CFA|F))?"
    private const val NUM = "(\\d[\\d\\s.,\\u00a0\\u202f]*)"
    private val sendTitleRx = Regex("(send money|envoyer de l'argent|envoyer l'argent)", I)
    private val labelRx = Regex("(?:send amount|montant(?: à envoyer)?)", I)
    private val receiveRx = Regex("(?:receive amount|montant re[cç]u)", I)
    private val labeledRx = Regex("(?:send amount|montant(?: à envoyer)?)\\s*[=:]\\s*$NUM$CUR", I)
    private val numRx = Regex("$NUM$CUR", I)
    private val skipRx = Regex("(fee|frais|balance|solde|receive|re[cç]evoir)", I)
    private val uiRx = Regex("(enter|scan|new number|search|contact|send|amount|saisir|scanner|nouveau)", I)
    private val phoneRx = Regex("(?<!\\d)(?:\\+?225[\\s.-]?)?(0\\d(?:[\\s.-]?\\d{2}){4})(?!\\d)")
    private val toRx = Regex("(?:to|à|pour|destinataire|recipient)\\s*[:=]?\\s*(\\S.{0,58})?", I)

    private fun digits(s: String): String? = s.filter(Char::isDigit).trimStart('0').ifEmpty { null }

    fun parse(t: List<String>): ParsedScreen {
        val send = t.any { sendTitleRx.matches(it.trim()) } ||
            (t.any { labelRx.containsMatchIn(it) } && t.any { receiveRx.containsMatchIn(it) })
        if (!send) return ParsedScreen(null, null, null, false)

        var amount: String? = null; var name: String? = null; var phone: String? = null
        for ((i, raw) in t.withIndex()) {
            val x = raw.trim()
            labeledRx.matchEntire(x)?.let { amount = digits(it.groupValues[1]) ?: amount }
            if (amount == null && labelRx.matches(x)) {
                t.getOrNull(i + 1)?.trim()?.let { numRx.matchEntire(it) }
                    ?.let { amount = digits(it.groupValues[1]) }
            }
            if (phone == null) phoneRx.find(x)?.let { phone = it.groupValues[1].filter(Char::isDigit) }
            val m = toRx.matchEntire(x) ?: continue
            val v = m.groupValues[1].trim()
            val cand = if (v.isNotEmpty()) v else t.getOrNull(i + 1)?.trim().orEmpty()
            if (cand.isEmpty() || cand.length > 60 || uiRx.containsMatchIn(cand) || skipRx.containsMatchIn(cand)) continue
            if (!(cand[0].isUpperCase() || cand[0].isDigit() || cand[0] == '+')) continue
            phoneRx.find(cand)?.let { phone = it.groupValues[1].filter(Char::isDigit) }
            val n = cand.replace(phoneRx, "")
                .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '\'' }.trim()
            if (n.isNotEmpty() && n != cand.filter(Char::isDigit)) name = n   // sans emojis
        }
        return ParsedScreen(amount, name, phone, true)
    }

    private fun spell(p: String) = p.toCharArray().joinToString(" ")   // 0 7 0 2 ...

    fun message(amount: String, name: String?, phone: String?): String {
        val who = when {
            name != null && phone != null -> "$name, numéro ${spell(phone)}"
            name != null -> name
            phone != null -> "le numéro ${spell(phone)}"
            else -> "un destinataire"
        }
        return "Attention. Vous allez transférer $amount francs à $who. Veuillez vous assurer que c'est bien le montant souhaité."
    }
}