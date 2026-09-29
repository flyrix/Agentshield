package ci.agent.shield

enum class Risk { LOW, HIGH, CRITICAL }
data class WaveEvent(val text: String, val risk: Risk, val reason: String)

object Config {
    val WAVE_PACKAGES = setOf("com.wave.personal")
    val GMAIL_PACKAGES = setOf("com.google.android.gm")
}

/** Règles bilingues FR/EN (les notifications Wave réelles sont en anglais). */
object WaveRuleEngine {
    private val I = RegexOption.IGNORE_CASE

    private val security = Regex(
        "(code pin|\\bpin\\b|mot de passe|password|nouvel appareil|new device|" +
        "nouvelle connexion|new login|\\b(log ?in|sign ?in)\\b|connexion|session)", I)

    // Sortie d'argent explicite
    private val outgoingStrong = Regex(
        "(you sent|you paid|you withdrew|withdrawal|vous avez (envoy|pay|retir)|retrait)", I)
    // Sortie probable (mots plus faibles)
    private val outgoing = Regex(
        "(withdraw|cash ?out|transfer successful|payment|paiement|envoy[ée]|transf[eé]r|pay[ée]|d[ée]bit)", I)
    private val incoming = Regex(
        "(you (have )?received|received|money received|re[çc]u|d[ée]p[ôo]t|deposit|cr[ée]dit|cash ?in)", I)

    fun analyze(text: String): WaveEvent = when {
        security.containsMatchIn(text) -> WaveEvent(text, Risk.CRITICAL, "Événement de sécurité du compte")
        incoming.containsMatchIn(text) && !outgoingStrong.containsMatchIn(text) ->
            WaveEvent(text, Risk.LOW, "Argent reçu")
        outgoingStrong.containsMatchIn(text) || outgoing.containsMatchIn(text) ->
            WaveEvent(text, Risk.HIGH, "Sortie d'argent détectée")
        else -> WaveEvent(text, Risk.LOW, "Information")
    }
}