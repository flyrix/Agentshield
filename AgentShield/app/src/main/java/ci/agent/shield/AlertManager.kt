package ci.agent.shield

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

object AlertManager {
    private const val CH = "alerts"

    fun fire(c: Context, e: WaveEvent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Alertes sécurité", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(c, CH).setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⚠ ${e.reason}").setContentText(e.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(e.text))
                .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM).build())
        siren(c)
    }

    /** Sirène ~10 s sur le flux ALARME, volume au maximum. */
    private fun siren(c: Context) {
        val am = c.getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        r.play()
        Handler(Looper.getMainLooper()).postDelayed({ r.stop() }, 10_000)
    }
}
