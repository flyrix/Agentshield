package ci.agent.shield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
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

        if (pkg in Config.WAVE_PACKAGES) {          // Wave : règles uniquement, jamais le modèle
            val e = WaveRuleEngine.analyze(text)
            log("Wave", text, e.risk.name)
            if (e.risk >= Risk.HIGH) AlertManager.fire(this, e)
        } else {
            scope.launch { handleMessage(pkg, title, body) }
        }
    }

    private suspend fun handleMessage(pkg: String, title: String, body: String) {
        val isMail = pkg in Config.GMAIL_PACKAGES
        val source = if (isMail) "Gmail" else label(pkg)
        val v = MailClassifier.classify(title, body, Prefs.vip(this))
        if (v.urgency == Urgency.IGNORE) return
        var important = v.urgency == Urgency.IMPORTANT
        if (LocalLlm.enabled(this)) {
            when (LocalLlm.judge(this, title, body)) {
                LocalLlm.Label.ARNAQUE -> { scamAlert(source, title, body); return }
                LocalLlm.Label.IMPORTANT -> important = true
                else -> {}
            }
        }
        if (!important) return
        log(source, "$title — $body", "IMPORTANT")
        if (Prefs.ttsEnabled(this)) withContext(Dispatchers.Main) {
            SpeechManager.speak(this@AgentNotificationService,
                if (isMail) "Mail important de $title. $body" else "Message important de $title dans $source. $body")
        }
    }

    private suspend fun scamAlert(source: String, title: String, body: String) {
        log(source, "$title — $body", "ARNAQUE?")
        val msg = "Message suspect de $title : possible arnaque. Ne communiquez aucun code et ne cliquez sur aucun lien."
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("scam", "Messages suspects", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(this, "scam")
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("⚠ Message suspect ($source)")
            .setContentText(msg).setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$body\n\n$msg"))
            .setPriority(NotificationCompat.PRIORITY_MAX).build())
        if (Prefs.ttsEnabled(this)) withContext(Dispatchers.Main) {
            SpeechManager.speak(this@AgentNotificationService, msg)
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