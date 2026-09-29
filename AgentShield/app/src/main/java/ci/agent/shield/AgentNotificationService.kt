package ci.agent.shield

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*

class AgentNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seen = LinkedHashMap<String, Long>()   // anti-doublons

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in Prefs.monitored(this)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val x = sbn.notification.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = "$title $body".trim()
        if (text.isEmpty() || isDuplicate(sbn.key, text)) return

        when (pkg) {
            in Config.WAVE_PACKAGES -> {
                val e = WaveRuleEngine.analyze(text)
                log("Wave", text, e.risk.name)
                if (e.risk >= Risk.HIGH) AlertManager.fire(this, e)
            }
            in Config.GMAIL_PACKAGES -> {
                val v = MailClassifier.classify(title, body, Prefs.vip(this))
                if (v.urgency == Urgency.IMPORTANT) {
                    log("Gmail", "$title — $body", "IMPORTANT")
                    if (Prefs.ttsEnabled(this)) SpeechManager.speak(this, "Mail important de $title. $body")
                }
            }
            else -> {   // autre application choisie : traitée comme un message
                val v = MailClassifier.classify(title, body, Prefs.vip(this))
                if (v.urgency == Urgency.IMPORTANT) {
                    val app = label(pkg)
                    log(app, "$title — $body", "IMPORTANT")
                    if (Prefs.ttsEnabled(this)) SpeechManager.speak(this, "Message important de $title dans $app. $body")
                }
            }
        }
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun log(source: String, text: String, risk: String) {
        scope.launch {
            AppDb.get(this@AgentNotificationService).dao()
                .insert(EventEntity(ts = System.currentTimeMillis(), source = source, text = text, risk = risk))
        }
    }

    private fun isDuplicate(key: String, text: String): Boolean {
        val k = key + text.hashCode(); val now = System.currentTimeMillis()
        seen.entries.removeIf { now - it.value > 30_000 }
        return seen.put(k, now) != null
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}