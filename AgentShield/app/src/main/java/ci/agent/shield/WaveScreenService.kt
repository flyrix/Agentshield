package ci.agent.shield

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class WaveScreenService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingKey: String? = null
    private var pending: Runnable? = null
    private var lastWarnKey = ""
    private var lastWarnTs = 0L
    private var lastDiag = 0

    override fun onAccessibilityEvent(ev: AccessibilityEvent?) {
        val pkg = ev?.packageName?.toString() ?: return
        if (pkg !in Config.WAVE_PACKAGES) return
        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>()
        collect(root, texts)
        // Clavier PIN interne (touches 0-9) : on ignore totalement l'écran.
        if (texts.isEmpty() || texts.count { it.length == 1 && it[0].isDigit() } >= 8) return
        if (Prefs.diag(this)) diag(texts)

        // Uniquement l'écran courant : jamais d'infos d'une transaction précédente.
        val s = TransferParser.parse(texts)
        val a = s.amount
        if (s.isConfirm && a != null && (s.name != null || s.phone != null)) schedule(a, s.name, s.phone)
    }

    /** Attend 1 s de stabilité avant d'avertir (évite de parler à chaque chiffre tapé). */
    private fun schedule(a: String, n: String?, p: String?) {
        val key = "$a|$n|$p"
        if (key == pendingKey) return
        pending?.let { h.removeCallbacks(it) }
        pendingKey = key
        val r = Runnable {
            pendingKey = null
            val t = System.currentTimeMillis()
            if (key != lastWarnKey || t - lastWarnTs > 90_000) {
                lastWarnKey = key; lastWarnTs = t
                warn(TransferParser.message(a, n, p))
            }
        }
        pending = r
        h.postDelayed(r, 1000)
    }

    private fun warn(msg: String) {
        SpeechManager.speak(this, msg)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("confirm", "Vérification des transferts", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(7001, NotificationCompat.Builder(this, "confirm")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Vérifiez le transfert").setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_MAX).build())
    }

    /** Champs éditables : « libellé = valeur » quand Wave expose le libellé (hint) du champ. */
    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int = 0) {
        if (n == null || depth > 25) return
        if (!n.isPassword) {
            val t = n.text?.toString()
            val hint = n.hintText?.toString()
            if (!t.isNullOrBlank()) {
                if (n.isEditable && !hint.isNullOrBlank() && !t.equals(hint, true)) out.add("$hint = $t") else out.add(t)
            } else n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    private fun diag(texts: List<String>) {
        val line = texts.joinToString(" | ").take(600)
        if (line.hashCode() == lastDiag) return
        lastDiag = line.hashCode()
        scope.launch {
            AppDb.get(this@WaveScreenService).dao().insert(
                EventEntity(ts = System.currentTimeMillis(), source = "Écran Wave", text = line, risk = "DIAG"))
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}