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
        if (pkg !in Config.WAVE_PACKAGES && pkg !in Config.GMAIL_PACKAGES) return
        val x = sbn.notification.extras
        val text = listOfNotNull(x.getCharSequence(Notification.EXTRA_TITLE), x.getCharSequence(Notification.EXTRA_TEXT))
            .joinToString(" ").trim()
        if (text.isEmpty() || isDuplicate(sbn.key, text)) return

        if (pkg in Config.WAVE_PACKAGES) {
            val e = WaveRuleEngine.analyze(text)
            scope.launch { AppDb.get(this@AgentNotificationService).dao()
                .insert(EventEntity(ts = System.currentTimeMillis(), source = "Wave", text = text, risk = e.risk.name)) }
            if (e.risk >= Risk.HIGH) AlertManager.fire(this, e)
        }
        // Phase 2 : Gmail -> classification + lecture TTS
    }

    private fun isDuplicate(key: String, text: String): Boolean {
        val k = key + text.hashCode(); val now = System.currentTimeMillis()
        seen.entries.removeIf { now - it.value > 30_000 }
        return seen.put(k, now) != null
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
