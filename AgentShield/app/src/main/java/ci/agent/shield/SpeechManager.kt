package ci.agent.shield

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

object SpeechManager {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    fun speak(c: Context, text: String) {
        val t = text.take(250)
        if (tts == null) {
            tts = TextToSpeech(c.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val e = tts!!
                    if (e.setLanguage(Locale.FRENCH) < 0) e.language = Locale.getDefault()
                    e.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT).build())
                    ready = true
                    while (pending.isNotEmpty()) say(pending.removeFirst())
                }
            }
        }
        if (ready) say(t) else pending.addLast(t)
    }

    private fun say(t: String) {
        tts?.speak(t, TextToSpeech.QUEUE_ADD, null, System.nanoTime().toString())
    }
}