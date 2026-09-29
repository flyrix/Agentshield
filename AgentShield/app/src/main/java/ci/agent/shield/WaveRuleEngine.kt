package ci.agent.shield

enum class Risk { LOW, HIGH, CRITICAL }
data class WaveEvent(val text: String, val risk: Risk, val reason: String)

object Config {
    /** Vérifie sur ton téléphone : adb shell pm list packages | grep -i wave */
    val WAVE_PACKAGES = setOf("com.wave.personal")
    val GMAIL_PACKAGES = setOf("com.google.android.gm")
}

/** Règles déterministes (niveau 1). Ajuste les regex avec de vraies notifications Wave. */
object WaveRuleEngine {
    private val security = Regex("(code pin|mot de passe|nouvel appareil|nouvelle connexion|connexion|session)", RegexOption.IGNORE_CASE)
    private val outgoing = Regex("(retrait|retir[ée]|envoy[ée]|transf[eé]r|paiement|pay[ée]|d[ée]bit)", RegexOption.IGNORE_CASE)
    private val incoming = Regex("(re[çc]u|dépôt|depot|cr[ée]dit)", RegexOption.IGNORE_CASE)

    fun analyze(text: String): WaveEvent = when {
        security.containsMatchIn(text) -> WaveEvent(text, Risk.CRITICAL, "Événement de sécurité du compte")
        outgoing.containsMatchIn(text) && !incoming.containsMatchIn(text) ->
            WaveEvent(text, Risk.HIGH, "Sortie d'argent détectée")
        else -> WaveEvent(text, Risk.LOW, "Information")
    }
}
