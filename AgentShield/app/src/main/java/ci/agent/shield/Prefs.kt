package ci.agent.shield

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("agent", Context.MODE_PRIVATE)
    fun ttsEnabled(c: Context) = sp(c).getBoolean("tts", true)
    fun setTts(c: Context, on: Boolean) = sp(c).edit().putBoolean("tts", on).apply()
    fun vipRaw(c: Context) = sp(c).getString("vip", "") ?: ""
    fun setVipRaw(c: Context, s: String) = sp(c).edit().putString("vip", s).apply()
    fun vip(c: Context): Set<String> =
        vipRaw(c).split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}